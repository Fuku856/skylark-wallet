package com.example.skylarkwallet.data.remote

import okhttp3.java.net.cookiejar.JavaNetCookieJar
import okhttp3.OkHttpClient
import java.io.Closeable
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * 1回の照会（1枚の券）で使うセッション。CAPTCHA はサイトの `ci_session` Cookie に紐づくため、
 * 入力画面の取得・画像の取得・POST を同じ Cookie で行う。照会が終わったら [close] で Cookie を破棄する。
 */
class SkylarkSession(baseClient: OkHttpClient) : Closeable {

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    val client: OkHttpClient = baseClient.newBuilder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .build()

    /** 直近に取得した入力フォーム。CAPTCHA 不一致後の再表示で更新される。 */
    @Volatile
    var form: InputForm? = null

    override fun close() {
        cookieManager.cookieStore.removeAll()
        form = null
    }
}
