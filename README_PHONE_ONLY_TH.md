# JARVIS Phone-Only — Samsung Galaxy A07 (Android)

รุ่นนี้ออกแบบให้ทำงานบนโทรศัพท์ Android โดยตรง ไม่ต้องมี Windows PC, Node.js server หรือคอมพิวเตอร์เป็น backend

## ฟังก์ชันในซอร์สโค้ด
- เรียกคำปลุก JARVIS ด้วย Porcupine บนอุปกรณ์
- รับคำสั่งภาษาไทยด้วย Android SpeechRecognizer
- ตอบเสียงผ่าน Android Text-to-Speech
- เปิด YouTube / Chrome / Maps / Gmail / Camera ผ่าน Android intents
- โทรผ่านหน้าจอ Dialer (ผู้ใช้กดยืนยันเอง)
- ตั้งปลุกผ่านแอป Clock (ผู้ใช้ตรวจสอบ/ยืนยันเอง)
- ควบคุมเสียงและปุ่มสื่อ
- ส่งบทสนทนาไป OpenAI Responses API โดยตรงผ่าน HTTPS
- เก็บบริบทข้อความล่าสุดในโทรศัพท์ และเข้ารหัสค่าตั้งค่าด้วย AndroidX EncryptedSharedPreferences

## สิ่งที่ต้องมี
1. โทรศัพท์ Android (เป้าหมาย Samsung Galaxy A07)
2. อินเทอร์เน็ตบนโทรศัพท์
3. OpenAI API Key ที่มี billing/เครดิต API ใช้งานได้ (แยกจาก ChatGPT subscription)
4. Picovoice AccessKey จาก https://console.picovoice.ai/
5. สำหรับสร้าง APK จากซอร์ส: Android Studio บนคอมพิวเตอร์เครื่องใดก็ได้ หรือบริการ build ที่เชื่อถือได้

## ตั้งค่าครั้งแรก
1. ติดตั้ง APK ที่ Build แล้ว
2. เปิด JARVIS และกรอก OpenAI API Key, ชื่อโมเดล API ที่บัญชีของคุณเข้าถึงได้, Picovoice AccessKey
3. กดบันทึกการตั้งค่า
4. อนุญาต Microphone และ Notifications
5. กดเปิดไมโครโฟนจากหน้าแอปขณะหน้าจอเปิด
6. ล็อกหน้าจอและพูด "JARVIS"; รอเสียงตอบรับก่อนพูดคำสั่ง

**API model:** ค่าเริ่มต้นในตัวอย่างคือ `gpt-5.5` แต่ต้องตรวจสอบชื่อรุ่นที่ API project ของคุณเปิดให้ใช้ได้ และแก้ในหน้าตั้งค่าให้ตรง อย่าคาดเดาว่าทุกบัญชีมีสิทธิ์เหมือนกัน

## Build APK
เปิดโฟลเดอร์โปรเจกต์ใน Android Studio รุ่นปัจจุบัน, ติดตั้ง Android SDK ที่ Android Studio แนะนำ, รอ Gradle Sync แล้วเลือก:
Build > Build Bundle(s) / APK(s) > Build APK(s)

ไฟล์ APK debug จะอยู่ที่ `app/build/outputs/apk/debug/app-debug.apk`

## ข้อจำกัดสำคัญ
- **ผมไม่สามารถติดตั้งแอปลง Galaxy A07 ของคุณจากแชตนี้ได้** เพราะไม่มีการเชื่อมต่อควบคุมอุปกรณ์หรือ ADB ของโทรศัพท์ และยังไม่ได้ Build APK ในสภาพแวดล้อมนี้
- ซอร์สโค้ดนี้ยังไม่ผ่านการ Compile/ติดตั้ง/ทดสอบจริงบน Galaxy A07; ต้อง Build และทดสอบบนเครื่องก่อนถือว่าใช้งานได้
- Android จำกัดการเริ่ม microphone foreground service จากเบื้องหลัง ต้องเปิดไมค์จากหน้าแอปขณะมองเห็นและอนุญาตสิทธิ์แล้ว
- Foreground service และ WakeLock ไม่รับประกันการฟัง wake word 24/7 ทุก firmware หรือทุกโหมดประหยัดพลังงาน
- SpeechRecognizer อาจใช้บริการรู้จำเสียงของระบบและอินเทอร์เน็ต
- TTS ใช้เสียงที่ติดตั้งในโทรศัพท์; pitch/rate ปรับได้ แต่ไม่รับประกันว่าจะเป็นเสียงผู้ชายคุณภาพเฉพาะแบบ JARVIS
- การเปิดแอปทำได้เฉพาะแอปที่ติดตั้งและ package ID ตรงกับโค้ด
- การโทรและตั้งปลุกเปิดหน้าจอระบบเพื่อให้ผู้ใช้ยืนยัน ไม่ดำเนินการเงียบ ๆ
- API Key เข้ารหัสใน SharedPreferences แต่ผู้ที่ควบคุมเครื่องที่ปลดล็อกแล้วอาจยังเข้าถึงข้อมูลได้; อย่าแชร์ APK ที่มี key และควรยกเลิก key ทันทีหากสงสัยว่ารั่ว
- API มีค่าใช้จ่ายตามการใช้งาน แยกจาก ChatGPT subscription

## ความเป็นส่วนตัว
ข้อความที่ส่งไป AI จะออกจากโทรศัพท์ผ่านอินเทอร์เน็ตไปยัง OpenAI API เพื่อสร้างคำตอบ; การเข้ารหัสค่าตั้งค่าไม่ได้หมายความว่าข้อความสนทนาทั้งหมดไม่เคยออกจากเครื่อง


## Build APK โดยใช้โทรศัพท์อย่างเดียว (ผ่าน GitHub Actions)
ไม่ต้องมี Windows หรือคอมพิวเตอร์ แต่ต้องมีบัญชี GitHub และอัปโหลดซอร์สโค้ด:
1. ดาวน์โหลด ZIP นี้ลงโทรศัพท์และแตกไฟล์ด้วยแอปจัดการไฟล์
2. สร้าง GitHub repository ใหม่ผ่าน Chrome บนโทรศัพท์
3. อัปโหลดไฟล์และโฟลเดอร์ทั้งหมดจากภายในโฟลเดอร์ `JARVIS_Galaxy_A07_ReleaseCandidate` รวมโฟลเดอร์ `.github` (เปิดแสดงไฟล์ซ่อนในแอปจัดการไฟล์ถ้าจำเป็น)
4. เปิดแท็บ Actions ของ repository และเลือก workflow `Build JARVIS Android APK`
5. กด Run workflow แล้วรอให้ job สำเร็จ
6. เปิด run ที่สำเร็จ เลื่อนลง Artifacts แล้วดาวน์โหลด `JARVIS-Galaxy-A07-debug`
7. แตก ZIP artifact แล้วแตะ `app-debug.apk` เพื่อติดตั้งบน Galaxy A07
8. หาก Android แจ้งเตือนความปลอดภัย ให้ตรวจสอบแหล่งที่มาของไฟล์ก่อนอนุญาต Install unknown apps เฉพาะเบราว์เซอร์/ตัวจัดการไฟล์ที่ใช้ติดตั้ง

หมายเหตุ: GitHub Actions เป็นบริการ build บนคลาวด์ ไม่ใช่การติดตั้งจากระยะไกล ผมไม่สามารถกดสร้าง repository หรือสั่งติดตั้งบนโทรศัพท์ของคุณแทนได้จากแชตนี้
