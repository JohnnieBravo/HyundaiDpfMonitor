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

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var live: TextView
    private lateinit var regenBanner: TextView
    private lateinit var path: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startLoggerIfAllowed() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ObdService.ACTION_STATE -> {
                    live.text = intent.getStringExtra(ObdService.EXTRA_TEXT) ?: ""
                    updateRegenBanner(intent.getBooleanExtra(ObdService.EXTRA_REGEN_ACTIVE,false), intent.getBooleanExtra(ObdService.EXTRA_ENGINE_RUNNING,false))
                }
                ObdService.ACTION_STATUS -> {
                    val value=intent.getStringExtra(ObdService.EXTRA_TEXT)?:""
                    status.text=value
                    val logging=value.contains("Logging active",true)||value.contains("Connected",true)
                    startButton.isEnabled=!logging
                    stopButton.isEnabled=logging||value.contains("Starting",true)||value.contains("Scanning",true)
                }
            }
            intent?.getStringExtra(ObdService.EXTRA_LOG_PATH)?.let { path.text="Logs: $it" }
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        status=findViewById(R.id.status); live=findViewById(R.id.liveData); regenBanner=findViewById(R.id.regenBanner); path=findViewById(R.id.logPath)
        startButton=findViewById(R.id.startButton); stopButton=findViewById(R.id.stopButton); stopButton.isEnabled=false
        startButton.setOnClickListener { requestAndStart() }
        stopButton.setOnClickListener { stopService(Intent(this,ObdService::class.java)); status.text="Stopped"; startButton.isEnabled=true; stopButton.isEnabled=false }
    }
    override fun onStart() {
        super.onStart()
        val f=IntentFilter().apply { addAction(ObdService.ACTION_STATE); addAction(ObdService.ACTION_STATUS) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(receiver,f)
    }
    override fun onStop(){ unregisterReceiver(receiver); super.onStop() }
    private fun updateRegenBanner(active:Boolean,running:Boolean){
        if(active&&running){regenBanner.text="DPF REGEN ACTIVE";regenBanner.setBackgroundResource(R.drawable.regen_banner_on)}
        else {regenBanner.text=if(running)"DPF REGEN OFF" else "DPF REGEN --";regenBanner.setBackgroundResource(R.drawable.regen_banner_off)}
    }
    private fun requestAndStart(){
        val needed=mutableListOf<String>()
        if(Build.VERSION.SDK_INT>=31){
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED) needed+=Manifest.permission.BLUETOOTH_SCAN
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) needed+=Manifest.permission.BLUETOOTH_CONNECT
        } else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) needed+=Manifest.permission.ACCESS_FINE_LOCATION
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) needed+=Manifest.permission.POST_NOTIFICATIONS
        if(needed.isEmpty()) startLoggerIfAllowed() else permissions.launch(needed.toTypedArray())
    }
    private fun startLoggerIfAllowed(){ ContextCompat.startForegroundService(this,Intent(this,ObdService::class.java)); status.text="Starting..." }
}
