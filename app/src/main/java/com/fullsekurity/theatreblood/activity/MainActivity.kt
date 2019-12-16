package com.fullsekurity.theatreblood.activity

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.FragmentManager
import com.fullsekurity.theatreblood.R
import com.fullsekurity.theatreblood.donor.DonorFragment
import com.fullsekurity.theatreblood.donors.DonorsFragment
import com.fullsekurity.theatreblood.input.InputFragment
import com.fullsekurity.theatreblood.repository.Repository
import com.fullsekurity.theatreblood.repository.storage.Donor
import com.fullsekurity.theatreblood.ui.UIViewModel
import com.fullsekurity.theatreblood.utils.Constants
import com.fullsekurity.theatreblood.utils.Constants.DONORS_FRAGMENT_TAG
import com.fullsekurity.theatreblood.utils.Constants.DONOR_FRAGMENT_TAG
import com.fullsekurity.theatreblood.utils.Constants.INPUT_FRAGMENT_TAG
import com.fullsekurity.theatreblood.utils.Constants.ROOT_FRAGMENT_TAG
import com.fullsekurity.theatreblood.utils.DaggerViewModelDependencyInjector
import com.fullsekurity.theatreblood.utils.ViewModelInjectorModule
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    private val tag = MainActivity::class.java.simpleName
    private var rootFragmentCount: Int = 0
    private lateinit var donorsFragment: DonorsFragment

    var repository: Repository = Repository()
    @Inject
    lateinit var uiViewModel: UIViewModel

    enum class UITheme {
        LIGHT, DARK, NOT_ASSIGNED,
    }

    var currentTheme: UITheme = UITheme.LIGHT

    override fun onResume() {
        super.onResume()
        val settings = getSharedPreferences("THEME", Context.MODE_PRIVATE)
        val name: String? = settings.getString("THEME", UITheme.LIGHT.name)
        if (name != null) {
            currentTheme = UITheme.valueOf(name)
        }
        setupRepositoryDatabase()
        setupToolbar()
    }

    override fun onStop() {
        super.onStop()
        repository.closeDatabase()
        repository.onCleared()
    }

    private val onNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DaggerViewModelDependencyInjector.builder()
            .viewModelInjectorModule(ViewModelInjectorModule(this))
            .build()
            .inject(this)
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        navView.setOnNavigationItemSelectedListener(onNavigationItemSelectedListener)
        if (savedInstanceState == null) {
            loadInitialFragments()
        }
    }

    private fun setupRepositoryDatabase() {
        repository.saveDatabase(this)
        repository.deleteDatabase(this)
        repository.setBloodDatabase(this)
        val progressBar = main_progress_bar
        progressBar.visibility = View.VISIBLE
        repository.initializeDatabase(progressBar, this)
    }

    fun finishActivity() {
        finish()
    }

    private fun loadInitialFragments() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left)
            .replace(R.id.home_container, InputFragment.newInstance(), INPUT_FRAGMENT_TAG)
            .addToBackStack(ROOT_FRAGMENT_TAG)
            .commitAllowingStateLoss()
        donorsFragment = DonorsFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right)
            .replace(R.id.donors_container, donorsFragment, DONORS_FRAGMENT_TAG)
            .addToBackStack(null)
            .commitAllowingStateLoss()
        rootFragmentCount = 2
    }

    private inline fun <T: Any> multipleObjectLet(vararg elements: T?, closure: (List<T>) -> Unit) {
        if (elements.all { it != null }) {
            closure(elements.filterNotNull())
        }
    }

    fun transitionToSingleDonorFragment(donor: Donor) {
        val inputFragment = supportFragmentManager.findFragmentByTag(INPUT_FRAGMENT_TAG)
        val donorsFragment = supportFragmentManager.findFragmentByTag(DONORS_FRAGMENT_TAG)
        multipleObjectLet(inputFragment, donorsFragment) {
            // will not execute if either inputFragment or donorsFragment is null
            (inputFragment, donorsFragment) -> supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left)
            .remove(inputFragment)
            .remove(donorsFragment)
            .replace(R.id.home_container, DonorFragment.newInstance(donor), DONOR_FRAGMENT_TAG)
            .addToBackStack(null)
            .commitAllowingStateLoss()
        }
    }

    private fun setupToolbar() {
        supportActionBar?.let { actionBar ->
            actionBar.setBackgroundDrawable(ColorDrawable(Color.parseColor(uiViewModel.primaryColor)))
            colorizeToolbarOverflowButton(toolbar, Color.parseColor(uiViewModel.toolbarTextColor))
            val upArrow = ContextCompat.getDrawable(this, R.drawable.toolbar_back_arrow)
            actionBar.setHomeAsUpIndicator(upArrow);
            toolbar.setTitleTextColor(Color.parseColor(uiViewModel.toolbarTextColor))
            toolbar.title = Constants.DONATE_PRODUCTS_TITLE
        }
    }

    private fun colorizeToolbarOverflowButton(toolbar: Toolbar, color: Int): Boolean {
        val overflowIcon = toolbar.overflowIcon ?: return false
        toolbar.overflowIcon = getTintedDrawable(overflowIcon, color)
        return true
    }

    private fun getTintedDrawable(inputDrawable: Drawable, color: Int): Drawable {
        val wrapDrawable = DrawableCompat.wrap(inputDrawable)
        DrawableCompat.setTint(wrapDrawable, color)
        DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_IN)
        return wrapDrawable
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            true
        }
        R.id.action_favorite -> {
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    fun showDonors(donorList: List<Donor>) {
        donorsFragment.showDonors(donorList)
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == rootFragmentCount) {
            supportFragmentManager.popBackStack(ROOT_FRAGMENT_TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        super.onBackPressed()
    }

}