package com.example.focusguard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.TimeUnit

class BlockedActivity : AppCompatActivity() {

    private lateinit var timerText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var deadline: Long = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            val remaining = if (deadline > 0L) deadline - System.currentTimeMillis() else 0L
            timerText.text = formatDuration(remaining)
            if (remaining > 0L) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        timerText = findViewById(R.id.timerText)

        val packageName = intent.getStringExtra("blocked_package") ?: "blocked app"
        val packageLabel = packageName
        val blockedText = findViewById<TextView>(R.id.blockedMessage)
        blockedText.text = "This app is blocked by Focus Guard\n\nAttempted app: $packageLabel"

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        deadline = prefs.getLong(MainActivity.DEADLINE_KEY, 0L)

        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun formatDuration(ms: Long): String {
        val safeValue = if (ms < 0L) 0L else ms
        val hours = TimeUnit.MILLISECONDS.toHours(safeValue)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safeValue) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safeValue) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
