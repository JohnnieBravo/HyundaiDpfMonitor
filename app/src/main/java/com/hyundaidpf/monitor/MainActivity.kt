package com.hyundaidpf.monitor

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView

class MainActivity : AppCompatActivity() {
    private lateinit var rootScroll: NestedScrollView
    private lateinit var status: TextView
    private lateinit var live: TextView
    private lateinit var regenBanner: TextView
    private lateinit var path: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var ecuInfoButton: Button
    private lateinit var ecuInfo: TextView

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startLoggerIfAllowed() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ObdService.ACTION_STATE -> {
                    live.text = intent.getStringExtra(ObdService.EXTRA_TEXT) ?: ""
                    updateRegenBanner(
                        intent.getBooleanExtra(ObdService.EXTRA_REGEN_ACTIVE, false),
                        intent.getBooleanExtra(ObdService.EXTRA_ENGINE_RUNNING, false)
                    )
                }

                ObdService.ACTION_ECU_INFO -> {
                    ecuInfo.text = intent.getStringExtra(ObdService.EXTRA_TEXT) ?: "No ECU information"
                }

                ObdService.ACTION_STATUS -> {
                    val value = intent.getStringExtra(ObdService.EXTRA_TEXT) ?: ""
                    status.text = value
                    val connected = intent.getBooleanExtra(ObdService.EXTRA_CONNECTED, false)
                    val activeOrStarting = connected ||
                        value.contains("Starting", true) ||
                        value.contains("Scanning", true) ||
                        value.contains("Connecting", true)
                    startButton.isEnabled = !activeOrStarting
                    stopButton.isEnabled = activeOrStarting
                    ecuInfoButton.isEnabled = connected
                }
            }

            intent?.getStringExtra(ObdService.EXTRA_LOG_PATH)?.let {
                path.text = "Logs: $it"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootScroll = findViewById(R.id.rootScroll)
        status = findViewById(R.id.status)
        live = findViewById(R.id.liveData)
        regenBanner = findViewById(R.id.regenBanner)
        path = findViewById(R.id.logPath)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        ecuInfoButton = findViewById(R.id.ecuInfoButton)
        ecuInfo = findViewById(R.id.ecuInfo)

        stopButton.isEnabled = false
        ecuInfoButton.isEnabled = false

        startButton.setOnClickListener { requestAndStart() }

        stopButton.setOnClickListener {
            stopService(Intent(this, ObdService::class.java))
            status.text = "Stopped"
            startButton.isEnabled = true
            stopButton.isEnabled = false
            ecuInfoButton.isEnabled = false
        }

        ecuInfoButton.setOnClickListener {
            startService(
                Intent(this, ObdService::class.java)
                    .setAction(ObdService.ACTION_READ_ECU_INFO)
            )
            ecuInfo.text = "Reading ECU information..."
        }

        // Orientation changes recreate the Activity. The logger itself remains in
        // ObdService; only reset the newly-created UI to a sensible scroll position.
        rootScroll.post { rootScroll.scrollTo(0, 0) }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ObdService.ACTION_STATE)
            addAction(ObdService.ACTION_STATUS)
            addAction(ObdService.ACTION_ECU_INFO)
        }


        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        restoreCachedUi()
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun restoreCachedUi() {
        val prefs = getSharedPreferences(ObdService.UI_PREFS, MODE_PRIVATE)
        val cachedStatus = prefs.getString(ObdService.PREF_STATUS, null)
        val cachedLive = prefs.getString(ObdService.PREF_LIVE, null)
        val cachedEcu = prefs.getString(ObdService.PREF_ECU_INFO, null)
        val cachedPath = prefs.getString(ObdService.PREF_LOG_PATH, null)
        val connected = prefs.getBoolean(ObdService.PREF_CONNECTED, false)
        val regenActive = prefs.getBoolean(ObdService.PREF_REGEN_ACTIVE, false)
        val engineRunning = prefs.getBoolean(ObdService.PREF_ENGINE_RUNNING, false)

        if (cachedStatus != null) status.text = cachedStatus
        if (cachedLive != null) live.text = cachedLive
        if (cachedEcu != null) ecuInfo.text = cachedEcu
        if (cachedPath != null) path.text = "Logs: $cachedPath"
        updateRegenBanner(regenActive, engineRunning)

        startButton.isEnabled = !connected
        stopButton.isEnabled = connected
        ecuInfoButton.isEnabled = connected
    }

    private fun updateRegenBanner(active: Boolean, running: Boolean) {
        if (active && running) {
            regenBanner.text = "DPF REGEN ACTIVE"
            regenBanner.setBackgroundResource(R.drawable.regen_banner_on)
        } else {
            regenBanner.text = if (running) "DPF REGEN OFF" else "DPF REGEN --"
            regenBanner.setBackgroundResource(R.drawable.regen_banner_off)
        }
    }

    private fun requestAndStart() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.BLUETOOTH_SCAN
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }
        } else if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isEmpty()) {
            startLoggerIfAllowed()
        } else {
            permissions.launch(needed.toTypedArray())
        }
    }

    private fun startLoggerIfAllowed() {
        ContextCompat.startForegroundService(this, Intent(this, ObdService::class.java))
        status.text = "Starting..."
    }
}
