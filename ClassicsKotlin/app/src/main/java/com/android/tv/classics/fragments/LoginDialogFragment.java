package com.android.tv.classics.fragments;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist.Guidance;
import androidx.leanback.widget.GuidedAction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.text.InputType;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.classics.LiveTvApplication;
import com.android.tv.classics.NavGraphDirections;
import com.android.tv.classics.R;
import com.android.tv.classics.jio.JioAPI;
import com.android.tv.classics.jio.store.PrefStore;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.AnalyticsListener;
import com.androidnetworking.interfaces.JSONObjectRequestListener;
import com.androidnetworking.interfaces.OkHttpResponseListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

import okhttp3.Response;

public class LoginDialogFragment extends GuidedStepSupportFragment {
    private static final int SEND_OTP = 10;
    private static final int VERIFY_OTP = 20;

    private static final int PHONE_NUMBER = 1;
    private static final int OTP = 2;
    private static final String TAG = "LoginDialogFragment";
    private String phoneNumber = "";
    private String phoneOTP= "";

    @NonNull
    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new Guidance(getString(R.string.LoginDialog),"Provide JIO Mobile Number and OTP to Login ","Login",null);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        addNonEditableAction(actions, PHONE_NUMBER, "Phone Number","9310949577"); //7906107828
        addAction(actions, SEND_OTP, "SEND OTP");
        addEditableAction(actions, OTP, "OTP","");
        addAction(actions, VERIFY_OTP, "VERIFY ");
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if(action.getId() == PHONE_NUMBER){
            phoneNumber = action.getDescription().toString();
        }

        if(action.getId() == OTP){
            phoneOTP = action.getDescription().toString();
        }

        if(action.getId() == SEND_OTP) {
            phoneNumber = getActions().get(0).getDescription().toString();
            if(phoneNumber != "") {
                JioAPI.sendOTP("+91"+phoneNumber)
                    .setAnalyticsListener(new AnalyticsListener() {
                        @Override
                        public void onReceived(long timeTakenInMillis, long bytesSent, long bytesReceived, boolean isFromCache) {
                            Log.d(TAG, " timeTakenInMillis : " + timeTakenInMillis);
                            Log.d(TAG, " bytesSent : " + bytesSent);
                            Log.d(TAG, " bytesReceived : " + bytesReceived);
                            Log.d(TAG, " isFromCache : " + isFromCache);
                        }
                    })
                    .getAsOkHttpResponse(new OkHttpResponseListener() {
                        @Override
                        public void onResponse(Response response) {
                            if(response.code() == 204){
                                Toast.makeText(getActivity(), "Successfully sent OTP to " + phoneNumber, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(ANError anError) {
                            Toast.makeText(getActivity(), "Error in sending OTP "+ anError.getErrorCode(), Toast.LENGTH_SHORT).show();
                        }
                    });
            } else{
                Toast.makeText(getActivity(), "Enter Phone number", Toast.LENGTH_SHORT).show();
            }
        }
        if(action.getId() == VERIFY_OTP){
            if(phoneNumber != "" && phoneOTP != "") {
                JioAPI.verifyOTP("+91" + phoneNumber, phoneOTP).getAsJSONObject(new JSONObjectRequestListener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        HashMap<String, String> headers = null;
                        JSONObject userDetails = null;
                        try {
                            headers = new HashMap<String, String>();
                            userDetails = response.getJSONObject("sessionAttributes").getJSONObject("user");
                            headers.put("authToken", response.getString("authToken"));
                            headers.put("refreshToken", response.getString("refreshToken"));
                            headers.put("ssotoken", response.getString("ssoToken"));
                            headers.put("userId", userDetails.getString("uid"));
                            headers.put("uniqueId", userDetails.getString("unique"));
                            headers.put("crmid", userDetails.getString("subscriberId"));

                            headers.put("appkey", "NzNiMDhlYzQyNjJm");
                            headers.put("deviceId", "1e075302d2fb0b64");
                            headers.put("User-Agent", "JioTV");
                            headers.put("os", "Android");
                            headers.put("versionCode", "285");
                            headers.put("devicetype", "phone");
                            headers.put("usergroup", "tvYR7NSNn7rymo3F");
                            headers.put("lbcookie", "1");

                            LiveTvApplication.getPrefStore().saveMap("headers", headers);
                            LiveTvApplication.getPrefStore().saveBoolean("isLoggedIn", true);
                            Toast.makeText(getActivity(), "Headers Saved", Toast.LENGTH_SHORT).show();
                            navigateToMediaBrowser();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        Toast.makeText(getActivity(), "Error in verify OTP " + anError.getErrorCode(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else{
                Toast.makeText(getActivity(), "Enter Phone or OTP ", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void navigateToMediaBrowser(){
        // When playback is finished, go back to the previous screen
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(NavGraphDirections.actionToMediaBrowser()
                        .setChannelId("100"));
    }

    private static void addAction(List<GuidedAction> actions, long id, String title) {
        actions.add(new GuidedAction.Builder()
                .id(id)
                .title(title)
                .build());
    }
    private static void addEditableAction(List<GuidedAction> actions, long id, String title, String desc) {
        actions.add(new GuidedAction.Builder()
                .id(id)
                .title(title)
                .descriptionEditable(true)
                .descriptionInputType(InputType.TYPE_CLASS_TEXT)
                .descriptionEditInputType(InputType.TYPE_CLASS_TEXT)
                .description(desc)
                .build());
    }
    private static void addNonEditableAction(List<GuidedAction> actions, long id, String title, String desc) {
        actions.add(new GuidedAction.Builder()
                .id(id)
                .title(title)
                .descriptionEditable(false)
                .descriptionInputType(InputType.TYPE_CLASS_TEXT)
                .descriptionEditInputType(InputType.TYPE_CLASS_TEXT)
                .description(desc)
                .build());
    }
}
