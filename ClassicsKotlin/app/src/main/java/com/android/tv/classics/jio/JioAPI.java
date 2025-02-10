package com.android.tv.classics.jio;

import com.android.tv.classics.LiveTvApplication;
import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.ANRequest;
import com.androidnetworking.common.ANResponse;
import com.androidnetworking.common.Priority;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JioAPI {
    private static final Map<String, String> BASE_HEADERS = Collections.unmodifiableMap(new HashMap<String, String>() {{
            put(Constants.APP_NAME, Constants.VALUES.APP_NAME);
            put(Constants.DEVICE_TYPE, Constants.VALUES.DEVICE_ID);
            put(Constants.OS, Constants.VALUES.OS);
    }});

    private static Map<String, String> createHeaders(Map<String, String> additionalHeaders) {
        Map<String, String> headers = new HashMap<>(BASE_HEADERS);
        headers.putAll(additionalHeaders);
        return headers;
    }

    private static JSONObject safeParseJson(String jsonString) {
        try {
            return new JSONObject(jsonString);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public static ANRequest sendOTP(String mobileNumber) {
        String formattedNumber = mobileNumber.contains("+91") ? mobileNumber : "+91" + mobileNumber;
        
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("number", Utils.encodePhoneNumber(formattedNumber));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return AndroidNetworking.post(Constants.otpURL)
                .addJSONObjectBody(jsonObject)
                .addHeaders(BASE_HEADERS)
                .setPriority(Priority.MEDIUM)
                .build();
    }

    public static ANRequest verifyOTP(String phoneNumber, String otp) {
        JSONObject jsonObject = safeParseJson(
            Utils.getJsonFromAssets(LiveTvApplication.getInstance().getApplicationContext(), "login-request.json")
        );
        
        try {
            jsonObject.put("number", Utils.encodePhoneNumber(phoneNumber));
            jsonObject.put("otp", otp);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return AndroidNetworking.post(Constants.verifyURL)
                .addJSONObjectBody(jsonObject)
                .addHeaders(BASE_HEADERS)
                .setPriority(Priority.MEDIUM)
                .build();
    }

    public static ANRequest refreshToken(Map<String, String> authHeaders) {
        Map<String, String> headers = createHeaders(new HashMap<String, String>() {{
            put(Constants.ACCESS_TOKEN, authHeaders.getOrDefault("authToken", ""));
            put(Constants.UNIQUE_ID, authHeaders.getOrDefault("uniqueId", ""));
            put(Constants.VERSION_CODE, Constants.VALUES.VERSION_CODE);
        }});

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(Constants.APP_NAME, "RJIL_JioTV");
            jsonObject.put(Constants.DEVICE_ID, authHeaders.getOrDefault("deviceId", ""));
            jsonObject.put(Constants.REFRESH_TOKEN, authHeaders.getOrDefault("refreshToken", ""));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return AndroidNetworking.post(Constants.refreshTokenURL)
                .addJSONObjectBody(jsonObject)
                .addHeaders(headers)
                .setPriority(Priority.MEDIUM)
                .build();
    }

    public static String getHeaderCookie(String playbackUrl, Map<String, String> authHeaders) {
        Map<String, String> headers = createHeaders(new HashMap<String, String>() {{
            put(Constants.ACCESS_TOKEN, authHeaders.getOrDefault("authToken", ""));
            put(Constants.APP_KEY, authHeaders.getOrDefault("appkey", ""));
            put(Constants.CRM_ID, authHeaders.getOrDefault("crmid", ""));
            put(Constants.DEVICE_ID, authHeaders.getOrDefault("deviceId", ""));
            put(Constants.SESSIONID, authHeaders.getOrDefault("uniqueId", ""));
            put(Constants.SUBSCRIBER_ID, authHeaders.getOrDefault("crmid", ""));
            put(Constants.UNIQUE_ID, authHeaders.getOrDefault("uniqueId", ""));
            put(Constants.USER_GROUP, authHeaders.getOrDefault("usergroup", ""));
            put(Constants.USER_ID, authHeaders.getOrDefault("userId", ""));
            put(Constants.VERSION_CODE, Constants.VALUES.VERSION_CODE);
            put(Constants.DM, Constants.VALUES.DM);
            put(Constants.OTT_USER, "false");
            put(Constants.LANGUAGE_ID, Constants.VALUES.LANGUAGE_ID);
            put(Constants.LBCOOKIES, Constants.VALUES.LBCOOKIES);
            put(Constants.OS_VERSION, Constants.VALUES.OS_VERSION);
        }});

        ANRequest request = AndroidNetworking.get(playbackUrl)
                .addHeaders(headers)
                .doNotCacheResponse()
                .setPriority(Priority.HIGH)
                .build();

        ANResponse response = request.executeForOkHttpResponse();
        return response.isSuccess() ? 
            response.getOkHttpResponse().header("set-cookie", "") : 
            "";
    }

    public static JSONObject getPlaybackUrl(Map<String, String> body, Map<String, String> authHeaders) {
        Map<String, String> headers = createHeaders(new HashMap<String, String>() {{
            put(Constants.ACCESS_TOKEN, authHeaders.getOrDefault("authToken", ""));
            put(Constants.APP_KEY, authHeaders.getOrDefault("appkey", ""));
            put(Constants.CHANNEL_ID, body.get("channel_id"));
            put(Constants.CRM_ID, authHeaders.getOrDefault("crmid", ""));
            put(Constants.DEVICE_ID, authHeaders.getOrDefault("deviceId", ""));
            put(Constants.SESSIONID, authHeaders.getOrDefault("uniqueId", ""));
            put(Constants.SUBSCRIBER_ID, authHeaders.getOrDefault("crmid", ""));
            put(Constants.UNIQUE_ID, authHeaders.getOrDefault("uniqueId", ""));
            put(Constants.USER_GROUP, authHeaders.getOrDefault("usergroup", ""));
            put(Constants.USER_ID, authHeaders.getOrDefault("userId", ""));
            put(Constants.VERSION_CODE, Constants.VALUES.VERSION_CODE);
            put(Constants.DM, Constants.VALUES.DM);
            put(Constants.OTT_USER, "false");
            put(Constants.LANGUAGE_ID, Constants.VALUES.LANGUAGE_ID);
            put(Constants.LBCOOKIES, Constants.VALUES.LBCOOKIES);
            put(Constants.OS_VERSION, Constants.VALUES.OS_VERSION);
        }});

        ANRequest request = AndroidNetworking.post(Constants.channelURL)
                .addHeaders(headers)
                .doNotCacheResponse()
                .addUrlEncodeFormBodyParameter(body)
                .setPriority(Priority.HIGH)
                .build();

        ANResponse response = request.executeForJSONObject();
        return response.isSuccess() ? (JSONObject) response.getResult() : new JSONObject();
    }

    public static JSONObject getChannels() {
        ANRequest request = AndroidNetworking.get(Constants.channelsURL).build();
        ANResponse response = request.executeForJSONObject();
        return response.isSuccess() ? (JSONObject) response.getResult() : new JSONObject();
    }

    public static JSONObject getEPG(String channelId) {
        ANRequest request = AndroidNetworking.get(
            Constants.epgUrl.replace("channelId", channelId)
        ).build();
        ANResponse response = request.executeForJSONObject();
        return response.isSuccess() ? (JSONObject) response.getResult() : new JSONObject();
    }
}