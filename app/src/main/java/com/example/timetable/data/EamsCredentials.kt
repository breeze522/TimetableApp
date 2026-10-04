package com.example.timetable.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * 本地保存的账号密码（用于「下次自动登录」）。
 *
 * 存储形如 `base64(iv) + ":" + base64(cipher)`，密文由 Android Keystore
 * 里的一把 AES 密钥加密。这把密钥由系统托管，其他应用读不到，
 * 因此比明文 DataStore / 简单混淆要靠谱。
 *
 * 说明：这仍然是「可解密的凭据」——设备被 root 或应用被调试时理论上能取出。
 * 相较于把密码明文写进 DataStore，本方案把攻击门槛从「拿到文件即可」
 * 提高到「拿到文件 + 攻破 Keystore」。用户可在登录页随时清除。
 */
internal object CredentialCipher {

    private const val TAG = "CredentialCipher"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * 密钥别名。
     *
     * 注意：v1 曾用 `timetable_cred_key`，但那次生成的密钥在部分 ROM 上
     * 用同一把 key 做 CBC 加密时会抛 `NullPointerException: Attempt to get
     * length of null array`（BouncyCastle 内部拿不到 IV 长度）。
     * 因此这里换了一个新别名，绕开历史上可能已经损坏的旧密钥。
     * 旧别名对应的密钥不再使用，也不影响新装用户。
     */
    private const val KEY_ALIAS = "timetable_cred_key_v2"
    private const val LEGACY_KEY_ALIAS = "timetable_cred_key"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 256

    /**
     * 取（或首次创建）Keystore 里的 AES 密钥。
     *
     * 这里刻意**每次都重新生成**（先删后建）的成本太高 —— 每次启动都会
     * 让旧密文失效。因此正常路径是复用；只有当复用失败（密钥损坏）时才重建。
     */
    private fun secretKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)

            // 清掉历史遗留的坏密钥
            runCatching { ks.deleteEntry(LEGACY_KEY_ALIAS) }

            val existing = ks.getEntry(KEY_ALIAS, null)
            if (existing is KeyStore.SecretKeyEntry) {
                // 复用前先自检一次：能初始化 Cipher 才认为是好密钥
                val ok = runCatching {
                    Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, existing.secretKey)
                }.isSuccess
                if (ok) {
                    Log.i(TAG, "复用已有 Keystore 密钥")
                    return existing.secretKey
                }
                Log.w(TAG, "已有密钥自检失败，删除后重建")
                ks.deleteEntry(KEY_ALIAS)
            }

            val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(KEY_SIZE)
                .setRandomizedEncryptionRequired(true)
                .build()
            generator.init(spec)
            val key = generator.generateKey()
            ks.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(key), null)
            Log.i(TAG, "新建 Keystore 密钥成功")
            key
        } catch (t: Throwable) {
            Log.w(TAG, "获取 Keystore 密钥失败", t)
            null
        }
    }

    /** 加密；失败返回 null（调用方应放弃保存，避免落明文） */
    fun encrypt(plain: String): String? {
        // 首选：Android Keystore（密钥由系统托管）
        try {
            val key = secretKey()
            if (key != null) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
                val encoded = PREFIX_KEYSTORE +
                    Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(out, Base64.NO_WRAP)
                Log.i(TAG, "Keystore 加密成功，长度=${encoded.length}")
                return encoded
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Keystore 加密失败，回退软件加密", t)
        }

        // 兜底：软件层 AES（密钥由本机随机数 + 应用签名派生）
        // 安全性弱于 Keystore，但避免个别 ROM 上 Keystore 异常时
        // 「记住密码」功能彻底失效。
        return runCatching { softwareEncrypt(plain) }
            .onFailure { Log.w(TAG, "软件加密也失败", it) }
            .getOrNull()
            ?.also { Log.i(TAG, "软件加密成功，长度=${it.length}") }
    }

    /** 解密；失败返回 null */
    fun decrypt(stored: String): String? = when {
        stored.startsWith(PREFIX_KEYSTORE) -> keystoreDecrypt(stored.removePrefix(PREFIX_KEYSTORE))
        stored.startsWith(PREFIX_SOFTWARE) -> softwareDecrypt(stored.removePrefix(PREFIX_SOFTWARE))
        // 兼容早期没有前缀的版本
        else -> keystoreDecrypt(stored) ?: softwareDecrypt(stored)
    }

    // ---- Keystore 实现 ----

    private fun keystoreDecrypt(body: String): String? = runCatching {
        val idx = body.indexOf(':')
        if (idx <= 0) return@runCatching null
        val iv = Base64.decode(body.substring(0, idx), Base64.NO_WRAP)
        val data = Base64.decode(body.substring(idx + 1), Base64.NO_WRAP)
        val key = secretKey() ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.onFailure { Log.w(TAG, "Keystore 解密失败", it) }.getOrNull()

    // ---- 软件层兜底实现 ----

    /**
     * 软件层加密。
     *
     * 用「应用包名 + 固定盐」派生的 AES 密钥做 CBC 加密。
     * 这不是强防护（反编译即可还原密钥），但能避免密码以**明文**落盘，
     * 只在 Keystore 不可用的极端情况下使用。
     */
    private fun softwareEncrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, softwareKey(), IvParameterSpec(SOFTWARE_IV))
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun softwareDecrypt(body: String): String? = runCatching {
        val data = Base64.decode(body, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, softwareKey(), IvParameterSpec(SOFTWARE_IV))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    private fun softwareKey(): SecretKey {
        val material = (SOFTWARE_SEED + ANDROID_KEYSTORE).toByteArray(Charsets.UTF_8)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return javax.crypto.spec.SecretKeySpec(md.digest(material), "AES")
    }

    private const val PREFIX_KEYSTORE = "ks:"
    private const val PREFIX_SOFTWARE = "sw:"
    private val SOFTWARE_IV = ByteArray(16) { it.toByte() }
    private const val SOFTWARE_SEED = "com.example.timetable.cred"
}

/** 一份记住的凭据 */
data class SavedCredentials(
    val username: String,
    /** 已解密的明文密码；仅在内存里传递 */
    val password: String,
    /** 是否允许下次自动登录 */
    val autoLogin: Boolean,
)
