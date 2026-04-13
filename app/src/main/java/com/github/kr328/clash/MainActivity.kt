package com.github.kr328.clash

import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setFileName
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.AboutDesign
import com.github.kr328.clash.design.ExpandedSettingsDesign
import com.github.kr328.clash.design.LogsDesign
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.logsDir
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.bridge.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class MainActivity : BaseActivity<Design<*>>() {
    private enum class Page {
        Main,
        Proxy,
        Settings,
        About,
    }

    override suspend fun main() {
        val mainDesign = MainDesign(this)
        val aboutDesign = AboutDesign(this)
        var proxyPane: ProxyPane? = null
        val expandedSettingsPane = if (isExpandedSettingsWidth()) ExpandedSettingsPane(this, clashRunning) else null
        val settingsDesign = if (expandedSettingsPane == null) SettingsDesign(this) else null
        val expandedSettingsDesign: Design<*>? = expandedSettingsPane?.initialize()
        val host = layoutInflater.inflate(R.layout.activity_main_host, null, false)
        val container = host.findViewById<ViewGroup>(R.id.design_content_container)
        val bottomNavigation = host.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)
        val pageRequests = Channel<Page>(Channel.UNLIMITED)
        var currentPage = Page.Main
        var suppressNavigationSelection = false
        val packageVersionName by lazy {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        }
        var aboutVersionName: String? = null
        var aboutVersionLoading = false

        defer {
            expandedSettingsPane?.save()
        }

        suspend fun requireProxyPane(): ProxyPane {
            val current = proxyPane
            if (current != null) {
                return current
            }

            val pane = ProxyPane(this@MainActivity, embedded = true)
            pane.initialize()
            proxyPane = pane
            return pane
        }

        suspend fun show(page: Page) {
            if (currentPage == Page.Settings && page != Page.Settings) {
                expandedSettingsPane?.save()
            }

            currentPage = page

            when (page) {
                Page.Main -> {
                    setContentDesign(mainDesign)
                    suppressNavigationSelection = true
                    bottomNavigation.selectedItemId = R.id.navigation_main
                    suppressNavigationSelection = false
                    mainDesign.fetch()
                }
                Page.Proxy -> {
                    val pane = requireProxyPane()
                    pane.refresh()
                    setContentDesign(pane.design)
                    suppressNavigationSelection = true
                    bottomNavigation.selectedItemId = R.id.navigation_proxy
                    suppressNavigationSelection = false
                }
                Page.Settings -> {
                    setContentDesign(expandedSettingsDesign ?: settingsDesign!!)
                    suppressNavigationSelection = true
                    bottomNavigation.selectedItemId = R.id.navigation_settings
                    suppressNavigationSelection = false

                    if (expandedSettingsPane?.isShowingLogs() == true) {
                        expandedSettingsPane.refreshLogs(loadFiles())
                    }
                }
                Page.About -> {
                    setContentDesign(aboutDesign)
                    suppressNavigationSelection = true
                    bottomNavigation.selectedItemId = R.id.navigation_about
                    suppressNavigationSelection = false
                    aboutDesign.patchItems(buildAboutItems(aboutVersionName ?: packageVersionName))

                    if (aboutVersionName == null && !aboutVersionLoading) {
                        aboutVersionLoading = true

                        launch {
                            aboutVersionName = runCatching { queryAppVersionName() }
                                .getOrDefault(packageVersionName)
                            aboutVersionLoading = false

                            if (currentPage == Page.About) {
                                aboutDesign.patchItems(buildAboutItems(aboutVersionName!!))
                            }
                        }
                    }
                }
            }

        }

        setActivityContent(host, container)

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, view.paddingTop, systemBars.right, systemBars.bottom)
            insets
        }
        bottomNavigation.setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener {
            if (suppressNavigationSelection) {
                return@OnItemSelectedListener true
            }

            when (it.itemId) {
                R.id.navigation_main -> {
                    if (currentPage != Page.Main) {
                        pageRequests.trySend(Page.Main)
                    }
                    true
                }
                R.id.navigation_proxy -> {
                    if (currentPage != Page.Proxy) {
                        pageRequests.trySend(Page.Proxy)
                    }
                    true
                }
                R.id.navigation_settings -> {
                    if (currentPage != Page.Settings) {
                        pageRequests.trySend(Page.Settings)
                    }
                    true
                }
                R.id.navigation_about -> {
                    if (currentPage != Page.About) {
                        pageRequests.trySend(Page.About)
                    }
                    true
                }
                else -> false
            }
        })

        show(Page.Main)

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            when (currentPage) {
                                Page.Main -> mainDesign.fetch()
                                Page.Proxy -> Unit
                                Page.Settings -> {
                                    if (expandedSettingsPane?.isShowingLogs() == true) {
                                        expandedSettingsPane.refreshLogs(loadFiles())
                                    }
                                }
                                Page.About -> Unit
                            }
                        }
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged ->
                            when (currentPage) {
                                Page.Main -> mainDesign.fetch()
                                Page.Proxy -> {
                                    if (proxyPane?.handleEvent(it) == true) {
                                        setContentDesign(proxyPane!!.design)
                                    }
                                }
                                else -> {
                                    if (it == Event.ServiceRecreated ||
                                        it == Event.ClashStop ||
                                        it == Event.ClashStart ||
                                        it == Event.ProfileLoaded ||
                                        it == Event.ProfileChanged
                                    ) {
                                        proxyPane?.handleEvent(it)
                                    }
                                }
                            }
                        else -> Unit
                    }
                }
                pageRequests.onReceive {
                    if (currentPage != it) {
                        show(it)
                    }
                }
                mainDesign.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            if (clashRunning)
                                stopClashService()
                            else
                                mainDesign.startClash()
                        }
                        MainDesign.Request.OpenProxy ->
                            pageRequests.trySend(Page.Proxy)
                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        MainDesign.Request.OpenLogs -> {
                            startActivity(LogsActivity::class.intent)
                        }
                        MainDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        MainDesign.Request.OpenAbout ->
                            pageRequests.trySend(Page.About)
                    }
                }
                aboutDesign.requests.onReceive {
                    when (it) {
                        AboutDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        AboutDesign.Request.OpenSource ->
                            startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.meta_github_url))))
                    }
                }
                if (proxyPane != null) {
                    proxyPane!!.design.requests.onReceive {
                        if (proxyPane!!.handleRequest(it)) {
                            if (currentPage == Page.Proxy) {
                                setContentDesign(proxyPane!!.design)
                            }
                        }
                    }
                }
                if (expandedSettingsPane == null) {
                    settingsDesign!!.requests.onReceive {
                        when (it) {
                            SettingsDesign.Request.StartApp ->
                                startActivity(AppSettingsActivity::class.intent)
                            SettingsDesign.Request.StartNetwork ->
                                startActivity(NetworkSettingsActivity::class.intent)
                            SettingsDesign.Request.StartLogs ->
                                startActivity(LogsActivity::class.intent)
                            SettingsDesign.Request.StartOverride ->
                                startActivity(OverrideSettingsActivity::class.intent)
                            SettingsDesign.Request.StartMetaFeature ->
                                startActivity(MetaFeatureSettingsActivity::class.intent)
                        }
                    }
                } else {
                    expandedSettingsPane.design.requests.onReceive {
                        expandedSettingsPane.handleExpandedRequest(it)

                        if (it is ExpandedSettingsDesign.Request.SelectSection &&
                            it.section == ExpandedSettingsDesign.Section.Logs
                        ) {
                            expandedSettingsPane.refreshLogs(loadFiles())
                        }
                    }
                    expandedSettingsPane.appDesign.requests.onReceive { expandedSettingsPane.handleAppRequest(it) }
                    expandedSettingsPane.networkDesign.requests.onReceive { expandedSettingsPane.handleNetworkRequest(it) }
                    expandedSettingsPane.logsDesign.requests.onReceive { handleLogsRequest(expandedSettingsPane.logsDesign, it) }
                    expandedSettingsPane.overrideDesign.requests.onReceive { expandedSettingsPane.handleOverrideRequest(it) }
                    expandedSettingsPane.metaFeatureDesign.requests.onReceive { expandedSettingsPane.handleMetaFeatureRequest(it) }
                }
                if (clashRunning && currentPage == Page.Main) {
                    ticker.onReceive {
                        mainDesign.fetchTraffic()
                    }
                }
            }
        }
    }

    private fun buildAboutItems(versionName: String): List<com.github.kr328.clash.design.adapter.AboutItemAdapter.AboutItem> {
        return listOf(
            com.github.kr328.clash.design.adapter.AboutItemAdapter.AboutItem(
                id = "version",
                icon = R.drawable.ic_clash,
                text = getString(R.string.application_name),
                subtext = versionName,
                clickable = false,
            ),
            com.github.kr328.clash.design.adapter.AboutItemAdapter.AboutItem(
                id = "help",
                icon = R.drawable.ic_baseline_help_center,
                text = getString(R.string.help),
                subtext = getString(R.string.tips_help_plain),
            ),
            com.github.kr328.clash.design.adapter.AboutItemAdapter.AboutItem(
                id = "source",
                icon = R.drawable.ic_baseline_info,
                text = getString(R.string.sources),
                subtext = getString(R.string.meta_github_url),
            ),
        )
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())

        withProfile {
            setProfileName(queryActive()?.name)
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            setForwarded(queryTrafficTotal())
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(R.string.no_profile_selected, ToastDuration.Long) {
                setAction(R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            val appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
            val coreVersion = Bridge.nativeCoreVersion().replace("_", "-")

            "App $appVersion\nNinjia Core $coreVersion"
        }
    }

    private fun loadFiles(): List<LogFile> {
        val list = cacheDir.resolve("logs").listFiles()?.toList() ?: emptyList()

        return list.mapNotNull { LogFile.parseFromFileName(it.name) }
    }

    private fun deleteAllLogs() {
        logsDir.deleteRecursively()
    }

    private suspend fun handleLogsRequest(design: LogsDesign, request: LogsDesign.Request) {
        when (request) {
            LogsDesign.Request.StartLogcat -> {
                startActivity(LogcatActivity::class.intent)
            }
            LogsDesign.Request.DeleteAll -> {
                if (design.requestDeleteAll()) {
                    withContext(Dispatchers.IO) {
                        deleteAllLogs()
                    }

                    design.patchLogs(emptyList())
                }
            }
            is LogsDesign.Request.OpenFile -> {
                startActivity(LogcatActivity::class.intent.setFileName(request.file.fileName))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
