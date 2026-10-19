package org.totschnig.myexpenses.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squareup.picasso.Picasso
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Singleton

@Module
open class NetworkModule {
    companion object {
        @JvmStatic
        @Singleton
        @Provides
        fun providePicasso(context: Context): Picasso = Picasso.Builder(context).build().also {
            Picasso.setSingletonInstance(it)
        }

        @JvmStatic
        @Provides
        fun provideOkHttpClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()

        @JvmStatic
        @Provides
        @Singleton
        fun provideGson(): Gson =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter)
                .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter)
                .registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter)
                .create()
    }
}
