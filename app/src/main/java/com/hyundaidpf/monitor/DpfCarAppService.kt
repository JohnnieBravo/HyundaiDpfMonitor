package com.hyundaidpf.monitor

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

class DpfCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session = DpfCarSession()
}

private class DpfCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = DpfCarScreen(carContext)
}

private class DpfCarScreen(carContext: CarContext) : Screen(carContext) {
    private val prefs = carContext.getSharedPreferences(ObdService.UI_PREFS, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var lastInvalidateAt = 0L
    private var refreshPending = false
    private val refreshRunnable = Runnable {
        refreshPending = false
        lastInvalidateAt = SystemClock.elapsedRealtime()
        invalidate()
    }

    private fun requestRefresh() {
        val now = SystemClock.elapsedRealtime()
        val wait = 1000L - (now - lastInvalidateAt)
        if (wait <= 0L) {
            handler.removeCallbacks(refreshRunnable)
            refreshPending = false
            lastInvalidateAt = now
            invalidate()
        } else if (!refreshPending) {
            refreshPending = true
            handler.postDelayed(refreshRunnable, wait)
        }
    }

    private val prefListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == ObdService.PREF_LIVE ||
                key == ObdService.PREF_STATUS ||
                key == ObdService.PREF_CONNECTED ||
                key == ObdService.PREF_REGEN_ACTIVE ||
                key == ObdService.PREF_ENGINE_RUNNING ||
                key == ObdService.PREF_REGEN_SOON
            ) {
                requestRefresh()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }


    override fun onGetTemplate(): Template {
        val connected = prefs.getBoolean(ObdService.PREF_CONNECTED, false)
        val regen = prefs.getBoolean(ObdService.PREF_REGEN_ACTIVE, false)
        val soon = prefs.getBoolean(ObdService.PREF_REGEN_SOON, false)
        val running = prefs.getBoolean(ObdService.PREF_ENGINE_RUNNING, false)
        val status = prefs.getString(ObdService.PREF_STATUS, "Disconnected") ?: "Disconnected"
        val live = parseLive(prefs.getString(ObdService.PREF_LIVE, null))

        val regenText = when {
            regen && running -> "ACTIVE"
            soon && running -> "SOON"
            running -> "OFF"
            else -> "--"
        }

        val load = compactNumber(live["DPF LOAD"], 1, "%")
        val soot = compactNumber(live["SOOT"], 2, "g")
        val dpfTemp = compactNumber(live["DPF UPSTREAM"], 0, "C")
        val pressure = compactNumber(live["DPF DELTA-P"], 0, "hPa")
        val rpm = compactNumber(live["RPM"], 0, "rpm")
        val speed = compactNumber(live["SPEED"], 0, "km/h")
        val avgDist = compactNumber(live["AVG REGEN DIST"], 0, "km")
        val avgTime = compactNumber(live["AVG REGEN TIME"], 0, "min")

        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("DPF REGEN  $regenText")
                    .addText("Load $load   •   Soot $soot")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("DPF")
                    .addText("$dpfTemp   •   ΔP $pressure")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("ENGINE")
                    .addText("$rpm   •   $speed")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("LAST REGEN AVERAGE")
                    .addText("$avgDist   •   $avgTime")
                    .build()
            )

        if (!connected) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("NOT CONNECTED")
                    .addText(status)
                    .build()
            )
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Hyundai DPF Monitor")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun compactNumber(value: String?, decimals: Int, unit: String): String {
        if (value.isNullOrBlank()) return "?"
        val number = Regex("""-?\d+(?:[.,]\d+)?""")
            .find(value)
            ?.value
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: return value

        val formatted = if (decimals == 0) {
            kotlin.math.round(number).toLong().toString()
        } else {
            "%.${decimals}f".format(java.util.Locale.US, number)
        }
        return "$formatted $unit"
    }

    private fun parseLive(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) {
                val key = line.substring(0, i).trim()
                val value = line.substring(i + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
            }
        }
        return out
    }
}
