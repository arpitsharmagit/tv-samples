package com.android.tv.classics.ui.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.android.tv.classics.NavGraphDirections
import com.android.tv.classics.R
import com.android.tv.classics.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private lateinit var loginViewModel: LoginViewModel
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

        val mobileEditText = binding.username
        val otpEditText = binding.password
        val loginButton = binding.login
        val verifyButton = binding.verify
        val loadingProgressBar = binding.loading

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

        val afterTextChangedListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // ignore
            }

            override fun afterTextChanged(s: Editable) {
                loginViewModel.loginDataChanged(
                    mobileEditText.text.toString(),
                    otpEditText.text.toString()
                )
            }
        }
        mobileEditText.addTextChangedListener(afterTextChangedListener)
        otpEditText.addTextChangedListener(afterTextChangedListener)
        otpEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loginViewModel.login(
                    mobileEditText.text.toString()
                )
            }
            false
        }

        loginButton.setOnClickListener {
            loadingProgressBar.visibility = View.VISIBLE
            loginViewModel.login(
                mobileEditText.text.toString()
            )
        }

        verifyButton.setOnClickListener {
            loadingProgressBar.visibility = View.VISIBLE
            loginViewModel.verify(
                mobileEditText.text.toString(),
                otpEditText.text.toString()
            )
        }
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