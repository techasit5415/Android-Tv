# Android TV Minimal Launcher (tvOS / Apple TV Style)

Launcher สำหรับ Android TV สไตล์มินิมอล คลีนๆ ไม่มีโฆษณากวนใจ ได้แรงบันดาลใจจาก UI ของ Apple TV (tvOS) พัฒนาและทดสอบจริงบน TCL BeyondTV (Android 9 Pie) และรองรับ Android TV ทั่วไป

เน้นความเร็ว กินแรมน้อย แสดงผลภาพพื้นหลังสวยงาม ควบคุมด้วยรีโมททีวีง่าย ไม่มีเมนูซับซ้อน และตัดพวกอีโมจิรกตาออกทั้งหมด

---

## รูปตัวอย่างหน้าจอ (Screenshots)

### หน้าหลัก (Home Screen)
แถบ Island ด้านบนรวมนาฬิกา วันที่ สภาพอากาศ แรม และนัดหมาย พร้อมปุ่มสลับ HDMI และตั้งค่า

![Home Screen](docs/home-clean.png)

### หน้าตั้งค่า (Launcher Settings)
การ์ดควบคุม 2 คอลัมน์สไตล์ Apple TV ปรับแต่งได้ครบทุกอย่างโดยที่เมนูไม่ตกขอบจอ

![Settings Screen](docs/settings-clean.png)

---

## รายละเอียดการแก้ไขและพัฒนาเชิงลึก (What Was Fixed & How It Works)

ส่วนนี้อธิบายปัญหาที่พบจริง วิธีการแก้ไขในโค้ด และผลลัพธ์ที่ได้จากการปรับปรุง:

### 1. ปรับดีไซน์แถบ Island ด้านบน และขนาดตัวอักษร (Top Bar Typography & Weather)
* **ปัญหาเดิม:** 
  - ตัวหนังสือขนาดเล็กเกินไป อ่านยากเมื่อนั่งดูจากระยะโซฟา
  - มีอีโมจิต่างๆ ปนอยู่เยอะทำให้ดูรกตาเหมือนงาน AI เจนเนอเรต
  - สภาพอากาศและนัดหมายไม่แสดงผลตามตำแหน่งที่อยู่จริง
* **สิ่งที่แก้ไข:**
  - **ไฟล์:** `activity_main.xml` และ `MainActivity.java`
  - ปรับขนาดฟอนต์นาฬิกาเป็น `50sp` (ฟอนต์บาง สไตล์ Apple TV) และวันที่เป็น `17sp` สีขาวคมชัด
  - ตัดอีโมจิสภาพอากาศและปฏิทินออกทั้งหมด เหลือเพียงข้อความแบบมินิมอล
  - ฟังก์ชัน `updateWeatherAsync()`: ส่ง Request ไปที่ `ip-api.com` เพื่อระบุตำแหน่งเมือง/จังหวัดจาก IP เน็ตบ้าน แล้วส่งพิกัดต่อไปยัง Open-Meteo API เพื่อดึงอุณหภูมิปัจจุบัน แสดงผลเป็น `25°C กรุงเทพฯ`
  - ฟังก์ชัน `updateUpcomingEventAsync()`: ใช้ `ContentResolver` ดึง Event ปฏิทินของวันนี้จาก `CalendarContract.Events` หากไม่มีงานจะขึ้น `ไม่มีนัดหมายวันนี้`
* **ผลลัพธ์:** แถบข้อมูลด้านบนอ่านง่าย ชัดเจนจากระยะไกล สวยงามคลีนตา และดึงข้อมูลจริงแบบเรียลไทม์

---

### 2. แก้ปัญหาการเลื่อนรีโมทระหว่างปุ่ม HDMI และปุ่ม Settings (D-Pad Navigation)
* **ปัญหาเดิม:**
  - เมื่อเคอร์เซอร์อยู่ที่ปุ่ม `HDMI 1` แล้วกดลูกศรขวาเพื่อจะไปที่ปุ่ม `Settings` (เฟือง) หรือกดซ้ายจาก Settings กลับมา HDMI ต้องกดย้ำ 2-3 ครั้ง เคอร์เซอร์มักจะหลุดกระโดดข้ามไปตกที่แถบวันที่ฝั่งซ้ายมือ
