package com.union.campus

import android.app.Application
import com.union.campus.di.ServiceLocator
import com.union.campus.work.ClassReminderWorker
import com.union.campus.work.ReminderWorker
import com.union.campus.work.UnionNotifications
import com.union.campus.work.XxtHomeworkWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UnionApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        UnionApplicationHolder.init(this)
        UnionNotifications.ensureChannel(this)
        applicationScope.launch {
            // 三套凭证各自恢复：一个模块恢复失败不能连累其它模块
            runCatching { ServiceLocator.jwRepository.restoreSession() }
            runCatching { ServiceLocator.showerRepository.restore() }
            runCatching { ServiceLocator.electricRepository.restore() }
            runCatching { ServiceLocator.xxtRepository.restoreSession() }
            ReminderWorker.schedule(this@UnionApplication)
            ClassReminderWorker.schedule(this@UnionApplication)
            XxtHomeworkWorker.schedule(this@UnionApplication)
        }
    }
}
