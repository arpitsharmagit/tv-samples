package com.android.tv.classics.jio

import android.content.Context
import android.util.Base64
import org.jetbrains.annotations.NotNull
import java.io.IOException
import java.nio.charset.StandardCharsets

object Utils {
    fun encodePhoneNumber(phoneNumber: String): String {
        return Base64.encodeToString(phoneNumber.toByteArray(StandardCharsets.UTF_8), 2)
    }

    fun getJsonFromAssets(context: Context, fileName: String): String? {
        return try {
            val inputStream = context.assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, charset("UTF-8"))
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}