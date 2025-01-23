package nethical.digipaws.ui.activity

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nethical.digipaws.R
import nethical.digipaws.data.blockers.PackageWand
import nethical.digipaws.databinding.ActivitySelectAppsBinding

class SelectAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectAppsBinding
    private lateinit var selectedAppList: HashSet<String>


    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedAppList = intent.getStringArrayListExtra("PRE_SELECTED_APPS")?.toHashSet() ?: HashSet() // get all selected apps


        binding.appList.layoutManager = LinearLayoutManager(this)
        val appItemList: MutableList<AppItem> = mutableListOf()

        binding.selectAppsMagic.setOnClickListener {
            val popupMenu = PopupMenu(this, binding.selectAppsMagic)

            // Inflate the menu resource
            popupMenu.menuInflater.inflate(R.menu.app_wand, popupMenu.menu)

            // Set click listener for menu items
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.select_social_media -> {
                        appItemList.forEach { item ->
                            if (PackageWand.SOCIAL_MEDIA_APPS.contains(item.appInfo.packageName)) {
                                selectedAppList.add(item.appInfo.packageName)
                            }
                        }
                        val slist = sortSelectedItemsToTop(appItemList)
                        (binding.appList.adapter as ApplicationAdapter).updateData(slist)
                        true
                    }

                    R.id.select_productive_apps -> {
                        appItemList.forEach { item ->
                            if (PackageWand.PRODUCTIVE_APPS.contains(item.appInfo.packageName)) {
                                selectedAppList.add(item.appInfo.packageName)
                            }
                        }
                        val slist = sortSelectedItemsToTop(appItemList)
                        (binding.appList.adapter as ApplicationAdapter).updateData(slist)
                        true
                    }

                    else -> false
                }
            }

            // Show the popup menu
            popupMenu.show()
        }
        if (intent.hasExtra("APP_LIST")) { // load only few specific apps instead of everything installed
            val appList = intent.getStringArrayListExtra("APP_LIST")

            appList?.forEach { packageName ->
                appItemList.add(
                    AppItem(
                        packageName,
                        packageManager.getApplicationInfo(packageName, 0)
                    )
                )
            }
        } else { // load all installed apps
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val apps = launcherApps.getActivityList(null, Process.myUserHandle())
                .map { it.applicationInfo }
                .filter { it.packageName != packageName }

            apps.forEach { appInfo ->
                appItemList.add(AppItem(appInfo.packageName, appInfo))
            }
        }

        appItemList.sortBy {
            it.appInfo.loadLabel(packageManager).toString().lowercase()
        }



        binding.confirmSelection.setOnClickListener {
            val selectedAppsArrayList = ArrayList(selectedAppList)
            val resultIntent = intent.apply {
                putStringArrayListExtra("SELECTED_APPS", selectedAppsArrayList)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        val sortedAppItemList = sortSelectedItemsToTop(appItemList)

        binding.appList.layoutManager = LinearLayoutManager(this)
        val filteredList = sortedAppItemList.toMutableList()
        binding.appList.adapter = ApplicationAdapter(filteredList, selectedAppList)

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.trim() ?: ""
                filteredList.clear()
                filteredList.addAll(
                    appItemList.filter {
                        it.appInfo.loadLabel(packageManager).toString()
                            .contains(query, ignoreCase = true)
                    }
                )
                binding.appList.adapter?.notifyDataSetChanged()
                return true
            }
        })

    }

    fun sortSelectedItemsToTop(appItemList: List<AppItem>): List<AppItem> {
        val sortedAppItemList = appItemList.sortedWith(compareBy<AppItem> {
            // First sort by selection status - false comes before true when using !
            !selectedAppList.contains(it.appInfo.packageName)
        }.thenBy {
            // Then sort alphabetically within each group
            it.appInfo.loadLabel(packageManager).toString().lowercase()
        })
        return sortedAppItemList
    }


    inner class ApplicationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
    }

    inner class ApplicationAdapter(
        private var apps: List<AppItem>,
        private val selectedAppList: HashSet<String>
    ) : RecyclerView.Adapter<ApplicationViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.select_apps_item, parent, false)
            return ApplicationViewHolder(view)
        }

        override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
            val appItem = apps[position]

            holder.appIcon.setImageDrawable(null)
            holder.appName.text = ""

            lifecycleScope.launch(Dispatchers.IO) {
                val packageManager = holder.itemView.context.packageManager
                val icon = appItem.appInfo.loadIcon(packageManager)
                val label = appItem.appInfo.loadLabel(packageManager)

                withContext(Dispatchers.Main) {
                    holder.appIcon.setImageDrawable(icon)
                    holder.appName.text = label
                }
            }

            // Remove the previous OnCheckedChangeListener before setting a new one
            holder.checkbox.setOnCheckedChangeListener(null)

            holder.checkbox.isChecked = selectedAppList.contains(appItem.appInfo.packageName)

            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedAppList.add(appItem.appInfo.packageName)
                } else {
                    selectedAppList.remove(appItem.appInfo.packageName)
                }
            }

            holder.itemView.setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            }
        }

        override fun getItemCount(): Int = apps.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newList: List<AppItem>) {
            apps = newList
            notifyDataSetChanged()
        }
    }

    data class AppItem(
        val packageName: String,
        val appInfo: ApplicationInfo
    )
}
