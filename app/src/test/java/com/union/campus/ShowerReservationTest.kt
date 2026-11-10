package com.union.campus

import com.union.campus.data.remote.shower.formatShowerCountdown
import com.union.campus.data.repo.parseShowerReservation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预约倒计时必须以服务端 remainTime 为准，不写死 5/8 分钟。 */
class ShowerReservationTest {

    private fun reservedState(remainTime: Int = 295) = buildJsonObject {
        put("userState", "reserved")
        put("remainTime", remainTime)
        put(
            "deviceInfo",
            buildJsonObject {
                put("device_key", "controller_faucet_01")
                put("device_name", "01")
                put("deviceAreaName", "11号楼一层")
                put("reserve_time", "1794215100")
                put("reserve_expires", "300")
                put("max_wait_time", "300")
            },
        )
    }

    @Test
    fun usesServerRemainTimeAsCountdown() {
        val before = System.currentTimeMillis()
        val reservation = parseShowerReservation(reservedState())!!
        val after = System.currentTimeMillis()

        assertEquals("controller_faucet_01", reservation.deviceUuid)
        assertEquals("01", reservation.deviceName)
        assertEquals("11号楼一层", reservation.areaName)
        assertEquals(300, reservation.maxWaitSeconds)
        assertTrue(reservation.expiresAt in (before + 295_000)..(after + 295_000))
    }

    @Test
    fun ignoresNonReservedState() {
        val normal = buildJsonObject {
            put("userState", "normal")
            put("remainTime", 0)
        }
        assertNull(parseShowerReservation(normal))
        assertNull(parseShowerReservation(null))
    }

    @Test
    fun fallsBackToReserveTimeAndWindow() {
        val nowSec = System.currentTimeMillis() / 1000
        val state = buildJsonObject {
            put("userState", "reserved")
            put("remainTime", 0)
            put(
                "deviceInfo",
                buildJsonObject {
                    put("device_key", "reserve_time_faucet_02")
                    put("reserve_time", (nowSec - 100).toString())
                    put("reserve_expires", "300")
                },
            )
        }
        val reservation = parseShowerReservation(state)!!
        assertTrue(reservation.expiresAt > System.currentTimeMillis())
        assertEquals(300, reservation.maxWaitSeconds)
    }

    @Test
    fun treatsExpiredAbsoluteWindowAsNoReservation() {
        val state = buildJsonObject {
            put("userState", "reserved")
            put("remainTime", 0)
            put(
                "deviceInfo",
                buildJsonObject {
                    put("device_key", "reserve_time_faucet_02")
                    put("reserve_time", "1000")
                    put("reserve_expires", "300")
                },
            )
        }
        assertNull(parseShowerReservation(state))
    }

    @Test
    fun formatsCountdownAsMinutesAndSeconds() {
        assertEquals("05:00", formatShowerCountdown(300))
        assertEquals("00:09", formatShowerCountdown(9))
        assertEquals("00:00", formatShowerCountdown(-1))
    }
}