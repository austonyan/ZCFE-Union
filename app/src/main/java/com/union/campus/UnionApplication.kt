package com.union.campus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.union.campus.di.ServiceLocator
import com.union.campus.work.ReminderWorker
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
        createNotificationChannel()
        applicationScope.launch {
            // 三套凭证各自恢复：一个模块恢复失败不能连累其它模块
            runCatching { ServiceLocator.jwRepository.restoreSession() }
            runCatching { ServiceLocator.showerRepository.restore() }
            runCatching { ServiceLocator.electricRepository.restore() }
            ReminderWorker.schedule(this@UnionApplication)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "union_reminders",
            "校园提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "上课、考试、低电费、评教截止等提醒" }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
