package nethical.digipaws.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import nethical.digipaws.R
import nethical.digipaws.ui.fragments.anti_uninstall.ChooseModeFragment
import nethical.digipaws.ui.fragments.installation.AccessibilityGuide
import nethical.digipaws.ui.fragments.installation.WelcomeFragment
import nethical.digipaws.ui.fragments.main.focus.FocusFragment
import nethical.digipaws.ui.fragments.main.reducers.ReducersFragment
import nethical.digipaws.ui.fragments.usage.AllAppsUsageFragment
import nethical.digipaws.ui.fragments.main.reducers.blockertools.appBlocker.AppBlockerGroupsFragment
import nethical.digipaws.ui.fragments.main.reducers.blockertools.appBlocker.CreateAppGroupFragment

class FragmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)

        val selectedFragment = intent.getStringExtra("fragment") ?: AllAppsUsageFragment.FRAGMENT_ID
        
        when (selectedFragment) {
            ChooseModeFragment.FRAGMENT_ID,
            WelcomeFragment.FRAGMENT_ID,
            AccessibilityGuide.FRAGMENT_ID,
            AppBlockerGroupsFragment.FRAGMENT_ID,
            CreateAppGroupFragment.FRAGMENT_ID,-> {
                // Hide bottom nav for these standalone fragments
                bottomNav.visibility = android.view.View.GONE
                
                val fragment = when (selectedFragment) {
                    ChooseModeFragment.FRAGMENT_ID -> ChooseModeFragment()
                    WelcomeFragment.FRAGMENT_ID -> WelcomeFragment()
                    AppBlockerGroupsFragment.FRAGMENT_ID -> AppBlockerGroupsFragment()
                    CreateAppGroupFragment.FRAGMENT_ID -> CreateAppGroupFragment()
                    else -> AccessibilityGuide()
                }
                fragment.arguments = intent.extras

                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, fragment)
                    .commit()
            }
            else -> {
                // Show bottom nav for main fragments
                bottomNav.visibility = android.view.View.VISIBLE
                
                if (savedInstanceState == null) {
                    bottomNav.selectedItemId = R.id.nav_usage
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_holder, AllAppsUsageFragment())
                        .commit()
                }
                
                bottomNav.setOnItemSelectedListener { item ->
                    val fragment = when (item.itemId) {
                        R.id.nav_usage -> AllAppsUsageFragment()
                        R.id.nav_focus -> FocusFragment()
                        R.id.nav_reducers -> ReducersFragment()
                        R.id.nav_info -> nethical.digipaws.ui.fragments.main.InfoFragment()
                        else -> AllAppsUsageFragment()
                    }
                    
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_holder, fragment)
                        .commit()
                    
                    true
                }
            }
        }
    }
}