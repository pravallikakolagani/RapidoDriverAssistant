# Rapido Driver Assistant

An Android assistant application designed to automate and simplify the ride acceptance process for Rapido bike drivers. The app provides a floating cyberpunk heads-up display (HUD), announces ride details aloud into the captain's Bluetooth helmet headset, uses an accessibility service to read incoming ride requests in real-time and automatically accept or reject them based on driver preferences, and automatically fixes the silent navigation map audio bug.

---

## 🛠️ Technology Stack & Architecture

* **Programming Language**: Kotlin (JVM 17 / Android 14 SDK 34)
* **Screen Reading & Automation**: Android Accessibility API (`AccessibilityService` / `RapidoAccessibilityService`)
* **Background Notification Interceptor**: Android Notification Listener (`NotificationListenerService` / `RapidoNotificationService`)
* **Floating Cyberpunk HUD**: WindowManager Overlay API (`SYSTEM_ALERT_WINDOW` / `OverlayService`)
* **Voice Feedback**: Android Text-To-Speech (`VoiceAnnouncer`) configured for Bluetooth SCO & navigation assistance
* **Navigation Map Audio Fixer**: `AudioBugFixer` automated Mute ➔ 300ms ➔ Unmute cycle restoring silent voice directions
* **Decision Engine**: `DecisionEngine` computing exact bearings, compass directions (N, NE, E, etc.), towards-home cones, and filter checks
* **Interactive Ride Simulator**: `SimulatorActivity` with 4 presets (P1: Towards, P2: Away, P3: Too Far, P4: Low Fare), mock Rapido captain popup screen, and notification dispatch

---

## 🚀 How to Build & Run

### 1. Build via Command Line
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\kolag\.antigravity\scratch\RapidoDriverAssistant
.\gradlew.bat assembleDebug
```
The APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Run Unit Tests
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
```

### 3. Launch Emulator & Install
```powershell
# Launch emulator
& "C:\Users\kolag\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Pixel_7

# Install APK
& "C:\Users\kolag\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## 📱 Testing with the Built-in Simulator (No Bike Required!)

1. **Grant Permissions**:
   - Open **Rapido Assistant**.
   - Tap **ENABLE** on **Accessibility Service** and toggle **Rapido Automation Service** ON.
   - Tap **ENABLE** on **Display Over Other Apps** and grant permission.
   - Tap **ENABLE** on **Notification Access** and grant permission.

2. **Test Preset 1 (Towards Home - Auto Accept)**:
   - Tap **🚀 LAUNCH RIDE SIMULATOR**.
   - Tap **P1: Towards**.
   - Tap **⚡ LAUNCH RIDE SCREEN**.
   - *Result*: Floating Cyberpunk HUD appears in **Neon Green** showing `TOWARDS HOME (NE)`, voice announces through speaker/headset, and the service clicks **ACCEPT** after the countdown!

3. **Test Preset 2 (Away from Home - Auto Reject)**:
   - Tap **P2: Away**, then tap **⚡ LAUNCH RIDE SCREEN**.
   - *Result*: Floating HUD appears in **Crimson Red** showing `AWAY FROM HOME`, voice announces rejection, and the service auto-clicks **SKIP/REJECT**.

4. **Test Preset 3 (Too Far Pickup) & Preset 4 (Low Fare)**:
   - Evaluates filter thresholds and auto-rejects rides not meeting driver earnings/distance rules.

5. **Test Notification Simulation**:
   - Tap **🔔 TRIGGER NOTIFICATION SIMULATION** to test background/lock-screen notification wake lock and direct action auto-accept.

6. **Test Map Audio Bug Fixer**:
   - Tap **🔊 TEST AUDIO BUG FIXER** or trigger "Go to map" in navigation. The assistant automatically cycles mute/unmute to restore voice guidance.
