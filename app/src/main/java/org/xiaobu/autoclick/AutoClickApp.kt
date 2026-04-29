package org.xiaobu.autoclick

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.xiaobu.autoclick.data.click.AutoClickStore
import org.xiaobu.autoclick.data.settings.AppSettingsStore
import org.xiaobu.autoclick.data.task.AutoTaskStore
import org.xiaobu.autoclick.data.trigger.AutoTriggerStore

class AutoClickApp : Application() {

    companion object {
        private lateinit var instance: AutoClickApp
        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
        private var currentToast: Toast? = null

        val appContext: Context
            get() = instance.applicationContext

        fun showToast(message: String) {
            if (message.isBlank()) return
            mainHandler.post {
                currentToast?.cancel()
                currentToast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT)
                currentToast?.show()
            }
        }
    }

    val autoClickStore: AutoClickStore by lazy {
        AutoClickStore(this)
    }

    val autoTriggerStore: AutoTriggerStore by lazy {
        AutoTriggerStore(this)
    }

    val autoTaskStore: AutoTaskStore by lazy {
        AutoTaskStore(this)
    }

    val appSettingsStore: AppSettingsStore by lazy {
        AppSettingsStore(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
