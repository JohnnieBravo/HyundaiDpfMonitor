package com.hyundaidpf.monitor

data class DpfState(
    var timestamp: Long = System.currentTimeMillis(),
    var rpm: Double? = null,
    var speedKmh: Int? = null,
    var engineRunning: Boolean = false,
    var engineState: String = "UNKNOWN",
    var regenActive: Boolean? = null,
    var regenActiveType: Boolean? = null,
    var status04: Boolean? = null,
    var regenTriggerPct: Double? = null,
    var sootG: Double? = null,
    var dpfPressureHpa: Double? = null,
    var turboTempC: Double? = null,
    var catalystTempC: Double? = null,
    var dpfTempC: Double? = null,
    var scrTempC: Double? = null,
    var avgRegenDistanceKm: Int? = null,
    var avgRegenTimeMin: Int? = null,
    var regenAborted: Boolean? = null,
    var regenAbortedRaw: Int? = null,
    var raw018b: String = "",
    var rawEd03: String = "",
    var rawEd1d: String = ""
)
