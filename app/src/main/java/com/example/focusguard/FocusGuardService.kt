package com.example.focusguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.edit

class FocusGuardService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (isBlockedPackage(packageName)) {
            val intent = Intent(this, BlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_package", packageName)
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() = Unit

    private fun isBlockedPackage(packageName: String): Boolean {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val deadline = prefs.getLong(MainActivity.DEADLINE_KEY, 0L)
        if (deadline <= 0L || deadline <= System.currentTimeMillis()) {
            prefs.edit {
                remove(MainActivity.DEADLINE_KEY)
            }
            return false
        }

        val blockedPackages = prefs.getStringSet(MainActivity.BLOCKED_APPS_KEY, emptySet()) ?: emptySet()
        return packageName in blockedPackages
    }
}
