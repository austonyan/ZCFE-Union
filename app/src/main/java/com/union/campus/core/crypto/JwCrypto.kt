package com.union.campus.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 教务系统（郑州财经学院 Kingosoft）加密算法。
 * 参照 authserver encrypt.js 与喜鹊儿 H5 前端复刻，基准值由 Python 参考实现生成（见 JwCryptoTest）。
 */
object JwCrypto {

    const val H5_ENCRYPT_KEY = "<token>"

    private const val AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    private const val B36 = "0123456789abcdefghijklmnopqrstuvwxyz"
    private val random = SecureRandom()

    /** CAS 登录密码加密：AES-CBC，盐值来自登录页 pwdEncryptSalt。 */
    fun encryptCasPassword(password: String, salt: String): String {
        val rand64 = randomChars(64)
        val iv = randomChars(16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        val plain = (rand64 + password).toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain))
    }

    /** 喜鹊儿 H5 参数加密：逐字符 (ord(data)+ord(key)+offset) 取后 3 位数字；每 9 位数字转 base36 补齐 6 位。 */
    fun ofEncrypt(data: String, key: String = H5_ENCRYPT_KEY): String {
        if (data.isEmpty() || key.isEmpty()) return data
        val s = key.length
        val o = data.length
        val g = (o + s - 1) / s // ceil(o/s)
        val c = 6 * ((o + 2) / 3) // 6 * ceil(o/3)
        val u = c % s
        val m = StringBuilder()
        for (t in 0 until g) {
            for (i in 1..s) {
                val r = t * s + i
                if (r > o) break
                val f = data[r - 1].code + key[i - 1].code + u
                m.append(f.toString().padStart(3, '0'))
            }
        }
        val out = StringBuilder()
        var r = 0
        while (r < m.length) {
            val j = minOf(r + 9, m.length)
            val chunk = m.substring(r, j).toLong()
            out.append(toBase36(chunk).padStart(6, '0').takeLast(6))
            r += 9
        }
        return out.toString()
    }

    /** H5 参数校验值：两次 MD5，中间去掉固定下标字符。 */
    fun getMd5_2(data: String): String {
        val first = md5Hex(data)
        val sb = StringBuilder()
        // Python: s = [''] + list(a)，删除下标 3/10/17/25，即原串的第 2/9/16/24 个字符
        for (i in first.indices) {
            val sIndex = i + 1
            if (sIndex == 3 || sIndex == 10 || sIndex == 17 || sIndex == 25) continue
            sb.append(first[i])
        }
        return md5Hex(sb.toString())
    }

    private fun toBase36(n: Long): String {
        var v = n
        if (v == 0L) return "0"
        val out = StringBuilder()
        while (v > 0) {
            out.insert(0, B36[(v % 36).toInt()])
            v /= 36
        }
        return out.toString()
    }

    private fun randomChars(count: Int): String {
        val sb = StringBuilder(count)
        repeat(count) { sb.append(AES_CHARS[random.nextInt(AES_CHARS.length)]) }
        return sb.toString()
    }

    fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun sha1Hex(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
