package com.example.detectordebilletes

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import java.util.*

class TTSActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale("es", "ES") // Opcional: establecer idioma a español
            textToSpeech.speak("Bienvenido", TextToSpeech.QUEUE_FLUSH, null, null)
            finish() // Finalizar la actividad después de hablar
        } else {
            // Manejo de errores
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
