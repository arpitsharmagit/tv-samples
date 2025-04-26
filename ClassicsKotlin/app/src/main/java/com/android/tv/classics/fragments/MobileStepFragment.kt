package com.android.tv.classics.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.navigation.Navigation
import com.android.tv.classics.LiveTvApplication
import com.android.tv.classics.NavGraphDirections
import com.android.tv.classics.R
import com.android.tv.classics.utils.FocusManager
import com.android.tv.classics.utils.TvLauncherUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren

class MobileStepFragment: GuidedStepSupportFragment() {
    companion object {
        private val TAG = MobileStepFragment::class.java.simpleName
        private const val NEXT = 1L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance("Mobile No", "Enter 10 digit JIO Mobile number.", "LOGIN",
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_jasmine_logo))
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val mobileEditor = GuidedAction.Builder(activity).infoOnly(true).title("Mobile Number").description("9310949577").build()
        val nextAction = GuidedAction.Builder(activity).id(NEXT).title("Login").build()
        actions.add(mobileEditor)
        actions.add(nextAction)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Restore focus state if available
        try {
            view.post {
                FocusManager.restoreFocusState(
                    this, 
                    R.id.mobile_step_fragment, 
                    view
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring focus", e)
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction?) {
        if(action!!.id == NEXT){
            val mobileNumber = actions[0].description.toString()
            if(mobileNumber.length != 10){
                LiveTvApplication.showToast("Please enter 10 digit mobile Number.")
                return
            }
            Log.i(TAG, "Entered Mobile Number $mobileNumber")
            LiveTvApplication.setMobileNumber(mobileNumber)

            if(LiveTvApplication.getAuthHeaders().isNotEmpty()){
                lifecycleScope.launch {
                    try {
                        TvLauncherUtils.refreshToken()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error refreshing token", e)
                    }
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(NavGraphDirections.actionToMediaBrowser())
                }
            } else {
                // Go to OTP Screen after sending TokenMobileStepFragment
                TvLauncherUtils.sendOTP(mobileNumber)
                Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(NavGraphDirections.actionOtpStep())
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // Save focus state when stopping
        try {
            FocusManager.saveFocusState(
                this,
                R.id.mobile_step_fragment
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving focus", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing coroutine operations if needed
        lifecycleScope.coroutineContext.cancelChildren()
    }
}
