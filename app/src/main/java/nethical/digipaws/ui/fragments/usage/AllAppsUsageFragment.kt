package nethical.digipaws.ui.fragments.usage

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.icu.util.Calendar
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nethical.digipaws.R
import nethical.digipaws.databinding.AppUsageItemBinding
import nethical.digipaws.databinding.FragmentAllAppUsageBinding
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

class AllAppsUsageFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "all_app_usage"
    }

    private var selectedDate:Long = System.currentTimeMillis()
    private var currentDate:Long = selectedDate
    private var earliestDate:Long = selectedDate

    private var _binding: FragmentAllAppUsageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllAppUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = AppUsageAdapter(emptyList())
        binding.appUsageRecyclerView.adapter = adapter

        lifecycleScope.launch {
            setUsageStats(
                requireContext().getSystemService(UsageStatsManager::class.java),
                currentDate
            )
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val usageStatsManager = requireContext().getSystemService(UsageStatsManager::class.java)
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                0, System.currentTimeMillis()
            )

            // Calculate earliest available date
            earliestDate = stats.minOfOrNull { it.firstTimeStamp } ?: System.currentTimeMillis()
            currentDate = System.currentTimeMillis()
            selectedDate = currentDate.coerceAtLeast(earliestDate) // Ensure valid range

        }

        binding.selectDate.setOnClickListener {
            showDatePickerDialog(selectedDate, earliestDate, currentDate) { newDate ->
                selectedDate = newDate
                binding.selectDate.text = formatDate(selectedDate)
                Log.d("date", formatDate(selectedDate))


                lifecycleScope.launch {
                    setUsageStats(
                        requireContext().getSystemService(UsageStatsManager::class.java),
                        selectedDate,
                        endDate = selectedDate + 24 * 60 * 60 * 1000
                    )
                }

            }
        }

        binding.appUsageRecyclerView.apply {
            addItemDecoration(
                DividerItemDecoration(
                    context, OrientationHelper.VERTICAL
                )
            )
            this.adapter = adapter
        }
    }

    private suspend fun setUsageStats(usageStatsManager: UsageStatsManager, selectedDate: Long, endDate: Long = System.currentTimeMillis()) {
        val startTime = ZonedDateTime.ofInstant(Date(selectedDate).toInstant(), ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneOffset.systemDefault())
            .toInstant()
            .toEpochMilli()


        val appUsageStats =  usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endDate
        ).map { AppUsageStats(it.packageName, it.totalTimeInForeground) }
            .groupBy { it.packageName }
            .map { (_, statsList) ->
                statsList.reduce { acc, stats ->
                    acc.apply { totalTimeInForeground += stats.totalTimeInForeground }
                }
            }.sortedByDescending { it.totalTimeInForeground }

        val launcherApps = requireContext().getSystemService(LauncherApps::class.java)

        val filteredAppUsageStats =
            appUsageStats.asSequence()
                .takeWhile { it.totalTimeInForeground > 5 * 1000 }.map { stats ->
                    Stats(
                        launcherApps.getApplicationInfo(
                            stats.packageName, 0, Process.myUserHandle()
                        ), stats
                    )
                }.toList()


        withContext(Dispatchers.Main) {
            try {
                val adapter = binding.appUsageRecyclerView.adapter as? AppUsageAdapter
                    ?: AppUsageAdapter(emptyList()).also { binding.appUsageRecyclerView.adapter = it }

                adapter.updateData(filteredAppUsageStats)
                updatePieChart(filteredAppUsageStats)
            } catch (e: Exception) {
                Log.e("AppUsageFragment", "Error updating UI with stats", e)
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    private fun showDatePickerDialog(
        selectedDate: Long,
        startDate: Long,
        endDate: Long,
        onDateSelected: (Long) -> Unit
    ) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedDate

        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val pickedCalendar = Calendar.getInstance()
                pickedCalendar.set(year, month, dayOfMonth)
                onDateSelected(pickedCalendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Restrict the selectable date range
        datePicker.datePicker.minDate = startDate
        datePicker.datePicker.maxDate = endDate
        datePicker.show()
    }

    private fun updatePieChart(statsList: List<Stats>) {
        val sortedStats = statsList.sortedByDescending { it.usageStats.totalTimeInForeground }
        val topApps = sortedStats.take(4)

        val othersTime = sortedStats.drop(4)
            .sumOf { it.usageStats.totalTimeInForeground }

        val entries = mutableListOf<PieEntry>()
        topApps.forEach { stats ->
            val appName =
                stats.applicationInfo.loadLabel(requireContext().packageManager).toString()
            val usageTime = stats.usageStats.totalTimeInForeground
            entries.add(PieEntry(usageTime.toFloat(), appName))
        }

        if (othersTime > 0) {
            entries.add(PieEntry(othersTime.toFloat(), "Others"))
        }

        val pieDataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorPrimary,
                    Color.BLUE
                ),
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorSecondary,
                    Color.WHITE
                ),
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorTertiary,
                    Color.WHITE
                ),
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorPrimaryVariant,
                    Color.CYAN
                ),
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    Color.GRAY
                )
            )

            // Add spacing between slices
            sliceSpace = 3f

            setDrawValues(false)

            // Increase selection shift
            selectionShift = 10f

            setGradientColor(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorPrimaryContainer,
                    Color.LTGRAY
                ),
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorSecondaryContainer,
                    Color.DKGRAY
                )
            )
        }

        val pieData = PieData(pieDataSet)

        binding.pieChart.apply {
            data = pieData
            description.isEnabled = false
            isRotationEnabled = true

            // Center hole styling
            isDrawHoleEnabled = true
            holeRadius = 85f
            transparentCircleRadius = 0f  // Remove transparent circle
            setHoleColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE))

            legend.isEnabled = false

            // External labels styling
            setDrawEntryLabels(false)  // Disable internal labels

            animateY(1200, Easing.EaseInOutQuart)


            //Todo: Add external labels
            invalidate()
        }
        addLegendsAroundChart(entries, pieDataSet.colors)
    }
    private fun addLegendsAroundChart(entries: List<PieEntry>, colors: List<Int>) {
        binding.legendView.removeAllViews()

        // Create and add all legend views first
        val legendViews = entries.mapIndexed { index, entry ->
            createLegendView(entry.label, colors[index]).also {
                binding.legendView.addView(it)
            }
        }

        // Wait for views to be measured
        binding.legendView.post {
            val centerX = binding.pieChart.width / 2f
            val centerY = binding.pieChart.height / 2f
            // Add a margin to the radius to position labels outside the chart
            val radius = binding.pieChart.radius + 60f  // Adjust this value to control how far labels are from the pie

            var currentAngle = -90f // Start from the top (-90 degrees)
            val total = entries.sumOf { it.value.toDouble() }

            legendViews.forEachIndexed { index, legendView ->
                val sliceAngle = (entries[index].value / total * 360f).toFloat()
                // Calculate the center angle of the current slice
                val midAngle = currentAngle + (sliceAngle / 2f)

                // Convert angle to radians for position calculation
                val radians = Math.toRadians(midAngle.toDouble())

                // Calculate position using the mid-angle
                val x = (centerX + radius * Math.cos(radians)).toFloat() - (legendView.width / 2f)
                val y = (centerY + radius * Math.sin(radians)).toFloat() - (legendView.height / 2f)

                // Set the position
                legendView.x = x
                legendView.y = y

                // Move to next slice
                currentAngle += sliceAngle
            }
        }
    }

    private fun createLegendView(label: String, color: Int): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 12f
            setTextColor(requireContext().getColor(R.color.text_color))
            setPadding(8, 4, 8, 4)


            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }


    data class AppUsageStats(val packageName: String, var totalTimeInForeground: Long)

    inner class AppUsageViewHolder(private val binding: AppUsageItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(stats: Stats, packageManager: PackageManager) {
            binding.appIcon.setImageDrawable(stats.applicationInfo.loadIcon(packageManager))
            binding.appName.text = stats.applicationInfo.loadLabel(packageManager)
            binding.appUsage.text = formatTime(stats.usageStats.totalTimeInForeground)

        }
    }

    private fun formatTime(timeInMillis: Long): String {
        val hours = timeInMillis / (1000 * 60 * 60)
        val minutes = (timeInMillis % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (timeInMillis % (1000 * 60)) / 1000

        return when {
            hours > 0 -> "$hours hr" + (if (minutes > 0) " $minutes mins" else "") + (if (seconds > 0) " $seconds secs" else "")
            minutes > 0 -> "$minutes mins" + (if (seconds > 0) " $seconds secs" else "")
            else -> "$seconds secs"
        }
    }




    data class Stats(val applicationInfo: ApplicationInfo, val usageStats: AppUsageStats)

    inner class AppUsageAdapter(private var appUsageStats: List<Stats>) :
        RecyclerView.Adapter<AppUsageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
            val binding = AppUsageItemBinding.inflate(layoutInflater, parent, false)
            return AppUsageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
            holder.bind(appUsageStats[position], requireContext().packageManager)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newAppUsageStats: List<Stats>) {
            appUsageStats = newAppUsageStats
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = appUsageStats.size
    }

}