package com.union.campus

import com.union.campus.core.crypto.XxtCrypto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 学习通登录加密回归：基准值由 openssl 用同一 key/iv 生成。
 * 失败意味着登录表单字段会整段不对，服务端只会回"账号密码错误"。
 */
class XxtCryptoTest {

    private fun decrypt(token: String): String {
        val key = "u2oh6Vu^HWe4_AES".toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
        return String(cipher.doFinal(Base64.getDecoder().decode(token)), Charsets.UTF_8)
    }

    @Test
    fun matchesOpensslReferenceVectors() {
        assertEquals("QCqeoCKq+0qODwdiV5vTww==", XxtCrypto.loginEncrypt("202400000001"))
        assertEquals("l85380oNaQHf7KDijNmxeA==", XxtCrypto.loginEncrypt("P@ssw0rd!"))
    }

    @Test
    fun roundTripsChineseAccount() {
        val raw = "13800001234"
        assertEquals(raw, decrypt(XxtCrypto.loginEncrypt(raw)))
    }
}
