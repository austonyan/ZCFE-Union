package com.union.campus

import com.union.campus.core.crypto.ShowerCrypto
import com.union.campus.core.crypto.JwCrypto
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwCryptoTest {

    // 基准值由 Python 参考实现（allAPI.md §1.3.2）生成
    @Test
    fun ofEncrypt_matchesPythonReference() {
        val qs1 = "action=jw_apply&xnxq=202620261&week=3"
        assertEquals(
            "2j829k3owqyb1wka5s3icm662s5rad2l0wd72vp07i2rj23c1tlde42s4oni1ivl9i3lxfh700002z",
            JwCrypto.ofEncrypt(qs1),
        )
        val qs2 = "userid=20230001&usertype=STU"
        assertEquals(
            "2rkcfn3qoshv1sycmr2nye541mfzyf2hg3si2gt29j3u9h791u7duq00005h",
            JwCrypto.ofEncrypt(qs2),
        )
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