* **สิ่งที่แก้ไข:**
  - **ไฟล์:** `MainActivity.java` (ในส่วน `pillKeys`)
  - กำหนด Logic ดักจับ `KeyEvent.ACTION_DOWN` แบบระบุคู่ปุ่มโดยตรง:
    - ถ้าเคอร์เซอร์อยู่ที่ `btnSettings` แล้วกด `DPAD_LEFT` -> บังคับโฟกัสไปที่ `btnInputs` ทันที
    - ถ้าเคอร์เซอร์อยู่ที่ `btnInputs` แล้วกด `DPAD_RIGHT` -> บังคับโฟกัสไปที่ `btnSettings` ทันที
* **ผลลัพธ์:** เลื่อนซ้าย-ขวาสลับระหว่างปุ่ม HDMI และปุ่ม Settings ได้แม่นยำใน 1 คลิก ไม่มีการกระโดดข้าม

---

### 3. แก้บั๊กหน้า Settings เมนูล้นตกขอบจอ และรีโมทกดเลื่อนไม่ได้ (Settings Dialog & Focus)
* **ปัญหาเดิม:**
  - หน้าต่าง Settings เดิมมีความสูงรวมเกือบ 600dp ซึ่งเกินความสูงของจอทีวี 1080p (ความสูงแสดงผลจริงคือ 540dp) ทำให้การ์ดด้านล่างสุดที่เป็นปุ่มทางลัดระบบ (Wi-Fi, แอพ, ตั้งค่าทีวี) ตกขอบจอด้านล่าง มองไม่เห็นและกดไม่ได้
  - เมื่อเปิดหน้า Settings ขึ้นมา เคอร์เซอร์ไม่จับที่ปุ่มใดเลย ทำให้กดปุ่มลูกศรบนรีโมทแล้วนิ่ง เลื่อนไม่ได้
  - ตัวหนังสือภาษาไทยในไฟล์ XML เดิมมีการเข้ารหัสผิดพลาด กลายเป็นเครื่องหมายคำถาม (`???`)
* **สิ่งที่แก้ไข:**
  - **ไฟล์:** `overlay_settings.xml`, `MainActivity.java`, `setting_option_bg.xml`
  - ปรับโครงสร้างหน้าต่างเป็นการ์ดลอย 2 คอลัมน์ (ความกว้าง 880dp) ฝั่งซ้ายคุม Wallpaper & Dock ส่วนฝั่งขวาคุม Audio-Only & System Shortcuts ความสูงรวมเหลือประมาณ 410dp อยู่ในขอบเขตหน้าจอพอดี
  - กำหนด `android:nextFocusUp`, `nextFocusDown`, `nextFocusLeft`, `nextFocusRight` ให้กับทุกปุ่มอย่างชัดเจน ไม่ให้ระบบ Android ค้นหาทิศทางโฟกัสเองแบบผิดพลาด
  - ใน `showSettings()` ใช้ `settingsOverlay.post(...)` เพื่อรอให้ View ทำ Layout เสร็จก่อน แล้วจึงสั่ง `requestFocus()` ไปที่ปุ่มแรก (`btnWallAuto`)
  - อัปเดต `setting_option_bg.xml` ให้ปุ่มที่ได้รับ Focus เปลี่ยนเป็นสีขาวสว่างชัดเจน และถ้าปุ่มนั้นถูกเลือกอยู่ด้วยจะมีขอบฟ้าเน้นความชัดเจน
* **ผลลัพธ์:** หน้า Settings แสดงผลครบทุกปุ่ม 100% ไม่ตกขอบ เลื่อนรีโมทได้ลื่นไหลทุกทิศทาง พร้อมไฟสถานะโฟกัสสีขาวชัดเจน

