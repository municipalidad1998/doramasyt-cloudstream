@file:Suppress("unused","UNUSED_PARAMETER")
package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi

// BasePlugin tiene registerMainAPI
open class BasePlugin {
    open var __filename: String = ""
    open var filename: String = ""
    open fun load() {}
    open fun beforeUnload() {}
    fun registerMainAPI(api: MainAPI) {}
    fun registerExtractorAPI(api: ExtractorApi) {}
}

// Plugin extiende BasePlugin — CloudStream carga esto
open class Plugin : BasePlugin() {
    open var resources: Resources? = null
    open var openSettings: ((Context) -> Unit)? = null
    open fun load(context: Context) {}
    open fun registerVideoClickAction(action: Any) {}
    open fun getFilename(): String = __filename
}
