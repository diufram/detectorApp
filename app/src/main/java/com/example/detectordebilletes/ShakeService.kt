package com.example.detectordebilletes

import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech

import java.util.Locale


class ShakeService : Service(), SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var shakeThreshold = 25f
    private var lastShakeTime: Long = 0
    private var textToSpeech: TextToSpeech? = null
    private var vibrator: Vibrator? = null

    private val CHANNEL_ID = "ShakeServiceChannel"

    override fun onCreate() {
        super.onCreate()
        Log.d("ShakeService", "Service Created")

        createNotificationChannel()
        val notification = buildNotification()
        startForeground(1, notification)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d("ShakeService", "Accelerometer registered")
        }

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Initialize Vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ShakeService", "Service started")
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shake Service")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.icono) // Asegúrate de tener un icono en drawable
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Shake Service Channel"
            val descriptionText = "Channel for Shake Service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            Log.d("ShakeService", "Sensor changed: gForce = $gForce")

            if (gForce > shakeThreshold) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShakeTime > 1000) {
                    lastShakeTime = currentTime
                    Log.d("ShakeService", "Shake detected")
                    vibrateAndSpeak()
                }
            }
        }
    }

    private fun vibrateAndSpeak() {
        // Vibrar por 500 milisegundos
        val vibrationDuration = 500L // Duración de la vibración en milisegundos
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Vibración para Android Oreo (API 26) y versiones superiores
                it.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                // Vibración para versiones anteriores
                @Suppress("DEPRECATION")
                it.vibrate(vibrationDuration)
            }
        }
        // Esperar un poco para asegurarnos de que la vibración termine antes de hablar
        android.os.Handler().postDelayed({
            speakWelcomeMessage()
        }, vibrationDuration)
    }


    private fun speakWelcomeMessage() {
        textToSpeech?.let {
            if (it.isSpeaking) {
                it.stop()
            }
            it.speak("Bienvenido que puedo hacer por usted", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ShakeService", "Service Destroyed")
        sensorManager.unregisterListener(this)
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        vibrator = null
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = textToSpeech?.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("ShakeService", "Language is not supported or missing data")
            }
        } else {
            Log.e("ShakeService", "TextToSpeech initialization failed")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
