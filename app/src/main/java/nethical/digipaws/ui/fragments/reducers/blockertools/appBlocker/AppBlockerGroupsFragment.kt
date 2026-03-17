package nethical.digipaws.ui.fragments.reducers.blockertools.appBlocker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nethical.digipaws.R
import nethical.digipaws.data.models.AppBlockingType
import nethical.digipaws.data.models.AppGroup
import nethical.digipaws.ui.activity.FragmentActivity
import nethical.digipaws.utils.DataStoreManager
import nethical.digipaws.utils.SavedPreferencesLoader

class AppBlockerGroupsFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "app_blocker_groups"
    }

    private lateinit var rvGroups: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var fabAddGroup: FloatingActionButton
    private lateinit var toolbar: MaterialToolbar

    private var groups: MutableList<AppGroup> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_app_blocker_groups, container, false)
        
        rvGroups = view.findViewById(R.id.rv_app_groups)
        tvEmptyState = view.findViewById(R.id.tv_empty_state)
        fabAddGroup = view.findViewById(R.id.fab_add_group)
        toolbar = view.findViewById(R.id.toolbar)


        toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }

        fabAddGroup.setOnClickListener {
            // Navigate to Create App Group
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", CreateAppGroupFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }

        rvGroups.layoutManager = LinearLayoutManager(requireContext())
        
        return view
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch {
            loadGroups()
        }
    }

    private suspend fun loadGroups() {
        val dataStoreManager = DataStoreManager(requireContext())
        dataStoreManager.settings.collect { settings ->
            withContext(Dispatchers.Main) {

                if (groups.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    rvGroups.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    rvGroups.visibility = View.VISIBLE
                    rvGroups.adapter = AppGroupAdapter(groups)
                }
            }
        }
    }


    inner class AppGroupAdapter(private val groupList: List<AppGroup>) :
        RecyclerView.Adapter<AppGroupAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_group_name)
            val tvDetails: TextView = view.findViewById(R.id.tv_group_details)
            val switchActive: SwitchMaterial = view.findViewById(R.id.switch_group_active)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_group, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val group = groupList[position]
            holder.tvName.text = group.name
            
            val typeText = if (group.blockingType == AppBlockingType.Timed) "Time Based" else "Usage Based"
            holder.tvDetails.text = "${group.selectedPackages.size} Apps • $typeText"
            
            holder.switchActive.setOnCheckedChangeListener(null)
            holder.switchActive.isChecked = group.isActive
            
            holder.switchActive.setOnCheckedChangeListener { _, isChecked ->
                val updatedGroup = group.copy(isActive = isChecked)
                groups[position] = updatedGroup
                val dataStoreManager = DataStoreManager(requireContext())
                CoroutineScope(Dispatchers.IO).launch {
                    dataStoreManager.updateGroups(groups)
                }
            }
            
            holder.itemView.setOnClickListener {
                // Future edit implementation
            }
        }

        override fun getItemCount() = groupList.size
    }
}
