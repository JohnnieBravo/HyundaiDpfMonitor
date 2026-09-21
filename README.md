# Hyundai DPF Monitor

**Current version: 0.3.0**

Direct Android BLE logger for vLinker MC+ / Hyundai Tucson.

## What it does
- Direct BLE connection to vLinker MC+
- Foreground logging that continues with the screen off
- Responsive portrait / landscape UI
- Preserves live connection state and ECU information across rotation and app resume
- Handles Hyundai Start/Stop engine transitions in the logger
- Reads basic ECU identifiers (VIN, part/spare number, software/supplier info)
- Scans for `vLinker MC-IOS` or MAC `c5:57:46:dc:6c:e9`
- BLE service `18F0`, RX notify `2AF0`, TX write `2AF1`
- Polls `010C`, `010D`, `018B`, `22ED03`, `22ED1D`
- Displays RPM, speed, DPF delta-P, soot, four temperatures and regen state
- Treats status bit `0x04` as an unknown aftertreatment event
- Logs CSV, raw TXT and event CSV
- Runs as a foreground service with screen off
- Announces DPF regeneration start/end using Serbian TTS

## Build
Open in Android Studio and use JDK 17.

## Logs
`Android/data/com.hyundaidpf.monitor/files/Documents/logs/`

## Protocol note
Hyundai-specific mappings are based on observed Tucson NX4 logs and are still being reverse-engineered. Raw logging is preserved.


## Disclaimer

**This is an unofficial, independent community project. It is not an official Hyundai Motor Company application, diagnostic tool, product, or service, and it is not affiliated with, endorsed by, sponsored by, or supported by Hyundai Motor Company or its dealers.**

This software is intended for research, educational, diagnostic-observation, and enthusiast use. Hyundai-specific parameters, DIDs, bit mappings, formulas, and interpretations in this project have been derived from observed vehicle data and reverse-engineering and may be incomplete, incorrect, or incompatible with other vehicles, ECUs, software versions, model years, or markets.

Use this software entirely at your own risk. Vehicle diagnostic communication can potentially cause unexpected ECU behavior, diagnostic trouble codes, loss of communication, component damage, excessive exhaust temperatures, fire, personal injury, or other damage if incorrect commands or procedures are used.

The author/contributors make no warranty that the software is accurate, safe, reliable, fit for a particular purpose, or suitable for use on any specific vehicle. To the maximum extent permitted by applicable law, the author/contributors accept no responsibility or liability for damage to a vehicle, ECU, DPF, engine, exhaust/aftertreatment system, diagnostic adapter, other property, data loss, personal injury, fire, or any other loss arising from the use or misuse of this software.

**Do not use service, actuator, coding, security-access, ECU-writing, forced-regeneration, or other active diagnostic functions unless you understand the procedure, required safety conditions, and consequences. Never perform a stationary DPF regeneration in an enclosed space or near combustible materials. Follow the vehicle manufacturer's workshop procedures and safety requirements.**

At the current stage, the project's normal monitoring functions are intended to be read-only. Experimental functionality should be clearly identified and treated accordingly.
