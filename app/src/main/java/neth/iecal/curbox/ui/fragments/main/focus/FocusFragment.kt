package neth.iecal.curbox.ui.fragments.main.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.databinding.FragmentFocusBinding
import androidx.core.view.isNotEmpty
import kotlin.math.abs
import kotlin.math.floor

class FocusFragment : Fragment() {

    private var _binding: FragmentFocusBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FocusViewModel by activityViewModels()

    private var isProgrammaticScroll = false
    private var itemWidthPx = 0
    private val snapHelper = LinearSnapHelper()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFocusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentRunningFocus.collect { (groupId, endTime) ->
                        val isRunning = groupId != null
                        binding.tvActiveGroup.visibility = if (isRunning) View.VISIBLE else View.GONE
                        binding.btnGoToStats.visibility = if (isRunning) View.GONE else View.VISIBLE
                        binding.tvSeconds.text = if (isRunning) "" else "mins"
                        binding.btnStartConfig.text = if (isRunning) getString(R.string.focus_end_session) else getString(R.string.focus_start)

                        if (isRunning) {
                            binding.rvRuler.stopScroll()
                            snapHelper.attachToRecyclerView(null)
                            val group = viewModel.groups.value.find { it.groupId == groupId }
                            binding.tvActiveGroup.text = group?.groupName
                            binding.btnStartConfig.isEnabled = group?.exitable == true
                            viewModel.startTimer(endTime)
                        } else {
                            snapHelper.attachToRecyclerView(binding.rvRuler)
                            binding.btnStartConfig.isEnabled = true
                            binding.tvMinutes.text = viewModel.selectedMins.toString()
                            scrollToMinute(viewModel.selectedMins, smooth = false)
                        }
                    }
                }

                launch {
                    var lastTotalMinutesLeft = -1.0
                    var floatPixelAccumulator = 0.0

                    viewModel.currentRunningTimer.collect { time ->
                        val currentFocus = viewModel.currentRunningFocus.value
                        if (currentFocus.first != null && time > 0) {
                            val totalMinutesLeft = time / 60000.0
                            val minutes = (time / 60000).toInt()
                            val seconds = ((time % 60000) / 1000).toInt()

                            binding.tvMinutes.text = minutes.toString()
                            binding.tvSeconds.text = String.format(Locale.getDefault(), ":%02d", seconds)

                            if (binding.rvRuler.width > 0 && binding.rvRuler.isNotEmpty()) {
                                // Dynamically fetch the exact physical width of a rendered item
                                if (itemWidthPx > 0) {
                                    if (lastTotalMinutesLeft < 0 || abs(lastTotalMinutesLeft - totalMinutesLeft) > 1.0) {
                                        val actualItemWidth = itemWidthPx
                                        val padding = (binding.rvRuler.width / 2) - (actualItemWidth / 2)
                                        val integerPart = floor(totalMinutesLeft).toInt()
                                        val fractionalPart = (totalMinutesLeft - integerPart).toFloat()
                                        val offset = padding - (fractionalPart * actualItemWidth).toInt()

                                        isProgrammaticScroll = true
                                        (binding.rvRuler.layoutManager as LinearLayoutManager)
                                            .scrollToPositionWithOffset(integerPart, offset)
                                        isProgrammaticScroll = false // Reset instantly, onScrolled is synchronous

                                        floatPixelAccumulator = 0.0
                                    } else {
                                        // 2. Smoothly scroll the delta to prevent layout thrashing
                                        val actualItemWidth = itemWidthPx
                                        val deltaMinutes = lastTotalMinutesLeft - totalMinutesLeft
                                        floatPixelAccumulator += deltaMinutes * actualItemWidth
                                        val pixelsToScroll = floatPixelAccumulator.toInt()

                                        if (pixelsToScroll != 0) {
                                            isProgrammaticScroll = true
                                            binding.rvRuler.scrollBy(-pixelsToScroll, 0)
                                            isProgrammaticScroll = false
                                            floatPixelAccumulator -= pixelsToScroll
                                        }
                                    }
                                    lastTotalMinutesLeft = totalMinutesLeft
                                }
                            }
                        } else {
                            // Reset state when timer stops
                            lastTotalMinutesLeft = -1.0
                        }
                    }
                }
            }
        }
        setupRuler()
        setupClicks()
    }

    private fun setupClicks() {
        binding.btnGoToStats.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, FocusStatsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnStartConfig.setOnClickListener {
            if (viewModel.currentRunningFocus.value.first != null) {
                viewModel.forceStopFocus()
            } else {
                FocusSetupBottomSheet().show(parentFragmentManager, FocusSetupBottomSheet.FRAGMENT_ID)
            }
        }
    }


    private fun updateTime(pos:Int){
        viewModel.selectedMins = pos.coerceAtLeast(1)
        binding.tvMinutes.text = viewModel.selectedMins.toString()
        binding.tvSeconds.text = "mins"
    }

    private fun setupRuler() {
        val initialSelectedMins = viewModel.selectedMins
        isProgrammaticScroll = true

        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRuler.layoutManager = layoutManager
        binding.rvRuler.adapter = RulerAdapter(240)

        binding.rvRuler.setOnTouchListener { _, _ ->
            viewModel.currentRunningFocus.value.first != null
        }

        snapHelper.attachToRecyclerView(binding.rvRuler)

        binding.rvRuler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isProgrammaticScroll || viewModel.currentRunningFocus.value.first != null) return
                val centerView = snapHelper.findSnapView(layoutManager) ?: return
                val pos = layoutManager.getPosition(centerView)
                updateTime(pos)
            }
        })

        binding.rvRuler.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (_binding == null || binding.rvRuler.width == 0) return
                binding.rvRuler.viewTreeObserver.removeOnGlobalLayoutListener(this)

                itemWidthPx = (20 * resources.displayMetrics.density).toInt()
                val padding = (binding.rvRuler.width / 2) - (itemWidthPx / 2)
                binding.rvRuler.setPadding(padding, 0, padding, 0)
                binding.rvRuler.clipToPadding = false

                binding.rvRuler.post {
                    if (viewModel.currentRunningFocus.value.first == null) {
                        scrollToMinute(initialSelectedMins, smooth = false)
                    }
                }
            }
        })
    }

    private fun scrollToMinute(minutes: Int, smooth: Boolean = true) {
        val targetPos = minutes.coerceAtLeast(0)
        isProgrammaticScroll = true

        if (smooth) {
            binding.rvRuler.smoothScrollToPosition(targetPos)
        } else {
            val padding = (binding.rvRuler.width / 2) - (itemWidthPx / 2)
            (binding.rvRuler.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(targetPos, padding)
        }

        binding.rvRuler.postDelayed({ isProgrammaticScroll = false }, 300)
        if (!smooth) updateTime(minutes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}