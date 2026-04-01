package neth.iecal.curbox.ui.fragments.usage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.color.MaterialColors
import neth.iecal.curbox.R
import neth.iecal.curbox.databinding.FragmentAppUsageBreakdownBinding
import neth.iecal.curbox.utils.TimeTools

class AppUsageBreakdown(private val stat: AllAppsUsageFragment.Stat) : Fragment() {


    private lateinit var binding: FragmentAppUsageBreakdownBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = FragmentAppUsageBreakdownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLineChart(binding.lineChart)
        plotUsageData()

        try {
            val appInfo = requireContext().packageManager.getApplicationInfo(stat.packageName, 0)
            binding.appName.text = appInfo.loadLabel(requireContext().packageManager)

            binding.appIcon.setImageDrawable(appInfo.loadIcon(requireContext().packageManager))
        } catch (_: Exception) {
        }
        binding.screentime.text = TimeTools.formatTime(stat.totalTime, false)
        binding.sessions.text = stat.startTimes.size.toString()
    }

    private fun setupLineChart(lineChart: LineChart) {
        lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = true

            // Enable touch gestures
            setTouchEnabled(true)
            setPinchZoom(true)

            // Configure X axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = 45f
                valueFormatter = HourAxisFormatter()
            }

            // Configure Y axis
            axisLeft.apply {
                valueFormatter = MinutesAxisFormatter()
                axisMinimum = 0f
            }
            axisRight.isEnabled = false

            // Animate chart
            animateX(1000)
        }
    }

    private fun plotUsageData() {
        val hourlyUsage = stat.hourlyUsage

        // Create entries from hourly usage data (convert ms to minutes)
        val entries = hourlyUsage.mapIndexed { hour, durationMs ->
            Entry(hour.toFloat(), durationMs / (1000f * 60f))
        }

        // Create and configure the dataset
        val dataSet = LineDataSet(entries, "Usage Time (minutes)")

        setupChartUI(binding.lineChart,dataSet)
    }



    private fun setupChartUI(
        chart: LineChart,
        lineDataSet: LineDataSet
    ) {

        val primaryColor = MaterialColors.getColor(
            requireContext(), com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(
                requireContext(),
                R.color.text_color
            )
        )
        lineDataSet.apply {
            color = primaryColor
            valueTextColor = primaryColor
            lineWidth = 3f
            setDrawCircles(false)

            setDrawValues(false)

            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            labelCount = 5
            setDrawGridLines(false) // Disable vertical grid lines
            textColor = primaryColor
        }

        chart.axisLeft.apply {
            isEnabled = true
            setDrawGridLines(false)
            textColor = primaryColor
            valueFormatter = MinutesAxisFormatter()
            axisMinimum = 0f
        }

        chart.apply {
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            animateY(800, Easing.EaseInCubic)

            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)

            setPinchZoom(false)

            data = LineData(lineDataSet)

        }
        chart.invalidate()
    }

    private class HourAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val hour = value.toInt()
            return String.format("%02d:00", hour)
        }
    }

    private class MinutesAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val totalMinutes = value.toInt()
            if (totalMinutes == 0) return "0m"
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) {
                if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            } else {
                "${minutes}m"
            }
        }
    }

    private class MinutesValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            if (value == 0f) return ""
            return "${value.toInt()}m"
        }
    }
}