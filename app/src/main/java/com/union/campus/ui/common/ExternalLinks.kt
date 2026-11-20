package com.union.campus.ui.common

import android.content.Intent
import android.content.Context
import androidx.core.net.toUri
import com.union.campus.UnionApplicationHolder

/** 外部跳转地址：官方教务系统、充值小程序与教务找回密码。 */
object ExternalLinks {
    // Same official origin as JwClient.jwUrl; never append credentials or session parameters.
    const val AcademicPortal = "https://jw.zzife.edu.cn"
    const val ShowerTopUp = "https://ur.alipay.com/_4nxyEZlWjjtkKJQIAIcpCk"
    const val ElectricTopUp = "https://ur.alipay.com/_7pzqnfDTdCJERAdDgLmAPP"
    const val RetrievePassword = "https://authserver.zzife.edu.cn/retrieve-password/retrievePassword/index.html"

    /** 洗浴与电费充值共用的提示文案。 */
    const val TopUpTip = "充值将跳转至支付宝官方小程序充值页面完成"
}

/** Open a trusted link and return whether the system accepted the handoff. */
fun openExternalLink(
    url: String,
    context: Context? = UnionApplicationHolder.appContext,
    onFailure: () -> Unit = {},
): Boolean {
    if (context == null) {
        onFailure()
        return false
    }
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    if (!opened) onFailure()
    return opened
}
