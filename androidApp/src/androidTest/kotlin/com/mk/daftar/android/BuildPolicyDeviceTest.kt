package com.mk.daftar.android

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The installed manifest policy of the debug application, read through PackageManager: least privilege, no cleartext,
 * no backup, and only the launcher exported. The release manifest is audited from its merged output by the build.
 */
class BuildPolicyDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theDebugApplicationIsDebuggable() {
        assertTrue(context.packageName.endsWith(".debug"), "this suite runs against the debug application")
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
    }

    @Test
    fun cleartextTrafficAndBackupAreRefused() {
        val flags = context.applicationInfo.flags
        assertEquals(0, flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
        assertEquals(0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun theOnlyRequestedPermissionIsInternet() {
        val requested = packageInfo(PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertEquals(listOf("android.permission.INTERNET"), requested)
    }

    @Test
    fun onlyTheLauncherActivityIsExported() {
        val info = packageInfo(
            PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS,
        )
        val exported = buildList {
            info.activities.orEmpty().filter(ActivityInfo::exported).forEach { add(it.name) }
            info.receivers.orEmpty().filter(ActivityInfo::exported).forEach { add(it.name) }
            info.services.orEmpty().filter(ServiceInfo::exported).forEach { add(it.name) }
            info.providers.orEmpty().filter(ProviderInfo::exported).forEach { add(it.name) }
        }
        assertEquals(listOf("com.mk.daftar.android.MainActivity"), exported)
    }

    private fun packageInfo(flags: Int): PackageInfo = context.packageManager.getPackageInfo(context.packageName, flags)
}
