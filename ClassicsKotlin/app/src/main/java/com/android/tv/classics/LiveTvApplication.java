package com.android.tv.classics;

import android.app.Application;

import com.android.tv.classics.jio.store.HttpStore;
import com.android.tv.classics.jio.store.PrefStore;
import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.interceptors.HttpLoggingInterceptor;

import java.util.HashMap;
import java.util.Map;

public class LiveTvApplication extends Application {

    private static HttpStore httpStore;
    private static PrefStore prefStore;
    private static LiveTvApplication instance;
    private static Map<String,String> authHeaders;

    public static LiveTvApplication getInstance() {
        return instance;
    }
    public static HttpStore getHttpStore() {
        return httpStore;
    }
    public static PrefStore getPrefStore() {
        return prefStore;
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

    public void bootstrapApplication(){

        // start prefstore
        prefStore = new PrefStore(getInstance());
        httpStore = new HttpStore(getInstance());

        // init network
        AndroidNetworking.initialize(getApplicationContext(),HttpStore.getHttpClient());
//        AndroidNetworking.enableLogging(); // simply enable logging
//        AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY); // enabling logging with level
        // start httpstore
        // initialise APIs
    }
}
