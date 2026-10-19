package org.totschnig.myexpenses.di

import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient

@Module
open class NetworkModule {
    companion object {

        @JvmStatic
        @Provides
        fun provideOkHttpClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
    }
}
