package com.android.tv.classics.ui.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.android.tv.classics.LiveTvApplication
import com.android.tv.classics.MainActivity
import com.android.tv.classics.NavGraphDirections
import com.android.tv.classics.R
import com.android.tv.classics.databinding.FragmentLoginBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LoginFragment : Fragment() {

    companion object {
        private val TAG = LoginFragment::class.java.simpleName
        private val database = FirebaseDatabase.getInstance()
    }

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var mobileEditText: EditText
    private lateinit var otpEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var verifyButton: Button
    private var _binding: FragmentLoginBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loginViewModel = ViewModelProvider(this, LoginViewModelFactory())
            .get(LoginViewModel::class.java)

        mobileEditText = binding.username
        otpEditText = binding.password
        loginButton = binding.login
        verifyButton = binding.verify
        val loadingProgressBar = binding.loading

        loginButton.isEnabled == false
        verifyButton.isEnabled == false

        loginViewModel.loginFormState.observe(viewLifecycleOwner,
            Observer { loginFormState ->
                if (loginFormState == null) {
                    return@Observer
                }
//                loginButton.isEnabled = loginFormState.isDataValid
                loginFormState.usernameError?.let {
                    mobileEditText.error = getString(it)
                }
                loginFormState.passwordError?.let {
                    otpEditText.error = getString(it)
                }
            })

        loginViewModel.loginResult.observe(viewLifecycleOwner,
            Observer { loginResult ->
                loginResult ?: return@Observer
                loadingProgressBar.visibility = View.GONE
                loginResult.error?.let {
                    showLoginFailed(it)
                }
                loginResult.success?.let {
                    updateUiWithUser(it)
                }
            })

        val afterMobileTextChangedListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // ignore
            }

            override fun afterTextChanged(s: Editable) {
                if(s!=null && s.length == 10){
                    loginButton.isEnabled == true
                    verifyButton.isEnabled == false
                }
                loginViewModel.loginDataChanged(
                    mobileEditText.text.toString(),
                    otpEditText.text.toString()
                )
            }
        }

        val afterOtpTextChangedListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // ignore
            }

            override fun afterTextChanged(s: Editable) {
                if(s!=null && s.length == 6 && mobileEditText.text.toString().length == 10 ){
                    loginButton.isEnabled = false;
                    verifyButton.isEnabled = true;
                    loginViewModel.loginDataChanged(
                        mobileEditText.text.toString(),
                        otpEditText.text.toString()
                    )
                }
            }
        }
        mobileEditText.addTextChangedListener(afterMobileTextChangedListener)

        mobileEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && mobileEditText.text.toString().length == 10) {
                handleLogin()
            }
            false
        }

        otpEditText.addTextChangedListener(afterOtpTextChangedListener)
        otpEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleVerify()
            }
            false
        }

        loginButton.setOnClickListener {
            loadingProgressBar.visibility = View.VISIBLE
            handleLogin()
        }

        verifyButton.setOnClickListener {
            loadingProgressBar.visibility = View.VISIBLE
            handleVerify()
        }
    }

    private fun handleLogin(){
        val tokenRef = database.getReference(mobileEditText.text.toString())
        tokenRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val cloudHeaders =  dataSnapshot.getValue()
                if(cloudHeaders!= null) {
                    LiveTvApplication.getPrefStore().saveBoolean("isLoggedIn", true)
                    LiveTvApplication.getPrefStore().saveData("mobileNumber", mobileEditText.text.toString())
                    LiveTvApplication.setAuthHeaders(cloudHeaders as MutableMap<String, String>)
                    navigateToMediaBrowser()
                }
                else{
                    loginViewModel.login(
                        mobileEditText.text.toString()
                    )
                    loginButton.isEnabled = false;
                    verifyButton.isEnabled = true;
                }
                Log.d(TAG, "Token read from realtime db")
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                loginViewModel.login(
                    mobileEditText.text.toString()
                )
                Log.w(TAG, "Unable to read token from realtime db.", error.toException())
            }
        })
    }

    private fun handleVerify(){
        loginViewModel.verify(
            mobileEditText.text.toString(),
            otpEditText.text.toString()
        )
    }
    private fun updateUiWithUser(model: LoggedInUserView) {
        val welcome = getString(R.string.welcome) + model.displayName
        // TODO : initiate successful logged in experience
        val appContext = context?.applicationContext ?: return
        Toast.makeText(appContext, welcome, Toast.LENGTH_LONG).show()
        if(model.isLoggedIn){
            navigateToMediaBrowser()
        }
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        val appContext = context?.applicationContext ?: return
        Toast.makeText(appContext, errorString, Toast.LENGTH_LONG).show()
    }
    private fun navigateToMediaBrowser() {
        // When playback is finished, go back to the previous screen
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
            .navigate(
                NavGraphDirections.actionToMediaBrowser()
                    .setChannelId("100")
            )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}