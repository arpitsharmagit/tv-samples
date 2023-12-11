package com.android.tv.classics.fragments;

import android.os.Bundle
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.navigation.Navigation
import com.android.tv.classics.LiveTvApplication
import com.android.tv.classics.NavGraphDirections
import com.android.tv.classics.R
import com.android.tv.classics.utils.TvLauncherUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MobileStepFragment: GuidedStepSupportFragment() {
    companion object {
        private val TAG = MediaBrowserFragment::class.java.simpleName
        private const val NEXT = 1L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance("Mobile No","Enter 10 digit JIO Mobile number.","LOGIN",
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_jasmine_logo))
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val mobileEditor =  GuidedAction.Builder(activity).infoOnly(true).title("Mobile Number").description("9310949577").build();
        val nextAction =  GuidedAction.Builder(activity).id(NEXT).title("Login").build();
        actions.add(mobileEditor)
        actions.add(nextAction)
    }

    override fun onGuidedActionClicked(action: GuidedAction?) {
        if(action!!.id == NEXT){
            val mobileNumber = actions[0].description.toString()
            if(mobileNumber.length != 10){
                LiveTvApplication.showToast("Please enter 10 digit mobile Number.");
                return
            }
            Log.i(TAG,"Entered Mobile Number $mobileNumber")
            LiveTvApplication.setMobileNumber(mobileNumber)

            if(LiveTvApplication.getAuthHeaders() != null){
                TvLauncherUtils.refreshToken()
                Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(NavGraphDirections.actionToMediaBrowser());
            }else{
                // Go to OTP Screen after sending TokenMobileStepFragment
                TvLauncherUtils.sendOTP(mobileNumber)
                Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(NavGraphDirections.actionOtpStep());
            }

        }
    }
}
