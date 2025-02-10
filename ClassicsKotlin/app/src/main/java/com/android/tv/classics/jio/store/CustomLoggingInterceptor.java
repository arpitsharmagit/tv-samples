package com.android.tv.classics.jio.store;


import android.util.Log;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;

public class CustomLoggingInterceptor implements Interceptor {
    private static final String TAG = "HttpLoggingManager";
    @Override
    public Response intercept(Chain chain) throws IOException {

        Request request = chain.request();

        // Log Request Details
        logRequest(request);

        // Proceed with the request
        Response response = chain.proceed(request);

        // Log Response Details
        logResponse(response);

        return response;
    }

    private void logRequest(Request request) {
        Log.d(TAG, "Request URL: " + request.url());
        Log.d(TAG, "Request Method: " + request.method());

        // Log Headers
        for (String name : request.headers().names()) {
            Log.d(TAG, "Request Header: " + name + " = " + request.headers().get(name));
        }

        // Log Request Body
        try {
            Request copyRequest = request.newBuilder().build();
            final Buffer buffer = new Buffer();
            if (copyRequest.body() != null) {
                copyRequest.body().writeTo(buffer);
                String requestBody = buffer.readUtf8();
                Log.d(TAG, "Request Body: " + requestBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error logging request body", e);
        }
    }

    private void logResponse(Response response) {
        Log.d(TAG, "Response URL: " + response.request().url());
        Log.d(TAG, "Response Code: " + response.code());

        // Log Headers
        for (String name : response.headers().names()) {
            Log.d(TAG, "Response Header: " + name + " = " + response.headers().get(name));
        }

        // Log Response Body
        try {
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                MediaType contentType = responseBody.contentType();
                String bodyString = responseBody.string();
                Log.d(TAG, "Response Body: " + bodyString);

                // Create a new response body as the original can only be read once
                response = response.newBuilder()
                        .body(ResponseBody.create(contentType, bodyString))
                        .build();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error logging response body", e);
        }
    }
}
