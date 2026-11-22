package com.voyarina.caliedu.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 设置里的敏感值先加密再落盘。
 *
 * 为什么不用 EncryptedSharedPreferences：那要多拉一个 security-crypto 依赖，
 * 而这里只需要藏一个 API Key。AES 密钥由 Android Keystore 生成并保管，
 * 它本身不出设备、也不进云备份——所以就算密文被同步走，也解不开。
 *
 * 密文带 `v1:` 前缀，用来区分旧版本存下来的明文：老用户升级后仍读得到 Key。
 */
object SecretBox {

    private const val ALIAS = "caliedu.secret"
    private const val PREFIX = "v1:"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
        }.getOrElse {
            // 个别定制 ROM 上 Keystore 会不可用。加密失败不能把功能一起废掉，
            // 退回明文存——至少 App 还能用，而不是填了 Key 也存不住。
            plain
        }
    }

    fun open(stored: String): String {
        if (stored.isEmpty()) return ""
        // 没有前缀的是旧版本留下的明文
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (raw.size <= IV_BYTES) return@runCatching ""
            val iv = raw.copyOfRange(0, IV_BYTES)
            val body = raw.copyOfRange(IV_BYTES, raw.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrDefault("")
        // 解不开就当没存过，让用户重填；绝不能把密文当成 Key 发出去。
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
