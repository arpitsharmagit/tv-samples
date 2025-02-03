package com.android.tv.classics.jio.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.tv.classics.jio.Constants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PrefStore {
    private SharedPreferences sharedPref;
    private Context context;
    private ObjectMapper objectMapper;

    public PrefStore(Context context){
        this.context= context;
        this.sharedPref = context.getSharedPreferences(Constants.preferenceFile,Context.MODE_PRIVATE);
        this.objectMapper = new ObjectMapper(); // Initialize ObjectMapper here
    }

    public void saveMap(String key, Map<String, String> inputMap){
        try {
            String jsonString = objectMapper.writeValueAsString(inputMap);
            saveData(key, jsonString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, String> getMap(String key){
        Map<String, String> outputMap = null;
        try {
            String jsonString = sharedPref.getString(key, (new JSONObject()).toString());
            // Use TypeReference to specify the Map type
            outputMap = objectMapper.readValue(jsonString, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            e.printStackTrace();
        }
        return outputMap;
    }

    public void saveData(String key, String value){
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, value);
        editor.apply(); // Use apply() for asynchronous saving
    }

    public String getData(String key){
        return sharedPref.getString(key, null);
    }

    public void saveBoolean(String key, boolean value){
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(key, value);
        editor.apply(); // Use apply() for asynchronous saving
    }

    public boolean getBoolean(String key){
        return sharedPref.getBoolean(key, false);
    }
}