package com.municipalidad1998.doramasyt

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DoramaYTProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DoramasYTProvider())
    }
}
