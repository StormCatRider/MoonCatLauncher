package com.github.codeworkscreativehub.mlauncher.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.SuperscriptSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.ColorIconsExtensions
import com.github.codeworkscreativehub.common.ColorManager
import com.github.codeworkscreativehub.common.attachGestureManager
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.isGestureNavigationEnabled
import com.github.codeworkscreativehub.common.launchCalendar
import com.github.codeworkscreativehub.common.openAlarmApp
import com.github.codeworkscreativehub.common.openBatteryManager
import com.github.codeworkscreativehub.common.openCameraApp
import com.github.codeworkscreativehub.common.openDeviceSettings
import com.github.codeworkscreativehub.common.openDialerApp
import com.github.codeworkscreativehub.common.openDigitalWellbeing
import com.github.codeworkscreativehub.common.openPhotosApp
import com.github.codeworkscreativehub.common.openTextMessagesApp
import com.github.codeworkscreativehub.common.openWebBrowser
import com.github.codeworkscreativehub.common.showShortToast
import com.github.codeworkscreativehub.mlauncher.MainViewModel
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.mooncat.productivity.ProductivityDialogs
import com.github.codeworkscreativehub.mlauncher.mooncat.pages.HomePages
import com.github.codeworkscreativehub.mlauncher.mooncat.pages.PageStore
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Constants.Action
import com.github.codeworkscreativehub.mlauncher.data.Constants.AppDrawerFlag
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.databinding.FragmentHomeBinding
import com.github.codeworkscreativehub.mlauncher.helper.FontManager
import com.github.codeworkscreativehub.mlauncher.helper.IconCacheTarget
import com.github.codeworkscreativehub.mlauncher.helper.IconPackHelper.getSafeAppIcon
import com.github.codeworkscreativehub.mlauncher.helper.ShortcutHelper
import com.github.codeworkscreativehub.mlauncher.helper.analytics.AppUsageMonitor
import com.github.codeworkscreativehub.mlauncher.helper.formatMillisToHMS
import com.github.codeworkscreativehub.mlauncher.helper.getHexForOpacity
import com.github.codeworkscreativehub.mlauncher.helper.getNextAlarm
import com.github.codeworkscreativehub.mlauncher.helper.getSystemIcons
import com.github.codeworkscreativehub.mlauncher.helper.hasUsageAccessPermission
import com.github.codeworkscreativehub.mlauncher.helper.initActionService
import com.github.codeworkscreativehub.mlauncher.helper.ismlauncherDefault
import com.github.codeworkscreativehub.mlauncher.helper.receivers.BatteryReceiver
import com.github.codeworkscreativehub.mlauncher.helper.receivers.DeviceAdmin
import com.github.codeworkscreativehub.mlauncher.helper.receivers.PrivateSpaceReceiver
import com.github.codeworkscreativehub.mlauncher.helper.setTopPadding
import com.github.codeworkscreativehub.mlauncher.helper.showPermissionDialog
import com.github.codeworkscreativehub.mlauncher.helper.utils.AppReloader
import com.github.codeworkscreativehub.mlauncher.helper.utils.BiometricHelper
import com.github.codeworkscreativehub.mlauncher.helper.utils.PrivateSpaceManager
import com.github.codeworkscreativehub.mlauncher.helper.wordOfTheDay
import com.github.codeworkscreativehub.mlauncher.listener.GestureAdapter
import com.github.codeworkscreativehub.mlauncher.listener.NotificationDotManager
import com.github.codeworkscreativehub.mlauncher.mooncat.model.DisplayMode
import com.github.codeworkscreativehub.mlauncher.services.ActionService
import com.github.codeworkscreativehub.mlauncher.ui.components.DialogManager
import com.github.codeworkscreativehub.mlauncher.ui.widgets.WidgetActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var homeFolders: HomeFolders
    private lateinit var viewModel: MainViewModel
    private lateinit var dialogBuilder: DialogManager
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var privateSpaceReceiver: PrivateSpaceReceiver
    private lateinit var vibrator: Vibrator

    private var longPressToSelectApp: Int = 0
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private fun getDayOfYearText(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val max = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        return "[$day/$max]"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs(requireContext())
        batteryReceiver = BatteryReceiver(binding.battery, prefs)
        dialogBuilder = DialogManager(requireContext(), requireActivity())
        if (PrivateSpaceManager(requireContext()).isPrivateSpaceSupported()) {
            privateSpaceReceiver = PrivateSpaceReceiver()
        }

        longPressToSelectApp = if (prefs.homeLocked) {
            R.string.long_press_to_select_app_locked
        } else {
            R.string.long_press_to_select_app
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        biometricHelper = BiometricHelper(this.requireActivity())

        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        homeFolders = HomeFolders(this, viewModel, prefs) {
            viewModel.homeAppsChanged.value = System.currentTimeMillis()
        }
        homeFolders.load()
        viewModel.ismlauncherDefault()

        deviceManager =
            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        @Suppress("DEPRECATION")
        vibrator = context?.getSystemService(VIBRATOR_SERVICE) as Vibrator

        FontManager.reloadFont(requireContext())

        initAppObservers()
        initClickListeners()
        initSwipeTouchListener()
        initPermissionCheck()
        initObservers()

        // Update view appearance/settings based on prefs
        updateUIFromPreferences()
    }

    override fun onStart() {
        super.onStart()

        // Handle status bar once per view creation
        setTopPadding(binding.mainLayout)


        // Update dynamic UI elements
        updateTimeAndInfo()

        // Register battery receiver
        context?.let { ctx ->
            binding.battery.let { textView ->
                batteryReceiver = BatteryReceiver(textView, prefs)
                val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                // Register receiver
                val stickyIntent = ctx.registerReceiver(batteryReceiver, intentFilter)
                // Immediately update UI with sticky intent
                stickyIntent?.let { batteryReceiver.onReceive(ctx, it) }
            }

            // Register private space receiver if supported
            if (PrivateSpaceManager(ctx).isPrivateSpaceSupported()) {
                privateSpaceReceiver = PrivateSpaceReceiver()
                ctx.registerReceiver(privateSpaceReceiver, IntentFilter(Intent.ACTION_PROFILE_AVAILABLE))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        homeFolders.hideProtected()

        context?.let { ctx ->
            try {
                batteryReceiver.let { ctx.unregisterReceiver(it) }
                if (PrivateSpaceManager(requireContext()).isPrivateSpaceSupported()) {
                    privateSpaceReceiver.let { ctx.unregisterReceiver(it) }
                }
            } catch (e: IllegalArgumentException) {
                // Receiver not registered — safe to ignore
                e.printStackTrace()
            }
        }

        dismissDialogs()
    }


    private fun updateUIFromPreferences() {
        val locale = prefs.appLanguage.locale()
        val is24HourFormat = DateFormat.is24HourFormat(requireContext())

        binding.apply {
            val best12Raw = DateFormat.getBestDateTimePattern(locale, "hm") // 12-hour with AM/PM
            val best12 = if (prefs.showClockFormat) {
                best12Raw // keep AM/PM
            } else {
                best12Raw.replace("a", "").trim() // strip AM/PM
            }

            val best24 = DateFormat.getBestDateTimePattern(locale, "Hm") // 24-hour

            val timePattern = if (is24HourFormat) best24 else best12

            clock.format12Hour = timePattern
            clock.format24Hour = timePattern

            // Date format
            val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEddMMM")
            date.format12Hour = datePattern
            date.format24Hour = datePattern

            // Static UI setup
            date.textSize = prefs.dateSize.toFloat()
            clock.textSize = prefs.clockSize.toFloat()
            alarm.textSize = prefs.alarmSize.toFloat()
            dailyWord.textSize = prefs.dailyWordSize.toFloat()
            battery.textSize = prefs.batterySize.toFloat()
            homeScreenPager.textSize = prefs.appSize.toFloat()

            battery.isVisible = prefs.showBattery
            mainLayout.setBackgroundColor(getHexForOpacity(prefs))

            date.setTextColor(prefs.dateColor)
            clock.setTextColor(prefs.clockColor)
            alarm.setTextColor(prefs.alarmClockColor)
            dailyWord.setTextColor(prefs.dailyWordColor)
            battery.setTextColor(prefs.batteryColor)
            totalScreenTime.setTextColor(prefs.appColor)
            setDefaultLauncher.setTextColor(prefs.appColor)

            val fabList = listOf(fabPhone, fabMessages, fabCamera, fabPhotos, fabBrowser, fabSettings, fabAction)
            val fabFlags = prefs.getMenuFlags("HOME_BUTTON_FLAGS", "0000011") // Might return list of wrong size
            val colors = ColorManager.getRandomHueColors(prefs.shortcutIconsColor, fabList.size)

            for (i in fabList.indices) {
                val fab = fabList[i]

                val isVisible = if (i < fabFlags.size) fabFlags[i] else false
                val color = colors[i]

                fab.isVisible = isVisible

                // Skip recoloring for fabAction
                if (fab != fabAction) {
                    fab.setColorFilter(
                        if (prefs.iconRainbowColors) color else prefs.shortcutIconsColor
                    )
                }
            }
        }
    }

    private fun updateTimeAndInfo() {
        val locale = prefs.appLanguage.locale()
        val is24HourFormat = DateFormat.is24HourFormat(requireContext())

        binding.apply {

            val best12Raw = DateFormat.getBestDateTimePattern(locale, "hm") // 12-hour with AM/PM
            val best12 = if (prefs.showClockFormat) {
                best12Raw // keep AM/PM
            } else {
                best12Raw.replace("a", "").trim() // strip AM/PM
            }

            val best24 = DateFormat.getBestDateTimePattern(locale, "Hm") // 24-hour

            val timePattern = if (is24HourFormat) best24 else best12

            clock.format12Hour = timePattern
            clock.format24Hour = timePattern

            // Date format
            // val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEddMMM")
            // date.format12Hour = datePattern
            // date.format24Hour = datePattern

            val basePattern = DateFormat.getBestDateTimePattern(locale, "EEEddMMM")

            val finalPattern = if (prefs.showDayOfYear) {
                "$basePattern   ${getDayOfYearText()}"
            } else {
                basePattern
            }

            date.format12Hour = finalPattern
            date.format24Hour = finalPattern


            alarm.text = getNextAlarm(requireContext(), prefs, viewModel.showAlarmDate.value ?: true)
            dailyWord.text = wordOfTheDay(prefs)
            if (dailyWord.text.isBlank()) dailyWord.isVisible = false
        }
    }


    override fun onClick(view: View) {
        when (view.id) {
            R.id.clock -> {
                when (val action = prefs.clickClockAction) {
                    Action.OpenApp -> openClickClockApp()
                    else -> handleOtherAction(action)
                }
            }

            R.id.date -> {
                when (val action = prefs.clickDateAction) {
                    Action.OpenApp -> openClickDateApp()
                    else -> handleOtherAction(action)
                }
            }

            R.id.totalScreenTime -> {
                when (val action = prefs.clickAppUsageAction) {
                    Action.OpenApp -> openClickUsageApp()
                    else -> handleOtherAction(action)
                }
            }

            R.id.setDefaultLauncher -> {
                viewModel.resetDefaultLauncherApp(requireContext())
            }

            R.id.battery -> {
                context?.openBatteryManager()
            }


            R.id.fabPhone -> {
                context?.openDialerApp()
            }

            R.id.fabMessages -> {
                context?.openTextMessagesApp()
            }

            R.id.fabCamera -> {
                context?.openCameraApp()
            }

            R.id.fabPhotos -> {
                context?.openPhotosApp()
            }

            R.id.fabBrowser -> {
                context?.openWebBrowser()
            }

            R.id.fabSettings -> {
                trySettings()
            }

            R.id.fabAction -> {
                when (val action = prefs.clickFloatingAction) {
                    Action.OpenApp -> openFabActionApp()
                    else -> handleOtherAction(action)
                }
            }

            else -> {
                try { // Launch app
                    val appLocation = view.id
                    val folder = homeFolders.folderAt(appLocation)
                    if (folder == null) homeAppClicked(appLocation) else homeFolders.toggle(folder)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (prefs.homeLocked) return true

        val slot = view.id
        val folder = homeFolders.folderAt(slot)
        if (folder != null) homeFolders.editFolder(folder)
        else homeFolders.chooseSlotAction(slot) {
            showAppList(AppDrawerFlag.SetHomeApp, includeHiddenApps = true, includeRecentApps = false, slot)
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSwipeTouchListener() {
        binding.touchArea.getHomeScreenGestureListener()
    }

    private fun initPermissionCheck() {
        val context = requireContext()
        if (prefs.recentAppsDisplayed || prefs.appUsageStats) {
            // Check if the usage permission is not granted
            if (!hasUsageAccessPermission(context)) {
                // Postpone showing the dialog until the activity is running
                Handler(Looper.getMainLooper()).post {
                    // Instantiate MainActivity and pass it to showPermissionDialog
                    showPermissionDialog(context)
                }
            }
        }
    }

    private fun initClickListeners() {
        binding.apply {
            clock.setOnClickListener(this@HomeFragment)
            date.setOnClickListener(this@HomeFragment)
            totalScreenTime.setOnClickListener(this@HomeFragment)
            setDefaultLauncher.setOnClickListener(this@HomeFragment)
            battery.setOnClickListener(this@HomeFragment)

            fabPhone.setOnClickListener(this@HomeFragment)
            fabMessages.setOnClickListener(this@HomeFragment)
            fabCamera.setOnClickListener(this@HomeFragment)
            fabPhotos.setOnClickListener(this@HomeFragment)
            fabBrowser.setOnClickListener(this@HomeFragment)
            fabAction.setOnClickListener(this@HomeFragment)
            fabSettings.setOnClickListener(this@HomeFragment)
        }
    }


    private fun initAppObservers() {
        binding.apply {
            firstRunTips.isVisible = prefs.firstSettingsOpen

            setDefaultLauncher.isVisible = !ismlauncherDefault(requireContext())

            val changeLauncherText = if (ismlauncherDefault(requireContext())) {
                R.string.advanced_settings_change_default_launcher
            } else {
                R.string.advanced_settings_set_as_default_launcher
            }

            setDefaultLauncher.text = getLocalizedString(changeLauncherText)
        }

        with(viewModel) {
            homeAppsNum.observe(viewLifecycleOwner) {
                if (prefs.appUsageStats) {
                    updateAppCountWithUsageStats(it)
                } else {
                    updateAppCount(it)
                }
            }
            homePressed.observe(viewLifecycleOwner) { press ->
                if (press != null && !press.handled) {
                    press.handled = true
                    goToStartPage()
                }
            }
            homeAppsChanged.observe(viewLifecycleOwner) {
                // Rebuild every slot, since the count did not change
                binding.homeAppsLayout.removeAllViews()
                if (prefs.appUsageStats) {
                    updateAppCountWithUsageStats(prefs.homeAppsNum)
                } else {
                    updateAppCount(prefs.homeAppsNum)
                }
            }
            launcherDefault.observe(viewLifecycleOwner) {
                binding.setDefaultLauncher.isVisible = it
            }
        }
    }

    private fun initObservers() {
        with(viewModel) {
            showDate.observe(viewLifecycleOwner) {
                binding.date.isVisible = it
            }
            showDayOfYear.observe(viewLifecycleOwner) {
                updateTimeAndInfo()
            }
            showClock.observe(viewLifecycleOwner) {
                binding.clock.isVisible = it
            }
            showAlarm.observe(viewLifecycleOwner) {
                binding.alarm.isVisible = it
            }
            showAlarmDate.observe(viewLifecycleOwner) {
                updateTimeAndInfo()
            }
            showDailyWord.observe(viewLifecycleOwner) {
                // Hidden while there is no daily text to show (no verses supplied or imported yet).
                binding.dailyWord.isVisible = it && wordOfTheDay(prefs).isNotBlank()
            }

            clockAlignment.observe(viewLifecycleOwner) { clockGravity ->
                binding.clock.gravity = clockGravity.value()

                // Set layout_gravity to align the TextClock (clock) within the parent (LinearLayout)
                binding.clock.layoutParams =
                    (binding.clock.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = clockGravity.value()
                    }
            }

            dateAlignment.observe(viewLifecycleOwner) { dateGravity ->
                binding.date.gravity = dateGravity.value()

                // Set layout_gravity to align the TextClock (date) within the parent (LinearLayout)
                binding.date.layoutParams =
                    (binding.date.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = dateGravity.value()
                    }
            }

            alarmAlignment.observe(viewLifecycleOwner) { alarmGravity ->
                binding.alarm.gravity = alarmGravity.value()

                // Set layout_gravity to align the TextView (alarm) within the parent (LinearLayout)
                binding.alarm.layoutParams =
                    (binding.alarm.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = alarmGravity.value()
                    }
            }

            dailyWordAlignment.observe(viewLifecycleOwner) { dailyWordGravity ->
                binding.dailyWord.gravity = dailyWordGravity.value()

                // Set layout_gravity to align the TextView (alarm) within the parent (LinearLayout)
                binding.dailyWord.layoutParams =
                    (binding.dailyWord.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = dailyWordGravity.value()
                    }
            }

            homeAppsAlignment.observe(viewLifecycleOwner) { (homeAppsGravity, onBottom) ->
                val horizontalAlignment = if (onBottom) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
                binding.homeAppsLayout.gravity = homeAppsGravity.value() or horizontalAlignment

                binding.homeAppsLayout.children.forEach { view ->
                    when (view) {
                        is LinearLayout -> view.gravity = homeAppsGravity.value()
                        is TextView -> view.gravity = homeAppsGravity.value()
                    }
                }
            }
        }
    }

    private fun homeAppClicked(location: Int) {
        if (prefs.getAppName(location).isEmpty() || homeFolders.isGrouped(location)) showLongPressToast()
        else viewModel.launchApp(prefs.getHomeAppModel(location), this)
    }

    private fun showAppList(flag: AppDrawerFlag, includeHiddenApps: Boolean = false, includeRecentApps: Boolean = true, n: Int = 0) {
        viewModel.getAppList(includeHiddenApps, includeRecentApps)
        try {
            if (findNavController().currentDestination?.id == R.id.mainFragment) {
                findNavController().navigate(
                    R.id.action_mainFragment_to_appListFragment,
                    Bundle().apply {
                        putString("flag", flag.toString())
                        putInt("n", n)
                        putString("profileType", "SYSTEM")
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showNotesManager() {
        try {
            if (findNavController().currentDestination?.id == R.id.mainFragment) {
                findNavController().navigate(R.id.action_mainFragment_to_notesManagerFragment)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("PrivateApi")
    private fun expandNotificationDrawer(context: Context) {
        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(context.getSystemService("statusbar"))
        } catch (exception: Exception) {
            initActionService(requireContext())?.openNotifications()
            exception.printStackTrace()
        }
    }

    @SuppressLint("PrivateApi")
    private fun expandQuickSettings(context: Context) {
        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandSettingsPanel")
                .invoke(context.getSystemService("statusbar"))
        } catch (exception: Exception) {
            initActionService(requireContext())?.openQuickSettings()
            exception.printStackTrace()
        }
    }

    private fun openSwipeUpApp() {
        if (prefs.appShortSwipeUp.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appShortSwipeUp, this)
        else
            requireContext().openDeviceSettings()
    }

    private fun openSwipeDownApp() {
        if (prefs.appShortSwipeDown.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appShortSwipeDown, this)
        else
            requireContext().openDialerApp()
    }

    private fun openSwipeLeftApp() {
        if (prefs.appShortSwipeLeft.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appShortSwipeLeft, this)
        else
            requireContext().openDeviceSettings()
    }

    private fun openSwipeRightApp() {
        if (prefs.appShortSwipeRight.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appShortSwipeRight, this)
        else
            requireContext().openDialerApp()
    }

    private fun openLongSwipeUpApp() {
        if (prefs.appLongSwipeUp.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appLongSwipeUp, this)
        else
            requireContext().openDeviceSettings()
    }

    private fun openLongSwipeDownApp() {
        if (prefs.appLongSwipeDown.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appLongSwipeDown, this)
        else
            requireContext().openDialerApp()
    }

    private fun openLongSwipeLeftApp() {
        if (prefs.appLongSwipeLeft.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appLongSwipeLeft, this)
        else
            requireContext().openDeviceSettings()
    }

    private fun openLongSwipeRightApp() {
        if (prefs.appLongSwipeRight.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appLongSwipeRight, this)
        else
            requireContext().openDialerApp()
    }

    private fun openClickClockApp() {
        if (prefs.appClickClock.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appClickClock, this)
        else
            requireContext().openAlarmApp()
    }

    private fun openClickUsageApp() {
        if (prefs.appClickUsage.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appClickUsage, this)
        else
            requireContext().openDigitalWellbeing()
    }

    private fun openClickDateApp() {
        if (prefs.appClickDate.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appClickDate, this)
        else
            requireContext().launchCalendar()
    }

    private fun openDoubleTapApp() {
        if (prefs.appDoubleTap.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appDoubleTap, this)
        else
            AppReloader.restartApp(requireContext())
    }

    private fun openFabActionApp() {
        if (prefs.appFloating.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appFloating, this)
        else
            findNavController().navigate(R.id.action_mainFragment_to_notesManagerFragment)
    }

    // This function handles all swipe actions that an independent of the actual swipe direction
    @SuppressLint("NewApi")
    private fun handleOtherAction(action: Action) {
        when (action) {
            Action.ShowNotification -> expandNotificationDrawer(requireContext())
            Action.LockScreen -> lockPhone()
            Action.TogglePrivateSpace -> PrivateSpaceManager(requireContext()).togglePrivateSpaceLock(showToast = false, launchSettings = false)
            Action.ShowAppList -> showAppList(AppDrawerFlag.LaunchApp, includeHiddenApps = false)
            Action.ShowNotesManager -> showNotesManager()
            Action.ShowProductivity -> ProductivityDialogs(requireActivity()) { showNotesManager() }.showHub()
            Action.ShowDigitalWellbeing -> requireContext().openDigitalWellbeing()
            Action.OpenQuickSettings -> expandQuickSettings(requireContext())
            Action.ShowRecents -> initActionService(requireContext())?.showRecents()
            Action.OpenPowerDialog -> initActionService(requireContext())?.openPowerDialog()
            Action.TakeScreenShot -> initActionService(requireContext())?.takeScreenShot()
            Action.PreviousPage -> navigateToPreviousPage()
            Action.NextPage -> navigateToNextPage()
            Action.RestartApp -> AppReloader.restartApp(requireContext())
            Action.ShowWidgetPage -> showWidgetPage()
            Action.OpenApp -> {
                // this should be handled in the respective onSwipe[Up,Down,Right,Left] functions
            }

            Action.Disabled -> {
                // Do nothing
            }

        }
    }

    private fun showWidgetPage() {
        val context = requireContext()
        val intent = Intent(context, WidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    private fun lockPhone() {
        val context = requireContext()
        val deviceAdmin = ComponentName(context, DeviceAdmin::class.java)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val actionService = ActionService.instance()

        when {
            // Use Device Admin if active
            dpm.isAdminActive(deviceAdmin) -> {
                dpm.lockNow()
            }
            // Fallback to ActionService if available
            actionService != null -> {
                actionService.lockScreen()
            }
            // Otherwise prompt the user to enable Device Admin
            else -> {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin)
                }
                startActivity(intent)
            }
        }
    }


    private fun showLongPressToast() = showShortToast(getLocalizedString(longPressToSelectApp))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)


    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun updateAppCountWithUsageStats(newAppsNum: Int) {
        val appUsageMonitor = AppUsageMonitor.getInstance(requireContext())
        val oldAppsNum = binding.homeAppsLayout.childCount // current number of apps
        val diff = newAppsNum - oldAppsNum

        if (diff > 0) {
            // Add new apps
            for (i in oldAppsNum until newAppsNum) {
                // Create a horizontal LinearLayout to hold both existingAppView and newAppView
                val parentLinearLayout = LinearLayout(context)
                parentLinearLayout.apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, // Use MATCH_PARENT for full width
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Create existingAppView
                val existingAppView =
                    layoutInflater.inflate(R.layout.home_app_button, null) as TextView
                existingAppView.apply {
                    // Set properties of existingAppView
                    textSize = prefs.appSize.toFloat()
                    id = i
                    val app = prefs.getHomeAppModel(i)
                    val folder = homeFolders.folderAt(i)
                    val grouped = homeFolders.isGrouped(i)
                    text = folder?.label ?: if (grouped) getLocalizedString(R.string.select_app) else prefs.getAppDisplayName(app)
                    if (folder == null && !grouped) applyHomeAppIcon(app, homeFolders.displayMode(app, i))
                    getHomeAppsGestureListener()
                    setOnClickListener(this@HomeFragment)
                    if (!prefs.extendHomeAppsArea) {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val padding: Int = prefs.textPaddingSize
                    setPadding(0, padding, 0, padding)
                    setTextColor(prefs.appColor)
                }

                // Create newAppView
                val newAppView = TextView(context)
                newAppView.apply {
                    // Set properties of newAppView
                    textSize = prefs.appSize.toFloat() / 1.5f
                    id = i
                    val homeApp = prefs.getHomeAppModel(i)
                    // A shortcut's package is its creator (e.g. the browser), whose usage would be misleading
                    text = if (homeApp.isShortcut || homeFolders.folderAt(i) != null || homeFolders.isGrouped(i)) "" else formatMillisToHMS(
                        appUsageMonitor.getUsageStats(
                            context,
                            homeApp.activityPackage
                        ), false
                    )
                    getHomeAppsGestureListener()
                    setOnClickListener(this@HomeFragment)
                    if (!prefs.extendHomeAppsArea) {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val padding: Int = prefs.textPaddingSize
                    setPadding(0, padding, 0, padding)
                    setTextColor(prefs.appColor)
                }

                // Add a space between existingAppView and newAppView
                val space = Space(context)
                space.layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f // Weight to fill available space
                )

                // Add existingAppView to parentLinearLayout
                parentLinearLayout.addView(existingAppView)
                // Add space and newAppView to parentLinearLayout
                parentLinearLayout.addView(space)
                parentLinearLayout.addView(newAppView)

                // Add parentLinearLayout to homeAppsLayout
                binding.homeAppsLayout.addView(homeFolders.visibleSlot(i, parentLinearLayout))
            }
        } else if (diff < 0) {
            // Remove extra apps
            binding.homeAppsLayout.removeViews(oldAppsNum + diff, -diff)
        }

        // Create a new TextView instance
        val totalText = getLocalizedString(R.string.total_screen_time)
        val totalTime = appUsageMonitor.getTotalScreenTime(requireContext())
        val totalScreenTime = formatMillisToHMS(totalTime, true)
        AppLogger.d("totalScreenTime", totalScreenTime)
        val totalScreenTimeJoin = "$totalText: $totalScreenTime"
        // Set properties for the TextView (optional)
        binding.totalScreenTime.apply {
            text = totalScreenTimeJoin
            if (totalTime > 300L) { // Checking if totalTime is greater than 5 minutes (300,000 milliseconds)
                isVisible = true
            }
        }

        // Update the total number of pages and calculate maximum apps per page
        updatePagesAndAppsPerPage(prefs.homeAppsNum, prefs.homePagesNum)
        adjustTextViewMargins()
    }

    private fun adjustTextViewMargins() {
        binding.apply {

            privateLayout.apply {
                // Set visibility based on both private space setup and hide logo preference
                isVisible = PrivateSpaceManager(requireContext()).isPrivateSpaceSetUp() && prefs.showPrivateSpaces

                // Initial icon
                fun updatePrivateFabIcon() {
                    val isLocked = PrivateSpaceManager(requireContext()).isPrivateSpaceLocked()
                    val iconRes = if (isLocked) R.drawable.private_profile_on
                    else R.drawable.private_profile_off
                    privateFab.setImageResource(iconRes)
                }

                updatePrivateFabIcon() // set initial icon

                privateFab.setOnClickListener {
                    // Toggle lock
                    PrivateSpaceManager(requireContext()).togglePrivateSpaceLock(
                        showToast = false,
                        launchSettings = false
                    )
                    // Update icon after toggle
                    updatePrivateFabIcon()
                }
            }

            val views = listOf(
                setDefaultLauncher,
                totalScreenTime,
                homeScreenPager,
                fabLayout,
                homeAppsLayout,
                privateLayout
            )

            // Check if device is using gesture navigation or 3-button navigation
            val isGestureNav = isGestureNavigationEnabled(requireContext())

            val numOfElements = 6
            val incrementBy = 35
            // Set margins based on navigation mode
            val margins = if (isGestureNav) {
                val startAt = resources.getDimensionPixelSize(R.dimen.bottom_margin_gesture_nav)
                List(numOfElements) { index -> startAt + (index * incrementBy) } // Adjusted margins for gesture navigation
            } else {
                val startAt = resources.getDimensionPixelSize(R.dimen.bottom_margin_3_button_nav)
                List(numOfElements) { index -> startAt + (index * incrementBy) } // Adjusted margins for 3-button navigation
            }

            val visibleViews = views.filter { it.isVisible }
            val visibleMargins =
                margins.take(visibleViews.size) // Trim margins list to match visible views

            // Reset margins for all views
            views.forEach { view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = 0
                view.layoutParams = params
            }

            // Apply correct spacing for visible views
            visibleViews.forEachIndexed { index, view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                var bottomMargin = visibleMargins.getOrElse(index) { 0 }

                // Add extra space above fabLayout if it's visible
                if (prefs.homeAlignmentBottom) {
                    if (visibleViews.contains(fabLayout)) {
                        if (view == homeAppsLayout) {
                            bottomMargin += 65
                        }
                    }
                }

                if (view == homeScreenPager) {
                    bottomMargin += 10
                }

                params.bottomMargin = bottomMargin
                view.layoutParams = params
            }
        }
    }


    internal fun renderFolderApp(row: TextView, app: AppListItem, mode: DisplayMode) {
        row.applyHomeAppIcon(app, mode)
    }

    private fun TextView.applyHomeAppIcon(app: AppListItem, mode: DisplayMode): Drawable? {
        if (app.activityPackage.isBlank() || mode == DisplayMode.TEXT_ONLY) return null

        val iconPack = prefs.iconPackHome
        val shortcutIcon = if (app.isShortcut) ShortcutHelper.getIcon(context, app) else null
        val originalIcon = shortcutIcon ?: getSafeAppIcon(
            context = context,
            packageName = app.activityPackage,
            useIconPack = iconPack == Constants.IconPacks.Custom && prefs.customIconPackHome.isNotEmpty(),
            iconPackTarget = IconCacheTarget.HOME
        )
        val icon = getSystemIcons(context, prefs, IconCacheTarget.HOME, originalIcon)
            ?: (originalIcon.constantState?.newDrawable(resources) ?: originalIcon).mutate()
        if (mode == DisplayMode.MONOCHROME_AND_TEXT) {
            val grayscale = ColorMatrix().apply { setSaturation(0f) }
            icon.colorFilter = ColorMatrixColorFilter(grayscale)
        }

        val iconScale = when (iconPack) {
            Constants.IconPacks.Disabled, Constants.IconPacks.System, Constants.IconPacks.Custom -> 2.8f
            else -> 1.4f
        }
        val iconSize = (prefs.appSize * iconScale).toInt()
        icon.setBounds(0, 0, iconSize, iconSize)
        when (prefs.homeAlignment) {
            Constants.Gravity.Left -> setCompoundDrawables(icon, null, null, null)
            Constants.Gravity.Center -> setCompoundDrawables(null, icon, null, null)
            Constants.Gravity.Right -> setCompoundDrawables(null, null, icon, null)
        }
        compoundDrawablePadding = (iconSize / 1.2f).toInt()

        if (mode == DisplayMode.ICON_ONLY) {
            contentDescription = text
            hint = null
            text = ""
        }
        return originalIcon
    }

    @SuppressLint("InflateParams", "DiscouragedApi", "UseCompatLoadingForDrawables", "ClickableViewAccessibility")
    private fun updateAppCount(newAppsNum: Int) {
        val oldAppsNum = binding.homeAppsLayout.childCount // current number of apps
        val diff = newAppsNum - oldAppsNum

        if (diff > 0) {
            // Add new apps
            for (i in oldAppsNum until newAppsNum) {
                val homeAppLabel = layoutInflater.inflate(R.layout.home_app_button, null) as TextView
                homeAppLabel.apply {
                    val homeApp = prefs.getHomeAppModel(i)
                    val folder = homeFolders.folderAt(i)
                    val grouped = homeFolders.isGrouped(i)
                    textSize = prefs.appSize.toFloat()
                    id = i
                    text = if (folder != null) {
                        folder.label
                    } else if (grouped || homeApp.activityPackage.isBlank()) {
                        getLocalizedString(R.string.select_app)
                    } else {
                        prefs.getAppDisplayName(homeApp)
                    }

                    getHomeAppsGestureListener()
                    setOnClickListener(this@HomeFragment)

                    if (!prefs.extendHomeAppsArea) {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    gravity = prefs.homeAlignment.value()
                    isFocusable = true
                    isFocusableInTouchMode = true

                    val padding: Int = prefs.textPaddingSize
                    setPadding(0, padding, 0, padding)
                    setTextColor(prefs.appColor)
                    val packageName = homeApp.activityPackage
                    val displayMode = homeFolders.displayMode(homeApp, i)
                    val originalIcon = if (folder == null && !grouped) applyHomeAppIcon(homeApp, displayMode) else null
                    if (originalIcon != null) {
                        val label = if (displayMode == DisplayMode.ICON_ONLY) "" else prefs.getAppDisplayName(homeApp)
                        val nm = NotificationManagerCompat.getEnabledListenerPackages(context)

                        if (nm.contains(context.packageName)) {
                            fun getCircledDigit(number: Int): String {
                                return when {
                                    number in 1..9 -> ('\u278A' + (number - 1)).toString() // ➊…➒
                                    number >= 10 -> '\u2789'.toString() // always ➓ for 10 or more
                                    else -> "" // no badge if 0 or invalid
                                }
                            }

                            val listener: (Map<String, Int>) -> Unit = { counts ->
                                val count = counts[packageName] ?: 0

                                this.text = if (count > 0) {
                                    val circledNumber = getCircledDigit(count)
                                    val newText = if (label.isEmpty()) circledNumber else "$label $circledNumber"
                                    val spannable = SpannableString(newText)

                                    val start = newText.indexOf(circledNumber)
                                    val end = start + circledNumber.length

                                    // Change size of the circled digit
                                    spannable.setSpan(
                                        AbsoluteSizeSpan((this.textSize * 0.8f).toInt(), false),
                                        start, end,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )

                                    // Set color
                                    val customColor = ColorIconsExtensions.getDominantColor(originalIcon)
                                    spannable.setSpan(
                                        ForegroundColorSpan(customColor),
                                        start, end,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )

                                    // Move emoji up slightly
                                    spannable.setSpan(
                                        SuperscriptSpan(),
                                        start, end,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )

                                    spannable
                                } else {
                                    label
                                }

                                AppLogger.d("HomeFragment", "Notification count updated for $packageName: $count")
                            }

                            // Register listener for this TextView
                            NotificationDotManager.registerListener(listener)

                            // Make sure we update immediately based on current counts
                            listener(NotificationDotManager.getAllCounts())

                            // Unregister when detached
                            this.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: View) {}
                                override fun onViewDetachedFromWindow(v: View) {
                                    NotificationDotManager.unregisterListener(listener)
                                }
                            })

                        } else {
                            AppLogger.d("HomeFragment", "Notification listener permission not enabled for ${context.packageName}")
                        }
                    }
                }
                // Add the view to the layout
                binding.homeAppsLayout.addView(homeFolders.visibleSlot(i, homeAppLabel))
            }
        } else if (diff < 0) {
            // Remove extra apps
            binding.homeAppsLayout.removeViews(oldAppsNum + diff, -diff)
        }

        // Update the total number of pages and calculate maximum apps per page
        updatePagesAndAppsPerPage(prefs.homeAppsNum, prefs.homePagesNum)
        adjustTextViewMargins()
    }


    private val homeScreenPager = "HomeScreenPager"

    private var currentPage = 0
    private lateinit var pageRanges: List<IntRange>

    internal fun displayPageForSlot(slot: Int): Int =
        HomePages.pageForSlot(PageStore(requireContext()).load(), prefs.homeAppsNum, prefs.homePagesNum.coerceAtLeast(1), slot)

    /** Shows the page chosen for Home, or opens the widget page when that is the choice. */
    private fun goToStartPage() {
        val start = HomePages.effectiveStart(PageStore(requireContext()).load(), prefs.homePagesNum)
        if (start == HomePages.WIDGET_PAGE) {
            showWidgetPage()
            return
        }
        if (::pageRanges.isInitialized && start in pageRanges.indices && start != currentPage) {
            currentPage = start
            updateAppsVisibility()
        }
    }

    private fun updatePagesAndAppsPerPage(totalApps: Int, totalPages: Int) {
        AppLogger.d(homeScreenPager, "updatePagesAndAppsPerPage: totalApps=$totalApps, totalPages=$totalPages")

        if (totalPages <= 0) {
            pageRanges = emptyList()
            AppLogger.d(homeScreenPager, "No pages to show. pageRanges cleared.")
            return
        }

        // Moon Cat: the user may choose how many apps each page holds; otherwise they are split evenly.
        pageRanges = HomePages.ranges(PageStore(requireContext()).load(), totalApps, totalPages)
        AppLogger.d(homeScreenPager, "Page ranges: $pageRanges")

        if (currentPage >= pageRanges.size) {
            currentPage = 0
            AppLogger.d(homeScreenPager, "Current page reset to 0 as it was out of bounds")
        }

        updateAppsVisibility()
    }

    private fun updateAppsVisibility() {
        if (pageRanges.isEmpty() || currentPage !in pageRanges.indices) {
            AppLogger.d(homeScreenPager, "updateAppsVisibility: Invalid currentPage=$currentPage or empty pageRanges")
            return
        }

        val visibleRange = pageRanges[currentPage]
        AppLogger.d(homeScreenPager, "Showing apps for currentPage=$currentPage, visibleRange=$visibleRange")

        for (i in 0 until getTotalAppsCount()) {
            val view = binding.homeAppsLayout.getChildAt(i)
            view.isVisible = i in visibleRange
        }

        // Update page selector icons
        val totalPages = pageRanges.size
        val pageSelectorIcons = MutableList(totalPages) { R.drawable.ic_new_page }
        pageSelectorIcons[currentPage] = R.drawable.ic_current_page

        val spannable = SpannableStringBuilder()
        val pageName = HomePages.name(PageStore(requireContext()).load(), currentPage)
        if (pageName.isNotEmpty()) spannable.append(pageName).append("  ")
        binding.homeScreenPager.contentDescription =
            getLocalizedString(R.string.mooncat_pages_position, currentPage + 1, totalPages) +
                if (pageName.isNotEmpty()) ", $pageName" else ""
        pageSelectorIcons.forEach { drawableRes ->
            val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)?.apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                colorFilter = PorterDuffColorFilter(prefs.appColor, PorterDuff.Mode.SRC_IN)
            }
            val imageSpan = drawable?.let { ImageSpan(it, ImageSpan.ALIGN_BASELINE) }

            val placeholder = SpannableString(" ") // Placeholder
            imageSpan?.let { placeholder.setSpan(it, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }

            spannable.append(placeholder)
            spannable.append(" ") // Space
        }

        binding.homeScreenPager.text = spannable
        if (prefs.homePagesNum > 1 && prefs.homePager) binding.homeScreenPager.isVisible = true
        if (prefs.showFloating) binding.fabLayout.isVisible = true
    }

    private fun navigateToPreviousPage() {
        val totalPages = pageRanges.size
        if (totalPages <= 0) {
            AppLogger.d(homeScreenPager, "handleSwipeLeft: No pages to swipe")
            return
        }

        currentPage = if (currentPage == 0) {
            totalPages - 1
        } else {
            currentPage - 1
        }

        AppLogger.d(homeScreenPager, "handleSwipeLeft: currentPage now $currentPage")
        updateAppsVisibility()
    }

    private fun navigateToNextPage() {
        val totalPages = pageRanges.size
        if (totalPages <= 0) {
            AppLogger.d(homeScreenPager, "handleSwipeRight: No pages to swipe")
            return
        }

        currentPage = if (currentPage == totalPages - 1) {
            0
        } else {
            currentPage + 1
        }

        AppLogger.d(homeScreenPager, "handleSwipeRight: currentPage now $currentPage")
        updateAppsVisibility()
    }

    private fun getTotalAppsCount(): Int {
        val count = binding.homeAppsLayout.childCount
        AppLogger.d(homeScreenPager, "getTotalAppsCount: $count")
        return count
    }


    private fun trySettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (prefs.settingsLocked) {
                biometricHelper.startBiometricSettingsAuth(object :
                    BiometricHelper.CallbackSettings {
                    override fun onAuthenticationSucceeded() {
                        sendToSettingFragment()
                    }

                    override fun onAuthenticationFailed() {
                        AppLogger.e(
                            "Authentication",
                            getLocalizedString(R.string.text_authentication_failed)
                        )
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errorMessage: CharSequence?
                    ) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED -> AppLogger.e(
                                "Authentication",
                                getLocalizedString(R.string.text_authentication_cancel)
                            )

                            else ->
                                AppLogger.e(
                                    "Authentication",
                                    getLocalizedString(R.string.text_authentication_error).format(
                                        errorMessage,
                                        errorCode
                                    )
                                )
                        }
                    }
                })
            } else {
                sendToSettingFragment()
            }
        }
    }

    private fun sendToSettingFragment() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        } catch (e: java.lang.Exception) {
            AppLogger.d("onLongClick", e.toString())
        }
    }

    private fun View.getHomeScreenGestureListener() {
        this.attachGestureManager(requireContext(), object : GestureAdapter() {
            override fun onShortSwipeLeft() {
                when (val action = prefs.shortSwipeLeftAction) {
                    Action.OpenApp -> openSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongSwipeLeft() {
                when (val action = prefs.longSwipeLeftAction) {
                    Action.OpenApp -> openLongSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onShortSwipeRight() {
                when (val action = prefs.shortSwipeRightAction) {
                    Action.OpenApp -> openSwipeRightApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongSwipeRight() {
                when (val action = prefs.longSwipeRightAction) {
                    Action.OpenApp -> openLongSwipeRightApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onShortSwipeUp() {
                when (val action = prefs.shortSwipeUpAction) {
                    Action.OpenApp -> openSwipeUpApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongSwipeUp() {
                when (val action = prefs.longSwipeUpAction) {
                    Action.OpenApp -> openLongSwipeUpApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onShortSwipeDown() {
                when (val action = prefs.shortSwipeDownAction) {
                    Action.OpenApp -> openSwipeDownApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongSwipeDown() {
                when (val action = prefs.longSwipeDownAction) {
                    Action.OpenApp -> openLongSwipeDownApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongPress() {
                trySettings()
            }

            override fun onDoubleTap() {
                when (val action = prefs.doubleTapAction) {
                    Action.OpenApp -> openDoubleTapApp()
                    else -> handleOtherAction(action)
                }
            }
        })
    }

    private fun View.getHomeAppsGestureListener() {
        this.attachGestureManager(requireContext(), object : GestureAdapter() {

            override fun onLongPress() {
                val row = this@getHomeAppsGestureListener
                val manage: () -> Unit = { textOnLongClick(row) }
                if (homeFolders.folderAt(row.id) != null || !homeFolders.dragFavorite(row, row.id, manage)) manage()
            }

            override fun onSingleTap() {
                textOnClick(this@getHomeAppsGestureListener)
            }

            override fun onShortSwipeLeft() {
                when (val action = prefs.shortSwipeLeftAction) {
                    Action.OpenApp -> openSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongSwipeLeft() {
                when (val action = prefs.longSwipeLeftAction) {
                    Action.OpenApp -> openLongSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onShortSwipeRight() {
                when (val action = prefs.shortSwipeRightAction) {
                    Action.OpenApp -> openSwipeRightApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongSwipeRight() {
                when (val action = prefs.longSwipeRightAction) {
                    Action.OpenApp -> openLongSwipeRightApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onShortSwipeUp() {
                when (val action = prefs.shortSwipeUpAction) {
                    Action.OpenApp -> openSwipeUpApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongSwipeUp() {
                when (val action = prefs.longSwipeUpAction) {
                    Action.OpenApp -> openLongSwipeUpApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onShortSwipeDown() {
                when (val action = prefs.shortSwipeDownAction) {
                    Action.OpenApp -> openSwipeDownApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongSwipeDown() {
                when (val action = prefs.longSwipeDownAction) {
                    Action.OpenApp -> openLongSwipeDownApp()
                    else -> handleOtherAction(action)
                }
            }
        })
    }

    override fun onDestroyView() {
        homeFolders.close()
        super.onDestroyView()
    }

    private fun dismissDialogs() {
        dialogBuilder.backupRestoreBottomSheet?.dismiss()
        dialogBuilder.saveLoadThemeBottomSheet?.dismiss()
        dialogBuilder.saveDownloadWOTDBottomSheet?.dismiss()
        dialogBuilder.singleChoiceBottomSheetPill?.dismiss()
        dialogBuilder.singleChoiceBottomSheet?.dismiss()
        dialogBuilder.colorPickerBottomSheet?.dismiss()
        dialogBuilder.sliderBottomSheet?.dismiss()
        dialogBuilder.flagSettingsBottomSheet?.dismiss()
        dialogBuilder.showDeviceBottomSheet?.dismiss()
    }
}
