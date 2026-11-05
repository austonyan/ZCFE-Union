package com.union.campus.core.crypto

import java.util.Base64
import java.util.UUID
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 洗浴（大白U帮原生 lz 接口）协议：DES/CBC + SHA-1 签名 + ztechMsg 表单。
 * 常量来自逆向结果，可能随 APK 升级变化。
 */
object ShowerCrypto {

    private const val DES_KEY = "5yoOxt9w"
    private const val DES_IV = "11111111"
    private const val SIGN_SALT = "q9KswPoz62jQk12W"

    private val json = Json { encodeDefaults = true }

    fun desEncrypt(plain: String): String {
        val cipher = Cipher.getInstance("DES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES"),
            IvParameterSpec(DES_IV.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun desDecrypt(cipherTextB64: String): ByteArray {
        val raw = Base64.getDecoder().decode(cipherTextB64)
        val cipher = Cipher.getInstance("DES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES"),
            IvParameterSpec(DES_IV.toByteArray(Charsets.UTF_8)),
        )
        return cipher.doFinal(raw)
    }

    /** 构造请求 ztechMsg：内层 {"head":{...},"data":"<DES-Base64>"}。 */
    fun buildEnvelope(act: String, data: JsonObject?, token: String): String {
        val body = buildJsonObject {
            putJsonObject("data") {
                data?.forEach { (k, v) -> put(k, v) }
            }
            putJsonObject("header") {
                put("device_type", "android")
                put("source_sys_version", "14")
                put("source_version", "2.2.1")
                put("source_model", "Pixel")
                put("token", token)
                put("act", act)
            }
        }
        val plaintext = body.toString()
        val encrypted = desEncrypt(plaintext)
        val msgId = "%010d%06d".format(System.currentTimeMillis() / 1000, (100000..999999).random())
        val uuid = UUID.randomUUID().toString()
        val length = plaintext.toByteArray(Charsets.UTF_8).size
        val verify = JwCrypto.sha1Hex(msgId + uuid + length + SIGN_SALT + encrypted)
        return buildJsonObject {
            putJsonObject("head") {
                put("verify", verify)
                put("msg_id", msgId)
                put("length", length)
                put("uuid", uuid)
            }
            put("data", encrypted)
        }.toString()
    }

    /** 解响应：解 DES；head.enc=GZ 时先 zlib inflate。返回内层 JSON（header/data）。 */
    fun decodeResponse(envelope: JsonObject): JsonObject {
        val cipherText = envelope["data"]?.toString()
            ?.removeSurrounding("\"")
            ?: throw IllegalStateException("响应缺少 data 字段")
        var payload = desDecrypt(cipherText)
        val enc = envelope["head"]?.jsonObject?.get("enc")?.toString()?.removeSurrounding("\"")
        if (enc.equals("GZ", ignoreCase = true)) {
            payload = try {
                inflate(payload, nowrap = false) // java.util.zip.Deflater 默认 zlib 包装
            } catch (_: Exception) {
                inflate(payload, nowrap = true)
            }
        }
        return json.parseToJsonElement(String(payload, Charsets.UTF_8)).jsonObject
    }

    private fun inflate(input: ByteArray, nowrap: Boolean): ByteArray {
        val inflater = Inflater(nowrap)
        inflater.setInput(input)
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && inflater.needsInput()) break
            output.write(buffer, 0, n)
        }
        inflater.end()
        return output.toByteArray()
    }
}
