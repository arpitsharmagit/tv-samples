package com.android.tv.classics.jio;

import com.android.tv.classics.LiveTvApplication;
import com.android.tv.classics.jio.models.ChannelsResponse;
import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.ANRequest;
import com.androidnetworking.common.ANResponse;
import com.androidnetworking.common.Priority;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class JioAPI {

    public static ANRequest sendOTP(String mobileNumber){
        if(!mobileNumber.contains("+91")){
            mobileNumber = "+91" + mobileNumber;
        }

        //headers
        Map<String,String> headers = new HashMap<String,String>(){{
            put(Constants.APP_NAME, "RJIL_JioTV");
            put(Constants.DEVICE_TYPE, "phone");
            put(Constants.OS, "android");
        }};;

        //Prepare request
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("number", Utils.encodePhoneNumber(mobileNumber));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return AndroidNetworking.post(Constants.otpURL)
                .addJSONObjectBody(jsonObject)
                .addHeaders(headers)
                .setPriority(Priority.MEDIUM)
                .build();
    }

    public static ANRequest verifyOTP(String phoneNumber, String otp) {
        //headers
        Map<String,String> headers = new HashMap<String,String>(){{
            put(Constants.APP_NAME, "RJIL_JioTV");
            put(Constants.DEVICE_TYPE, "phone");
            put(Constants.OS, "android");
        }};;

        //Prepare request
        JSONObject jsonObject = new JSONObject();
        try {
            String strRequest = Utils.getJsonFromAssets(LiveTvApplication.getInstance().getApplicationContext(),"login-request.json");
            jsonObject = new JSONObject(strRequest);
            jsonObject.put("number",Utils.encodePhoneNumber(phoneNumber));
            jsonObject.put("otp",otp);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return AndroidNetworking.post(Constants.verifyURL)
                .addJSONObjectBody(jsonObject)
                .addHeaders(headers)
                .setPriority(Priority.MEDIUM)
                .build();
    }

    public static ANRequest RefreshToken(Map<String, String> authHeaders) {
        //headers
        Map<String,String> headers = new HashMap<String,String>(){{
            put(Constants.ACCESS_TOKEN,authHeaders.getOrDefault("authToken",""));
            put(Constants.UNIQUE_ID,authHeaders.getOrDefault("uniqueId",""));
            put(Constants.APP_NAME, "RJIL_JioTV");
            put(Constants.DEVICE_TYPE, "phone");
            put(Constants.OS, "android");
            put(Constants.VERSION_CODE,"285");
        }};;

        //Prepare request
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(Constants.APP_NAME, "RJIL_JioTV");
            jsonObject.put(Constants.DEVICE_ID, authHeaders.getOrDefault("deviceId",""));
            jsonObject.put(Constants.REFRESH_TOKEN, authHeaders.getOrDefault("refreshToken",""));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return AndroidNetworking.post(Constants.refreshTokenURL)
                .addJSONObjectBody(jsonObject)
                .addHeaders(headers)
                .setPriority(Priority.MEDIUM)
                .build();
    }

    public static String GetHeaderCookie(String playbackUrl, Map<String, String> authHeaders) {
        //Prepare request
        Map<String,String> headers = new HashMap<String,String>(){{
            put(Constants.ACCESS_TOKEN,authHeaders.getOrDefault("authToken",""));
            put(Constants.APP_KEY,authHeaders.getOrDefault("appkey",""));
            put(Constants.CAM_ID,"");
            put(Constants.CHANNEL_ID,"144");
            put(Constants.CRM_ID,authHeaders.getOrDefault("crmid",""));
            put(Constants.DEVICE_ID, authHeaders.getOrDefault("deviceId",""));
            put(Constants.DEVICE_TYPE, "phone");
            put("dm", "OnePlus ONEPLUS A5000");
            put(Constants.OTT_USER,"false");
            put(Constants.LANG_ID,"");
            put(Constants.LANGUAGE_ID,"6");
            put(Constants.LBCOOKIES,"1");
            put(Constants.OS, "android");
            put(Constants.OS_VERSION, "10");
            put(Constants.SESSIONID, authHeaders.getOrDefault("uniqueId",""));
            put(Constants.SUBSCRIBER_ID,authHeaders.getOrDefault("crmid",""));
            put(Constants.UNIQUE_ID,authHeaders.getOrDefault("uniqueId",""));
            put(Constants.USER_GROUP,authHeaders.getOrDefault("usergroup",""));
            put(Constants.USER_ID,authHeaders.getOrDefault("userId",""));
            put(Constants.VERSION_CODE,"285");
        }};

        ANRequest request = AndroidNetworking.get(playbackUrl)
                .addHeaders(authHeaders)
                .doNotCacheResponse()
                .setPriority(Priority.HIGH)
                .build();

        ANResponse response = request.executeForOkHttpResponse();
        if(response.isSuccess()){
            String cookieValue = response.getOkHttpResponse().header("set-cookie","");
            return cookieValue;
        }
        return "";
    }

    public static JSONObject GetPlaybackUrl(String channelId, Map<String, String> authHeaders) {
        //headers
        Map<String,String> headers = new HashMap<String,String>(){{
            put(Constants.ACCESS_TOKEN,authHeaders.getOrDefault("authToken",""));
            put(Constants.APP_KEY,authHeaders.getOrDefault("appkey",""));
            put(Constants.CAM_ID,"");
            put(Constants.CHANNEL_ID,channelId);
            put(Constants.CRM_ID,authHeaders.getOrDefault("crmid",""));
            put(Constants.DEVICE_ID, authHeaders.getOrDefault("deviceId",""));
            put(Constants.DEVICE_TYPE, "phone");
            put("dm", "OnePlus ONEPLUS A5000");
            put(Constants.OTT_USER,"false");
            put(Constants.LANG_ID,"");
            put(Constants.LANGUAGE_ID,"6");
            put(Constants.LBCOOKIES,"1");
            put(Constants.OS, "android");
            put(Constants.OS_VERSION, "10");
            put(Constants.SESSIONID, authHeaders.getOrDefault("uniqueId",""));
            put(Constants.SUBSCRIBER_ID,authHeaders.getOrDefault("crmid",""));
            put(Constants.UNIQUE_ID,authHeaders.getOrDefault("uniqueId",""));
            put(Constants.USER_GROUP,authHeaders.getOrDefault("usergroup",""));
            put(Constants.USER_ID,authHeaders.getOrDefault("userId",""));
            put(Constants.VERSION_CODE,"285");
        }};
        //Prepare request
        Map<String,String> body = new HashMap<String,String>(){{
            put("channel_id",channelId);
            put("stream_type","Seek");
        }};

        ANRequest request = AndroidNetworking.post(Constants.channelURL)
                .addHeaders(headers)
                .doNotCacheResponse()
                .addUrlEncodeFormBodyParameter(body)
                .setPriority(Priority.HIGH)
                .build();

        ANResponse response = request.executeForJSONObject();
        return response.isSuccess() ? (JSONObject) response.getResult() : new JSONObject();
    }

    public static JSONObject GetChannels() {
        ANRequest request = AndroidNetworking.get(Constants.channelsURL).build();
        ANResponse response = request.executeForJSONObject();
        return response.isSuccess() ? (JSONObject) response.getResult() : new JSONObject();
    }
}
