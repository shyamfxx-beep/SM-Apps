// Package: com.sm.core.config
// Runtime configuration for Supabase backend and backend storage bucket
package com.sm.core.config

/**
 * Global configuration constants.
 * Yahan par Supabase ke URL aur anon key set kiye jaate hain.
 * User se run-time par ya build configuration se inject ho sakte hain.
 */
object AppConfig {
    // Apne Supabase project settings -> API se project URL yahan dalein
    var SUPABASE_URL: String = "https://your-project-ref.supabase.co"

    // Apne Supabase project settings -> API se anon public key yahan dalein
    var SUPABASE_ANON_KEY: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.your-anon-key-placeholder"

    // Storage bucket name jismein audio recordings upload hongi
    const val STORAGE_BUCKET_RECORDINGS = "recordings"

    // 60 seconds audio chunk size
    const val AUDIO_CHUNK_DURATION_SECONDS = 60

    // Maximum cache cap on device: 200MB (bytes)
    const val LOCAL_AUDIO_CACHE_CAP_BYTES: Long = 200 * 1024 * 1024

    // AAC Audio encoding settings
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_BIT_RATE = 32000 // 32 kbps mono
    const val AUDIO_CHANNEL_COUNT = 1

    /**
     * Agar user runtime par custom credentials provide kare toh update kar sakein
     */
    fun updateCredentials(url: String, anonKey: String) {
        if (url.isNotBlank() && anonKey.isNotBlank()) {
            SUPABASE_URL = url.trim().removeSuffix("/")
            SUPABASE_ANON_KEY = anonKey.trim()
        }
    }
}
