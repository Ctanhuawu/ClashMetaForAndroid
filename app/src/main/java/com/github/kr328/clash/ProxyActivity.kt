package com.github.kr328.clash

import com.github.kr328.clash.design.ProxyDesign
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class ProxyActivity : BaseActivity<ProxyDesign>() {
    override suspend fun main() {
        val proxyPane = ProxyPane(this)

        setContentDesign(proxyPane.initialize())

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    if (proxyPane.handleEvent(it)) {
                        setContentDesign(proxyPane.design)
                    }
                }
                proxyPane.design.requests.onReceive {
                    if (proxyPane.handleRequest(it)) {
                        setContentDesign(proxyPane.design)
                    }
                }
            }
        }
    }
}
