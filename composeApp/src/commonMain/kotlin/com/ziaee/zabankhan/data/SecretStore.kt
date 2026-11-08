package com.ziaee.zabankhan.data

/** Device-bound credentials; implementations must throw on storage failures. */
interface SecretStore {
    fun get(name: String): String?
    fun set(name: String, value: String)
    fun remove(name: String)
}

expect fun createSecretStore(): SecretStore
internal const val LLM_API_KEY = "llm_api_key"
internal const val SYNC_DEVICE_ID = "drive_sync_device_id"
