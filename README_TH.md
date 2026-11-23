# JARVIS Galaxy A07 — Version 2.2 Release Candidate

## สิ่งที่ปรับปรุงในรุ่นนี้
- หน้าตั้งค่าในแอปสำหรับ Backend URL, Client Token และ Picovoice AccessKey (ไม่ต้องแก้ Kotlin source)
- บันทึกสถานะไมค์เพื่อให้หน้าจอแสดงสถานะเมื่อเปิดแอปใหม่
- ปุ่มพูดสั่งงานตอนนี้หยุด wake-word capture ก่อนเรียก SpeechRecognizer
- เปลี่ยนการกลับไปรอคำปลุกให้ทำหลัง TTS อ่านคำตอบจบ เพื่อลดการฟังเสียงตัวเอง
- ตั้งค่าเสียง TTS ช้าลงและ pitch ต่ำลงเล็กน้อย (เสียงผู้ชายขึ้นกับ engine/voice ที่ติดตั้ง)
- ปิด Android Auto Backup เพื่อไม่สำรองค่า AccessKey ใน Shared Preferences
- เพิ่มสคริปต์ช่วยตรวจสอบสภาพแวดล้อม Windows

## สำคัญ: สถานะการ Build
แพ็กเกจนี้เป็น Release Candidate Source Code ยังไม่ใช่ APK ที่ผ่านการ Build หรือทดสอบบน Galaxy A07 จริง
การสร้าง APK ต้องใช้ Windows ที่มี Android Studio, Android SDK Platform 35, Build Tools และอินเทอร์เน็ตเพื่อดาวน์โหลด Gradle plugins / Porcupine SDK
ในสภาพแวดล้อมที่สร้างไฟล์นี้ไม่มี Android SDK และไม่สามารถดาวน์โหลด dependencies จากอินเทอร์เน็ตได้ จึงยังไม่สามารถยืนยันผล Build ได้

## 1. เตรียม Windows
1. ติดตั้ง Android Studio: https://developer.android.com/studio
2. เปิด Android Studio > More Actions > SDK Manager
3. ติดตั้ง Android SDK Platform 35 และ Android SDK Build-Tools ที่ Android Studio แนะนำ
4. ใช้ JDK ที่มากับ Android Studio (JBR)
5. เปิดโปรเจกต์โฟลเดอร์นี้ แล้วรอ Gradle Sync ให้สำเร็จ

## 2. ติดตั้ง Backend
1. ติดตั้ง Node.js LTS: https://nodejs.org/
2. เปิด PowerShell ในโฟลเดอร์ `backend`
3. รัน `npm install`
4. รัน `Copy-Item .env.example .env`
5. เปิด `.env` แล้วใส่ OpenAI API Key, โมเดล API ที่บัญชีของคุณใช้ได้, และ Client Token แบบสุ่มอย่างน้อย 32 ตัวอักษร
6. รัน `npm start`
7. ทดสอบ `http://localhost:3000/health`

ตัวอย่างสร้าง token:
`[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`

ห้ามใส่ OpenAI API Key ลงในแอป Android

## 3. ตั้งค่า Picovoice
1. สมัครที่ https://console.picovoice.ai/
2. คัดลอก AccessKey
3. ในแอป JARVIS ใส่ AccessKey ในช่อง Picovoice AccessKey แล้วกดบันทึก

## 4. เชื่อมโทรศัพท์กับ Windows
1. ให้ Galaxy A07 และ Windows อยู่ Wi-Fi เดียวกัน
2. บน Windows รัน `ipconfig` แล้วหา IPv4 ของ Wi-Fi adapter
3. ตั้งค่า Windows Firewall อนุญาต Node.js / TCP 3000 เฉพาะ Private network
4. ในแอป ใส่ Backend URL เช่น `http://192.168.1.20:3000/chat`
5. ใส่ Client Token ให้ตรงกับ `.env`
6. กดบันทึกการตั้งค่า

HTTP ใช้เพื่อทดสอบใน LAN ส่วนตัวเท่านั้น ห้ามเปิด port 3000 สู่ Internet โดยตรง หากใช้งานจริงจากภายนอกต้อง deploy backend หลัง HTTPS และเพิ่มระบบยืนยันตัวตน

