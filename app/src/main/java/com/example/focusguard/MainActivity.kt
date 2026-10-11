package com.example.focusguard

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "focus_guard_prefs"
        const val BLOCKED_APPS_KEY = "blocked_apps"
        const val DEADLINE_KEY = "deadline"
    }

    private lateinit var statusText: TextView
    private lateinit var appsContainer: LinearLayout
    private lateinit var daysInput: EditText
    private lateinit var hoursInput: EditText
    private lateinit var minutesInput: EditText
    private lateinit var startButton: Button
    private lateinit var settingsButton: Button
    private val selectedPackages = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        appsContainer = findViewById(R.id.appsContainer)
        daysInput = findViewById(R.id.daysInput)
        hoursInput = findViewById(R.id.hoursInput)
        minutesInput = findViewById(R.id.minutesInput)
        startButton = findViewById(R.id.startBlockButton)
        settingsButton = findViewById(R.id.settingsButton)

        startButton.setOnClickListener { startBlocking() }
        settingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        loadAppList()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun loadAppList() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val stored = prefs.getStringSet(BLOCKED_APPS_KEY, emptySet()) ?: emptySet()
        selectedPackages.clear()
        selectedPackages.addAll(stored)

        appsContainer.removeAllViews()

        val pm = packageManager
        val launchableApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                appInfo.packageName != packageName &&
                    pm.getLaunchIntentForPackage(appInfo.packageName) != null
            }
            .sortedBy { appInfo ->
                pm.getApplicationLabel(appInfo).toString().lowercase(Locale.getDefault())
            }

        val defaultBlocked = setOf(
            "com.google.android.youtube",
            "com.android.chrome"
        )

        for (appInfo in launchableApps) {
            val packageName = appInfo.packageName
            val label = pm.getApplicationLabel(appInfo).toString()
            val shouldBeChecked = selectedPackages.contains(packageName) || defaultBlocked.contains(packageName)
            if (shouldBeChecked) selectedPackages.add(packageName)

            val checkBox = CheckBox(this).apply {
                text = label
                isChecked = shouldBeChecked
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedPackages.add(packageName)
                    } else {
                        selectedPackages.remove(packageName)
                    }
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                        putStringSet(BLOCKED_APPS_KEY, selectedPackages)
                    }
                }
            }

            appsContainer.addView(checkBox)
        }
    }

    private fun startBlocking() {
        val selected = selectedPackages.toSet().ifEmpty {
            Toast.makeText(this, "Choose at least one app to block.", Toast.LENGTH_SHORT).show()
            return
        }

        val days = readDurationValue(daysInput) ?: return
        val hours = readDurationValue(hoursInput) ?: return
        val minutes = readDurationValue(minutesInput) ?: return
        val maxMinutes = 30L * 24L * 60L
        if (days > 30L || hours > maxMinutes / 60L || minutes > maxMinutes) {
            showDurationError()
            return
        }
        val totalMinutes = days * 24 * 60 + hours * 60 + minutes
        if (totalMinutes !in 1L..maxMinutes) {
            showDurationError()
            return
        }
        val totalMillis = totalMinutes * 60_000L

        val deadline = System.currentTimeMillis() + totalMillis
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit {
            putStringSet(BLOCKED_APPS_KEY, selected)
            putLong(DEADLINE_KEY, deadline)
        }

        Toast.makeText(this, "Focus lock started.", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun readDurationValue(input: EditText): Long? {
        val text = input.text.toString().trim()
        val value = if (text.isEmpty()) 0L else text.toLongOrNull()
        if (value == null || value < 0L) {
            showDurationError()
            return null
        }
        return value
    }

    private fun showDurationError() {
        Toast.makeText(this, "Set a duration from 1 minute up to 30 days.", Toast.LENGTH_LONG).show()
    }

    private fun refreshStatus() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val deadline = prefs.getLong(DEADLINE_KEY, 0L)

        if (deadline > System.currentTimeMillis()) {
            val remaining = deadline - System.currentTimeMillis()
            val targetText = formatDuration(remaining)
            statusText.text = getString(R.string.status_running, targetText)
            startButton.isEnabled = false
        } else {
            statusText.text = getString(R.string.status_idle)
            prefs.edit {
                remove(DEADLINE_KEY)
            }
        }

        val isLocked = deadline > System.currentTimeMillis()
        startButton.isEnabled = !isLocked
        daysInput.isEnabled = !isLocked
        hoursInput.isEnabled = !isLocked
        minutesInput.isEnabled = !isLocked
        settingsButton.isEnabled = !isLocked
        for (index in 0 until appsContainer.childCount) {
            appsContainer.getChildAt(index).isEnabled = !isLocked
        }
    }

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
