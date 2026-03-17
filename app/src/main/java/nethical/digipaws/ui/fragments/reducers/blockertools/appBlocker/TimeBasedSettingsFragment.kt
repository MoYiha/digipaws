package nethical.digipaws.ui.fragments.reducers.blockertools.appBlocker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import nethical.digipaws.R

class TimeBasedSettingsFragment : BottomSheetDialogFragment() {

    companion object {
        const val FRAGMENT_ID = "time_based_settings"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_app_blocker_usage_settings, container, false)
        return view
    }
}