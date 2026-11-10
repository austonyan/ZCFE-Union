package com.union.campus.ui.common

import android.content.Intent
import android.net.Uri
import com.union.campus.UnionApplicationHolder

/** 外部跳转地址：支付宝充值小程序与教务找回密码。 */
object ExternalLinks {
    const val ShowerTopUp = "https://ur.alipay.com/_4nxyEZlWjjtkKJQIAIcpCk"
    const val ElectricTopUp = "https://ur.alipay.com/_7pzqnfDTdCJERAdDgLmAPP"
    const val RetrievePassword = "https://authserver.zzife.edu.cn/retrieve-password/retrievePassword/index.html"

    /** 洗浴与电费充值共用的提示文案。 */
    const val TopUpTip = "充值将跳转至支付宝官方充值页面完成，本应用不托管支付密码。"
}

/** 用系统浏览器打开外部链接；没有可用应用时静默失败。 */
fun openExternalLink(url: String) {
    val context = UnionApplicationHolder.appContext ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
