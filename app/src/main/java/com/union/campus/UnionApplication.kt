package com.union.campus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
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
            ServiceLocator.jwRepository.restoreSession()
            ServiceLocator.showerRepository.restore()
            ServiceLocator.electricRepository.restore()
            ReminderWorker.schedule(this@UnionApplication)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "union_reminders",
                "校园提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "上课、考试、低电费、评教截止等提醒" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
