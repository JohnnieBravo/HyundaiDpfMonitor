package com.hyundaidpf.monitor

object ObdDecoder {
    private val hexLine=Regex("^[0-9A-Fa-f]{6,}$")
    fun extractCanLines(response:String)=response.lineSequence().map{it.trim()}.filter{hexLine.matches(it)}.map{it.uppercase()}.toList()
    private fun hexToBytes(hex:String):ByteArray?=try{if(hex.length%2!=0)null else ByteArray(hex.length/2){i->hex.substring(i*2,i*2+2).toInt(16).toByte()}}catch(_:Exception){null}
    fun decodeIsoTp(response:String,canId:String="7E8"):ByteArray?{
        val frames=extractCanLines(response).mapNotNull{line->if(!line.startsWith(canId))null else hexToBytes(line.substring(canId.length))}
        if(frames.isEmpty())return null
        val first=frames.first();if(first.isEmpty())return null
        val type=(first[0].toInt() ushr 4)and 0x0F
        if(type==0){val len=first[0].toInt()and 0x0F;if(first.size<1+len)return null;return first.copyOfRange(1,1+len)}
        if(type==1){
            if(first.size<2)return null
            val total=((first[0].toInt()and 0x0F)shl 8)or(first[1].toInt()and 0xFF)
            val out=ArrayList<Byte>(total);first.copyOfRange(2,first.size).forEach{out.add(it)};var seq=1
            for(frame in frames.drop(1)){if(frame.isEmpty())continue;if(((frame[0].toInt() ushr 4)and 0x0F)!=2)continue;if((frame[0].toInt()and 0x0F)!=(seq and 0x0F))return null;frame.copyOfRange(1,frame.size).forEach{out.add(it)};seq++;if(out.size>=total)break}
            if(out.size<total)return null;return ByteArray(total){out[it]}
        };return null
    }
    fun decodeRpm(response:String):Double?{for(line in extractCanLines(response)){if(!line.startsWith("7E8"))continue;val d=hexToBytes(line.substring(3))?:continue;if(d.size>=5&&u(d[1])==0x41&&u(d[2])==0x0C)return((u(d[3])shl 8)or u(d[4]))/4.0};return null}
    fun decodeSpeed(response:String):Int?{for(line in extractCanLines(response)){if(!line.startsWith("7E8"))continue;val d=hexToBytes(line.substring(3))?:continue;if(d.size>=4&&u(d[1])==0x41&&u(d[2])==0x0D)return u(d[3])};return null}
    fun apply018b(response:String,s:DpfState):Boolean{val p=decodeIsoTp(response)?:return false;if(p.size<9||u(p[0])!=0x41||u(p[1])!=0x8B)return false;val st=u(p[3]);s.regenActive=st and 1!=0;s.regenActiveType=st and 2!=0;s.status04=st and 4!=0;s.regenTriggerPct=u(p[4])*100.0/255.0;s.avgRegenTimeMin=(u(p[5])shl 8)or u(p[6]);s.avgRegenDistanceKm=(u(p[7])shl 8)or u(p[8]);s.raw018b=response;return true}
    fun applyEd1d(response:String,s:DpfState):Boolean{val p=decodeIsoTp(response)?:return false;if(p.size<9||u(p[0])!=0x62||u(p[1])!=0xED||u(p[2])!=0x1D)return false;val raw=u(p[7])or(u(p[8])shl 8);s.sootG=raw*256.0/65535.0;s.rawEd1d=response;return true}
    fun applyEd03(response:String,s:DpfState):Boolean{val p=decodeIsoTp(response)?:return false;if(p.size<44||u(p[0])!=0x62||u(p[1])!=0xED||u(p[2])!=0x03)return false;val d=p.copyOfRange(3,p.size);if(d.size<38)return false;s.dpfPressureHpa=i16le(d,13)*0.0829175;s.catalystTempC=temp(d,17);s.dpfTempC=temp(d,19);s.scrTempC=temp(d,21);s.turboTempC=temp(d,23);s.regenAbortedRaw=u(d[37]);s.regenAborted=(u(d[37])and 1)!=0;s.rawEd03=response;return true}
    private fun u(b:Byte)=b.toInt()and 0xFF
    private fun u16le(d:ByteArray,i:Int)=u(d[i])or(u(d[i+1])shl 8)
    private fun i16le(d:ByteArray,i:Int):Int{val x=u16le(d,i);return if(x>=0x8000)x-0x10000 else x}
    private fun temp(d:ByteArray,i:Int)=u16le(d,i)/16.0-273.15
}
