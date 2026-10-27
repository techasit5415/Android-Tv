# คู่มือการปรับแต่งและแก้ปัญหา TCL Android TV (BeyondTV) ฉบับสมบูรณ์

คู่มือนี้รวบรวมขั้นตอนการ Debloat ลบแอปขยะในระบบ, การติดตั้งและแก้ไขโค้ด Apple TV-Style Launcher (`tv-vibehack`), การแก้บั๊ก Focus/ภาษา และวิธีแก้ปัญหาเครื่องเสียง Soundbar เปิดเองอัตโนมัติ

---

## สารบัญ
1. [ข้อมูลระบบและสเปกอุปกรณ์](#1-ข้อมูลระบบและสเปกอุปกรณ์)
2. [การ Debloat ปิดแอปขยะและแอปสอดแนม (Telemetry)](#2-การ-debloat-ปิดแอปขยะและแอปสอดแนม-telemetry)
3. [การติดตั้งและตั้งค่า Launcher ใหม่ (`tv-vibehack`)](#3-การติดตั้งและตั้งค่า-launcher-ใหม่-tv-vibehack)
4. [บันทึกการแก้บั๊กในตัว Launcher](#4-บันทึกการแก้บั๊กในตัว-launcher)
5. [การแก้ปัญหาลำโพง LG SN5 เปิดเองตอนกดปิดทีวี (Optical TOSLINK)](#5-การแก้ปัญหาลำโพง-lg-sn5-เปิดเองตอนกดปิดทีวี-optical-toslink)
6. [การปรับแต่งหน้าตา Launcher ตามความชอบ](#6-การปรับแต่งหน้าตา-launcher-ตามความชอบ)
7. [การคืนค่าเดิม (Restore) & ความปลอดภัยหลังเสร็จงาน](#7-การคืนค่าเดิม-restore--ความปลอดภัยหลังเสร็จงาน)

---

## 1. ข้อมูลระบบและสเปกอุปกรณ์

* **ทีวี:** TCL BeyondTV (บอร์ด Realtek RTD2850)
* **ระบบปฏิบัติการ:** Android TV 9 (Pie)
* **เครื่องเสียง:** LG SN5 Soundbar เชื่อมต่อผ่านสาย Optical (TOSLINK)
* **IP Address ของทีวี:** `192.168.254.22` (Port ADB: `5555`)
* **Launcher ใหม่:** `com.gathin.tvhome` (Apple TV / Minimalist Living Room UI)

---

## 2. การ Debloat ปิดแอปขยะและแอปสอดแนม (Telemetry)

ทีวี TCL มาพร้อมกับแอปพื้นหลังจำนวนมากที่แย่ง RAM ขนาด 1.5GB–2GB ส่งผลให้เครื่องหน่วงและมีโฆษณาแทรก เราแบ่งการปิดแอปออกเป็น 3 ชุดโดย**ไม่กระทบฟังก์ชัน HDMI, TV Tuner, เสียง และ Chromecast**:

### ชุดที่ 1: ระบบดักเก็บข้อมูล & โหมดหน้าร้าน (Telemetry & Demo)
```powershell
adb shell pm disable-user --user 0 com.tcl.bi            # TCL Analytics / Telemetry
adb shell pm disable-user --user 0 com.tcl.esticker      # สติกเกอร์โฆษณาหน้าร้าน
adb shell pm disable-user --user 0 com.tcl.showmode      # โหมดโชว์หน้าห้าง
```

### ชุดที่ 2: ระบบฟีดโฆษณาและแท็บแนะนำ (Ad Feeds & Recommendations)
```powershell
adb shell pm disable-user --user 0 com.tcl.waterfall.overseas               # TCL Channel ฟีดคลิปวิดีโอ
adb shell pm disable-user --user 0 com.tcl.tv.tclhome_master               # TCL Home
adb shell pm disable-user --user 0 com.tcl.tv.tclhome_passive              # TCL Home Service
adb shell pm disable-user --user 0 com.tcl.partnercustomizer               # ตัวแทรกคอนเทนต์พาร์ตเนอร์
adb shell pm disable-user --user 0 com.tcl.partnercustomizer.resource      # ทรัพยากรแบนเนอร์โฆษณา
adb shell pm disable-user --user 0 com.google.android.tvrecommendations    # ฟีดแถวโฆษณา Google Leanback
adb shell pm disable-user --user 0 com.google.android.leanbacklauncher.recommendations
```

### ชุดที่ 3: แอปขยะกินแรมและระบบแจ้งเตือนซ้ำซ้อน
```powershell
adb shell pm disable-user --user 0 com.tcl.tvweishi                       # Safety Guard (ตัวเร่งเครื่องปลอม กินแรม)
adb shell pm disable-user --user 0 com.tcl.copydatatotv                   # TCL Data Transfer
adb shell pm disable-user --user 0 com.tcl.appmarket2                     # สโตร์ซ้ำซ้อนของ TCL
adb shell pm disable-user --user 0 com.tcl.messagebox                     # หน้าต่างข้อความแจ้งเตือนป๊อปอัป
adb shell pm disable-user --user 0 com.tcl.notereminder                   # ตัวแจ้งเตือนโน้ต
adb shell pm disable-user --user 0 com.tcl.usercenter                     # ศูนย์ล็อกอินบัญชี TCL
adb shell pm disable-user --user 0 com.tcl.useragreement                  # ข้อตกลงยินยอมแชร์ข้อมูล
adb shell pm disable-user --user 0 com.tcl.MultiScreenInteraction_TV      # เซอร์วิสแคสต์รุ่นเก่าของ TCL
adb shell pm disable-user --user 0 com.google.android.feedback            # Google Feedback Crash Reporter
adb shell pm disable-user --user 0 com.google.android.tv.bugreportsender  # Bug Report Sender
```

> **ตำแหน่งไฟล์สำรองในเครื่องคอม:**  
> โฟลเดอร์ `C:\Users\techa\Downloads\tv-vibehack\backup-tcl-beyondtv/` ประกอบด้วย:
> - `packages_initial.txt`: รายชื่อแพ็กเกจเดิมทั้งหมดก่อนแก้ไข
> - `packages_disabled.txt`: รายชื่อที่ปิดการทำงาน
> - `restore.ps1`: สคริปต์กู้คืนระบบกลับสู่สถานะเดิม

---

## 3. การติดตั้งและตั้งค่า Launcher ใหม่ (`tv-vibehack`)

### การตั้งค่า 4 แอปหลักบน Dock (config.xml)
ในไฟล์ `launcher/app/src/main/res/values/config.xml` กำหนด Package Name ให้ตรงกับแอปในเครื่อง:
```xml
<string-array name="dock_packages">
    <item>com.google.android.youtube.tv</item>      <!-- YouTube -->
    <item>com.netflix.ninja</item>                  <!-- Netflix -->
    <item>in.startv.hotstar.dplus.tv</item>         <!-- Disney+ Hotstar -->
    <item>com.amazon.amazonvideo.livingroom</item>  <!-- Prime Video -->
</string-array>
```

### การคอมไพล์ (Build) และติดตั้งผ่าน ADB
ใช้ Java 17 ในการสั่ง Gradle Build:
```powershell
$env:JAVA_HOME = "C:\Users\techa\Downloads\tv-vibehack\tools\jdk17\jdk-17.0.20.1+1"
cd C:\Users\techa\Downloads\tv-vibehack\launcher
.\gradlew.bat assembleDebug

# ติดตั้งลงทีวี
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### การตั้งให้เป็นหน้าแรกหลัก (Default Home) และปิด Launcher เก่า
```powershell
# ตั้ง tv-vibehack เป็นหน้าหลัก
adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity

# ปิด Launcher เดิมของ Google TV เพื่อคืนแรม
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

---

## 4. บันทึกการแก้บั๊กในตัว Launcher

### 4.1 แก้บั๊กภาษาตุรกีในเมนูตั้งค่า
* **ปัญหา:** ตัว Launcher ต้นฉบับมีข้อความภาษาตุรกีฝังตรงอยู่ในไฟล์เลย์เอาต์ (Hardcoded Turkish) เช่น `Arkaplan`, `Kenar Boşluğu`, `Geliştirici`, `Varsayılan Yap`
* **การแก้ไข:** แก้ไขที่ไฟล์ `launcher/app/src/main/res/layout/overlay_settings.xml` แปลงเป็นภาษาไทยกระชับและเข้าใจง่าย เช่น `วอลเปเปอร์`, `สไตล์แถบสถานะ`, `โหมด Dock`, `ตัวจัดการแอป`, `ตั้งค่าระบบทีวี`

### 4.2 แก้บั๊ก Focus รั่ว / เลื่อนแอปด้านหลังตอนเปิดเมนูตั้งค่า
* **ปัญหา:** เมื่อเปิดหน้าต่างตั้งค่า (Settings Overlay) แล้วกดปุ่มลูกศรลง (D-pad Down) โฟกัสทะลุไปเลื่อนไอคอนแอปในฉากหลัง และมีเงาเอฟเฟกต์กระจก (Glass Highlight) เด้งซ้อนขึ้นมา
* **การแก้ไข:**
  1. ใน `MainActivity.java`: 
     - เมื่อเปิดหน้าตั้งค่า สั่งให้ `grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS)` เพื่อบล็อกไม่ให้แอปด้านหลังรับโฟกัส
     - สั่ง `hideGlass()` และ `hideHero()` ปิดเงากระจกและภาพพรีวิวทันที
     - ใน `dispatchKeyEvent()` เพิ่มการตรวจสอบ: ถ้าหน้าต่าง Overlay (Settings หรือ Context Menu) กำลังแสดงอยู่ ให้ข้ามการประมวลผลปุ่มลูกศรขึ้น/ลงของฉากหลัง
  2. ใน `overlay_settings.xml`:
     - เพิ่ม `android:descendantFocusability="afterDescendants"`
     - ใส่ `android:nextFocusDown="@id/..."` และ `android:nextFocusUp="@id/..."` ล็อกโฟกัสให้อยู่เฉพาะภายในรายการการตั้งค่า ไม่หลุดออกไปข้างนอก

---


### 4.3 การลบแถบโปสเตอร์หนังตัวอย่าง (แอปแปลกปลอม)
* **ปัญหา:** Launcher ต้นฉบับมีการฝังแถบโปรโมตหนังตัวอย่างสำหรับ Stremio (เช่น Deadpool, Dune, Spider-Man, The Penguin, Breaking Bad) ปนเข้ามาในหน้ารวมแอป ทำให้เข้าใจผิดว่าเป็นแอปแปลกปลอมในเครื่อง
* **การแก้ไข:** ใน AppAdapter.java ตัดส่วน stremioRow และการดึงโปสเตอร์จาก Metahub ออกทั้งหมด ทำให้หน้ารวมแอปแสดงเฉพาะแอปที่ติดตั้งจริงในทีวี 100%

### 4.4 การเพิ่มแถบเพลง Now Playing (Spotify) สไตล์ Apple TV
* **ฟังก์ชัน:** เมื่อเปิดฟังเพลงใน Spotify (หรือเครื่องเล่นเพลงอื่นๆ) จะมี Pill กระจกฝ้าโผล่ขึ้นมาที่แถบด้านบนข้างปฏิทินแบบอัตโนมัติ: [ 🎵 ชื่อเพลง • ศิลปิน ] พร้อมตัวหนังสือเลื่อน (Marquee Scroll)
  * หากกดปุ่มขึ้นไปคลิกที่ตัวเพลง จะเป็นการเปิดแอป Spotify ขึ้นมาทันที
  * เมื่อหยุดเล่นเพลง แถบ Now Playing จะซ่อนตัวเองโดยอัตโนมัติเพื่อคืนพื้นที่หน้าจอ
* **การทำงานเชิงเทคนิค:**
  1. สร้าง MediaNotificationListener (สืบทอดจาก NotificationListenerService) ร่วมกับ MediaSessionManager เพื่อดักจับสถานะ PlaybackState และ MediaMetadata ของ Spotify
  2. เปิดสิทธิ์ระบบด้วยคำสั่ง: db shell cmd notification allow_listener com.gathin.tvhome/.MediaNotificationListener 0
  3. ออกแบบปุ่มและไอคอนเวกเตอร์ ic_music.xml พร้อมจัดเลย์เอาต์ 	opButtons ไม่ให้ชนกับวันที่

---


### 4.5 การแสดงผลการใช้งาน RAM คู่กับนาฬิกา (Clock Telemetry)
* **การออกแบบ:** ย้ายสถานะ RAM มาไว้ที่แถบส่วนหัวด้านบนของนาฬิกา [ GOOD NIGHT • 1.3 / 2.0 GB (63%) ]
* **ข้อดี:** 
  1. ไม่แย่งพื้นที่แถบปุ่มขวา ทำให้เมนู Settings, Wi-Fi, Calendar และ Now Playing แสดงผลได้ครบถ้วน ไม่ถูกเบียดตกขอบจอ
  2. สบายตา สไตล์ Ambient Status ที่กลมกลืนไปกับนาฬิกาและคำทักทาย
* **การทำงาน:** ใช้ ActivityManager.getMemoryInfo() คำนวณปริมาณ RAM ที่ใช้อยู่และ RAM ทั้งหมด อัปเดตอัตโนมัติทุกๆ 1 นาทีพร้อมนาฬิกา และรีเฟรชทันทีเมื่อกลับสู่หน้าโฮม

---

## 5. การแก้ปัญหาลำโพง LG SN5 เปิดเองตอนกดปิดทีวี (Optical TOSLINK)

### สาเหตุเชิงเทคนิค
1. **พฤติกรรมของบอร์ดทีวี TCL:** ทีวี TCL ใช้ระบบ **Suspend-to-RAM (STR / Quick Start)** เมื่อกดปุ่ม Power ปิดทีวี ระบบไม่ได้ปิดสนิท แต่เข้าสู่โหมด Standby ในเสี้ยววินาทีก่อนตัดไฟ ชิปเสียง Realtek จะปล่อยแสงไฟกระพริบ (Optical Light Pulse) สั้นๆ ออกมาทางพอร์ต Optical
2. **ฟังก์ชันของ LG SN5:** ซาวด์บาร์รุ่นนี้เปิดฟังก์ชัน **AUTO POWER ON** ไว้เป็นค่าโรงงาน เมื่อเซนเซอร์ของซาวด์บาร์จับแสงไฟ Optical จากทีวีได้ มันจะเข้าใจว่า "ทีวีกำลังเปิดใช้งาน" จึงสั่งเปิดตัวเองขึ้นมาทันที

### ทางเลือกในการแก้ไข (เลือกวิธีใดวิธีหนึ่ง)

#### ทางเลือกที่ 1: ปิด Auto Power ที่รีโมต LG Soundbar (แนะนำที่สุด)
1. หยิบทรีโมตของลำโพง **LG Soundbar**
2. กดปุ่ม **`AUTO POWER`** หรือปุ่ม **`[ ! ]` (Setting)** บนรีโมต LG
3. ปรับให้หน้าจอขึ้นเป็น **`AUTO POWER OFF`**
   * *ผลลัพธ์:* ลำโพงจะไม่เปิดตัวเองเมื่อมีแสงกระพริบ แต่คุณต้องกดเปิด-ปิดลำโพงด้วยรีโมต LG หรือเปิดค้างไว้

#### ทางเลือกที่ 2: ปิดโหมด Quick Start ในทีวี TCL
หากปิด Quick Start ทีวีจะตัดไฟพอร์ต Optical ทันทีแบบสมบูรณ์
1. เข้าไปที่ **ตั้งค่าทีวี (TV Settings)** $\rightarrow$ **การตั้งค่าอุปกรณ์ (Device Preferences)**
2. เลือกหัวข้อ **พลังงาน (Power)**
3. ปิดการทำงานของ **"เริ่มต้นทันที" (Quick Start / Fast Boot)** ให้เป็น **Off**

#### ทางเลือกที่ 3: สลับไปใช้สาย HDMI ARC
* ย้ายจากสาย Optical ไปเสียบพอร์ต **HDMI ARC (HDMI 1)** ระหว่างทีวีกับซาวด์บาร์ LG SN5
* ระบบ HDMI-CEC จะสั่งเปิด-ปิดตามทีวีอย่างแม่นยำ 100% ผ่านสัญญาณดิจิทัล และสามารถใช้รีโมตทีวีเพิ่ม-ลดเสียงซาวด์บาร์ได้โดยตรง

---

## 6. การปรับแต่งหน้าตา Launcher ตามความชอบ

| ฟังก์ชัน | วิธีการปรับแต่ง |
| :--- | :--- |
| **อยากให้ไอคอนแอปโชว์ตลอดเวลา** (ไม่แอบซ่อน) | เข้าเมนู **ตั้งค่า (รูปฟันเฟือง)** $\rightarrow$ หัวข้อ **โหมด Dock** $\rightarrow$ เลือก **"แสดงตลอดเวลา"** |
| **การเปิดดูแอปทั้งหมด (All Apps)** | กดปุ่ม **ลูกศรลง (D-pad Down)** บนรีโมต 2 ครั้ง จะเลื่อนลงไปที่หน้าตารางแอปทั้งหมด |
| **การเปลี่ยนลำดับ Dock 4 แอป** | แก้ไขไฟล์ `launcher/app/src/main/res/values/config.xml` ในส่วน `dock_packages` แล้ว Build ใหม่ |
| **การปรับแถบด้านบน (Top Bar)** | ในเมนูตั้งค่าสามารถเลือกซ่อน หรือเปลี่ยนให้แสดงเฉพาะไอคอนได้ |

---

## 7. การคืนค่าเดิม (Restore) & ความปลอดภัยหลังเสร็จงาน

### การคืนค่าระบบกลับเป็นแบบเดิมของ TCL (เมื่อต้องการ)
หากต้องการยกเลิกทุกอย่างและกลับไปใช้หน้าจอเดิมของ TCL เพียงรันคำสั่ง:
```powershell
# เปิดใช้งาน Stock Launcher เดิม
adb shell pm enable com.google.android.tvlauncher

# ตั้งให้ Stock Launcher เป็นตัวหลัก
adb shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity

# หรือรันสคริปต์คืนค่าแอปทั้งหมดที่สำรองไว้
cd C:\Users\techa\Downloads\tv-vibehack\backup-tcl-beyondtv
powershell -ExecutionPolicy Bypass -File .\restore.ps1
```

### การปิดโหมดพัฒนาเพื่อความปลอดภัย (Security Lockdown)
เมื่อปรับแต่งทุกอย่างเรียบร้อย แนะนำให้ปิด Network Debugging เพื่อป้องกันการเชื่อมต่อโดยไม่ได้รับอนุญาต:
1. ไปที่ **การตั้งค่าทีวี (Settings)** $\rightarrow$ **การตั้งค่าอุปกรณ์ (Device Preferences)** $\rightarrow$ **สำหรับนักพัฒนาซอฟต์แวร์ (Developer Options)**
2. ปิด **"การแก้ไขข้อบกพร่อง USB" (USB Debugging)** และ **"การแก้ไขข้อบกพร่องเครือข่าย" (Network Debugging)**
3. รีสตาร์ตทีวี 1 ครั้ง พอร์ต 5555 จะปิดลงอย่างปลอดภัย
