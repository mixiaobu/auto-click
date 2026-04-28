package org.xiaobu.autoclick

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.xiaobu.autoclick.data.click.AutoClickStore

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

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
