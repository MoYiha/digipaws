package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.keywordBlocker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AppBlockingType
import neth.iecal.curbox.data.models.KeywordGroup
import neth.iecal.curbox.databinding.FragmentKeywordBlockerBinding
import neth.iecal.curbox.ui.activity.FragmentActivity

class KeywordBlockerFragment : Fragment() {

    private var _binding: FragmentKeywordBlockerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: KeywordBlockerViewModel by activityViewModels()
    private var isUpdatingUi = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeywordBlockerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!viewModel.keywordBlockerConfig.value.isActive) {
            viewModel.setIsActive(true)
        }
        binding.rvKeywordGroups.layoutManager = LinearLayoutManager(requireContext())
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnMenu.setOnClickListener { view ->
            showPopupMenu(view)
        }

        binding.fabAddGroup.setOnClickListener {
            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                putExtra("fragment", CreateKeywordGroupFragment.FRAGMENT_ID)
            }
            startActivity(intent)
        }
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.menu_keyword_blocker, popup.menu)

        val config = viewModel.keywordBlockerConfig.value
        popup.menu.findItem(R.id.menu_block_unsupported_browsers).isChecked = config.blockAllExceptSupported

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_block_unsupported_browsers -> {
                    val newValue = !item.isChecked
                    item.isChecked = newValue
                    viewModel.setBlockAllExceptSupported(newValue)
                    true
                }
                R.id.menu_help -> {
                    Toast.makeText(requireContext(), "Help coming soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.keywordBlockerConfig.collectLatest { config ->
                isUpdatingUi = true

                if (config.keywordGroups.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvKeywordGroups.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvKeywordGroups.visibility = View.VISIBLE
                    binding.rvKeywordGroups.adapter = KeywordGroupAdapter(config.keywordGroups)
                }
                isUpdatingUi = false
            }
        }
    }

    inner class KeywordGroupAdapter(private val groupList: List<KeywordGroup>) :
        RecyclerView.Adapter<KeywordGroupAdapter.ViewHolder>() {

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
            val typeText = if (group.blockingType == AppBlockingType.Usage) "Usage Based" else "Time Based"
            holder.tvDetails.text = "${group.selectedKeywords.size} Keywords • $typeText"
            
            holder.switchActive.setOnCheckedChangeListener(null)
            holder.switchActive.isChecked = group.isActive
            holder.switchActive.setOnCheckedChangeListener { _, isChecked ->
                viewModel.updateGroupActiveState(group.id, isChecked)
            }
            
            holder.itemView.setOnClickListener {
                val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                    putExtra("fragment", CreateKeywordGroupFragment.FRAGMENT_ID)
                    putExtra("group_id", group.id)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount() = groupList.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "keyword_blocker"
    }
}
