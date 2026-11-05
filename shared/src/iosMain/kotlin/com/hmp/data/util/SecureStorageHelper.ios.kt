package com.hmp.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataUsingEncoding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
actual object SecureStorageHelper {
    private val keysDir: String by lazy {
        val docs = NSFileManager.defaultManager.URLForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask,
            null,
            false,
            null,
        )!!
        val dir = "${docs.path}/.keys"
        if (!NSFileManager.defaultManager.fileExistsAtPath(dir)) {
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        }
        dir
    }

    private val key: ByteArray by lazy {
        val path = "$keysDir/.aes_key"
        if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
            val data = NSFileManager.defaultManager.contentsAtPath(path)
            if (data != null) {
                val bytes = data.bytes
                if (bytes != null) {
                    val base64Str = bytes.readBytes(data.length.toInt()).decodeToString()
                    return@lazy Base64.decode(base64Str)
                }
            }
            generateAndStoreKey()
        } else {
            generateAndStoreKey()
        }
    }

    private fun generateAndStoreKey(): ByteArray {
        val keyBytes = ByteArray(32)
        Random.nextBytes(keyBytes)
        val path = "$keysDir/.aes_key"
        val base64Str = Base64.encode(keyBytes)
        val nsData = (base64Str as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        if (nsData != null) {
            NSFileManager.defaultManager.createFileAtPath(path, nsData, null)
        }
        return keyBytes
    }

    private fun simpleEncrypt(plainText: ByteArray, keyBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12)
        Random.nextBytes(iv)
        val cipherText = ByteArray(plainText.size)
        for (i in plainText.indices) {
            cipherText[i] = (plainText[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return iv to cipherText
    }

    private fun simpleDecrypt(cipherText: ByteArray, keyBytes: ByteArray, iv: ByteArray): ByteArray {
        val plainText = ByteArray(cipherText.size)
        for (i in cipherText.indices) {
            plainText[i] = (cipherText[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return plainText
    }

    actual fun encrypt(plainText: String): String {
        val (iv, cipherText) = simpleEncrypt(plainText.encodeToByteArray(), key)
        val ivBase64 = Base64.encode(iv)
        val cipherBase64 = Base64.encode(cipherText)
        return "$ivBase64:$cipherBase64"
    }

    actual fun decrypt(encrypted: String): String {
        val parts = encrypted.split(":")
        if (parts.size != 2) {
            if (encrypted.startsWith("kc_")) {
                return decryptOldFormat(encrypted)
            }
            throw IllegalArgumentException("Invalid encrypted format")
        }

        val iv = Base64.decode(parts[0])
        val cipherText = Base64.decode(parts[1])
        val plainBytes = simpleDecrypt(cipherText, key, iv)
        return plainBytes.decodeToString()
    }

    private fun decryptOldFormat(token: String): String {
        val filePath = "$keysDir/$token"
        val data = NSFileManager.defaultManager.contentsAtPath(filePath) ?: return token
        val bytes = data.bytes ?: return token
        return bytes.readBytes(data.length.toInt()).decodeToString()
    }
}
