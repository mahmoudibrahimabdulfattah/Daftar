package com.mk.daftar.core.account

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * The activity that Credential Manager presents its sheet from. The main activity attaches itself when it resumes and
 * detaches when it pauses, so a sign-in never starts from a destroyed window.
 */
object CloudActivity {
    private var reference: WeakReference<Activity>? = null

    /** The attached activity, or null while none is resumed. */
    val current: Activity?
        get() = reference?.get()

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (reference?.get() === activity) reference = null
    }
}
