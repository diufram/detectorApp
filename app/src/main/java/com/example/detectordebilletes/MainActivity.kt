package com.example.detectordebilletes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val PERMISSION_REQUEST_CODE = 1
    private val LOCATION_PERMISSION_REQUEST_CODE = 2
    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this, this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d("MainActivity", "Checking permissions for foreground service and location.")
            checkPermissions()
        } else {
            Log.d("MainActivity", "Android version is below Tiramisu, starting ShakeService directly.")
            startShakeService()
        }
    }

    private fun checkPermissions() {
        val locationPermissionsGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!locationPermissionsGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        } else {
            startShakeService()
        }
    }

    private fun startShakeService() {
        Log.d("MainActivity", "Starting ShakeService.")
        val serviceIntent = Intent(this, ShakeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkNotificationPermission()
                } else {
                    Log.d("MainActivity", "Location permissions denied.")
                    // Manejar la denegación de permisos de ubicación si es necesario
                }
            }
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startShakeService()
                } else {
                    Log.d("MainActivity", "Foreground service permission denied.")
                    // Manejar la denegación del permiso si es necesario
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // El servicio de TextToSpeech se inicializó correctamente
            Log.d("MainActivity", "TextToSpeech initialized.")
            speakWelcomeMessage()
        } else {
            Log.e("MainActivity", "Failed to initialize TextToSpeech.")
        }
    }

    private fun speakWelcomeMessage() {
        val welcomeMessage = "Bienvenido que puedo hacer por usted"
        textToSpeech.speak(welcomeMessage, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
