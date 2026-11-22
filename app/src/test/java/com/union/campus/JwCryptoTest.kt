package com.union.campus

import com.union.campus.core.crypto.ShowerCrypto
import com.union.campus.core.crypto.JwCrypto
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwCryptoTest {

    // 基准值由 Python 参考实现（allAPI.md §1.3.2）生成；测试用固定 key，真实 key 只在本地配置
    @Test
    fun ofEncrypt_matchesPythonReference() {
        val qs1 = "action=jw_apply&xnxq=202620261&week=3"
        assertEquals(
            "34ne0f3sgfe3357uuh2jst5x3w1ubw2fo26n2y2uih2mrl9i29nsp51rrs8c25j8qo3q2lui000050",
            JwCrypto.ofEncrypt(qs1, TEST_KEY),
        )
        val qs2 = "userid=20230001&usertype=STU"
        assertEquals(
            "3bsrfc3t1k6h30f0ka1o7odp2p568s2awcus2hzztc3ob3m9292wel00003q",
            JwCrypto.ofEncrypt(qs2, TEST_KEY),
        )
    }

    private companion object {
        const val TEST_KEY = "TestKey123"
    }

    @Test
    fun getMd5_2_matchesPythonReference() {
        assertEquals(
            "b53c3e63530a7ebe3a6b180eba7558e2",
            JwCrypto.getMd5_2("action=jw_apply&xnxq=202620261&week=3"),
        )
        assertEquals(
            "a11323b2a635aab10a399c604899f86a",
            JwCrypto.getMd5_2("userid=20230001&usertype=STU"),
        )
    }

    @Test
    fun sha1AndMd5Known() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", JwCrypto.sha1Hex("abc"))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", JwCrypto.md5Hex("abc"))
    }
}

class ShowerCryptoTest {

    @Test
    fun desRoundTrip() {
        val plain = """{"data":{"uuid":"demo","deviceType":"faucet"},"header":{"act":"turnOn"}}"""
        val encrypted = ShowerCrypto.desEncrypt(plain)
        val decrypted = String(ShowerCrypto.desDecrypt(encrypted), Charsets.UTF_8)
        assertEquals(plain, decrypted)
    }

    @Test
    fun envelopeHasHeadAndData() {
        val envelope = ShowerCrypto.buildEnvelope(
            act = "getOrgs",
            data = buildJsonObject { put("org_type", "school"); put("org_name", "") },
            token = "",
        )
        assertTrue("no head: $envelope", envelope.contains("\"head\""))
        assertTrue("no data: $envelope", envelope.contains("\"data\""))
        // act 在加密的内层 payload 中：解密后校验
        val dataField = envelope.substringAfter("\"data\":\"").substringBefore("\"}")
        val plain = String(ShowerCrypto.desDecrypt(dataField), Charsets.UTF_8)
        assertTrue("no act in payload: $plain", plain.contains("\"act\":\"getOrgs\""))
    }
}
