package com.hyundaidpf.monitor

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class ObdService : Service() {
    companion object {
        const val ACTION_STATE="com.hyundaidpf.monitor.STATE"
        const val ACTION_STATUS="com.hyundaidpf.monitor.STATUS"
        const val EXTRA_TEXT="text"; const val EXTRA_LOG_PATH="log_path"
        const val EXTRA_REGEN_ACTIVE="regen_active"; const val EXTRA_ENGINE_RUNNING="engine_running"
        private const val CHANNEL="obd_logger"; private const val NOTIFICATION_ID=1001
        private const val TARGET_MAC="c5:57:46:dc:6c:e9"; private const val TARGET_NAME="vLinker MC-IOS"
        private val SERVICE_UUID=UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb")
        private val RX_UUID=UUID.fromString("00002af0-0000-1000-8000-00805f9b34fb")
        private val TX_UUID=UUID.fromString("00002af1-0000-1000-8000-00805f9b34fb")
    }
    private lateinit var btManager:BluetoothManager
    private var scanner:android.bluetooth.le.BluetoothLeScanner?=null
    private var gatt:BluetoothGatt?=null; private var rx:BluetoothGattCharacteristic?=null; private var tx:BluetoothGattCharacteristic?=null
    private val state=DpfState(); private lateinit var logger:CsvLogger
    private data class Pending(val label:String,val command:String,val logRaw:Boolean,val onDone:(String)->Unit)
    private val queue=ConcurrentLinkedQueue<Pending>(); @Volatile private var busy=false
    private val response=StringBuilder(); private var timeoutThread:Thread?=null
    private var status04StartedAt:Long?=null
    private var confirmedRegen:Boolean?=null
    private var regenCandidate:Boolean?=null
    private var regenCandidateCount=0
    private val regenConfirmSamples=3
    private var tts:TextToSpeech?=null; private var ttsReady=false
    private val tone by lazy{ToneGenerator(AudioManager.STREAM_NOTIFICATION,80)}

    override fun onCreate(){super.onCreate();btManager=getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager;logger=CsvLogger(this);initTts();createChannel();startForeground(NOTIFICATION_ID,notification("Starting vLinker logger"));sendStatus("Starting");startScan()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_STICKY
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy(){if(scanPermitted())scanner?.stopScan(scanCallback);if(permitted()){gatt?.disconnect();gatt?.close()};gatt=null;tts?.stop();tts?.shutdown();try{tone.release()}catch(_:Exception){};super.onDestroy()}
    private fun permitted()=Build.VERSION.SDK_INT<31||ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    private fun scanPermitted()=Build.VERSION.SDK_INT<31||ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED

    private fun startScan(){
        if(!scanPermitted()){sendStatus("Bluetooth scan permission missing");stopSelf();return}
        val adapter=btManager.adapter?:run{sendStatus("Bluetooth unavailable");stopSelf();return}
        scanner=adapter.bluetoothLeScanner;sendStatus("Scanning for vLinker...")
        scanner?.startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback)
    }
    private val scanCallback=object:ScanCallback(){
        override fun onScanResult(callbackType:Int,result:ScanResult){
            val d=result.device;val name=try{if(permitted())d.name else null}catch(_:Exception){null}
            if(d.address.equals(TARGET_MAC,true)||name==TARGET_NAME){if(scanPermitted())scanner?.stopScan(this);connect(d)}
        }
    }
    private fun connect(device:BluetoothDevice){if(!permitted())return;sendStatus("Connecting to ${device.address}...");gatt=device.connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE)}
    private val gattCallback=object:BluetoothGattCallback(){
        override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int){
            if(newState==BluetoothProfile.STATE_CONNECTED){sendStatus("Connected, discovering services...");if(permitted())g.discoverServices()}
            else if(newState==BluetoothProfile.STATE_DISCONNECTED){sendStatus("Disconnected - rescanning");rx=null;tx=null;busy=false;queue.clear();response.clear();if(permitted())g.close();gatt=null;Thread{Thread.sleep(1500);startScan()}.start()}
        }
        override fun onServicesDiscovered(g:BluetoothGatt,status:Int){
            val svc=g.getService(SERVICE_UUID);rx=svc?.getCharacteristic(RX_UUID);tx=svc?.getCharacteristic(TX_UUID)
            if(rx==null||tx==null){sendStatus("18F0/2AF0/2AF1 not found");if(permitted())g.disconnect();return}
            if(!permitted())return;g.setCharacteristicNotification(rx,true)
            val cccd=rx?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if(cccd!=null){if(Build.VERSION.SDK_INT>=33)g.writeDescriptor(cccd,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) else{@Suppress("DEPRECATION") cccd.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;@Suppress("DEPRECATION") g.writeDescriptor(cccd)}}
            Thread{Thread.sleep(600);initializeAdapter()}.start()
        }
        @Deprecated("Deprecated in API 33") override fun onCharacteristicChanged(gatt:BluetoothGatt,characteristic:BluetoothGattCharacteristic){onRx(characteristic.value?:return)}
        override fun onCharacteristicChanged(gatt:BluetoothGatt,characteristic:BluetoothGattCharacteristic,value:ByteArray){onRx(value)}
    }
    @Synchronized private fun onRx(bytes:ByteArray){response.append(bytes.toString(Charsets.US_ASCII));if(response.toString().trimEnd().endsWith(">"))finishCommand()}
    private fun initializeAdapter(){
        listOf("ATZ","ATE0","ATH1","ATS0","ATM0","ATAT1","ATAL","ATSP6").forEach{enqueue("INIT",it,false){}}
        enqueue("READY","ATI",false){sendStatus("Logging active");pollCycle()}
    }
    private fun pollCycle(){
        if(gatt==null||tx==null)return
        enqueue("HDR_7DF","ATSH7DF",false){}
        enqueue("RPM","010C",false){ObdDecoder.decodeRpm(it)?.let{v->state.rpm=v}}
        enqueue("SPEED","010D",false){ObdDecoder.decodeSpeed(it)?.let{v->state.speedKmh=v}}
        enqueue("PID_8B","018B",true){ObdDecoder.apply018b(it,state)}
        enqueue("HDR_7E0","ATSH7E0",false){}
        enqueue("ED03","22ED03",true){ObdDecoder.applyEd03(it,state)}
        enqueue("ED1D","22ED1D",true){ObdDecoder.applyEd1d(it,state);state.engineRunning=(state.rpm?:0.0)>300.0;state.timestamp=System.currentTimeMillis();processEvents();logger.logLive(state);broadcastState();Thread{Thread.sleep(1000);pollCycle()}.start()}
    }
    private fun processEvents(){
        val current04=state.status04==true&&state.engineRunning
        if(current04&&status04StartedAt==null){status04StartedAt=System.currentTimeMillis();logger.event("STATUS_04_START",state)}
        else if(!current04&&status04StartedAt!=null){val d=(System.currentTimeMillis()-status04StartedAt!!)/1000.0;logger.event("STATUS_04_END",state,d);status04StartedAt=null}
        val r=state.regenActive
        if(!state.engineRunning||r==null){
            regenCandidate=null
            regenCandidateCount=0
            return
        }
        if(r==regenCandidate){
            regenCandidateCount++
        } else {
            regenCandidate=r
            regenCandidateCount=1
        }
        if(regenCandidateCount<regenConfirmSamples)return
        if(confirmedRegen==null){
            confirmedRegen=r
            return
        }
        if(confirmedRegen!=r){
            confirmedRegen=r
            logger.event(if(r)"REGEN_START" else "REGEN_END",state)
            if(r)announceRegenStart() else announceRegenEnd()
        }
    }
    private fun initTts(){tts=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){val e=tts?:return@TextToSpeech;val sr=Locale("sr","RS");val lr=e.setLanguage(sr);if(lr==TextToSpeech.LANG_MISSING_DATA||lr==TextToSpeech.LANG_NOT_SUPPORTED)e.setLanguage(Locale("sr"));e.setSpeechRate(.95f);e.setPitch(1.08f);e.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());selectPreferredFemaleVoice(e);ttsReady=true}}}
    private fun selectPreferredFemaleVoice(e:TextToSpeech){try{val vs=e.voices?.filter{it.locale.language.equals("sr",true)}?:return;if(vs.isNotEmpty())e.voice=vs.firstOrNull{val n=it.name.lowercase(Locale.ROOT);n.contains("female")||n.contains("woman")||n.contains("fem")||n.contains("sr-rs-x-sre")||n.contains("sr-rs-x-srf")}?:vs.first()}catch(_:Exception){}}
    private fun announceRegenStart(){try{tone.startTone(ToneGenerator.TONE_PROP_BEEP2,220)}catch(_:Exception){};if(ttsReady)tts?.speak("Regeneracija u toku",TextToSpeech.QUEUE_FLUSH,null,"regen_start")}
    private fun announceRegenEnd(){if(ttsReady)tts?.speak("Regeneracija završena",TextToSpeech.QUEUE_FLUSH,null,"regen_end")}
    private fun enqueue(label:String,command:String,raw:Boolean,done:(String)->Unit){queue.add(Pending(label,command,raw,done));pump()}
    @Synchronized private fun pump(){
        if(busy)return;val p=queue.peek()?:return;val c=tx?:return;val g=gatt?:return;if(!permitted())return
        busy=true;response.clear();val bytes=(p.command+"\r").toByteArray(Charsets.US_ASCII)
        val ok=if(Build.VERSION.SDK_INT>=33)g.writeCharacteristic(c,bytes,BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)==BluetoothStatusCodes.SUCCESS else{@Suppress("DEPRECATION") c.writeType=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;@Suppress("DEPRECATION") c.value=bytes;@Suppress("DEPRECATION") g.writeCharacteristic(c)}
        if(!ok){busy=false;queue.poll();pump();return};timeoutThread?.interrupt();timeoutThread=Thread{try{Thread.sleep(3500);synchronized(this){if(busy)finishCommand()}}catch(_:InterruptedException){}}.also{it.start()}
    }
    @Synchronized private fun finishCommand(){if(!busy)return;timeoutThread?.interrupt();timeoutThread=null;val p=queue.poll()?:run{busy=false;return};val text=response.toString().trim();if(p.logRaw)logger.raw(p.label,p.command,text);try{p.onDone(text)}catch(_:Exception){};busy=false;pump()}
    private fun broadcastState(){
        val shownRegen=confirmedRegen?:state.regenActive
        val text=buildString{
            appendLine("RPM              : ${state.rpm?.let{"%.0f".format(it)}?:"?"}");appendLine("SPEED            : ${state.speedKmh?:"?"} km/h");appendLine("ENGINE           : ${if(state.engineRunning)"RUNNING" else "OFF"}");appendLine()
            appendLine("REGEN            : ${if(shownRegen==true)"ON" else "OFF"}");appendLine("STATUS 0x04      : ${if(state.status04==true)"ON" else "OFF"}");appendLine("DPF LOAD         : ${state.regenTriggerPct?.let{"%.2f %%".format(it)}?:"?"}");appendLine("SOOT             : ${state.sootG?.let{"%.3f g".format(it)}?:"?"}");appendLine("DPF DELTA-P      : ${state.dpfPressureHpa?.let{"%.2f hPa".format(it)}?:"?"}");appendLine()
            appendLine("TURBO UPSTREAM   : ${state.turboTempC?.let{"%.1f C".format(it)}?:"?"}");appendLine("CAT UPSTREAM     : ${state.catalystTempC?.let{"%.1f C".format(it)}?:"?"}");appendLine("DPF UPSTREAM     : ${state.dpfTempC?.let{"%.1f C".format(it)}?:"?"}");appendLine("SCR UPSTREAM     : ${state.scrTempC?.let{"%.1f C".format(it)}?:"?"}");appendLine();appendLine("AVG REGEN DIST   : ${state.avgRegenDistanceKm?:"?"} km");appendLine("AVG REGEN TIME   : ${state.avgRegenTimeMin?:"?"} min")
        }
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra(EXTRA_TEXT,text).putExtra(EXTRA_LOG_PATH,logger.folderPath()).putExtra(EXTRA_REGEN_ACTIVE,shownRegen==true).putExtra(EXTRA_ENGINE_RUNNING,state.engineRunning))
        val nt=if(shownRegen==true&&state.engineRunning)"DPF REGEN ACTIVE | ${state.rpm?.toInt()?:0} rpm" else "${state.rpm?.toInt()?:0} rpm | Soot ${state.sootG?.let{"%.2f".format(it)}?:"?"} g"
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification(nt))
    }
    private fun sendStatus(text:String){sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_TEXT,text).putExtra(EXTRA_LOG_PATH,logger.folderPath()))}
    private fun createChannel(){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"OBD logging",NotificationManager.IMPORTANCE_LOW))}
    private fun notification(text:String)=NotificationCompat.Builder(this,CHANNEL).setContentTitle("Hyundai DPF Monitor").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOngoing(true).build()
}