---

### 4. แก้ปัญหาถอนการติดตั้งแอพแล้วหน้า Launcher ไม่ยอมอัปเดต (Uninstalled App Persistence)
* **ปัญหาเดิม:**
  - เมื่อไปถอนการติดตั้งแอพออกจากทีวี (เช่น TOT IPTV) แล้วสลับกลับมาที่หน้า Home ของ Launcher ไอคอนของแอพที่ถอนไปยังคงค้างอยู่บนหน้าจอเหมือนเดิมจนกว่าจะรีสตาร์ททีวี
  - สาเหตุเกิดจากโค้ดเดิมใน `onResume()` สั่งรีโหลดแอพเฉพาะเวลาที่จำนวนแอพใน Adapter เท่ากับ 0 (`getItemCount() == 0`)
* **สิ่งที่แก้ไข:**
  - **ไฟล์:** `MainActivity.java`
  - ใน `onCreate()`: เพิ่มการลงทะเบียน `BroadcastReceiver` สำหรับดักฟัง Event การเปลี่ยนแปลงแอพของ Android:
    - `Intent.ACTION_PACKAGE_ADDED` (เมื่อมีแอพใหม่ติดตั้งเข้ามา)
    - `Intent.ACTION_PACKAGE_REMOVED` (เมื่อมีแอพถูกถอนออก)
    - `Intent.ACTION_PACKAGE_REPLACED` (เมื่อแอพถูกอัปเดต)
    เมื่อมีเหตุการณ์เหล่านี้เกิดขึ้น ตัว Receiver จะสั่งให้ `adapter.reload()` ทันที
  - ใน `onResume()`: เปลี่ยนให้เรียก `adapter.reload()` ทุกครั้งที่ผู้ใช้สลับกลับเข้าหน้าโฮม
* **ผลลัพธ์:** เมื่อถอนหรือติดตั้งแอพใหม่ รายชื่อแอพบนหน้า Launcher จะรีเฟรชตัวเองทันทีแบบเรียลไทม์ แอพที่ถอนไปแล้วจะหายไปทันทีโดยไม่ต้องรีสตาร์ท

---

### 5. Remap ปุ่มลัด TOT IPTV บนรีโมท ให้กดแล้วเปิด AIS PLAY (Remote Key Remapping)
* **ปัญหาเดิม:**
  - รีโมททีวี (โดยเฉพาะรุ่นที่จำหน่ายในไทย) มีปุ่มลัดฮาร์ดแวร์สกรีนป้ายว่า TOT IPTV ซึ่งเมื่อไม่ได้ใช้บริการนี้แล้ว ปุ่มนี้จะกลายเป็นปุ่มที่ไม่มีประโยชน์ หรือกดแล้วขึ้น Error แอพไม่พร้อมใช้งาน
* **สิ่งที่แก้ไข:** ทำระบบดักจับแบบ 2 ชั้น (Double-Layer Interception)
  1. **ดักจับระดับระบบผ่าน Accessibility Service:**
     - **ไฟล์:** `TvAccessibilityService.java` และ `MainActivity.java`
     - เขียนฟังก์ชันตรวจจับ KeyCode ฮาร์ดแวร์บนรีโมท (รวมถึงปุ่ม TV, Guide, DVR, Colored Buttons, Vendor App Keys)
     - เมื่อตรวจพบรหัสปุ่มเหล่านี้ จะสร้าง Intent สั่งเปิดแอพ **AIS PLAY (`com.ais.mimo.aisplay.tv`)** ขึ้นมาทันที
  2. **สวมรอย Intent Package เดิม (โมดูล `tot_forwarder`):**
     - ในกรณีที่เฟิร์มแวร์ทีวีใช้วิธีเรียกเปิด Package Name ของ TOT IPTV โดยตรง (`cn.itv.mobile.tv`)
     - สร้างโมดูลแอพขนาดเล็กขึ้นมาในโปรเจกต์ ใช้ Package Name `cn.itv.mobile.tv`
     - เมื่อเฟิร์มแวร์ทีวีเรียกเปิดตัวนี้ โค้ดใน `MainActivity` ของโมดูลจะส่ง Intent ต่อไปยัง AIS PLAY ในทันที และปิดตัวเองลง
     - ซ่อนชื่อแอพนี้ไว้ใน `hidden_apps` ของ Launcher เพื่อไม่ให้มีไอคอนซ้ำซ้อนโผล่มาบนหน้าโฮม
