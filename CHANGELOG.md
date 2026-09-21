# Changelog

## 0.3.0 - 2026-09-21

First public test release.

### Added
- Direct BLE connection to vLinker MC+ using service 18F0 / RX 2AF0 / TX 2AF1.
- Live Hyundai Tucson DPF dashboard.
- RPM and vehicle speed.
- DPF load / trigger value, estimated soot mass and differential pressure.
- Turbo, catalyst, DPF and SCR temperatures.
- Average regeneration distance and time.
- Raw response logging, live CSV logging and event logging.
- Foreground service so logging can continue with the display off.
- Serbian TTS notification for DPF regeneration start/end.
- Separate tracking for status bit 0x04 without assuming its exact meaning.
- ECU information reader for VIN and selected UDS identifiers.
- Responsive portrait and landscape layouts.
- Start/Stop-aware engine state tracking: RUNNING, START_STOP_OFF, RESTARTING and VEHICLE_OFF.
- UI state persistence across rotation and returning to the app.

### Fixed
- Landscape layout clipping and scroll-position issues.
- UI incorrectly showing Disconnected while the BLE foreground service remained connected.
- ECU information disappearing after rotation.
- START / STOP / READ ECU INFO controls losing their correct state after Activity recreation.
- Android connectedDevice foreground-service startup crash caused by recreating the service only to restore UI.
- Kotlin build issues introduced during Start/Stop state changes.

### Notes
- Hyundai-specific PID / DID mappings are based on observed Tucson NX4 data and are still being reverse-engineered.
- Normal monitoring is intended to be read-only.
- Status bit 0x04 remains intentionally unlabeled beyond its raw meaning until its function is confirmed.
