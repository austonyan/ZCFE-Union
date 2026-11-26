package com.union.campus.data.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 凭证存储：AndroidKeyStore 生成 AES-GCM 密钥，凭证 JSON 加密后落盘。
 * 凭证不出设备（power.md §2.4 原则 4）。
 */
class CredentialStore(private val context: Context) {

    @Serializable
    data class Credentials(
        val jwStudentNo: String = "",
        val jwCookie: String = "", // JSESSIONID
        val jwPassword: String = "",
        val showerMobile: String = "",
        val showerToken: String = "",
        val showerServiceHost: String = "",
        val showerUid: String = "",
        val showerPassword: String = "",
        val electricAccount: String = "",
        val electricCustomerCode: String = "7268",
        val electricCommand: String = "JBSWaterElecService",
        // 学习通（超星）：账号密码与会话 cookie 都只在本机 Keystore 加密保存
        val xxtAccount: String = "",
        val xxtPassword: String = "",
        val xxtCookies: String = "",
    )

    private val file by lazy { java.io.File(context.filesDir, "credentials.bin") }
    private val json = Json { ignoreUnknownKeys = true }

    private val key: SecretKey by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("union_credentials", null) as? SecretKey) ?: run {
            val gen = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            gen.init(KeyGenParameterSpec.Builder(
                "union_credentials",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes("GCM")
                .setEncryptionPaddings("NoPadding")
                .setKeySize(256)
                .build())
            gen.generateKey()
        }
    }

    fun load(): Credentials {
        if (!file.exists()) return Credentials()
        return runCatching {
            val raw = file.readBytes()
            val ivLen = raw[0].toInt()
            val iv = raw.copyOfRange(1, 1 + ivLen)
            val data = raw.copyOfRange(1 + ivLen, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(data)
            json.decodeFromString(Credentials.serializer(), String(plain, Charsets.UTF_8))
        }.getOrDefault(Credentials())
    }

    fun save(credentials: Credentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val data = cipher.doFinal(
            json.encodeToString(Credentials.serializer(), credentials).toByteArray(Charsets.UTF_8),
        )
        val out = ByteArray(1 + iv.size + data.size)
        out[0] = iv.size.toByte()
        iv.copyInto(out, 1)
        data.copyInto(out, 1 + iv.size)
        file.writeBytes(out)
    }

    /** 全部退出：清除凭证文件与缓存。 */
    fun clear() {
        file.delete()
    }
}
