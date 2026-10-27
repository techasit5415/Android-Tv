# TCL BeyondTV (Android TV 9, Pie)

## Device Specs
- **Model / Device:** TCL BeyondTV
- **Android Version:** 9 (API level 28, Pie)
- **Firmware:** PPR1.180610.011 release-keys
- **Stock Launcher:** `com.google.android.tvlauncher`
- **Live TV App:** `com.tcl.tv` (keep this, controls HDMI inputs & live tuner playback)

## Debloated Services
- TCL Business Intelligence / Telemetry (`com.tcl.bi`)
- Store Retail Demo (`com.tcl.esticker`, `com.tcl.showmode`)
- Overseas Recommendation & Ad feeds (`com.tcl.waterfall.overseas`, `com.tcl.tv.tclhome_master`, `com.tcl.tv.tclhome_passive`, `com.tcl.partnercustomizer`, `com.tcl.partnercustomizer.resource`)
- Stock Leanback Ad/Recommendation rows (`com.google.android.tvrecommendations`, `com.google.android.leanbacklauncher.recommendations`)
- Background Cleaners & Duplicate Stores (`com.tcl.tvweishi`, `com.tcl.copydatatotv`, `com.tcl.appmarket2`, `com.tcl.messagebox`, `com.tcl.notereminder`, `com.tcl.usercenter`, `com.tcl.useragreement`, `com.tcl.MultiScreenInteraction_TV`)
- Bug / Feedback reporters (`com.google.android.feedback`, `com.google.android.tv.bugreportsender`)

## Preserved Services
- All user streaming apps: Netflix, YouTube, Disney+ Hotstar, Prime Video, AIS PLAY, TrueID TV, TrueVisions, Viu, WeTV, iQiyi, Bilibili, Spotify, Twitch, etc.
- Sound & Hardware Audio (LG SN5 soundbar compatibility via HDMI ARC / Optical / CEC)
- Chromecast built-in (`com.google.android.apps.mediashell`)
- Google Voice Assistant (`com.google.android.katniss`)
- Google Play Store & Services (`com.android.vending`, `com.google.android.gms`)
- TCL TV Hardware & Inputs (`com.tcl.tv`, `com.tcl.tvinput`, `com.tcl.settings`, `com.tcl.dashboard`, `com.tcl.autopair`, `com.tcl.tcl_bt_rcu_service`)

## How to Restore
Run the restore script from PowerShell:
```powershell
.\backup-tcl-beyondtv\restore.ps1 -TvHost <TV_IP>
```
Or manually re-enable any package:
```powershell
adb shell pm enable <package.name>
```

## How to Change Dock Order or Wallpaper
1. Edit `launcher/app/src/main/res/values/config.xml`
2. Update the `<string-array name="dock_apps">` items in your desired order.
3. Replace `wallpaper_day.jpg` or `wallpaper_night.jpg` under `launcher/app/src/main/res/drawable-nodpi/`
4. Rebuild and install:
```powershell
$env:JAVA_HOME = "C:\Users\techa\Downloads\tv-vibehack\tools\jdk17\jdk-17.0.20.1+1"
cd launcher
.\gradlew.bat assembleDebug
adb push app\build\outputs\apk\debug\app-debug.apk /data/local/tmp/tvhome.apk
adb shell pm install -r /data/local/tmp/tvhome.apk
adb shell rm /data/local/tmp/tvhome.apk
```
