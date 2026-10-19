package com.example.skylarkwallet.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.skylarkwallet.BuildConfig
import com.example.skylarkwallet.data.local.crypto.AeadStringCipher
import com.example.skylarkwallet.data.local.crypto.CryptoManager
import com.example.skylarkwallet.data.local.crypto.StringCipher
import com.example.skylarkwallet.data.local.db.AppDatabase
import com.example.skylarkwallet.data.local.db.VoucherDao
import com.example.skylarkwallet.data.remote.SkylarkEndpoint
import com.example.skylarkwallet.data.remote.SkylarkScraper
import com.example.skylarkwallet.data.repository.SkylarkLookupRepository
import com.example.skylarkwallet.data.repository.VoucherRepositoryImpl
import com.example.skylarkwallet.domain.repository.LookupRepository
import com.example.skylarkwallet.domain.repository.VoucherRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val BASE_URL = "https://www.skylarkyutaiken.com/"
    private const val HTTP_LOG_TAG = "OkHttp"
    private val LONG_DIGITS = Regex("\\d{9,}")
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "ja-JP,ja;q=0.9")
                    .build(),
            )
        }
        .apply {
            // デバッグビルドのみ。本文（9桁・13桁・HTML）は出さず、Cookie も伏せる。
            // バーコード画像の URL（/input/barcode?13桁）にも番号が入るため、URL 中の長い数字列も伏せる
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor { message -> Log.d(HTTP_LOG_TAG, message.replace(LONG_DIGITS, "***")) }.apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Cookie")
                        redactHeader("Set-Cookie")
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideEndpoint(): SkylarkEndpoint = SkylarkEndpoint(BASE_URL.toHttpUrl())

    @Provides
    @Singleton
    fun provideScraper(client: OkHttpClient, endpoint: SkylarkEndpoint): SkylarkScraper =
        SkylarkScraper(client, endpoint)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "skylark.db").build()

    @Provides
    fun provideVoucherDao(db: AppDatabase): VoucherDao = db.voucherDao()

    @Provides
    @Singleton
    fun provideCipher(@ApplicationContext context: Context): StringCipher =
        AeadStringCipher { CryptoManager.createAead(context) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindVoucherRepository(impl: VoucherRepositoryImpl): VoucherRepository

    @Binds
    abstract fun bindLookupRepository(impl: SkylarkLookupRepository): LookupRepository
}
