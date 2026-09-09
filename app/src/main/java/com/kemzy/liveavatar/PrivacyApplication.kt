package com.kemzy.liveavatar

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

class PrivacyApplication : Application() {
    @Volatile
    var unlocked: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0 && activity !is PrivacyLockActivity) {
                    unlocked = false
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity !is PrivacyLockActivity && !unlocked) {
                    activity.startActivity(
                        Intent(activity, PrivacyLockActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun markUnlocked() {
        unlocked = true
    }
}
