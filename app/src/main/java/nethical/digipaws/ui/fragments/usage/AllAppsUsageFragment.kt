package nethical.digipaws.ui.fragments.usage

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nethical.digipaws.R
import nethical.digipaws.databinding.AppUsageItemBinding
import nethical.digipaws.databinding.DialogPermissionInfoBinding
import nethical.digipaws.databinding.FragmentAllAppUsageBinding
import nethical.digipaws.ui.activity.FragmentActivity
import nethical.digipaws.ui.activity.SelectAppsActivity
import nethical.digipaws.utils.TimeTools
import nethical.digipaws.utils.UsageStatsHelper
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

class AllAppsUsageFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "all_app_usage"
    }

    private var selectedDate: Long = System.currentTimeMillis()
    private var currentDate: Long = selectedDate
    private var earliestDate: Long = selectedDate

    private var _binding: FragmentAllAppUsageBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AllAppsUsageViewModel

    val selectIgnoredAppsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                selectedApps?.let {
                    lifecycleScope.launch(Dispatchers.IO) {
                        nethical.digipaws.utils.DataStoreManager(requireContext()).updateUsageTrackerIgnoredApps(it)
                    }
                    viewModel.ignoredPackages.addAll(it)
                    viewModel.reload()
                }
            }
        }

    private var csvDataToExport: String = ""

    private val createCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        requireContext().contentResolver.openOutputStream(it)?.use { stream ->
                            stream.write(csvDataToExport.toByteArray())
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Data exported successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ExportCSV", "Error writing file", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to export data",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
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

        viewModel = ViewModelProvider(this)[AllAppsUsageViewModel::class.java]

        if (!hasUsageStatsPermission(requireContext())) {
            makeUsageStatsPermissoinDialog()
        }

        // Setup RecyclerView
        val adapter = AppUsageAdapter(emptyList())
        binding.appUsageRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.appUsageRecyclerView.adapter = adapter

        // Setup PieChart
        setupPieChart()

        // Observe ViewModel
        observeViewModel(adapter)

        // Setup week navigation
        binding.btnPrevWeek.setOnClickListener {
            viewModel.goToPreviousWeek()
        }
        binding.btnNextWeek.setOnClickListener {
            viewModel.goToNextWeek()
        }

        // Setup bar graph tap listener
        binding.weeklyBarGraph.setOnDaySelectedListener { dayData ->
            val index = viewModel.weeklyData.value?.indexOf(dayData) ?: return@setOnDaySelectedListener
            viewModel.selectDay(index)
            selectedDate = dayData.dateMillis
        }

        // Menu
        binding.openMenu.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), binding.openMenu)
            popupMenu.menuInflater.inflate(R.menu.usage_tracker_options, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.select_ignored -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val ignoredApps = nethical.digipaws.utils.DataStoreManager(requireContext()).settings.first().usageTrackerIgnoredApps
                            withContext(Dispatchers.Main) {
                                val intent = Intent(requireContext(), SelectAppsActivity::class.java)
                                intent.putStringArrayListExtra(
                                    "PRE_SELECTED_APPS",
                                    ArrayList(ignoredApps)
                                )
                                selectIgnoredAppsLauncher.launch(
                                    intent,
                                    ActivityOptionsCompat.makeCustomAnimation(
                                        requireContext(),
                                        R.anim.fade_in,
                                        R.anim.fade_out
                                    )
                                )
                            }
                        }
                        true
                    }

                    R.id.export_as_csv -> {

                        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText("Select Export Range")
                            .setSelection(
                                androidx.core.util.Pair(
                                    MaterialDatePicker.thisMonthInUtcMilliseconds(),
                                    MaterialDatePicker.todayInUtcMilliseconds()
                                )
                            )
                            .build()

                        dateRangePicker.addOnPositiveButtonClickListener { selection ->
                            val startDateMs = selection.first
                            val endDateMs = selection.second

                            val options =
                                arrayOf("Daily Breakdown (Time Series)", "Total Summary", "Both")
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Select Data Format")
                                .setSingleChoiceItems(options, 0) { dialog, which ->
                                    generateAndExportCsv(startDateMs, endDateMs, which)
                                    dialog.dismiss()
                                }
                                .show()
                        }
                        dateRangePicker.show(childFragmentManager, "EXPORT_DATE_picker")
                        true
                    }

                    R.id.add_shortcut_usage_tracker -> {
                        val intent =
                            Intent(requireContext(), FragmentActivity::class.java).apply {
                                action = Intent.ACTION_CREATE_SHORTCUT
                            }

                        intent.putExtra("fragment", FRAGMENT_ID)
                        val shortcutInfo =
                            ShortcutInfoCompat.Builder(
                                requireContext(),
                                "digipaws_usage_tracker"
                            )
                                .setShortLabel("Usage Stats")
                                .setLongLabel("Usage Stats")
                                .setIntent(intent)
                                .setIcon(
                                    IconCompat.createWithResource(
                                        requireContext(),
                                        R.drawable.baseline_query_stats_24
                                    )
                                )
                                .build()

                        val supported =
                            ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())
                        val dynamicShortcuts =
                            ShortcutManagerCompat.getDynamicShortcuts(requireContext())

                        if (supported) {
                            if (dynamicShortcuts.contains(shortcutInfo)) {
                                return@setOnMenuItemClickListener false
                            }
                        }
                        val pinnedShortcutCallbackIntent =
                            Intent("example.intent.action.SHORTCUT_CREATED")

                        val successCallback = PendingIntent.getBroadcast(
                            requireContext(),
                            3000,
                            pinnedShortcutCallbackIntent,
                            FLAG_IMMUTABLE
                        )

                        ShortcutManagerCompat.requestPinShortcut(
                            requireContext(),
                            shortcutInfo,
                            successCallback.intentSender
                        )

                        true
                    }

                    else -> false
                }
            }

            popupMenu.show()
        }

        // Initialize ViewModel data
        viewModel.initialize()

        lifecycleScope.launch(Dispatchers.IO) {
            findDataAvailabilityRange()
        }
    }

    private fun setupPieChart() {
        binding.pieChart.apply {
            description.isEnabled = false
            isRotationEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 85f
            transparentCircleRadius = 88f
            setTransparentCircleColor(
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    Color.WHITE
                )
            )
            setTransparentCircleAlpha(120)
            setHoleColor(
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    Color.WHITE
                )
            )
            legend.isEnabled = false
            setDrawEntryLabels(false)
            setDrawCenterText(false) // We use our own overlay TextViews
        }
    }

    private fun observeViewModel(adapter: AppUsageAdapter) {
        viewModel.weeklyData.observe(viewLifecycleOwner) { data ->
            val selectedIdx = viewModel.selectedDayIndex.value ?: 6
            binding.weeklyBarGraph.setData(data, selectedIdx)
        }

        viewModel.selectedDayIndex.observe(viewLifecycleOwner) { index ->
            binding.weeklyBarGraph.setSelectedIndex(index)
        }

        viewModel.selectedDayStats.observe(viewLifecycleOwner) { stats ->
            adapter.updateData(stats)
            updatePieChart(stats)
        }

        viewModel.totalTime.observe(viewLifecycleOwner) { totalMs ->
            binding.totalUsage.text = TimeTools.formatTimeForWidget(totalMs)
        }

        viewModel.comparisonText.observe(viewLifecycleOwner) { text ->
            binding.comparisonText.text = text
            binding.comparisonText.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.weekRangeLabel.observe(viewLifecycleOwner) { label ->
            binding.tvWeekRange.text = label
        }

        viewModel.canGoNext.observe(viewLifecycleOwner) { canGo ->
            binding.btnNextWeek.alpha = if (canGo) 1f else 0.3f
            binding.btnNextWeek.isEnabled = canGo
        }

        viewModel.dateSublabel.observe(viewLifecycleOwner) { label ->
            binding.dateSublabel.text = label
        }
    }

    fun findDataAvailabilityRange() {
        val usageStatsManager = requireContext().getSystemService(UsageStatsManager::class.java)
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            0, System.currentTimeMillis()
        )

        earliestDate = stats.minOfOrNull { it.firstTimeStamp } ?: System.currentTimeMillis()
        currentDate = System.currentTimeMillis()
        selectedDate = currentDate.coerceAtLeast(earliestDate)
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.reload()
        }
    }

    private fun makeUsageStatsPermissoinDialog() {
        val dialogBinding =
            DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogBinding.title.text =
            getString(R.string.enable_2, "Device Usage Access")

        dialogBinding.desc.text =
            "DigiPaws requires device usage access to monitor apps, helping you manage screen time effectively and stay focused on your goals. Rest assured, all data stays securely on your device and is never shared with anyone, ensuring your privacy is fully protected."

        dialogBinding.point1.text = "Track what apps you use"
        dialogBinding.point2.visibility = View.GONE
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .show()

        dialogBinding.btnReject.setOnClickListener {
            dialog.dismiss()
            activity?.finish()
        }
        dialogBinding.btnAccept.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Find 'Digipaws' and press enable",
                Toast.LENGTH_LONG
            )
                .show()
            requestUsageStatsPermission(requireContext())
            dialog.dismiss()
        }
    }

    private fun updatePieChart(statsList: List<Stat>) {
        binding.pieLegend.removeAllViews()

        if (statsList.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.invalidate()
            return
        }

        val sortedStats = statsList.sortedByDescending { it.totalTime }
        val topApps = sortedStats.take(3)

        val othersTime = sortedStats.drop(3)
            .sumOf { it.totalTime }

        val entries = mutableListOf<PieEntry>()
        topApps.forEach { stats ->
            entries.add(PieEntry(stats.totalTime.toFloat()))
        }

        if (othersTime > 0) {
            entries.add(PieEntry(othersTime.toFloat()))
        }

        val colorPrimary = MaterialColors.getColor(
            requireView(),
            com.google.android.material.R.attr.colorPrimary
        )
        val colorSecondary = MaterialColors.getColor(
            requireView(),
            com.google.android.material.R.attr.colorSecondary
        )
        val colorTertiary = MaterialColors.getColor(
            requireView(),
            com.google.android.material.R.attr.colorTertiary
        )
        val colorSurfaceVariant = MaterialColors.getColor(
            requireView(),
            com.google.android.material.R.attr.colorSurfaceVariant
        )

        val sliceColors = listOf(colorPrimary, colorSecondary, colorTertiary, colorSurfaceVariant)

        val pieDataSet = PieDataSet(entries, "").apply {
            colors = sliceColors
            sliceSpace = 3f
            setDrawValues(false)
            setDrawIcons(false)
            selectionShift = 5f
        }

        val pieData = PieData(pieDataSet)

        binding.pieChart.apply {
            data = pieData
            animateY(800, Easing.EaseInOutQuart)
            invalidate()
        }

        // Build legend: colored dot + app name
        val pm = requireContext().packageManager
        val density = resources.displayMetrics.density

        topApps.forEachIndexed { index, stat ->
            val itemLayout = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
            }

            // Colored dot
            val dot = android.view.View(requireContext()).apply {
                val size = (7 * density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(sliceColors.getOrElse(index) { colorSurfaceVariant })
                }
                background = drawable
            }
            itemLayout.addView(dot)

            // App name only (no icon — keeps legend compact)
            try {
                val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                val nameView = android.widget.TextView(requireContext()).apply {
                    text = appInfo.loadLabel(pm)
                    setTextColor(MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurfaceVariant))
                    textSize = 11f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = (5 * density).toInt()
                    }
                }
                itemLayout.addView(nameView)
            } catch (_: Exception) { }

            binding.pieLegend.addView(itemLayout)
        }
    }

    private fun generateAndExportCsv(startMs: Long, endMs: Long, mode: Int) {
        Toast.makeText(requireContext(), "Generating Analysis CSV...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val sb = StringBuilder()
            val usageStatsHelper = UsageStatsHelper(requireContext())
            val pm = requireContext().packageManager
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val header =
                "Date,App Name,Package Name,Category,Duration (ms),Duration (mins),Is System App,Install Date,Last Update,Start Times\n"

            fun processStatsForRange(start: Long, end: Long, dateLabel: String): String {
                val rangeSb = StringBuilder()
                val statsMap = usageStatsHelper.getForegroundStatsByTimestamps(start, end)

                statsMap.forEach { it ->
                    if (it.totalTime > 0) {
                        var appName = it.packageName
                        var category = "Undefined"
                        var isSystem = "No"
                        var installDate = "N/A"
                        var lastUpdate = "N/A"

                        try {
                            val appInfo = pm.getApplicationInfo(it.packageName, 0)
                            val pkgInfo = pm.getPackageInfo(it.packageName, 0)

                            appName =
                                appInfo.loadLabel(pm).toString().replace(",", " ")
                            isSystem =
                                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) "Yes" else "No"
                            installDate =
                                dateFormat.format(Date(pkgInfo.firstInstallTime))
                            lastUpdate =
                                dateFormat.format(Date(pkgInfo.lastUpdateTime))

                            category = when (appInfo.category) {
                                ApplicationInfo.CATEGORY_GAME -> "Game"
                                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                                ApplicationInfo.CATEGORY_VIDEO -> "Video"
                                ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                                else -> "Other"
                            }
                        } catch (e: Exception) { /* App likely uninstalled */
                        }

                        val minutes = it.totalTime / 1000 / 60

                        rangeSb.append("$dateLabel,$appName,${it.packageName},$category,${it.totalTime},$minutes,$isSystem,$installDate,$lastUpdate\n,${it.startTimes}\n")
                    }
                }
                return rangeSb.toString()
            }

            if (mode == 0 || mode == 2) {
                sb.append("--- DAILY BREAKDOWN ---\n")
                sb.append(header)

                val startInstant =
                    Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
                val endInstant =
                    Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault()).toLocalDate()

                var current = startInstant
                while (!current.isAfter(endInstant)) {
                    val dayStart =
                        current.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val dayEnd = current.atTime(23, 59, 59).atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()

                    sb.append(processStatsForRange(dayStart, dayEnd, current.toString()))
                    current = current.plusDays(1)
                }
                sb.append("\n")
            }

            if (mode == 1 || mode == 2) {
                sb.append(
                    "--- TOTAL SUMMARY (${dateFormat.format(Date(startMs))} to ${
                        dateFormat.format(
                            Date(endMs)
                        )
                    }) ---\n"
                )
                sb.append(header)
                sb.append(processStatsForRange(startMs, endMs, "TOTAL RANGE"))
            }

            csvDataToExport = sb.toString()

            withContext(Dispatchers.Main) {
                val name = "UsageData_${dateFormat.format(Date(startMs))}.csv"
                createCsvLauncher.launch(name)
            }
        }
    }

    private fun resizeIcon(icon: Drawable, width: Int, height: Int): Drawable {
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

        val density = Resources.getSystem().displayMetrics.density
        val targetWidth = (width * density).toInt()
        val targetHeight = (height * density).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            targetWidth,
            targetHeight,
            true
        )

        return BitmapDrawable(Resources.getSystem(), scaledBitmap)
    }

    inner class AppUsageViewHolder(private val binding: AppUsageItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(stats: Stat, packageManager: PackageManager) {
            val appInfo = try {
                packageManager.getApplicationInfo(stats.packageName, 0)
            } catch (e: Exception) {
                binding.appIcon.setImageResource(R.drawable.baseline_warning_24)
                binding.appName.text = stats.packageName
                binding.appUsage.text = TimeTools.formatTimeForWidget(stats.totalTime)
                binding.appCategory.text = "APP"
                return
            }
            binding.root.setOnClickListener {
                activity?.supportFragmentManager?.beginTransaction()
                    ?.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    ?.replace(R.id.fragment_holder, AppUsageBreakdown(stats))
                    ?.addToBackStack(null)
                    ?.commit()
            }
            binding.root.setOnLongClickListener {

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Add to ignored packages?")
                    .setMessage("This action will cause the tracker to not display any stats from this app.")
                    .setCancelable(true)
                    .setPositiveButton("Okay") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dataStore = nethical.digipaws.utils.DataStoreManager(requireContext())
                            val ignoredAppsSP = dataStore.settings.first().usageTrackerIgnoredApps.toMutableList()
                            if (!ignoredAppsSP.contains(stats.packageName)) {
                                ignoredAppsSP.add(stats.packageName)
                                dataStore.updateUsageTrackerIgnoredApps(ignoredAppsSP)
                                withContext(Dispatchers.Main) {
                                    viewModel.ignoredPackages.addAll(ignoredAppsSP)
                                    viewModel.reload()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }

            binding.appIcon.setImageDrawable(appInfo.loadIcon(packageManager))
            binding.appName.text = appInfo.loadLabel(packageManager)
            binding.appUsage.text = TimeTools.formatTimeForWidget(stats.totalTime)
            binding.appCategory.text = viewModel.getAppCategory(stats.packageName)
        }
    }

    inner class AppUsageAdapter(
        private var appUsageStats: List<Stat>
    ) : RecyclerView.Adapter<AppUsageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
            val binding =
                AppUsageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    class Stat(
        val packageName: String,
        val totalTime: Long,
        val startTimes: List<ZonedDateTime>
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}