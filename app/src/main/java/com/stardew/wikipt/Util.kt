package com.stardew.wikipt

import android.text.Html
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Bg {
    val pool: ExecutorService = Executors.newCachedThreadPool()
}

/** Executa [work] em segundo plano e entrega o resultado na thread principal. */
fun <T> AppCompatActivity.runAsync(work: () -> T, done: (Result<T>) -> Unit) {
    Bg.pool.execute {
        val r: Result<T> = try {
            Result.success(work())
        } catch (e: Throwable) {
            Result.failure(e)
        }
        runOnUiThread {
            if (!isFinishing && !isDestroyed) done(r)
        }
    }
}

fun AppCompatActivity.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun stripHtml(s: String): String =
    Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()
