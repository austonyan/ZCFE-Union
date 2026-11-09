package com.union.campus

import com.union.campus.data.remote.shower.showerAlreadyStoppedMessage
import com.union.campus.data.remote.shower.showerNeedsReserveMessage
import com.union.campus.data.remote.shower.showerServiceClosedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 洗浴非服务时间应返回友好提示，而不是“数据源返回异常”。 */
class ShowerErrorTest {

    @Test
    fun recognizesServiceClosedMessages() {
        assertEquals(
            "当前不在洗浴服务时间，请按浴室开放时间使用",
            showerServiceClosedMessage("当前非服务时间"),
        )
        assertEquals(
            "当前不在洗浴服务时间，请按浴室开放时间使用",
            showerServiceClosedMessage("浴室未开放，请稍后再试"),
        )
        assertEquals(
            "当前不在洗浴服务时间，请按浴室开放时间使用",
            showerServiceClosedMessage("不在开放时间"),
        )
    }

    @Test
    fun normalMessageIsNotMistakenForServiceClosed() {
        assertNull(showerServiceClosedMessage("操作成功"))
        assertNull(showerServiceClosedMessage("设备不存在"))
        assertNull(showerServiceClosedMessage(""))
    }

    /** code 130：直接 turnOn 时上游要求先 reserve，仓库据此补一次预约。 */
    @Test
    fun recognizesReserveRequiredMessages() {
        assertTrue(showerNeedsReserveMessage("您尚未预约任何设备"))
        assertTrue(showerNeedsReserveMessage("请先预约设备"))
        assertFalse(showerNeedsReserveMessage("预约成功"))
        assertFalse(showerNeedsReserveMessage("操作成功"))
        assertFalse(showerNeedsReserveMessage(""))
    }

    /** code 100170：设备已结束，endShower 应幂等清掉本地会话。 */
    @Test
    fun recognizesAlreadyStoppedMessages() {
        assertTrue(showerAlreadyStoppedMessage("您没有正在使用的设备"))
        assertTrue(showerAlreadyStoppedMessage("订单已结束"))
        assertFalse(showerAlreadyStoppedMessage("您尚未预约任何设备"))
        assertFalse(showerAlreadyStoppedMessage(""))
    }
}