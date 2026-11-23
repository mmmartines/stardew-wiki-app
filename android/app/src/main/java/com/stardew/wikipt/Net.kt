package com.stardew.wikipt

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

/** Estado da conexão do aparelho. */
object Net {
    private var cm: ConnectivityManager? = null

    fun init(ctx: Context) {
        cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    fun online(): Boolean = try {
        val c = cm ?: return true
        val n = c.activeNetwork
        val caps = if (n != null) c.getNetworkCapabilities(n) else null
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        true // na dúvida, tenta
    }
}

/** Aparelho sem internet: nem tenta a rede. */
class OfflineException : IOException("offline")

/** A wiki respondeu com erro (ex.: 403 do firewall para certos termos, 5xx fora do ar). */
class ServerException(val code: Int?, detail: String) : IOException(detail)
