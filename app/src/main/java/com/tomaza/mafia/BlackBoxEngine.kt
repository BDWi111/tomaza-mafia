package com.tomaza.mafia

import android.app.Activity
import top.niunaijun.blackbox.BlackBoxCore

class BlackBoxEngine : CloneEngine {
    override fun launch(activity: Activity, entry: CloneEntry, profile: DeviceProfile) {
        val core = BlackBoxCore.get()
        val prefs = activity.getSharedPreferences("bb", Activity.MODE_PRIVATE)
        val key = "installed_${entry.id}"
        if (!prefs.getBoolean(key, false)) {
            core.installPackageAsUser(entry.pkg, entry.id)
            prefs.edit().putBoolean(key, true).apply()
        }
        core.launchApk(entry.pkg, entry.id)
    }
}