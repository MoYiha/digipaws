package nethical.digipaws.ui.fragments.main.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
// Make sure to import your generated binding class
import nethical.digipaws.databinding.FragmentFocusBinding

class FocusFragment : Fragment() {

    private var _binding: FragmentFocusBinding? = null
    private val binding get() = _binding!!

    private var currentMinutes = 59
    private var isProgrammaticScroll = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFocusBinding.inflate(inflater, container, false)

        setupRuler()

        binding.btn15m.setOnClickListener { scrollToMinute(15) }
        binding.btn30m.setOnClickListener { scrollToMinute(30) }
        binding.btn60m.setOnClickListener { scrollToMinute(60) }
        binding.btn120m.setOnClickListener { scrollToMinute(120) }

        return binding.root
    }

    private fun setupRuler() {
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRuler.layoutManager = layoutManager
        binding.rvRuler.adapter = RulerAdapter(240) // Give it up to 240 mins

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.rvRuler)

        binding.rvRuler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isProgrammaticScroll) return

                val centerView = snapHelper.findSnapView(layoutManager)
                if (centerView != null) {
                    val pos = layoutManager.getPosition(centerView)
                    updateMinutesDisplay(pos + 1)
                }
            }
        })

        binding.rvRuler.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (_binding == null || binding.rvRuler.width == 0) return

                binding.rvRuler.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val displayMetrics = resources.displayMetrics
                val itemWidthPx = (20 * displayMetrics.density).toInt()
                val padding = (binding.rvRuler.width / 2) - (itemWidthPx / 2)

                binding.rvRuler.setPadding(padding, 0, padding, 0)
                binding.rvRuler.clipToPadding = false

                scrollToMinute(currentMinutes, smooth = false)
            }
        })
    }

    private fun scrollToMinute(minutes: Int, smooth: Boolean = true) {
        val targetPos = minutes - 1
        isProgrammaticScroll = true
        updateMinutesDisplay(minutes)

        if (smooth) {
            binding.rvRuler.smoothScrollToPosition(targetPos)
        } else {
            (binding.rvRuler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(targetPos, 0)
        }

        binding.rvRuler.postDelayed({
            isProgrammaticScroll = false
        }, 300)
    }

    private fun updateMinutesDisplay(minutes: Int) {
        currentMinutes = minutes
        binding.tvMinutes.text = currentMinutes.toString()
    }

    // 4. Clean up the binding to prevent memory leaks!
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}