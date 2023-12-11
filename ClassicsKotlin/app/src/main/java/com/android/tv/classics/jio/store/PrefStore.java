package com.android.tv.classics.jio.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.tv.classics.jio.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PrefStore {
    private SharedPreferences sharedPref;
    private Context context;

    public PrefStore(Context context){
        this.context = context;
        this.sharedPref = context.getSharedPreferences(Constants.preferenceFile,Context.MODE_PRIVATE);
    }

    public void saveMap(String key, Map<String, String> inputMap){
        JSONObject jsonObject = new JSONObject(inputMap);
        String jsonString = jsonObject.toString();
        saveData(key,jsonString);
    }

    public Map<String, String> getMap(String key){
        Map<String, String> outputMap = new HashMap<>();
        try {
            String jsonString = sharedPref.getString(key, (new JSONObject()).toString());
            outputMap = new ObjectMapper().readValue(jsonString, HashMap.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return outputMap.size() == 0? null : outputMap;
    }

    public void saveData(String key, String value){
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public String getData(String key){
        return sharedPref.getString(key, null);
    }

    public void saveBoolean(String key, boolean value){
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(key, value);
        editor.commit();
    }

    public boolean getBoolean(String key){
        return sharedPref.getBoolean(key, false);
    }
}

