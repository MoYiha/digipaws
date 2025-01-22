package nethical.digipaws.ui.fragments.usage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import nethical.digipaws.R

class AppUsageBreakdown(private val stat: AllAppsUsageFragment.Stat) : Fragment() {

    private lateinit var lineChart: LineChart

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app_usage_breakdown, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lineChart = view.findViewById(R.id.lineChart)
        setupLineChart()
        plotUsageData()
    }

    private fun setupLineChart() {
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
        // Initialize 24-hour time slots with zero usage
        val hourlyUsage = MutableList(24) { 0L }

        // Process each start time
        stat.startTimes.forEach { startTime ->
            val hour = startTime.hour
            // Convert milliseconds to minutes and add to the appropriate hour slot
            hourlyUsage[hour] = hourlyUsage[hour] + (stat.totalTime / (1000 * 60))
        }

        // Create entries from hourly usage data
        val entries = hourlyUsage.mapIndexed { hour, minutes ->
            Entry(hour.toFloat(), minutes.toFloat())
        }

        // Create and configure the dataset
        val dataSet = LineDataSet(entries, "Usage (minutes)").apply {
            color = resources.getColor(R.color.md_theme_primary)
            valueTextColor = resources.getColor(R.color.white)
            lineWidth = 2f
            setDrawFilled(true)
            fillColor = resources.getColor(R.color.md_theme_primary)
            fillAlpha = 50
            setDrawCircles(true)
            circleRadius = 4f
            circleHoleRadius = 2f
            setDrawValues(false)
            valueFormatter = MinutesValueFormatter()
        }

        // Set the data and refresh
        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
    }

    private class HourAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val hour = value.toInt()
            return String.format("%02d:00", hour)
        }
    }

    private class MinutesAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return "${value.toInt()} min"
        }
    }

    private class MinutesValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            if (value == 0f) return ""
            return "${value.toInt()}m"
        }
    }
}