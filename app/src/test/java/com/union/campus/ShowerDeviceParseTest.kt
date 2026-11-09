package com.union.campus

import com.union.campus.data.repo.parseShowerDevice
import com.union.campus.data.repo.showerDeviceState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设备列表解析：uuid 必须取每台唯一的 device_key，并读出 need_reserve。 */
class ShowerDeviceParseTest {

    @Test
    fun usesPerDeviceKeyInsteadOfSharedControllerKey() {
        val device = parseShowerDevice(
            buildJsonObject {
                put("device_key", "controller-1_faucet_01")
                put("controller_key", "controller-1")
                put("device_name", "01")
                put("device_status", "ready")
                put("need_reserve", "1")
            },
        )
        assertEquals("controller-1_faucet_01", device.uuid)
        assertEquals("01", device.name)
        assertEquals(0, device.state)
        assertTrue(device.needReserve)
    }

    @Test
    fun readsNeedReserveAndState() {
        val running = parseShowerDevice(
            buildJsonObject {
                put("device_key", "k_02")
                put("running_status", "running")
                put("need_reserve", "0")
            },
        )
        assertFalse(running.needReserve)
        assertEquals(1, running.state)
    }

    @Test
    fun missingNeedReserveDefaultsToFalse() {
        val device = parseShowerDevice(buildJsonObject { put("device_key", "k_03") })
        assertFalse(device.needReserve)
    }

    @Test
    fun mapsCommonDeviceStatuses() {
        assertEquals(0, showerDeviceState(buildJsonObject { put("device_status", "ready") }))
        assertEquals(1, showerDeviceState(buildJsonObject { put("running_status", "running") }))
        assertEquals(2, showerDeviceState(buildJsonObject { put("running_status", "reserved") }))
        assertEquals(3, showerDeviceState(buildJsonObject { put("device_status", "fault") }))
    }
}