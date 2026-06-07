package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.keywordBlocker

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import neth.iecal.curbox.databinding.FragmentAppBlockerUsageSettingsBinding
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.UsageDayItem
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.UsageSettingsAdapter

class KeywordUsageBasedSettingsFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAppBlockerUsageSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KeywordBlockerViewModel by activityViewModels()

    private val daysOfWeek = listOf(
        "Same Limit Everyday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    private val dayItems = mutableListOf<UsageDayItem>()
    private lateinit var adapter: UsageSettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppBlockerUsageSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadConfig()
    }

    override fun onDismiss(dialog: DialogInterface) {
        saveConfig()
        super.onDismiss(dialog)
    }

    private fun setupRecyclerView() {
        dayItems.clear()
        daysOfWeek.forEach { dayName ->
            dayItems.add(UsageDayItem(dayName, false, 0, 0))
        }

        adapter = UsageSettingsAdapter(dayItems, onUniformToggle = { isUniform ->
            handleUniformLimitToggle(isUniform)
        }, onDisabledClick = {
            Toast.makeText(requireContext(), "Disable everyday to add granular changes", Toast.LENGTH_SHORT).show()
        })
        binding.daysListContainer.layoutManager = LinearLayoutManager(requireContext())
        binding.daysListContainer.adapter = adapter
    }

    private fun loadConfig() {
        val config = viewModel.currentUsageConfig
        
        dayItems[0].isEnabled = config.isDailyUniform
        dayItems[0].hours = (config.uniformLimit / 60).toInt()
        dayItems[0].minutes = (config.uniformLimit % 60).toInt()

        val dailyLimits = config.dailyLimits
        setDayItem(1, dailyLimits[1])
        setDayItem(2, dailyLimits[2])
        setDayItem(3, dailyLimits[3])
        setDayItem(4, dailyLimits[4])
        setDayItem(5, dailyLimits[5])
        setDayItem(6, dailyLimits[6])
        setDayItem(7, dailyLimits[0])

        handleUniformLimitToggle(config.isDailyUniform)
        adapter.notifyDataSetChanged()
    }

    private fun setDayItem(itemIndex: Int, minutesLimit: Long) {
        val item = dayItems[itemIndex]
        if (minutesLimit > 0) {
            item.isEnabled = true
            item.hours = (minutesLimit / 60).toInt()
            item.minutes = (minutesLimit % 60).toInt()
        } else {
            item.isEnabled = false
            item.hours = 0
            item.minutes = 0
        }
    }

    private fun handleUniformLimitToggle(isUniform: Boolean) {
        for (i in 1 until dayItems.size) {
            dayItems[i].isInteractionEnabled = !isUniform
            if (isUniform) {
                dayItems[i].isEnabled = false
            }
        }
        adapter.notifyDataSetChanged()
    }

    fun saveConfig() {
        val config = viewModel.currentUsageConfig
        config.isDailyUniform = dayItems[0].isEnabled

        if (config.isDailyUniform) {
            config.uniformLimit = (dayItems[0].hours * 60 + dayItems[0].minutes).toLong()
            config.dailyLimits.fill(0)
        } else {
            config.uniformLimit = 0
            config.dailyLimits[1] = (dayItems[1].hours * 60 + dayItems[1].minutes).toLong()
            config.dailyLimits[2] = (dayItems[2].hours * 60 + dayItems[2].minutes).toLong()
            config.dailyLimits[3] = (dayItems[3].hours * 60 + dayItems[3].minutes).toLong()
            config.dailyLimits[4] = (dayItems[4].hours * 60 + dayItems[4].minutes).toLong()
            config.dailyLimits[5] = (dayItems[5].hours * 60 + dayItems[5].minutes).toLong()
            config.dailyLimits[6] = (dayItems[6].hours * 60 + dayItems[6].minutes).toLong()
            config.dailyLimits[0] = (dayItems[7].hours * 60 + dayItems[7].minutes).toLong()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    companion object {
        const val FRAGMENT_ID = "KeywordUsageBasedSettingsBottomSheet"
    }
}
