package com.ziaee.zabankhan.data

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

actual fun createSecretStore(): SecretStore = KeychainSecretStore()

@OptIn(ExperimentalForeignApi::class)
private class KeychainSecretStore : SecretStore {
    private fun attribute(key: CFStringRef?): Any = requireNotNull(CFBridgingRelease(CFRetain(key)))
    private fun query(name: String): Map<Any, Any> = mapOf(
        attribute(kSecClass) to attribute(kSecClassGenericPassword),
        attribute(kSecAttrService) to "com.ziaee.zabankhan",
        attribute(kSecAttrAccount) to name,
    )

    private fun <T> dictionary(values: Map<Any, Any>, block: (CFDictionaryRef) -> T): T {
        val ref = requireNotNull(CFBridgingRetain(values))
        try { return block(ref.reinterpret()) } finally { CFRelease(ref) }
    }

    override fun get(name: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        result.value = null
        val status = dictionary(query(name)) { base ->
            val search = requireNotNull(CFDictionaryCreateMutableCopy(null, 0, base))
            try {
                // Security requires a CFBoolean here. Bridging it through a Kotlin Map
                // turns it into a CFNumber and makes SecItemCopyMatching return -50.
                CFDictionarySetValue(search, kSecReturnData, kCFBooleanTrue)
                SecItemCopyMatching(search, result.ptr)
            } finally { CFRelease(search) }
        }
        if (status == errSecItemNotFound) return@memScoped null
        check(status == errSecSuccess) { "Cannot read Keychain ($status)" }
        val data = CFBridgingRelease(result.value) as NSData
        if (data.length == 0uL) "" else requireNotNull(data.bytes).reinterpret<ByteVar>().readBytes(data.length.toInt()).decodeToString()
    }

    override fun set(name: String, value: String) {
        val bytes = value.encodeToByteArray()
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val attributes = mapOf(
            attribute(kSecValueData) to data,
            attribute(kSecAttrAccessible) to attribute(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly),
        )
        val status = dictionary(query(name)) { query ->
            dictionary(attributes) { SecItemUpdate(query, it) }
        }
        if (status == errSecItemNotFound) {
            val added = dictionary(query(name) + attributes) { SecItemAdd(it, null) }
            check(added == errSecSuccess) { "Cannot save Keychain item ($added)" }
        } else check(status == errSecSuccess) { "Cannot update Keychain item ($status)" }
    }

    override fun remove(name: String) {
        val status = dictionary(query(name)) { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) { "Cannot remove Keychain item ($status)" }
    }
}
