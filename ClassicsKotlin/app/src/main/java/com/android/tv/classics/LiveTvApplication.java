package com.android.tv.classics;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.tv.classics.jio.store.HttpStore;
import com.android.tv.classics.jio.store.PrefStore;
import com.android.tv.classics.utils.TvLauncherUtils;
import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.interceptors.HttpLoggingInterceptor;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LiveTvApplication extends Application {
    public static String TAG = "LiveTvApplication";
    private static String mobileNumber;
    private static HttpStore httpStore;
    private static PrefStore prefStore;
    private static LiveTvApplication instance;
    private static Map<String,String> authHeaders;

    public static LiveTvApplication getInstance() {
        return instance;
    }

    public static Context getContext(){
        if (instance == null) {
            instance = new LiveTvApplication();
        }
        return instance;
    }

    public static void showToast(String data) {
        Toast.makeText(getContext(), data,
                Toast.LENGTH_SHORT).show();
    }

    public static HttpStore getHttpStore() {
        return httpStore;
    }
    public static PrefStore getPrefStore() {
        return prefStore;
    }

    public static String getMobileNumber() {
        if(mobileNumber == null){
            return prefStore.getData("mobileNumber");
        }
        return mobileNumber;
    }
    public static DatabaseReference getCloudDatabase(){
        return FirebaseDatabase.getInstance()
                .getReference(getMobileNumber());
    }
    public static void setMobileNumber(String newMobileNumber) {
        mobileNumber = newMobileNumber;
        prefStore.saveData("mobileNumber", mobileNumber);
        if(mobileNumber!=null){
            initCloudSettings();
        }
    }

    public static Map<String,String> getAuthHeaders() {
        if(authHeaders == null){
            return prefStore.getMap("headers");
        }
        return authHeaders;
    }
    public static void setAuthHeaders(Map<String,String> newAuthHeaders){
        authHeaders = newAuthHeaders;
        prefStore.saveMap("headers", newAuthHeaders);
    }

    public LiveTvApplication() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        bootstrapApplication();
    }

    private static void initCloudSettings() {
        getCloudDatabase().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Object cloudHeaders =  dataSnapshot.getValue();
                if(cloudHeaders!= null) {
                    Log.d(TAG, "Login Headers found for "+ getMobileNumber());
                    LiveTvApplication.setAuthHeaders((Map<String, String>)cloudHeaders);
//                    TvLauncherUtils.Companion.refreshToken();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Cloud Database Error " + getMobileNumber(),databaseError.toException());
            }
        });
    }

    public void bootstrapApplication(){

        // start prefstore
        prefStore = new PrefStore(getInstance());
        httpStore = new HttpStore(getInstance());

        // init network
        AndroidNetworking.initialize(getApplicationContext(),HttpStore.getHttpClient());
        AndroidNetworking.enableLogging(); // simply enable logging
        AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY); // enabling logging with level

        prefStore.saveData("mobileNumber", "9310949577");
        // start httpstore
        // initialise APIs
        if(getMobileNumber()!=null){
            initCloudSettings();
        }
    }
}
