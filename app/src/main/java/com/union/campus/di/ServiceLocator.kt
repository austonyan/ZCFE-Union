package com.union.campus.di

import android.content.Context
import com.union.campus.data.remote.electric.ElectricClient
import com.union.campus.data.remote.jw.JwClient
import com.union.campus.data.remote.shower.ShowerClient
import com.union.campus.data.repo.ElectricRepository
import com.union.campus.data.repo.JwRepository
import com.union.campus.data.repo.ShowerRepository
import com.union.campus.data.repo.TodoRepository
import com.union.campus.data.store.CredentialStore
import com.union.campus.data.store.EntityStore
import com.union.campus.data.store.SettingsStore

/**
 * 轻量服务定位器（单模块应用无需 DI 框架）。
 */
object ServiceLocator {

    lateinit var settings: SettingsStore
        private set
    lateinit var credentials: CredentialStore
        private set
    lateinit var entities: EntityStore
        private set

    val jwClient by lazy { JwClient() }
    val showerClient by lazy { ShowerClient() }
    val electricClient by lazy { ElectricClient() }

    val jwRepository by lazy {
        JwRepository(jwClient, entities, credentials)
    }
    val showerRepository by lazy {
        ShowerRepository(showerClient, entities, credentials, settings)
    }
    val electricRepository by lazy {
        ElectricRepository(electricClient, entities, credentials, settings)
    }
    val todoRepository by lazy {
        TodoRepository(jwRepository, electricRepository, settings)
    }

    fun init(context: Context) {
        settings = SettingsStore(context.applicationContext)
        credentials = CredentialStore(context.applicationContext)
        entities = EntityStore(context.applicationContext)
    }
}
