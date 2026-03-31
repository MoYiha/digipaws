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
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.databinding.FragmentFocusBinding
import neth.iecal.curbox.utils.TimeTools

class FocusFragment : Fragment() {

    private var _binding: FragmentFocusBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: FocusViewModel by activityViewModels()

    private var isProgrammaticScroll = false

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
                        if (groupId != null) {
                            binding.activeContainer.visibility = View.VISIBLE
                            binding.setupContainer.visibility = View.GONE

                            val group = viewModel.groups.value.find { it.groupId == groupId }
                            binding.tvActiveGroup.text = group!!.groupName

                            binding.btnStop.visibility = if (group.exitable) View.VISIBLE else View.GONE
                            viewModel.startTimer(endTime)
                        } else {
                            binding.activeContainer.visibility = View.GONE
                            binding.setupContainer.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    viewModel.currentRunningTimer.collect { time ->
                        binding.tvCountdown.text = TimeTools.formatTimeInHHMM(time)
                    }
                }
            }
        }
        setupRuler()
        setupClicks()
    }
    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = neth.iecal.curbox.data.db.AppDatabase.getInstance(requireContext())
            val runningSessions = db.focusStatsDao().getRunningSessions()
            if (runningSessions.isEmpty()) {
                val intent = android.content.Intent(neth.iecal.curbox.blockers.FocusModeBlocker.INTENT_ACTION_UNSUSPEND_ALL)
                intent.setPackage(requireContext().packageName)
                requireContext().sendBroadcast(intent)
            }
        }
    }

    private fun setupClicks() {
        binding.btn15m.setOnClickListener { scrollToMinute(15) }
        binding.btn30m.setOnClickListener { scrollToMinute(30) }
        binding.btn60m.setOnClickListener { scrollToMinute(60) }
        binding.btn120m.setOnClickListener { scrollToMinute(120) }

        binding.btnGoToStats.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, FocusStatsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnStartConfig.setOnClickListener {
            FocusSetupBottomSheet().show(parentFragmentManager, FocusSetupBottomSheet.FRAGMENT_ID)
        }

        binding.btnStop.setOnClickListener {
            viewModel.forceStopFocus()
        }

    }


    private fun updateTime(pos:Int){
        viewModel.selectedMins = pos + 1
        binding.tvMinutes.text = viewModel.selectedMins.toString()
    }

    private fun setupRuler() {
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRuler.layoutManager = layoutManager
        binding.rvRuler.adapter = RulerAdapter(240)

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.rvRuler)

        binding.rvRuler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isProgrammaticScroll) return
                val centerView = snapHelper.findSnapView(layoutManager) ?: return
                val pos = layoutManager.getPosition(centerView)
                updateTime(pos)
            }
        })

        binding.rvRuler.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (_binding == null || binding.rvRuler.width == 0) return
                binding.rvRuler.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val itemWidthPx = (20 * resources.displayMetrics.density).toInt()
                val padding = (binding.rvRuler.width / 2) - (itemWidthPx / 2)
                binding.rvRuler.setPadding(padding, 0, padding, 0)
                binding.rvRuler.clipToPadding = false
            }
        })
    }

    private fun scrollToMinute(minutes: Int, smooth: Boolean = true) {
        val targetPos = (minutes - 1).coerceAtLeast(0)
        isProgrammaticScroll = true

        if (smooth) {
            binding.rvRuler.smoothScrollToPosition(targetPos)
        } else {
            (binding.rvRuler.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(targetPos, 0)
        }

        binding.rvRuler.postDelayed({ isProgrammaticScroll = false }, 300)
        updateTime(minutes - 1)
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}