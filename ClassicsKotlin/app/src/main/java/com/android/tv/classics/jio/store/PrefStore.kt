package com.android.tv.classics.jio.store

import android.content.Context
import android.content.SharedPreferences
import com.android.tv.classics.jio.Constants
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.json.JSONObject

class PrefStore(private val context: Context) {
    private val sharedPref: SharedPreferences = context.getSharedPreferences(Constants.preferenceFile, Context.MODE_PRIVATE)
    private val objectMapper: ObjectMapper = ObjectMapper()

    fun saveMap(key: String, inputMap: Map<String, String>) {
        try {
            val jsonString = objectMapper.writeValueAsString(inputMap)
            saveData(key, jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getMap(key: String): Map<String, String>? {
        try {
            val jsonString = sharedPref.getString(key, JSONObject().toString())
            // Use TypeReference to specify the Map type
            return objectMapper.readValue(jsonString, object : TypeReference<Map<String, String>>() {})
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun saveData(key: String, value: String) {
        sharedPref.edit().apply {
            putString(key, value)
            apply() // Use apply() for asynchronous saving
        }
    }

    fun getData(key: String): String? {
        return sharedPref.getString(key, null)
    }

    fun saveBoolean(key: String, value: Boolean) {
        sharedPref.edit().apply {
            putBoolean(key, value)
            apply() // Use apply() for asynchronous saving
        }
    }

    fun getBoolean(key: String): Boolean {
        return sharedPref.getBoolean(key, false)
    }
}