package neth.iecal.curbox.ui.fragments.main.reducers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import neth.iecal.curbox.R

import android.content.Intent
import neth.iecal.curbox.ui.activity.FragmentActivity
import com.google.android.material.card.MaterialCardView
import neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.grayscale.GrayscaleFragment
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.appBlocker.AppBlockerGroupsFragment

class ReducersFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reducers, container, false)
        val appBlockerCard = view.findViewById<MaterialCardView>(R.id.card_app_blocker)
        val reelBlockerCard = view.findViewById<MaterialCardView>(R.id.card_reels_blocker)
        val keywordBlockerCard = view.findViewById<MaterialCardView>(R.id.card_keyword_blocker)
        val autoDndCard = view.findViewById<MaterialCardView>(R.id.card_autodnd)
        
        appBlockerCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", AppBlockerGroupsFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }
        
        reelBlockerCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", neth.iecal.curbox.ui.fragments.main.reducers.blockertools.reelBlocker.ReelBlockerFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }

        keywordBlockerCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", neth.iecal.curbox.ui.fragments.main.reducers.blockertools.keywordBlocker.KeywordBlockerFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }

        autoDndCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", neth.iecal.curbox.ui.fragments.main.reducers.blockertools.autodnd.AutoDndFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }

        val reelCounterCard = view.findViewById<MaterialCardView>(R.id.card_reel_counter)
        reelCounterCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.reel_counter.ReelCounterFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }
        
        val grayscaleCard = view.findViewById<MaterialCardView>(R.id.card_grayscale)
        grayscaleCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", GrayscaleFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }
        
        val intentsCard = view.findViewById<MaterialCardView>(R.id.card_intents)
        intentsCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.mindful_messages.MindfulMessagesFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }

        val uiHiderCard = view.findViewById<MaterialCardView>(R.id.card_ui_hider)
        uiHiderCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", neth.iecal.curbox.ui.fragments.main.reducers.blockertools.uiHider.UiHiderFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }

        val intentsLogCard = view.findViewById<MaterialCardView>(R.id.card_logged_intents)
        intentsLogCard.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", neth.iecal.curbox.ui.fragments.main.reducers.analytics.IntentsLogFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }

        return view
    }
}
