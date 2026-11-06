package com.ziaee.zabankhan.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private lateinit var secretContext: Context
fun initializeSecretStore(context: Context) { secretContext = context.applicationContext }
actual fun createSecretStore(): SecretStore = AndroidSecretStore(secretContext)

private const val SECRET_PREFS = "zabankhan_secrets"
private const val KEY_ALIAS = "com.ziaee.zabankhan.secrets.v1"

private class AndroidSecretStore(context: Context) : SecretStore {
    // A dedicated no-backup preferences file: excluded from both cloud backup and
    // device transfer by backup_rules.xml and data_extraction_rules.xml.
    private val prefs = context.getSharedPreferences(SECRET_PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    @Synchronized override fun get(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        val parts = encoded.split(':')
        check(parts.size == 2) { "Invalid secret record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    @Synchronized override fun set(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val record = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        check(prefs.edit().putString(name, record).commit()) { "Cannot save secret" }
    }

    @Synchronized override fun remove(name: String) {
        check(prefs.edit().remove(name).commit()) { "Cannot remove secret" }
    }
}
