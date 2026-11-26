// Package: com.sm.core.supabase
// Singleton Supabase client provider with Postgrest, Storage, Realtime and Auth
package com.sm.core.supabase

import com.sm.core.config.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage

/**
 * Supabase client instance provider.
 * Thread-safe singleton jo agent aur owner dono apps ke liye client manage karta hai.
 */
object SupabaseClientProvider {

    @Volatile
    private var clientInstance: SupabaseClient? = null

    /**
     * Client initialization logic.
     * Agar client already bana hua hai toh wahi return karega, warna naya create karega.
     */
    fun getClient(): SupabaseClient {
        return clientInstance ?: synchronized(this) {
            clientInstance ?: buildClient().also { clientInstance = it }
        }
    }

    /**
     * Fresh client build function.
     * Realtime, Postgrest, Storage, aur Auth plugins install karta hai.
     */
    private fun buildClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = AppConfig.SUPABASE_URL,
            supabaseKey = AppConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Auth)
        }
    }

    /**
     * Agar credentials change ho jayein toh client ko reset karne ke liye
     */
    fun resetClient(newUrl: String? = null, newKey: String? = null) {
        synchronized(this) {
            if (!newUrl.isNullOrBlank() && !newKey.isNullOrBlank()) {
                AppConfig.updateCredentials(newUrl, newKey)
            }
            clientInstance = null
        }
    }

    val postgrest: Postgrest get() = getClient().postgrest
    val storage: Storage get() = getClient().storage
    val realtime: Realtime get() = getClient().realtime
    val auth: Auth get() = getClient().auth
}
