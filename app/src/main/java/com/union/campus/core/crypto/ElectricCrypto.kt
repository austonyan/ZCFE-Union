package com.union.campus.core.crypto

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** 电费（完美校园 智能水电）loginCheck 结果解密：Base64 → AES-128-ECB/PKCS7。 */
object ElectricCrypto {

    private const val LOGIN_CHECK_KEY = "1234567812345678"

    fun decryptLoginCheck(base64Body: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(LOGIN_CHECK_KEY.toByteArray(Charsets.UTF_8), "AES"),
        )
        val decoded = Base64.getDecoder().decode(base64Body)
        val plain = cipher.doFinal(decoded)
        return String(plain, Charsets.UTF_8).trimEnd('\u0000')
    }
}
