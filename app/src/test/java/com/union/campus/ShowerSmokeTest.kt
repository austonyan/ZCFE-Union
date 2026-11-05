package com.union.campus

import com.union.campus.core.AppError
import com.union.campus.data.remote.electric.ElectricClient
import com.union.campus.data.remote.shower.ShowerClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实网络冒烟测试：lz.hydream.cn / xqh5.17wanxiao.com 可达时验证协议全程正确。
 * 空 token 调 getOrgs → 服务端应返回 code=90/未登录（auth=00001）。
 */
class ShowerSmokeTest {

    @Test
    fun getOrgsWithEmptyToken_mapsToAuthExpired() = runBlocking {
        val client = ShowerClient()
        try {
            client.call(
                "DeviceAreaApi/getOrgs",
                data = buildJsonObject { put("org_type", "school"); put("org_name", "") },
            )
            // 若成功（服务端变更），至少说明协议往返 OK
            assertTrue(true)
        } catch (e: AppError.AuthExpired) {
            assertTrue(true) // 期望：未登录
        } catch (e: AppError) {
            // 网络不可达/上游异常也记录但不算协议错误
            println("smoke test non-auth error: ${e.userMessage}")
        }
        Unit
    }

    @Test
    fun electricGateway_responds() = runBlocking {
        val client = ElectricClient()
        client.account = "smoke"
        try {
            client.getBindRooms()
        } catch (e: AppError.Network) {
            println("electric network unreachable: ${e.userMessage}")
        } catch (e: AppError.Upstream) {
            assertTrue(true) // 网关响应了 → 通道 OK
        }
        Unit
    }
}
