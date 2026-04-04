package com.doramasyt

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DoramasYTPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DoramasYTProvider())
    }
}
