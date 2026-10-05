package com.ksm.draftcsrapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ksm.draftcsrapp.databinding.ViewTopMessageBinding
import kotlin.math.abs

enum class MessageType(@ColorRes val color: Int, @DrawableRes val icon: Int, val durationMs: Long) {
    SUCCESS(R.color.nd_green_600, R.drawable.ic_check_circle, 2500),
    ERROR(R.color.nd_error, R.drawable.ic_error, 4000),
    WARNING(R.color.nd_amber_600, R.drawable.ic_warning, 3000),
    INFO(R.color.nd_primary, R.drawable.ic_info, 2500)
}

// Pengganti Toast: pesan pop-up yang meluncur dari atas layar (di bawah status bar),
// hilang otomatis, dan bisa ditutup dengan tap atau swipe ke atas.
fun Activity.showMessage(message: String, type: MessageType = MessageType.INFO) =
    TopMessage.show(this, message, type)

object TopMessage {

    private const val TAG_VIEW = "top_message"
    private const val ANIM_IN_MS = 250L
    private const val ANIM_OUT_MS = 200L

    private val handler = Handler(Looper.getMainLooper())
    private var resumedActivity: Activity? = null

    // Pesan yang dikirim tepat sebelum finish() (mis. "Draft berhasil disimpan"). Activity-nya
    // keburu tertutup, jadi pesan ditampilkan di activity berikutnya yang tampil.
    private var pending: Pair<String, MessageType>? = null

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumedActivity = activity
                pending?.let { (msg, type) ->
                    pending = null
                    display(activity, msg, type)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (resumedActivity === activity) resumedActivity = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun show(activity: Activity, message: String, type: MessageType = MessageType.INFO) {
        // Ditunda satu giliran supaya finish() yang dipanggil tepat setelah show() sudah tercatat
        handler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                val next = resumedActivity
                if (next != null && next !== activity) display(next, message, type)
                else pending = message to type
            } else {
                display(activity, message, type)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun display(activity: Activity, message: String, type: MessageType) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        // Hanya satu pesan sekaligus: pesan baru menggantikan yang lama
        decor.findViewWithTag<View>(TAG_VIEW)?.let { decor.removeView(it) }

        val b = ViewTopMessageBinding.inflate(LayoutInflater.from(activity), decor, false)
        val root = b.root
        root.tag = TAG_VIEW
        root.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, type.color))
        b.ivIcon.setImageResource(type.icon)
        b.ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.white))
        b.tvMessage.text = message

        val density = activity.resources.displayMetrics.density
        val statusBarTop = ViewCompat.getRootWindowInsets(decor)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
        val side = (16 * density).toInt()
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply { setMargins(side, statusBarTop + (8 * density).toInt(), side, 0) }

        decor.addView(root)

        val hideRunnable = Runnable { dismiss(root) }

        root.alpha = 0f
        root.post {
            root.translationY = -(root.height + root.top).toFloat()
            root.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(ANIM_IN_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
            handler.postDelayed(hideRunnable, type.durationMs)
        }

        // Tap = tutup, swipe ke atas = tutup, lepas di tengah jalan = kembali ke posisi semula
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        var downY = 0f
        root.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    handler.removeCallbacks(hideRunnable)
                }
                MotionEvent.ACTION_MOVE -> {
                    v.translationY = (event.rawY - downY).coerceAtMost(0f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = event.rawY - downY
                    if (abs(dy) < touchSlop || dy < -v.height / 3f) {
                        dismiss(v)
                    } else {
                        v.animate().translationY(0f).setDuration(ANIM_OUT_MS).start()
                        handler.postDelayed(hideRunnable, type.durationMs)
                    }
                }
            }
            true
        }
    }

    private fun dismiss(view: View) {
        val parent = view.parent as? ViewGroup ?: return
        view.setOnTouchListener(null)
        view.animate()
            .translationY(-(view.height + view.top).toFloat())
            .alpha(0f)
            .setDuration(ANIM_OUT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { parent.removeView(view) }
            .start()
    }
}
