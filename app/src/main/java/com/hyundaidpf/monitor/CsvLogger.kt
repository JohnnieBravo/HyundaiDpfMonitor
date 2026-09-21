package com.hyundaidpf.monitor

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvLogger(context: Context) {
    private val dir: File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "logs").apply { mkdirs() }
    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val liveFile = File(dir, "hyundai_dpf_live_$stamp.csv")
    val rawFile = File(dir, "hyundai_raw_$stamp.txt")
    val eventFile = File(dir, "hyundai_events_$stamp.csv")
    init {
        liveFile.appendText("timestamp,rpm,speed_kmh,engine_running,engine_state,regen_active,regen_active_type,status_04,regen_trigger_pct,soot_g,dpf_pressure_hpa,turbo_temp_c,catalyst_temp_c,dpf_temp_c,scr_temp_c,avg_regen_distance_km,avg_regen_time_min,regen_aborted\n")
        eventFile.appendText("timestamp,event,duration_s,rpm,speed_kmh,engine_state,dpf_pressure_hpa,turbo_temp_c,dpf_temp_c,status_04\n")
    }
    @Synchronized fun logLive(s: DpfState) {
        val aborted = if (s.engineState == "RUNNING") s.regenAborted else null
        liveFile.appendText(listOf(iso(s.timestamp),s.rpm,s.speedKmh,s.engineRunning,s.engineState,s.regenActive,s.regenActiveType,s.status04,s.regenTriggerPct,s.sootG,s.dpfPressureHpa,s.turboTempC,s.catalystTempC,s.dpfTempC,s.scrTempC,s.avgRegenDistanceKm,s.avgRegenTimeMin,aborted).joinToString(",")+"\n")
    }
    @Synchronized fun raw(label:String, command:String, response:String) {
        rawFile.appendText("\n[${iso(System.currentTimeMillis())}] $label\nTX: $command\nRX:\n$response\n")
    }
    @Synchronized fun event(name:String,s:DpfState,durationS:Double?=null) {
        eventFile.appendText(listOf(iso(System.currentTimeMillis()),name,durationS?:"",s.rpm?:"",s.speedKmh?:"",s.engineState,s.dpfPressureHpa?:"",s.turboTempC?:"",s.dpfTempC?:"",s.status04?:"").joinToString(",")+"\n")
    }
    fun folderPath():String=dir.absolutePath
    private fun iso(ms:Long):String=SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(Date(ms))
}
