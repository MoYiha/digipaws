package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.shared

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AppUsageConfig
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.UsageDayItem
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.UsageSettingsAdapter

abstract class BaseUsageSettingsFragment : BottomSheetDialogFragment() {

    private val daysOfWeek = listOf(
        "Same Limit Everyday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    private val dayItems = mutableListOf<UsageDayItem>()
    private lateinit var adapter: UsageSettingsAdapter
    private lateinit var daysListContainer: RecyclerView

    protected abstract fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View
    protected abstract fun loadUsageConfig(): AppUsageConfig
    protected abstract fun saveUsageConfig(config: AppUsageConfig)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflateView(inflater, container)
        daysListContainer = root.findViewById(R.id.daysListContainer)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        populateFromConfig()
    }

    override fun onDismiss(dialog: DialogInterface) {
        persistConfig()
        super.onDismiss(dialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also {
            it.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun setupRecyclerView() {
        dayItems.clear()
        daysOfWeek.forEach { dayItems.add(UsageDayItem(it, false, 0, 0)) }

        adapter = UsageSettingsAdapter(
            dayItems,
            onUniformToggle = { isUniform -> handleUniformLimitToggle(isUniform) },
            onDisabledClick = {
                Toast.makeText(requireContext(), "Disable everyday to add granular changes", Toast.LENGTH_SHORT).show()
            }
        )
        daysListContainer.layoutManager = LinearLayoutManager(requireContext())
        daysListContainer.adapter = adapter
    }

    private fun populateFromConfig() {
        val config = loadUsageConfig()

        dayItems[0].isEnabled = config.isDailyUniform
        dayItems[0].hours = (config.uniformLimit / 60).toInt()
        dayItems[0].minutes = (config.uniformLimit % 60).toInt()

        // dayItems 1-7 = Mon-Sun; dailyLimits indices 1-6 = Mon-Sat, index 0 = Sun
        setDayItem(1, config.dailyLimits[1])
        setDayItem(2, config.dailyLimits[2])
        setDayItem(3, config.dailyLimits[3])
        setDayItem(4, config.dailyLimits[4])
        setDayItem(5, config.dailyLimits[5])
        setDayItem(6, config.dailyLimits[6])
        setDayItem(7, config.dailyLimits[0])

        handleUniformLimitToggle(config.isDailyUniform)
        adapter.notifyDataSetChanged()
    }

    private fun setDayItem(itemIndex: Int, minutesLimit: Long) {
        val item = dayItems[itemIndex]
        item.isEnabled = minutesLimit > 0
        item.hours = if (minutesLimit > 0) (minutesLimit / 60).toInt() else 0
        item.minutes = if (minutesLimit > 0) (minutesLimit % 60).toInt() else 0
    }

    private fun handleUniformLimitToggle(isUniform: Boolean) {
        for (i in 1 until dayItems.size) {
            dayItems[i].isInteractionEnabled = !isUniform
            if (isUniform) dayItems[i].isEnabled = false
        }
        adapter.notifyDataSetChanged()
    }

    private fun persistConfig() {
        val isDailyUniform = dayItems[0].isEnabled
        val config = AppUsageConfig(
            isDailyUniform = isDailyUniform,
            uniformLimit = if (isDailyUniform) (dayItems[0].hours * 60 + dayItems[0].minutes).toLong() else 0L
        )

        if (!isDailyUniform) {
            config.dailyLimits[1] = (dayItems[1].hours * 60 + dayItems[1].minutes).toLong()
            config.dailyLimits[2] = (dayItems[2].hours * 60 + dayItems[2].minutes).toLong()
            config.dailyLimits[3] = (dayItems[3].hours * 60 + dayItems[3].minutes).toLong()
            config.dailyLimits[4] = (dayItems[4].hours * 60 + dayItems[4].minutes).toLong()
            config.dailyLimits[5] = (dayItems[5].hours * 60 + dayItems[5].minutes).toLong()
            config.dailyLimits[6] = (dayItems[6].hours * 60 + dayItems[6].minutes).toLong()
            config.dailyLimits[0] = (dayItems[7].hours * 60 + dayItems[7].minutes).toLong()
        }

        saveUsageConfig(config)
    }
}
