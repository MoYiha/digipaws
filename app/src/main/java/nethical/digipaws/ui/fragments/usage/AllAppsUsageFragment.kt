package nethical.digipaws.ui.fragments.usage

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
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
import nethical.digipaws.ui.activity.SelectAppsActivity
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.TimeTools
import nethical.digipaws.utils.UsageStatsHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AllAppsUsageFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "all_app_usage"
    }

    private var selectedDate:Long = System.currentTimeMillis()
    private var currentDate:Long = selectedDate
    private var earliestDate:Long = selectedDate

    private var _binding: FragmentAllAppUsageBinding? = null
    private val binding get() = _binding!!


    val selectIgnoredAppsLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            selectedApps?.let {
                val savedPreferencesLoader = SavedPreferencesLoader(requireContext())
                savedPreferencesLoader.saveIgnoredAppUsageTracker(it.toSet())
            }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllAppUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = AppUsageAdapter(emptyList())
        binding.appUsageRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.appUsageRecyclerView.adapter = adapter

        lifecycleScope.launch(Dispatchers.IO) {
            setUsageStats()

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
        binding.selectIgnoredApps.setOnClickListener {
            val savedPreferencesLoader = SavedPreferencesLoader(requireContext())

            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.loadIgnoredAppUsageTracker())
            )
            selectIgnoredAppsLauncher.launch(intent,
                ActivityOptionsCompat.makeCustomAnimation(requireContext(), R.anim.fade_in, R.anim.fade_out))
        }
        binding.selectDate.setOnClickListener {
            showDatePickerDialog(selectedDate, earliestDate, currentDate) { newDate ->
                selectedDate = newDate
                binding.selectDate.text = TimeTools.formatDate(newDate)
                val localDate = Instant.ofEpochMilli(newDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                lifecycleScope.launch(Dispatchers.IO) {
                    setUsageStats(localDate)
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

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch(Dispatchers.IO) {
            val localDate = Instant.ofEpochMilli(selectedDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            setUsageStats(localDate)
        }
    }

    private suspend fun setUsageStats(date : LocalDate = LocalDate.now()) {
        val usageStatsHelper = UsageStatsHelper(requireContext())
        val list = usageStatsHelper.getForegroundStatsByDay(date)
        val totalTime = TimeTools.formatTime(calculateTotalScreenTimeInHours(list),false)

        withContext(Dispatchers.Main) {
            try {
                val adapter = binding.appUsageRecyclerView.adapter as AppUsageAdapter
                adapter.updateData(list)
                updatePieChart(list)
                binding.totalUsage.setText(totalTime)
            } catch (e: Exception) {
                Log.e("AppUsageFragment", "Error updating UI with stats", e)
            }
        }
    }

    private fun calculateTotalScreenTimeInHours(stats: List<Stat>): Long {
        val totalTimeInMillis = stats.sumOf { it.totalTime }

        return totalTimeInMillis
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

    private fun updatePieChart(statsList: List<Stat>) {
        val sortedStats = statsList.sortedByDescending { it.totalTime }
        val topApps = sortedStats.take(3)

        val othersTime = sortedStats.drop(3)
            .sumOf { it.totalTime }

        val entries = mutableListOf<PieEntry>()
        val pm = requireContext().packageManager
        topApps.forEach { stats ->
            val appInfo = pm.getApplicationInfo(stats.packageName,0)
            val icon = appInfo.loadIcon(pm)
            val usageTime = stats.totalTime

            entries.add(PieEntry(usageTime.toFloat(),resizeIcon(icon,25,25)))
        }

        if (othersTime > 0) {
            entries.add(PieEntry(othersTime.toFloat(), ""))
        }
        val pieDataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                // Material Blue 500
                Color.parseColor("#2196F3"),

                // Material Red 500
                Color.parseColor("#F44336"),

                // Material Green 500
                Color.parseColor("#4CAF50"),

                // Material Yellow 500
                requireContext().getColor(R.color.md_theme_inverseSurface)
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
            setDrawEntryLabels(true)  // Disable internal labels
            animateY(1200, Easing.EaseInOutQuart)


            //Todo: Add external labels
            invalidate()
        }
    }

    private fun resizeIcon(icon: Drawable, width: Int, height: Int): Drawable {
        // Convert Drawable to Bitmap
        val bitmap = if (icon is BitmapDrawable) {
            icon.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(
                icon.intrinsicWidth,
                icon.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            icon.setBounds(0, 0, canvas.width, canvas.height)
            icon.draw(canvas)
            bitmap
        }

        // Calculate the target size in pixels (assuming density is needed)
        val density = Resources.getSystem().displayMetrics.density
        val targetWidth = (width * density).toInt()
        val targetHeight = (height * density).toInt()

        // Create scaled bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            targetWidth,
            targetHeight,
            true
        )

        // Convert back to Drawable
        return BitmapDrawable(Resources.getSystem(), scaledBitmap)
    }

    inner class AppUsageViewHolder(private val binding: AppUsageItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(stats: Stat, packageManager: PackageManager) {
            val appInfo = packageManager.getApplicationInfo(stats.packageName, 0)
            binding.root.setOnClickListener{
                activity?.supportFragmentManager?.beginTransaction()
                    ?.setCustomAnimations(R.anim.fade_in,R.anim.fade_out)
                    ?.replace(R.id.fragment_holder, AppUsageBreakdown(stats))
                    ?.addToBackStack(null)
                    ?.commit()
            }

            // Load app icon and label on the main thread
            binding.appIcon.setImageDrawable(appInfo.loadIcon(packageManager))
            binding.appName.text = appInfo.loadLabel(packageManager)
            binding.appUsage.text = TimeTools.formatTime(stats.totalTime)
        }
    }

    inner class AppUsageAdapter(
        private var appUsageStats: List<Stat>
    ) : RecyclerView.Adapter<AppUsageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
            val binding = AppUsageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return AppUsageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
            holder.bind(appUsageStats[position], holder.itemView.context.packageManager)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newAppUsageStats: List<Stat>) {
            appUsageStats = newAppUsageStats
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = appUsageStats.size
    }



    class Stat(
        val packageName: String,
        val totalTime: Long,
        val startTimes: List<ZonedDateTime>
    )

}