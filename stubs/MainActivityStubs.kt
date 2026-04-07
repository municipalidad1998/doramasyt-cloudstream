@file:Suppress("unused")
@file:JvmName("MainActivityKt")
package com.lagradost.cloudstream3

import com.lagradost.nicehttp.Requests

var app: Requests = Requests()
fun getApp(): Requests = app
fun setApp(r: Requests) { app = r }
