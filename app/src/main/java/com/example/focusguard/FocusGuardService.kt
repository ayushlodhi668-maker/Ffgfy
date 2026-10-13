package com.example.focusguard

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import java.util.Locale

private data class YoutubeSurface(
    val subscriptionsSelected: Boolean = false,
    val homeSelected: Boolean = false,
    val shortsSelected: Boolean = false,
    val hasPlayerControls: Boolean = false,
    val subscribedChannel: Boolean = false,
    val unsubscribedChannel: Boolean = false
)

class FocusGuardService : AccessibilityService() {

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val YOUTUBE_SUBSCRIPTIONS_URL = "https://www.youtube.com/feed/subscriptions"
        private const val VIDEO_ENTRY_WINDOW_MS = 20_000L
        private const val REDIRECT_INTERVAL_MS = 1_500L
    }

    private var lastSubscriptionsFeedAt = 0L
    private var lastYoutubeRedirectAt = 0L
    private var youtubePlayerWasAllowed = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == YOUTUBE_PACKAGE && isSelectedAndLockActive(packageName)) {
            if (!isAllowedYoutubeSurface()) {
                redirectToSubscriptions()
            }
            return
        }

        if (isBlockedPackage(packageName)) {
            val intent = Intent(this, BlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("blocked_package", packageName)
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun isBlockedPackage(packageName: String): Boolean {
        if (!isSelectedAndLockActive(packageName)) return false
        return true
    }

    private fun isSelectedAndLockActive(packageName: String): Boolean {
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

    private fun isAllowedYoutubeSurface(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val root = rootInActiveWindow ?: return youtubePlayerWasAllowed ||
            (lastSubscriptionsFeedAt > 0L && now - lastSubscriptionsFeedAt <= VIDEO_ENTRY_WINDOW_MS)
        val surface = readYoutubeSurface(root)

        if (surface.homeSelected || surface.shortsSelected || surface.unsubscribedChannel) {
            youtubePlayerWasAllowed = false
            return false
        }

        val enteredFromSubscriptions = lastSubscriptionsFeedAt > 0L &&
            now - lastSubscriptionsFeedAt <= VIDEO_ENTRY_WINDOW_MS
        if (surface.hasPlayerControls) {
            if (surface.subscribedChannel && enteredFromSubscriptions) {
                youtubePlayerWasAllowed = true
                return true
            }
            youtubePlayerWasAllowed = false
            return false
        }

        if (surface.subscriptionsSelected) {
            lastSubscriptionsFeedAt = now
            youtubePlayerWasAllowed = false
            return true
        }

        return youtubePlayerWasAllowed
    }

    private fun readYoutubeSurface(root: AccessibilityNodeInfo): YoutubeSurface {
        var subscriptionsSelected = false
        var homeSelected = false
        var shortsSelected = false
        var hasPlayerControls = false
        var subscribedChannel = false
        var unsubscribedChannel = false

        fun inspect(node: AccessibilityNodeInfo) {
            val text = buildString {
                append(node.text ?: "")
                append(' ')
                append(node.contentDescription ?: "")
            }.lowercase(Locale.ROOT)
            val selected = node.isSelected || node.isChecked
            val className = node.className?.toString()?.lowercase(Locale.ROOT).orEmpty()

            if (selected && text.hasAny("subscriptions", "subscription", "सदस्यताएं", "सदस्यताएँ", "सब्सक्रिप्शन")) {
                subscriptionsSelected = true
            }
            if (selected && text.contains("home")) homeSelected = true
            if (selected && text.contains("shorts")) shortsSelected = true
            if (className.contains("seekbar") || text.hasAny("seek bar", "seekbar")) {
                hasPlayerControls = true
            }
            if (text.hasAny("subscribed", "unsubscribe", "सदस्यता ली गई", "सदस्यता रद्द करें")) {
                subscribedChannel = true
            }
            if ((text.contains("subscribe") && !text.contains("subscribed") &&
                    !text.contains("unsubscribe") && !text.contains("subscriptions")) ||
                text.hasAny("सदस्यता लें", "चैनल की सदस्यता लें")
            ) {
                unsubscribedChannel = true
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::inspect)
            }
        }

        inspect(root)
        return YoutubeSurface(
            subscriptionsSelected = subscriptionsSelected,
            homeSelected = homeSelected,
            shortsSelected = shortsSelected,
            hasPlayerControls = hasPlayerControls,
            subscribedChannel = subscribedChannel,
            unsubscribedChannel = unsubscribedChannel
        )
    }

    private fun redirectToSubscriptions() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastYoutubeRedirectAt < REDIRECT_INTERVAL_MS) return
        lastYoutubeRedirectAt = now

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_SUBSCRIPTIONS_URL)).apply {
            setPackage(YOUTUBE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val blockedIntent = Intent(this, BlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_package", YOUTUBE_PACKAGE)
            }
            startActivity(blockedIntent)
        }
    }

    private fun String.hasAny(vararg values: String): Boolean = values.any(::contains)
}
