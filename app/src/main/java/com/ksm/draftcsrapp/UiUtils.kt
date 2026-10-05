package com.ksm.draftcsrapp

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

// Layar edge-to-edge: header berwarna navy digambar sampai ke belakang status bar, jadi
// kontennya didorong ke bawah sebesar tinggi status bar (ditambah padding bawaan layout).
fun View.applyStatusBarPadding() {
    val base = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updatePadding(top = base + insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
        insets
    }
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}

// Sama seperti applyStatusBarPadding, untuk bar di bawah (di atas navigation bar)
fun View.applyNavigationBarPadding() {
    val base = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updatePadding(bottom = base + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
        insets
    }
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}
