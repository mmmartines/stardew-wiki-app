package com.stardew.wikipt

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // só na versão de teste: aponta no log a linha que abriu um recurso sem fechar
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
        Net.init(this)
        Lang.load(this)
        WebBridge.init(this)
    }
}
