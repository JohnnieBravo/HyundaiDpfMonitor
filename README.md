# Hyundai DPF Monitor - v0.3.0

Direct Android BLE logger for vLinker MC+ / Hyundai Tucson.

## What it does
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
