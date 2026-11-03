package com.stardew.wikipt

import android.content.Context
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.material.button.MaterialButton

/**
 * Avisa quando o aparelho fica sem internet e some sozinho quando a conexão volta.
 * - [full] = true: tela inteira no tema do app, com opção de ver as páginas salvas (favoritos funcionam offline).
 * - Sempre: faixa abaixo da barra superior enquanto estiver sem conexão.
 */
class Offline private constructor(
    private val act: AppCompatActivity,
    private val anchor: View,
    private val full: Boolean,
    private val onSeeSaved: (() -> Unit)?,
    private val onOnline: () -> Unit,
) {
    private val cm = act.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val main = Handler(Looper.getMainLooper())
    private val content = act.findViewById<FrameLayout>(android.R.id.content)
    private var banner: TextView? = null
    private var screen: View? = null
    private var dismissed = false
    private var wasOffline = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update()
        override fun onLost(network: Network) = update()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
    }

    private val check = Runnable { apply(isOnline()) }

    /** Espera um instante antes de decidir, para não piscar ao trocar de Wi-Fi para dados móveis. */
    private fun update() {
        main.removeCallbacks(check)
        main.postDelayed(check, 800)
    }

    private fun isOnline(): Boolean = try {
        val n = cm.activeNetwork
        val c = if (n != null) cm.getNetworkCapabilities(n) else null
        c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        true // na dúvida, não bloqueia
    }

    private fun apply(online: Boolean) {
        if (act.isFinishing || act.isDestroyed) return
        if (online) {
            banner?.visibility = View.GONE
            screen?.let { content.removeView(it) }
            screen = null
            dismissed = false
            if (wasOffline) onOnline()
            wasOffline = false
        } else {
            wasOffline = true
            showBanner()
            if (full && !dismissed && screen == null) showScreen()
        }
    }

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()
    private val pixel: Typeface? by lazy { ResourcesCompat.getFont(act, R.font.pixel) }
    private fun color(id: Int) = ContextCompat.getColor(act, id)

    private fun showBanner() {
        val b = banner ?: TextView(act).apply {
            text = act.getString(R.string.offline_banner)
            setTextColor(0xFFFFE9B8.toInt())
            setBackgroundColor(color(R.color.wood_dark))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            elevation = dp(4).toFloat()
            content.addView(this, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            banner = this
        }
        b.visibility = View.VISIBLE
        b.bringToFront()
        place()
    }

    /** A faixa fica logo abaixo da barra superior. */
    private fun place() {
        val b = banner ?: return
        val loc = IntArray(2); val base = IntArray(2)
        anchor.getLocationInWindow(loc); content.getLocationInWindow(base)
        b.translationY = (loc[1] - base[1] + anchor.height).toFloat()
    }

    private fun showScreen() {
        val root = FrameLayout(act).apply {
            setBackgroundColor(color(R.color.bg))
            isClickable = true // bloqueia toques na tela de trás
            isFocusable = true
            elevation = dp(8).toFloat()
        }
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ContextCompat.getDrawable(act, R.drawable.dialog_bg)
            setPadding(dp(26), dp(26), dp(26), dp(22))
        }
        card.addView(ImageView(act).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(80), dp(80)))
        card.addView(TextView(act).apply {
            text = act.getString(R.string.offline_screen_title)
            typeface = pixel
            textSize = 22f
            setTextColor(color(R.color.ink))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        })
        card.addView(TextView(act).apply {
            text = act.getString(R.string.offline_screen_text)
            textSize = 15f
            setTextColor(color(R.color.ink))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        })
        val wait = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(6))
        }
        wait.addView(ProgressBar(act).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(22), dp(22)))
        wait.addView(TextView(act).apply {
            text = act.getString(R.string.offline_waiting)
            typeface = pixel
            textSize = 15f
            setTextColor(color(R.color.wood_text))
            setPadding(dp(10), 0, 0, 0)
        })
        card.addView(wait)
        if (onSeeSaved != null) {
            card.addView(MaterialButton(act).apply {
                text = act.getString(R.string.offline_see_saved)
                typeface = pixel
                isAllCaps = false
                setOnClickListener {
                    dismissed = true
                    content.removeView(root)
                    screen = null
                    onSeeSaved.invoke()
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
        }
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { leftMargin = dp(22); rightMargin = dp(22) }
        root.addView(card, lp)
        content.addView(root, FrameLayout.LayoutParams(-1, -1))
        screen = root
    }

    private fun start() {
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) { /* sem acesso ao estado da rede: não mostra aviso */ }
        main.removeCallbacks(check)
        apply(isOnline())
    }

    private fun stop() {
        main.removeCallbacks(check)
        try { cm.unregisterNetworkCallback(callback) } catch (e: Exception) {}
    }

    companion object {
        /** Liga o aviso na tela; [anchor] é a barra superior. */
        fun attach(
            act: AppCompatActivity, anchor: View, full: Boolean = false,
            onSeeSaved: (() -> Unit)? = null, onOnline: () -> Unit = {},
        ): Offline {
            val o = Offline(act, anchor, full, onSeeSaved, onOnline)
            anchor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> o.place() }
            act.lifecycle.addObserver(LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> o.start()
                    Lifecycle.Event.ON_STOP -> o.stop()
                    else -> {}
                }
            })
            return o
        }
    }
}