## 5. Build APK
วิธีแนะนำ:
1. เปิดโฟลเดอร์โปรเจกต์ใน Android Studio
2. รอ Gradle Sync สำเร็จ
3. เลือก Build > Build Bundle(s) / APK(s) > Build APK(s)
4. ไฟล์ debug APK อยู่ที่ `app/build/outputs/apk/debug/app-debug.apk`

หรือเปิด PowerShell ในโฟลเดอร์โปรเจกต์แล้วรัน `.\BUILD_WINDOWS.ps1` สคริปต์จะใช้ Gradle ที่ติดตั้งอยู่ หรือดาวน์โหลด Gradle 8.9 มาไว้ในโฟลเดอร์ `.build-tools` ให้เอง (ต้องมีอินเทอร์เน็ต)

Debug APK ใช้ทดสอบเท่านั้น ก่อนเผยแพร่ควรสร้าง Signed Release APK ผ่าน Build > Generate Signed Bundle / APK

## 6. ติดตั้งและทดสอบ Galaxy A07
1. เปิด Developer options และ USB debugging หากติดตั้งผ่าน USB
2. ติดตั้ง APK และอนุญาต Microphone / Notifications
3. เปิดแอป กรอกค่าตั้งค่า 3 ช่อง แล้วกดบันทึก
4. กดเปิดไมโครโฟนขณะหน้าแอปมองเห็น
5. ตรวจสอบการแจ้งเตือนบริการ JARVIS
6. ล็อกหน้าจอและพูด "JARVIS"; รอคำตอบรับแล้วจึงพูดคำสั่ง
7. ทดสอบเปิด YouTube, โทรผ่าน Dialer, ตั้งปลุก, สนทนาออนไลน์
8. ทดสอบปิดไมค์จากปุ่มในแอปและยืนยันว่า notification หายไป

## 7. ตั้งค่า Samsung
- Settings > Battery > Background usage limits > Never sleeping apps > เพิ่ม JARVIS
- Settings > Apps > JARVIS > Battery > Unrestricted (ถ้ามี)
- อย่าใส่ใน Sleeping / Deep sleeping apps
- ปิด Power saving mode ระหว่างทดสอบ
- อย่ากด Force stop

## ข้อจำกัดและความปลอดภัย
- Android จำกัดการเริ่ม microphone foreground service จากเบื้องหลัง; ต้องเปิดจากหน้าแอปขณะมองเห็นและได้รับสิทธิ์แล้ว
- Foreground service และ WakeLock ไม่รับประกันการฟัง 24/7 ทุก firmware/ทุกสภาวะ
- Wake word JARVIS ของ Porcupine เป็นโมเดลคำปลุกภาษาอังกฤษ; ความแม่นยำขึ้นกับสำเนียง สภาพเสียง และไมโครโฟน
- SpeechRecognizer อาจใช้บริการ Google/เครือข่ายตามอุปกรณ์และการตั้งค่า
- เสียงผู้ชายขึ้นกับเสียง TTS ที่ติดตั้งใน Samsung/Google; โค้ดปรับ pitch ต่ำลง แต่ไม่ได้สร้างเสียงเฉพาะบุคคล
- การโทรเปิด Dialer ให้ผู้ใช้ยืนยัน ไม่โทรออกเอง
- การตั้งปลุกเปิดแอป Clock ให้ผู้ใช้ตรวจสอบ/ยืนยัน
- Client Token และ Picovoice AccessKey ที่อยู่ในแอปไม่ใช่ secrets ที่ป้องกันการดึงออกจาก APK ได้สมบูรณ์
- ตัวอย่าง Backend ใช้ JSON file memory แบบพื้นฐาน ไม่เหมาะกับผู้ใช้หลายคนหรือ production
- ยังต้องทดสอบบนเครื่องจริงก่อนยืนยันการใช้งาน และไม่สามารถรับประกัน 100% ได้

## เอกสารอ้างอิง
- Android microphone foreground service: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Android foreground service background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Picovoice Porcupine Android: https://picovoice.ai/docs/quick-start/porcupine-android/
