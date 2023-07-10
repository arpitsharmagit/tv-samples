package com.android.tv.classics.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.util.Patterns
import com.android.tv.classics.data.LoginRepository
import com.android.tv.classics.data.Result

import com.android.tv.classics.R
import com.android.tv.classics.data.LoginDataSource

class LoginViewModel(private val loginRepository: LoginRepository) : ViewModel() {

    val loginSuccess = MutableLiveData<String?>()
    val loginFailedMessage = MutableLiveData<String?>()

    private val _loginForm = MutableLiveData<LoginFormState>()
    val loginFormState: LiveData<LoginFormState> = _loginForm

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    fun login(mobileNo: String) {
        // can be launched in a separate asynchronous job
        loginRepository.login(mobileNo, object: LoginDataSource.LoginCallBack{
            override fun onSuccess(message: String?) {
                loginSuccess.postValue(message)
                _loginResult.value =
                    LoginResult(success = LoggedInUserView(displayName = message.toString(),isLoggedIn = false))
            }

            override fun onError(message: String?) {
                loginFailedMessage.postValue(message)
                _loginResult.value = LoginResult(error = R.string.login_failed)
            }
        } )
    }

    fun verify(mobileNo: String, otp: String) {
        // can be launched in a separate asynchronous job
        loginRepository.verify(mobileNo, otp, object: LoginDataSource.LoginCallBack{
            override fun onSuccess(message: String?) {
                loginSuccess.postValue(message)
                _loginResult.value =
                    LoginResult(success = LoggedInUserView(displayName = message.toString(),isLoggedIn = true))
            }

            override fun onError(message: String?) {
                loginFailedMessage.postValue(message)
                _loginResult.value = LoginResult(error = R.string.login_failed)
            }
        } )
    }


    fun loginDataChanged(mobileNo: String, otp: String) {
        if (!isMobileNoValid(mobileNo)) {
            _loginForm.value = LoginFormState(usernameError = R.string.invalid_username)
        } else if (!isOTPValid(otp)) {
            _loginForm.value = LoginFormState(passwordError = R.string.invalid_password)
        } else {
            _loginForm.value = LoginFormState(isDataValid = true)
        }
    }

    // A placeholder mobileNo validation check
    private fun isMobileNoValid(mobileNo: String): Boolean {
        return if (mobileNo.length == 10) {
            Patterns.PHONE.matcher(mobileNo).matches()
        } else {
            mobileNo.isNotBlank()
        }
    }

    // A placeholder otp validation check
    private fun isOTPValid(otp: String): Boolean {
        return otp.length == 6
    }
}