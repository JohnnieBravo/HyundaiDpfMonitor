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
    private var lastRenderedSignature = ""
    private var refreshPending = false
    private val refreshRunnable = Runnable {
        refreshPending = false
        val signature = displaySignature()
        if (signature != lastRenderedSignature) {
            lastRenderedSignature = signature
            lastInvalidateAt = SystemClock.elapsedRealtime()
            invalidate()
        }
    }

    private fun requestRefresh(immediate: Boolean = false) {
        val signature = displaySignature()
        if (signature == lastRenderedSignature) return

        if (immediate) {
            handler.removeCallbacks(refreshRunnable)
            refreshPending = false
            lastRenderedSignature = signature
            lastInvalidateAt = SystemClock.elapsedRealtime()
            invalidate()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val wait = 5000L - (now - lastInvalidateAt)
        if (wait <= 0L) {
            handler.removeCallbacks(refreshRunnable)
            refreshPending = false
            lastRenderedSignature = signature
            lastInvalidateAt = now
            invalidate()
        } else if (!refreshPending) {
            refreshPending = true
            handler.postDelayed(refreshRunnable, wait)
        }
    }

    private fun displaySignature(): String {
        val live = parseLive(prefs.getString(ObdService.PREF_LIVE, null))
        val connected = prefs.getBoolean(ObdService.PREF_CONNECTED, false)
        val regen = prefs.getBoolean(ObdService.PREF_REGEN_ACTIVE, false)
        val soon = prefs.getBoolean(ObdService.PREF_REGEN_SOON, false)
        val running = prefs.getBoolean(ObdService.PREF_ENGINE_RUNNING, false)

        // Deliberately quantize rapidly changing values. The phone logger keeps
        // full resolution; this affects only the Android Auto presentation.
        val temp = numeric(live["DPF UPSTREAM"])?.let { kotlin.math.round(it / 5.0) * 5.0 }
        return listOf(
            connected, regen, soon, running,
            compactNumber(live["DPF LOAD"], 1, "%"),
            compactNumber(live["SOOT"], 2, "g"),
            temp
        ).joinToString("|")
    }

    private fun numeric(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        return Regex("""-?\\d+(?:[.,]\\d+)?""")
            .find(value)?.value?.replace(',', '.')?.toDoubleOrNull()
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
                val urgent = key == ObdService.PREF_REGEN_ACTIVE ||
                    key == ObdService.PREF_REGEN_SOON ||
                    key == ObdService.PREF_CONNECTED
                requestRefresh(urgent)
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
        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("DPF REGEN")
                    .addText(regenText)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("LOAD / SOOT")
                    .addText("$load   •   $soot")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("DPF TEMPERATURE")
                    .addText(dpfTemp)
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
