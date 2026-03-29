package nethical.digipaws.ui.fragments.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import nethical.digipaws.databinding.FragmentInfoBinding

class InfoFragment : Fragment() {

    private var _binding: FragmentInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSupport.setOnClickListener {
            // Replace with actual website/donation link
            openUrl("https://github.com/nethical6")
        }
        
        binding.btnDiscord.setOnClickListener {
            // Replace with actual Discord invite link
            openUrl("https://discord.com/invite/Vs9mwUtuCN")
        }

        binding.cardInstagram.setOnClickListener {
            openUrl("https://instagram.com/digipaws.app")
        }

        binding.cardTiktok.setOnClickListener {
            openUrl("https://tiktok.com/@digipaws.app")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
