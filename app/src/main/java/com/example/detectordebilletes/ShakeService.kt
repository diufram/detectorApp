package com.example.detectordebilletes

import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.VoiceInteractor
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
import android.media.MediaPlayer
import java.io.File
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import okhttp3.OkHttpClient
import java.io.IOException
import okhttp3.Request

import okhttp3.*
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaTypeOrNull

import org.json.JSONObject



import java.util.Locale



class ShakeService : Service(), SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var shakeThreshold = 25f
    private var lastShakeTime: Long = 0
    private var vibrator: Vibrator? = null

    private val handler = Handler(Looper.getMainLooper())
    private val serviceTimeout = 10000L // Tiempo para cerrar el servicio (10 segundos)

    private var isActionPerformed = false // Variable para asegurarnos de que la acción solo ocurra una vez

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

        // Initialize Vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ShakeService", "Service started")
        return START_STICKY // Mantener el servicio activo incluso después de que Android lo destruye
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

            // Detectar si la sacudida supera el umbral
            if (gForce > shakeThreshold && !isActionPerformed) { // Solo ejecuta si no se ha ejecutado antes
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShakeTime > 1000) {
                    lastShakeTime = currentTime
                    Log.d("ShakeService", "Shake detected")

                    isActionPerformed = true // Marcar que la acción ha sido realizada
                    vibrateAndSpeak() // Ejecutar la acción de vibración y reproducción de audio
                    scheduleServiceStop() // Programar el cierre del servicio en 10 segundos
                }
            }
        }
    }

    private fun scheduleServiceStop() {
        handler.postDelayed({
            stopSelf() // Cerrar el servicio después de 10 segundos
            Log.d("ShakeService", "Service stopped after 10 seconds")
        }, serviceTimeout)
    }

    private fun vibrateAndSpeak() {
        // Vibrar por 500 milisegundos y luego enviar el texto a la API
        val vibrationDuration = 500L // Duración de la vibración en milisegundos
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(vibrationDuration)
            }
        }

        val apiUrl = "http://192.168.0.2:5000/text"
        val text = "Hola, Bienvenido que puedo hacer por usted"
        Handler(Looper.getMainLooper()).postDelayed({
            sendTextAndReceiveAudio(apiUrl, text)
        }, vibrationDuration)
    }

    // Cliente OkHttp
    val client = OkHttpClient()

    // Función para enviar texto, recibir audio y reproducirlo
    private fun sendTextAndReceiveAudio(apiUrl: String, text: String) {
        // Crear el cuerpo de la solicitud POST con el texto en formato JSON
        val jsonData = JSONObject().apply {
            put("text", text) // Aquí se agrega el texto como parte del JSON
        }.toString()

        // Usar toMediaTypeOrNull() en lugar de parse() para el tipo de contenido
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonData)

        // Construir la solicitud POST
        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody) // Añadir el cuerpo JSON a la solicitud POST
            .build()

        // Ejecutar la solicitud
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace() // Manejar error
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.let { responseBody ->
                        // Crear un archivo temporal para guardar el audio MP3
                        val tempFile = File.createTempFile("output", ".mp3", cacheDir)

                        // Guardar la respuesta (audio) en el archivo
                        tempFile.outputStream().use { output ->
                            responseBody.byteStream().copyTo(output)
                        }

                        // Reproducir el archivo de audio guardado
                        Handler(Looper.getMainLooper()).post {
                            playAudio(tempFile)
                        }
                    }
                } else {
                    // Manejar error en la respuesta
                    println("Error: ${response.code}")
                }
            }
        })
    }

    // Función para reproducir el archivo de audio
    fun playAudio(audioFile: File) {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(audioFile.path)
            prepare()
            start()
        }
        mediaPlayer.setOnCompletionListener { it.release() }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ShakeService", "Service Destroyed")
        sensorManager.unregisterListener(this)
        vibrator = null
        handler.removeCallbacksAndMessages(null) // Eliminar todos los temporizadores al destruir el servicio
    }

    override fun onInit(status: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null
}
