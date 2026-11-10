package com.union.campus

import com.union.campus.data.remote.shower.showerServiceClosedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}