package com.android.tv.classics.fragments;

import android.os.Bundle
import android.text.InputType
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

class OtpStepFragment: GuidedStepSupportFragment() {
    companion object {
        private val TAG = OtpStepFragment::class.java.simpleName
        private const val VERIFY = 1L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance("OTP","Enter 6 digit OTP received on ${LiveTvApplication.getMobileNumber()}.","LOGIN -> OTP",
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_jasmine_logo))
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val otpEditor =  GuidedAction.Builder(activity).title("OTP").description("000000").descriptionEditable(true).inputType(InputType.TYPE_CLASS_NUMBER).build()
        val verifyAction =  GuidedAction.Builder(activity).id(VERIFY).title("VERIFY").build();
        actions.add(otpEditor)
        actions.add(verifyAction)
    }

    override fun onGuidedActionClicked(action: GuidedAction?) {
        if(action!!.id == VERIFY){
            val otp = actions[0].description.toString()
            if(otp.length != 6 || otp == "000000"){
                LiveTvApplication.showToast("Please enter 6 digit OTP.");
                return
            }
            Log.i(TAG,"Entered OTP $otp")
            TvLauncherUtils.verifyOTP(otp)

            GlobalScope.launch{
                delay(500)
                LiveTvApplication.getAuthHeaders()?.let{
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(NavGraphDirections.actionToMediaBrowser());
                }
            }
        }
    }
}
