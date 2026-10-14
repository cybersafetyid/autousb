package com.cybersafetyid.mobile.autousb.shell.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec

object AdbKeyGenerator {

    private const val KEY_FILENAME_PRIVATE = "autousb_adb_key"
    private const val KEY_FILENAME_PUBLIC = "autousb_adb_key.pub"

    fun getOrCreateKeyPair(context: Context): KeyPair {
        val privFile = File(context.filesDir, KEY_FILENAME_PRIVATE)
        val pubFile = File(context.filesDir, KEY_FILENAME_PUBLIC)

        if (privFile.exists() && pubFile.exists()) {
            try {
                val privBytes = Base64.decode(privFile.readText().trim(), Base64.NO_WRAP)
                val keySpec = PKCS8EncodedKeySpec(privBytes)
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(keySpec)
                return KeyPair(null, privateKey)
            } catch (_: Exception) {}
        }

        return generateAndSaveKeyPair(context, privFile, pubFile)
    }

    private fun generateAndSaveKeyPair(context: Context, privFile: File, pubFile: File): KeyPair {
        return try {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            val keyPair = keyGen.generateKeyPair()

            val privEncoded = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
            privFile.writeText(privEncoded)

            val pubEncoded = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            pubFile.writeText(pubEncoded)

            keyPair
        } catch (e: Exception) {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            keyGen.generateKeyPair()
        }
    }
}
