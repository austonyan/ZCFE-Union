package com.union.campus

import android.content.ClipboardManager
import android.content.Context

/** 全局上下文持有（剪贴板 / intent 用 Activity 上下文）。 */
object UnionApplicationHolder {
    var appContext: Context? = null
        private set
    var clipboard: ClipboardManager? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
}
