package com.mk.daftar.android

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.mk.daftar.core.security.androidSecurityConfigurationModule
import com.mk.daftar.di.initKoin
import com.mk.daftar.lifecycle.VaultLifecycleCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

/**
 * Starts dependency injection once, with the build variant's key policy. The process-level ON_STOP is a fallback
 * lock only: its events are deliberately delayed, so the activity's own stop is the immediate trigger.
 */
class DaftarApplication : Application() {

    /** The lock and cover coordinator. Valid after [onCreate]. */
    lateinit var vaultLifecycle: VaultLifecycleCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@DaftarApplication)
            modules(androidSecurityConfigurationModule(BuildVariant.securityConfiguration))
        }
        vaultLifecycle = GlobalContext.get().get()
        vaultLifecycle.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) vaultLifecycle.processStopped()
            },
        )
    }
}