* **ผลลัพธ์:** เมื่อกดปุ่ม TOT IPTV บนรีโมท ระบบจะเปิดแอพ AIS PLAY ขึ้นมาทันทีจากทุกหน้าจอ

---

## โครงสร้างโปรเจกต์ (Project Structure)

```text
├── launcher/               # ตัว Launcher หลัก (Android Leanback TV App)
│   ├── app/                # ซอร์สโค้ด UI, Adapter, AccessibilityService
│   └── build.gradle
├── tot_forwarder/          # โมดูล Forwarder ดักปุ่ม TOT IPTV ไปเปิด AIS PLAY
│   ├── src/
│   └── build.gradle
├── tools/                  # สคริปต์สำรองข้อมูลและ Debloat ทีวีผ่าน ADB
├── docs/                   # รูปภาพประกอบเอกสาร
└── README.md
```

---

## วิธีบิลด์และติดตั้ง (Build & Installation)

### ข้อกำหนด
- JDK 17
- Android SDK (API 28 ขึ้นไป)
- เปิดโหมด **USB Debugging / Network ADB** บน Android TV

### 1. คอมไพล์โปรเจกต์ผ่าน Gradle
เปิด Terminal ในโฟลเดอร์โปรเจกต์แล้วรัน:

```bash
cd launcher
./gradlew assembleDebug
```
*(บน Windows ใช้ `gradlew.bat assembleDebug`)*

จะได้ไฟล์ APK 2 ตัว:
- Launcher: `launcher/app/build/outputs/apk/debug/app-debug.apk`
- TOT Forwarder: `tot_forwarder/build/outputs/apk/debug/tot_forwarder-debug.apk`

### 2. ติดตั้งลงทีวีผ่าน ADB
เชื่อมต่อทีวีผ่าน Wi-Fi หรือสาย USB:

```bash
adb connect <IP_ของทีวี>:5555
adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb install -r tot_forwarder/build/outputs/apk/debug/tot_forwarder-debug.apk
```

### 3. เปิดสิทธิ์การดักปุ่มรีโมท (Accessibility Service)
เพื่อให้ตัว Launcher ดักจับปุ่มทางลัดบนรีโมททีวีได้ ให้รันคำสั่ง:

```bash
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.TvAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

### 4. ให้สิทธิ์อ่านปฏิทิน (สำหรับแสดงนัดหมาย)
```bash
adb shell pm grant com.gathin.tvhome android.permission.READ_CALENDAR
```

### 5. เปิดใช้งาน Launcher
กดปุ่ม Home บนรีโมททีวี แล้วเลือกตั้งค่าให้แอพนี้เป็นหน้าหลัก (Always)
หรือสั่งเปิดโดยตรงผ่าน ADB:

```bash
adb shell am start -n com.gathin.tvhome/.MainActivity
```

---

## การปรับแต่งรายการแอพใน Dock
สามารถแก้ไขลำดับแอพที่ปักหมุดไว้แถวแรกสุด (Dock) ได้ที่ไฟล์:
`launcher/app/src/main/res/values/config.xml`

```xml
<string-array name="dock_apps">
    <item>com.google.android.youtube.tv</item>
    <item>com.netflix.ninja</item>
    <item>in.startv.hotstar.dplus.tv</item>
    <item>com.amazon.amazonvideo.livingroom</item>
</string-array>
```
ใส่ `Package Name` ของแอพที่ต้องการปักหมุดตามลำดับที่ต้องการได้เลย
