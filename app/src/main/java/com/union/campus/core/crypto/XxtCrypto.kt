package com.union.campus.core.crypto

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 学习通（超星）网页端登录参数加密。
 *
 * passport2.chaoxing.com/fanyalogin 的 uname / password 都需要
 * AES-128-CBC(PKCS7) + base64，key 与 iv 是前端硬编码的同一个字符串。
 * 结论来自 xxt-assistant 的实测逆向，基准向量见 XxtCryptoTest（openssl 生成）。
 */
object XxtCrypto {

    private const val AES_KEY = "u2oh6Vu^HWe4_AES"

    /** 登录表单字段加密；PKCS5 在 16 字节分组下与 PKCS7 等价。 */
    fun loginEncrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val key = AES_KEY.toByteArray(Charsets.UTF_8)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }
}
