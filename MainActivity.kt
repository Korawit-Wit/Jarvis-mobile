package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var toggle: Button
    private lateinit var serverUrl: EditText
    private lateinit var clientToken: EditText
    private lateinit var picovoiceKey: EditText
    private var active = false
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

    private val updates = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            status.text = intent?.getStringExtra("status") ?: "พร้อมทำงาน"
            transcript.text = intent?.getStringExtra("transcript") ?: transcript.text
            active = intent?.getBooleanExtra("active", active) ?: active
            toggle.text = if (active) "ปิดไมโครโฟน" else "เปิดไมโครโฟน"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(2, 12, 25)
        window.navigationBarColor = Color.rgb(2, 12, 25)
        active = config.getBoolean("active", false)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 30, 24, 28)
            setBackgroundColor(Color.rgb(3, 12, 25))
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 34f
            setTextColor(Color.rgb(42, 190, 255))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "PERSONAL ANDROID ASSISTANT  •  RELEASE CANDIDATE"
            textSize = 10f
            setTextColor(Color.rgb(110, 190, 220))
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = if (active) "MICROPHONE • ACTIVE" else "STANDBY • พร้อมทำงาน"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 18)
        }
        toggle = Button(this).apply {
            text = if (active) "ปิดไมโครโฟน" else "เปิดไมโครโฟน"
            setOnClickListener { requestAndToggle() }
        }
        val speak = Button(this).apply {
            text = "พูดสั่งงานตอนนี้"
            setOnClickListener { sendService("COMMAND_NOW") }
        }
        transcript = TextView(this).apply {
            text = "ตัวอย่าง: “JARVIS เปิด YouTube”"
            textSize = 15f
            setTextColor(Color.rgb(185, 220, 240))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val configTitle = TextView(this).apply {
            text = "ตั้งค่าการเชื่อมต่อ"
            textSize = 18f
            setTextColor(Color.rgb(42, 190, 255))
            setPadding(0, 18, 0, 8)
        }
        serverUrl = field("OpenAI API Key (เริ่มต้นด้วย sk-)",
            config.getString("openai_api_key", "") ?: "", true)
        clientToken = field("ชื่อโมเดล API เช่น gpt-5.5 (ตรวจสอบสิทธิ์รุ่นที่ใช้ได้)",
            config.getString("openai_model", "gpt-5.5") ?: "gpt-5.5")
        picovoiceKey = field("Picovoice AccessKey",
            config.getString("picovoice_key", "") ?: "", true)
        val save = Button(this).apply {
            text = "บันทึกการตั้งค่า"
            setOnClickListener {
                config.edit()
                    .putString("openai_api_key", serverUrl.text.toString().trim())
                    .putString("openai_model", clientToken.text.toString().trim())
                    .putString("picovoice_key", picovoiceKey.text.toString().trim())
                    .apply()
                status.text = "บันทึกการตั้งค่าแล้ว"
                Toast.makeText(this@MainActivity, "บันทึกการตั้งค่าแล้ว", Toast.LENGTH_SHORT).show()
            }
        }
        val note = TextView(this).apply {
            text = "แอปนี้เชื่อม OpenAI API จากโทรศัพท์โดยตรง ไม่ต้องใช้ Windows • กรอก API Key และ Picovoice AccessKey ก่อนเปิดไมค์ • API มีค่าใช้จ่ายแยกจาก ChatGPT"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(status)
        content.addView(toggle)
        content.addView(speak)
        content.addView(transcript)
        content.addView(configTitle)
        content.addView(serverUrl)
        content.addView(clientToken)
        content.addView(picovoiceKey)
        content.addView(save)
        content.addView(note)
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
    }

    private fun field(hintText: String, value: String, secret: Boolean = false): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(value)
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(12, 8, 12, 8)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.jarvis.STATUS")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updates, filter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(updates, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(updates) } catch (_: Exception) {}
    }

    private fun requestAndToggle() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 10)
            return
        }
        if (active) sendService("STOP") else sendService("START")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 10) {
            val micGranted = permissions.indices.any {
                permissions[it] == Manifest.permission.RECORD_AUDIO &&
                    results.getOrNull(it) == PackageManager.PERMISSION_GRANTED
            }
            if (micGranted) sendService("START")
            else status.text = "กรุณาอนุญาตสิทธิ์ไมโครโฟน"
        }
    }

    private fun sendService(action: String) {
        val i = Intent(this, VoiceService::class.java).setAction(action)
        if (action == "START" && Build.VERSION.SDK_INT >= 26) startForegroundService(i)
        else startService(i)
    }
}
