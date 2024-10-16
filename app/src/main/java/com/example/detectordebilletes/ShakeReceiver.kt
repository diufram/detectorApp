package com.example.detectordebilletes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class ShakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val activityIntent = Intent(context, TTSActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            // Puedes pasar datos adicionales si es necesario
        }
        context.startActivity(activityIntent)
    }
}
