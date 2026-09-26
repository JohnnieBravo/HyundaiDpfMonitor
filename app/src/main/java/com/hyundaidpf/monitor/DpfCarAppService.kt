package com.hyundaidpf.monitor

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = DpfCarSession()
}

private class DpfCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = DpfCarScreen(carContext)
}

private class DpfCarScreen(carContext: CarContext) : Screen(carContext) {
    private val prefs = carContext.getSharedPreferences(ObdService.UI_PREFS, Context.MODE_PRIVATE)

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
                invalidate()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
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

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("DPF regeneration")
                    .addText(regenText)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("DPF load / soot")
                    .addText("${live["DPF LOAD"] ?: "?"}  |  ${live["SOOT"] ?: "?"}")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("DPF temperature")
                    .addText(live["DPF UPSTREAM"] ?: "?")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Engine")
                    .addText("${live["RPM"] ?: "?"}  |  ${live["SPEED"] ?: "?"}")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Last regen average")
                    .addText("${live["AVG REGEN DIST"] ?: "?"}  |  ${live["AVG REGEN TIME"] ?: "?"}")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(if (connected) "Connected" else "Not connected")
                    .addText(status)
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Hyundai DPF Monitor")
            .setHeaderAction(Action.APP_ICON)
            .build()
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
