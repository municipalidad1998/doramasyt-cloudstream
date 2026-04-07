@file:Suppress("UNUSED_PARAMETER","unused")
package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.cloudstream3.MainAPI

open class Plugin {
    open fun load(context: Context) {}
}

fun registerMainAPI(api: MainAPI) {}
