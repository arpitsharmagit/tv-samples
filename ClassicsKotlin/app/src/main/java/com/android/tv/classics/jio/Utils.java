package com.android.tv.classics.jio;

import android.content.Context;
import android.util.Base64;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Utils {
    public static String encodePhoneNumber(String phoneNumber) {
        return Base64.encodeToString(phoneNumber.getBytes(StandardCharsets.UTF_8), 2);
    }

    static String getJsonFromAssets(Context context, String fileName) {
        String jsonString;
        try {
            InputStream is = context.getAssets().open(fileName);

            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            jsonString = new String(buffer, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return jsonString;
    }
}
