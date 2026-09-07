package com.visionmate.pro.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

class PhoneCallManager(private val context: Context) {

    fun makeEmergencyCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: SecurityException) {
            // Fallback to ACTION_DIAL if CALL_PHONE permission is not granted at runtime
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not place call", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not place call", Toast.LENGTH_SHORT).show()
        }
    }
}
