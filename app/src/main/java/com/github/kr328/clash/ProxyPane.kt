package com.github.kr328.clash

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class ProxyPane(
    private val activity: BaseActivity<*>,
    private val embedded: Boolean = false,
) {
    lateinit var design: ProxyDesign
        private set

    private lateinit var names: List<String>
    private lateinit var states: List<ProxyState>
    private lateinit var unorderedStates: Map<String, ProxyState>
    private val reloadLock = Semaphore(10)
    private val designReloadLock = Mutex()
    private var generation = 0

    suspend fun initialize(): ProxyDesign {
        reloadDesign()
        return design
    }

    suspend fun refresh(): Boolean {
        reloadDesign()
        return true
    }

    suspend fun handleEvent(event: BaseActivity.Event): Boolean {
        return when (event) {
            BaseActivity.Event.ServiceRecreated,
            BaseActivity.Event.ClashStart,
            BaseActivity.Event.ClashStop,
            BaseActivity.Event.ProfileChanged,
            BaseActivity.Event.ProfileLoaded -> {
                reloadDesign()
                true
            }
            else -> false
        }
    }

    suspend fun handleRequest(request: ProxyDesign.Request): Boolean {
        return when (request) {
            ProxyDesign.Request.ReLaunch -> {
                reloadDesign()
                true
            }
            ProxyDesign.Request.ReloadAll -> {
                names.indices.forEach { index ->
                    design.requests.trySend(ProxyDesign.Request.Reload(index))
                }
                false
            }
            is ProxyDesign.Request.Reload -> {
                val currentGeneration = generation
                val currentDesign = design
                val currentNames = names
                val currentStates = states
                val currentLinks = unorderedStates

                activity.launch {
                    val group = reloadLock.withPermit {
                        withClash {
                            queryProxyGroup(
                                currentNames[request.index],
                                activity.uiPreferences.proxySort
                            )
                        }
                    }

                    if (generation != currentGeneration || design !== currentDesign) {
                        return@launch
                    }

                    val state = currentStates[request.index]
                    state.now = group.now

                    currentDesign.updateGroup(
                        request.index,
                        group.proxies,
                        group.type == Proxy.Type.Selector,
                        state,
                        currentLinks
                    )
                }
                false
            }
            is ProxyDesign.Request.Select -> {
                withClash {
                    patchSelector(names[request.index], request.name)
                    states[request.index].now = request.name
                }

                design.requestRedrawVisible()
                false
            }
            is ProxyDesign.Request.UrlTest -> {
                val currentGeneration = generation
                val currentDesign = design

                activity.launch {
                    withClash {
                        healthCheck(names[request.index])
                    }

                    if (generation != currentGeneration || design !== currentDesign) {
                        return@launch
                    }

                    currentDesign.requests.send(ProxyDesign.Request.Reload(request.index))
                }
                false
            }
            is ProxyDesign.Request.PatchMode -> {
                design.showModeSwitchTips()

                withClash {
                    val override = queryOverride(Clash.OverrideSlot.Session)
                    override.mode = request.mode
                    patchOverride(Clash.OverrideSlot.Session, override)
                }
                false
            }
        }
    }

    private suspend fun reloadDesign() {
        designReloadLock.withLock {
            generation += 1

            val mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }
            names = withClash { queryProxyGroupNames(activity.uiPreferences.proxyExcludeNotSelectable) }
            states = List(names.size) { ProxyState("?") }
            unorderedStates = names.indices.map { index -> names[index] to states[index] }.toMap()

            design = ProxyDesign(
                activity,
                mode,
                names,
                activity.uiPreferences,
                embedded = embedded,
            )

            design.requests.send(ProxyDesign.Request.ReloadAll)
        }
    }
}
