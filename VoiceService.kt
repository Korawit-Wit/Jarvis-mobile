package com.example.jarvis

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.*
import android.speech.*
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Intent
import android.provider.AlarmClock
import android.net.Uri
import android.view.KeyEvent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.Locale
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class VoiceService : Service(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var porcupineManager: PorcupineManager? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var listening = false
    private var commandMode = false
    private val handler = Handler(Looper.getMainLooper())
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("jarvis_memory", MODE_PRIVATE) }
    private val config by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            this, "jarvis_secure_config", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val openAiApiKey get() = config.getString("openai_api_key", "").orEmpty()
    private val openAiModel get() = config.getString("openai_model", "gpt-5.5").orEmpty()
    private val picovoiceAccessKey get() = config.getString("picovoice_key", "").orEmpty()

    private val sessionId by lazy {
        prefs.getString("session_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("session_id", it).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createChannel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("th", "TH"))
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startListeningService()
            "STOP" -> stopListeningService()
            "COMMAND_NOW" -> {
                if (listening) {
                    commandMode = true
                    try { porcupineManager?.stop() } catch (_: Exception) {}
                    publish("LISTENING • กรุณาพูดคำสั่ง", "กำลังรอคำสั่งเสียง", true)
                    beginRecognition()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("jarvis_voice", "JARVIS Voice", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "jarvis_voice")
            .setContentTitle("JARVIS กำลังทำงาน")
            .setContentText("แตะเพื่อเปิดหน้าควบคุม • ไมโครโฟนเปิดอยู่")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }


    private fun startListeningService() {
        if (listening) return
        // Promote immediately: Android requires a started foreground service to publish
        // its notification promptly after startForegroundService().
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(21, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(21, notification())
        if (picovoiceAccessKey.isBlank()) {
            publish("SETUP REQUIRED • ต้องตั้งค่า Picovoice AccessKey",
                "กรุณากรอก Picovoice AccessKey ในหน้าตั้งค่า JARVIS ก่อนเปิดไมโครโฟน", false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        listening = true
        commandMode = false
        config.edit().putBoolean("active", true).apply()
        acquireCpuWakeLock()
        startWakeWordEngine()
    }

    private fun acquireCpuWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS:WakeWord")
            cpuWakeLock?.setReferenceCounted(false)
            if (cpuWakeLock?.isHeld != true) cpuWakeLock?.acquire()
        } catch (_: Exception) {
            publish("WAKE LOCK UNAVAILABLE", "ระบบไม่อนุญาตให้คง CPU ทำงาน", true)
        }
    }

    private fun startWakeWordEngine() {
        if (!listening || commandMode) return
        try {
            if (porcupineManager == null) {
                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(picovoiceAccessKey)
                    .setKeywords(arrayOf(Porcupine.BuiltInKeyword.JARVIS))
                    .build(this, PorcupineManagerCallback { keywordIndex: Int ->
                        handler.post {
                            if (listening && !commandMode) onWakeWordDetected()
                        }
                    })
            }
            porcupineManager?.start()
            publish("LOCK-SCREEN READY • รอคำว่า JARVIS",
                "ระบบตรวจจับคำปลุกทำงานบนอุปกรณ์", true)
        } catch (e: Exception) {
            publish("WAKE WORD ERROR", "เริ่มระบบตรวจจับคำปลุกไม่สำเร็จ: ${e.message ?: "ตรวจสอบ AccessKey"}", true)
            stopListeningService()
        }
    }

    private fun onWakeWordDetected() {
        if (!listening || commandMode) return
        commandMode = true
        try { porcupineManager?.stop() } catch (_: Exception) {}
        publish("WAKE WORD DETECTED • LISTENING",
            "ครับ ผมอยู่ตรงนี้ กรุณาพูดคำสั่งได้เลยครับ", true)
        speak("ครับ ผมอยู่ตรงนี้ กรุณาพูดคำสั่งได้เลยครับ", resumeWake = false)
        handler.postDelayed({ if (listening && commandMode) beginRecognition() }, 1100)
    }

    private fun stopListeningService() {
        listening = false
        commandMode = false
        config.edit().putBoolean("active", false).apply()
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        try { porcupineManager?.stop() } catch (_: Exception) {}
        try { porcupineManager?.delete() } catch (_: Exception) {}
        porcupineManager = null
        try { cpuWakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        cpuWakeLock = null
        publish("STANDBY • ปิดไมโครโฟนแล้ว", "ไมโครโฟนหยุดทำงาน", false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun beginRecognition() {
        if (!listening || !commandMode) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            publish("SPEECH RECOGNITION UNAVAILABLE",
                "ไม่พบบริการแปลงเสียงเป็นข้อความ กรุณาติดตั้งหรือเปิด Google Speech Services", true)
            returnToWakeWord(1800)
            return
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        publish("LISTENING • รับคำสั่ง", "ไมโครโฟนพร้อมรับคำสั่งภาษาไทย", true)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        publish("PROCESSING • กำลังวิเคราะห์", "กำลังประมวลผลคำสั่งเสียง", true)
                    }
                    override fun onError(error: Int) {
                        if (listening && commandMode) returnToWakeWord(700)
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (text.isBlank()) returnToWakeWord(500) else handleSpeech(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            returnToWakeWord(1200)
        }
    }

    private fun returnToWakeWord(delayMs: Long) {
        commandMode = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        handler.postDelayed({
            if (listening && !commandMode) startWakeWordEngine()
        }, delayMs)
    }


    private fun handleSpeech(raw: String) {
        val spoken = raw.trim()
        if (spoken.isBlank()) {
            returnToWakeWord(500)
            return
        }
        saveMemory("USER", spoken)
        executeCommand(spoken)
    }

    private fun executeCommand(command: String) {
        val c = command.lowercase(Locale.ROOT)
        saveMemory("USER", command)
        when {
            c.contains("หยุดฟัง") || c.contains("ปิดไมค์") || c.contains("พักการทำงาน") -> {
                speak("รับทราบครับ กำลังปิดไมโครโฟน")
                handler.postDelayed({ stopListeningService() }, 1000)
                return
            }
            c.contains("เปิด youtube") || c.contains("ยูทูบ") -> launch("com.google.android.youtube", "YouTube")
            c.contains("เปิด chrome") || c.contains("เปิด google") -> launch("com.android.chrome", "Chrome")
            c.contains("เปิดแผนที่") || c.contains("เปิด maps") || c.contains("นำทาง") -> launch("com.google.android.apps.maps", "Google Maps")
            c.contains("เปิด gmail") -> launch("com.google.android.gm", "Gmail")
            c.contains("เปิดกล้อง") -> {
                try { startActivity(Intent("android.media.action.IMAGE_CAPTURE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); reply("กำลังเปิดกล้องครับ") }
                catch (_: Exception) { reply("ไม่สามารถเปิดกล้องได้ครับ") }
            }
            c.contains("โทร") -> {
                val number = Regex("[0-9]{3,}").find(command)?.value
                if (number != null) {
                    try {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        reply("เปิดหน้าจอโทรออกไปยัง $number แล้วครับ กรุณากดยืนยันโทร")
                    } catch (_: Exception) { reply("ไม่สามารถเปิดหน้าจอโทรศัพท์ได้ครับ") }
                } else reply("กรุณาพูดหมายเลขโทรศัพท์ที่ต้องการโทรครับ")
            }
            c.contains("ตั้งปลุก") || c.contains("ตั้งนาฬิกาปลุก") -> setAlarm(command)
            c.contains("เพิ่มเสียง") || c.contains("เสียงดังขึ้น") -> adjustVolume(AudioManager.ADJUST_RAISE)
            c.contains("ลดเสียง") || c.contains("เสียงเบาลง") -> adjustVolume(AudioManager.ADJUST_LOWER)
            c.contains("ปิดเสียง") || c.contains("mute") -> adjustVolume(AudioManager.ADJUST_MUTE)
            c.contains("เล่นเพลง") || c.contains("เล่นต่อ") -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            c.contains("หยุดเพลง") || c.contains("พักเพลง") -> mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            c.contains("เพลงถัดไป") -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            c.contains("เพลงก่อนหน้า") -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            c.contains("เวลา") -> reply("ตอนนี้เวลา ${java.text.SimpleDateFormat("HH:mm", Locale("th","TH")).format(java.util.Date())} ครับ")
            else -> {
                askOnlineAI(command)
                return
            }
        }
        saveMemory("JARVIS", "processed: $command")
    }



    private fun askOnlineAI(userText: String) {
        if (openAiApiKey.isBlank()) {
            reply("กรุณากรอก OpenAI API Key ในหน้าตั้งค่าก่อนใช้งานสมองออนไลน์ครับ")
            return
        }
        publish("PROCESSING • กำลังเชื่อมต่อสมอง AI", userText, true)
        networkExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $openAiApiKey")
                }
                val history = prefs.getString("history", "").orEmpty()
                val instructions = "คุณคือ JARVIS ผู้ช่วยส่วนตัวภาษาไทย พูดสุภาพ สุขุม เป็นธรรมชาติ กระชับ และตอบเป็นภาษาไทย เว้นแต่ผู้ใช้ขอภาษาอื่น ห้ามอ้างว่าทำสิ่งที่ยังไม่ได้ทำจริง"
                val body = JSONObject().apply {
                    put("model", openAiModel.ifBlank { "gpt-5.5" })
                    put("instructions", instructions)
                    put("input", "บริบทบทสนทนาล่าสุด:\\n$history\\n\\nข้อความใหม่จากผู้ใช้:\\n$userText")
                    put("store", false)
                }
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) {
                    val apiMessage = try { JSONObject(responseText).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                    throw IllegalStateException("OpenAI API HTTP $code: ${apiMessage ?: responseText.take(240)}")
                }
                val json = JSONObject(responseText)
                val output = json.optJSONArray("output") ?: throw IllegalStateException("Missing output")
                val parts = mutableListOf<String>()
                for (i in 0 until output.length()) {
                    val item = output.optJSONObject(i) ?: continue
                    val content = item.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val part = content.optJSONObject(j) ?: continue
                        if (part.optString("type") == "output_text") {
                            val t = part.optString("text").trim()
                            if (t.isNotBlank()) parts.add(t)
                        }
                    }
                }
                val answer = parts.joinToString("\\n").trim()
                if (answer.isBlank()) throw IllegalStateException("Empty AI response")
                saveMemory("JARVIS", answer)
                handler.post {
                    if (listening) {
                        commandMode = true
                        publish("SPEAKING • JARVIS ONLINE", answer, true)
                        speak(answer)
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    if (listening) {
                        commandMode = true
                        val message = when {
                            e.message?.contains("401") == true -> "API Key ไม่ถูกต้องหรือยังไม่มีสิทธิ์ใช้งานครับ"
                            e.message?.contains("429") == true -> "คำขอ API เกินโควตาหรือมีการเรียกใช้งานถี่เกินไปครับ"
                            else -> "ขออภัยครับ เชื่อมต่อ AI ไม่สำเร็จ กรุณาตรวจสอบอินเทอร์เน็ต API Key และชื่อโมเดลครับ"
                        }
                        publish("AI CONNECTION ERROR", message, true)
                        speak(message)
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun launch(pkg: String, label: String) {
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i == null) reply("ไม่พบแอป $label ในโทรศัพท์ครับ")
            else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                reply("กำลังเปิด $label ให้ครับ")
            }
        } catch (_: Exception) { reply("เปิด $label ไม่สำเร็จครับ") }
    }

    private fun setAlarm(text: String) {
        val match = Regex("(\\d{1,2})[:.]([0-5]\\d)").find(text)
        if (match == null) {
            reply("กรุณาระบุเวลา เช่น ตั้งปลุก 06:30 ครับ")
            return
        }
        val hour = match.groupValues[1].toIntOrNull() ?: return
        val minute = match.groupValues[2].toIntOrNull() ?: return
        if (hour !in 0..23) { reply("เวลาไม่ถูกต้องครับ"); return }
        try {
            val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
            reply("เปิดหน้าตั้งนาฬิกาปลุกเวลา %02d:%02d แล้วครับ กรุณาตรวจสอบและยืนยัน".format(hour, minute))
        } catch (_: Exception) { reply("ไม่สามารถเปิดแอปนาฬิกาได้ครับ") }
    }

    private fun adjustVolume(direction: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        reply("ปรับระดับเสียงให้แล้วครับ")
    }

    private fun mediaKey(code: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val down = KeyEvent(KeyEvent.ACTION_DOWN, code)
        val up = KeyEvent(KeyEvent.ACTION_UP, code)
        audio.dispatchMediaKeyEvent(down)
        audio.dispatchMediaKeyEvent(up)
        reply("ส่งคำสั่งควบคุมเพลงแล้วครับ")
    }

    private fun reply(message: String) {
        publish("SPEAKING • JARVIS", message, true)
        speak(message)
    }

    private fun speak(message: String, resumeWake: Boolean = true) {
        if (!ready || tts == null) {
            if (resumeWake && listening) returnToWakeWord(1000)
            return
        }
        val utteranceId = "jarvis_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && resumeWake) handler.post {
                    if (listening && commandMode) returnToWakeWord(450)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && resumeWake) handler.post {
                    if (listening && commandMode) returnToWakeWord(700)
                }
            }
        })
        tts?.setSpeechRate(0.94f)
        tts?.setPitch(0.90f)
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun saveMemory(role: String, message: String) {
        val old = prefs.getString("history", "").orEmpty()
        val updated = (old + "\n$role: $message").lines().takeLast(30).joinToString("\n")
        prefs.edit().putString("history", updated).apply()
    }

    private fun publish(status: String, transcript: String, active: Boolean) {
        config.edit().putBoolean("active", active).apply()
        sendBroadcast(Intent("com.example.jarvis.STATUS").apply {
            setPackage(packageName)
            putExtra("status", status)
            putExtra("transcript", transcript)
            putExtra("active", active)
        })
    }

    override fun onDestroy() {
        listening = false
        config.edit().putBoolean("active", false).apply()
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        try { porcupineManager?.stop(); porcupineManager?.delete() } catch (_: Exception) {}
        porcupineManager = null
        try { cpuWakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        cpuWakeLock = null
        tts?.stop()
        tts?.shutdown()
        networkExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
