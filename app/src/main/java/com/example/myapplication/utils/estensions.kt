package com.example.myapplication.utils

import android.os.SystemClock
import android.view.View

fun View.tap(interval: Long = 1000L, action: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener {
        if (SystemClock.elapsedRealtime() - lastClickTime < interval) return@setOnClickListener
        lastClickTime = SystemClock.elapsedRealtime()
        action(it)
    }
}