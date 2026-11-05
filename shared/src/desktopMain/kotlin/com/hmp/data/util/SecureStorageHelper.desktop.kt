package com.hmp.data.util

import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual object SecureStorageHelper {
    private const val KEY_ALIAS = "hmp_secure_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEYSTORE_PASSWORD = "hmp_ks_pass"
    private const val KEY_PASSWORD = "hmp_key_pass"

    private val keyStoreFile by lazy {
        val appDir = File(System.getProperty("user.home"), ".hmp")
        if (!appDir.exists()) appDir.mkdirs()
        File(appDir, "keystore.p12")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("PKCS12")
        if (keyStoreFile.exists()) {
            keyStoreFile.inputStream().use { keyStore.load(it, KEYSTORE_PASSWORD.toCharArray()) }
        } else {
            keyStore.load(null, null)
        }

        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, KeyStore.PasswordProtection(KEY_PASSWORD.toCharArray())) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val key = keyGen.generateKey()
            keyStore.setEntry(
                KEY_ALIAS,
                KeyStore.SecretKeyEntry(key),
                KeyStore.PasswordProtection(KEY_PASSWORD.toCharArray()),
            )
            keyStoreFile.parentFile.mkdirs()
            keyStoreFile.outputStream().use { keyStore.store(it, KEYSTORE_PASSWORD.toCharArray()) }
            key
        }
    }

    actual fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "${Base64.getEncoder().encodeToString(iv)}:${Base64.getEncoder().encodeToString(cipherText)}"
    }

    actual fun decrypt(encrypted: String): String {
        val parts = encrypted.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted format")
        val iv = Base64.getDecoder().decode(parts[0])
        val cipherText = Base64.getDecoder().decode(parts[1])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }
}
