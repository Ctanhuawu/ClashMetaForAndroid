package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setFileName
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.ExpandedSettingsDesign
import com.github.kr328.clash.design.LogsDesign
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.util.logsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class SettingsActivity : BaseActivity<Design<*>>() {
    override suspend fun main() {
        val expandedPane = if (isExpandedSettingsWidth()) ExpandedSettingsPane(this, clashRunning) else null
        val design: Design<*> = expandedPane?.initialize() ?: SettingsDesign(this)

        defer {
            expandedPane?.save()
        }

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    if (it == Event.ActivityStart && expandedPane?.isShowingLogs() == true) {
                        expandedPane.refreshLogs(loadFiles())
                    }
                }
                if (expandedPane == null) {
                    (design as SettingsDesign).requests.onReceive {
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
                    expandedPane.design.requests.onReceive {
                        expandedPane.handleExpandedRequest(it)

                        if (it is ExpandedSettingsDesign.Request.SelectSection &&
                            it.section == ExpandedSettingsDesign.Section.Logs
                        ) {
                            expandedPane.refreshLogs(loadFiles())
                        }
                    }
                    expandedPane.appDesign.requests.onReceive { expandedPane.handleAppRequest(it) }
                    expandedPane.networkDesign.requests.onReceive { expandedPane.handleNetworkRequest(it) }
                    expandedPane.logsDesign.requests.onReceive { handleLogsRequest(expandedPane.logsDesign, it) }
                    expandedPane.overrideDesign.requests.onReceive { expandedPane.handleOverrideRequest(it) }
                    expandedPane.metaFeatureDesign.requests.onReceive { expandedPane.handleMetaFeatureRequest(it) }
                }
            }
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
}
