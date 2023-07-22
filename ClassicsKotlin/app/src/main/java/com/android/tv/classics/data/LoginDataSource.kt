package com.android.tv.classics.data

import android.provider.Settings
import android.util.Log
import com.android.tv.classics.LiveTvApplication
import com.android.tv.classics.MainActivity
import com.android.tv.classics.jio.JioAPI
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.AnalyticsListener
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.androidnetworking.interfaces.OkHttpResponseListener
import com.google.firebase.database.FirebaseDatabase
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {
    companion object {
        private val TAG = LoginDataSource::class.java.simpleName
        private val database = FirebaseDatabase.getInstance()
    }
    interface LoginCallBack {
        fun onSuccess(message:String?)
        fun onError(message: String?)
    }

    fun login(mobileNo: String, loginCallBack: LoginCallBack) {
        try {
            JioAPI.sendOTP("+91$mobileNo")
                .setAnalyticsListener(AnalyticsListener { timeTakenInMillis, bytesSent, bytesReceived, isFromCache ->
                    Log.d(TAG, " timeTakenInMillis : $timeTakenInMillis")
                    Log.d(TAG, " bytesSent : $bytesSent")
                    Log.d(TAG, " bytesReceived : $bytesReceived")
                    Log.d(TAG, " isFromCache : $isFromCache")
                })
                .getAsOkHttpResponse(object : OkHttpResponseListener {
                    override fun onResponse(response: Response) {
                        if (response.code == 204) {
                            LiveTvApplication.getPrefStore().saveData("mobileNumber", mobileNo)
                            loginCallBack.onSuccess("Successfully sent OTP to $mobileNo")
                        }
                        else {
                            loginCallBack.onError(response.message);
                        }
                    }
                    override fun onError(anError: ANError) {
                        loginCallBack.onError("Error in sending OTP ${anError.errorCode}")
                    }
                })
        } catch (e: Throwable) {
            loginCallBack.onError("Error in sending OTP ${e.message}")
        }
    }

    fun verify(mobileNo: String, phoneOTP: String, loginCallBack: LoginCallBack) {
        try {
            JioAPI.verifyOTP("+91$mobileNo", phoneOTP)
                .getAsJSONObject(object : JSONObjectRequestListener {
                    override fun onResponse(response: JSONObject) {
                        var headers: HashMap<String?, String?>? = null
                        var userDetails: JSONObject? = null
                        try {
                            headers = HashMap()
                            userDetails =
                                response.getJSONObject("sessionAttributes").getJSONObject("user")
                            headers["authToken"] = response.getString("authToken")
                            headers["refreshToken"] = response.getString("refreshToken")
                            headers["ssotoken"] = response.getString("ssoToken")
                            headers["userId"] = userDetails.getString("uid")
                            headers["uniqueId"] = userDetails.getString("unique")
                            headers["crmid"] = userDetails.getString("subscriberId")
                            headers["appkey"] = "NzNiMDhlYzQyNjJm"
                            headers["deviceId"] = "1e075302d2fb0b64"
                            headers["User-Agent"] = "JioTV"
                            headers["os"] = "Android"
                            headers["versionCode"] = "285"
                            headers["devicetype"] = "phone"
                            headers["usergroup"] = "tvYR7NSNn7rymo3F"
                            headers["lbcookie"] = "1"
                            LiveTvApplication.getPrefStore().saveMap("headers", headers)
                            LiveTvApplication.getPrefStore().saveBoolean("isLoggedIn", true)

                            val tokenRef = database.getReference(mobileNo)
                            tokenRef.setValue(headers)

                            loginCallBack.onSuccess("Logged In Successfully $mobileNo")
                        } catch (e: JSONException) {
                            loginCallBack.onError(e.message);
                        }
                    }

                    override fun onError(anError: ANError) {
                        loginCallBack.onError("Error in verify OTP ${anError.errorCode}")
                    }
                })
        } catch (e: Throwable) {
            loginCallBack.onError("Error in verify API call ${e.message}")
        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}