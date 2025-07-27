package com.di.fitric.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @Named("ApiKey")
    fun provideGeminiApiKey(): String {
        return com.di.fitric.BuildConfig.GEMINI_API_KEY
    }
}