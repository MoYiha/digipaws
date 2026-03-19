package nethical.digipaws.ui.fragments.main.reducers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import nethical.digipaws.R

import android.content.Intent
import nethical.digipaws.ui.activity.FragmentActivity
import com.google.android.material.card.MaterialCardView
import nethical.digipaws.ui.fragments.main.reducers.blockertools.appBlocker.AppBlockerGroupsFragment

class ReducersFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reducers, container, false)
        val appBlockerCard = view.findViewById<MaterialCardView>(R.id.card_app_blocker)
        
        appBlockerCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", AppBlockerGroupsFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }
        
        return view
    }
}
