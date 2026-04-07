@file:Suppress("UNUSED_PARAMETER","unused")
@file:JvmName("MainActivityKt")
package com.lagradost.cloudstream3

import com.lagradost.nicehttp.Requests

val app: Requests get() = Requests()
fun getApp(): Requests = Requests()
