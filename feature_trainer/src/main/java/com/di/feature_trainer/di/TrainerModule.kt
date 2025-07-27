// TrainerModule.kt

package com.di.feature_trainer.di

import android.util.Log
import com.di.feature_trainer.data.TrainerRepository
import com.di.feature_trainer.data.TrainerRepositoryImpl
import com.di.feature_trainer.data.network.GeminiLiveWebSocket
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for providing dependencies related to the AI Trainer feature.
 *
 * This module is installed in the [SingletonComponent], meaning all dependencies
 * provided here will have a singleton scope and live as long as the application process.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TrainerModule {

    /* ====================================================================== */
    /*  Repository Binding                                                    */
    /* ====================================================================== */

    /**
     * Binds the [TrainerRepositoryImpl] implementation to the [TrainerRepository] interface.
     * This allows Hilt to provide [TrainerRepository] wherever it's injected,
     * using the [TrainerRepositoryImpl] concrete class.
     *
     * @param impl The concrete implementation of [TrainerRepository].
     * @return An instance of [TrainerRepository].
     */
    @Binds
    @Singleton // Ensure the repository itself is a singleton
    abstract fun bindTrainerRepository(
        impl: TrainerRepositoryImpl
    ): TrainerRepository

    /* ====================================================================== */
    /*  Concrete Singleton Providers                                          */
    /* ====================================================================== */

    /**
     * Companion object to hold `@Provides` methods, which create and provide
     * concrete instances of dependencies.
     */
    companion object {

        /**
         * Provides a singleton instance of [GeminiLiveWebSocket].
         * This WebSocket client is responsible for the low-level communication
         * with the Gemini Live API.
         *
         * @param okHttpClient The OkHttpClient instance used for WebSocket connections.
         * @param json The Json serializer/deserializer for API messages.
         * @return A singleton instance of [GeminiLiveWebSocket].
         */
        @Provides
        @Singleton
        fun provideGeminiLiveWebSocket(
            okHttpClient: OkHttpClient,
            json: Json
        ): GeminiLiveWebSocket = GeminiLiveWebSocket(okHttpClient, json)

        /**
         * Provides a singleton instance of [Json] configured for Kotlinx Serialization.
         * This setup ensures compatibility with Google's API schema, specifically:
         * - `ignoreUnknownKeys = true`: Allows parsing JSON with fields not defined in data classes,
         *   making the app resilient to future API changes.
         * - `explicitNulls = false`: Prevents serializing null values if they are defaulted in data classes.
         * - `encodeDefaults = true`: Ensures all default values are explicitly encoded in the JSON output,
         *   which can be important for API compatibility.
         * - `isLenient = true`: Allows for more relaxed parsing of JSON, such as unquoted property names.
         *
         * @return A singleton instance of [Json].
         */
        @OptIn(ExperimentalSerializationApi::class) // Opt-in for specific serialization features.
        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
            isLenient = true
        }

        /**
         * Provides a singleton instance of [OkHttpClient] configured for networking.
         * This client includes:
         * - **`HttpLoggingInterceptor`**: Logs detailed information about HTTP requests and responses.
         *   Set to `BODY` level for comprehensive logging of headers and bodies, useful for debugging.
         *   (Note: In production builds, this should ideally be set to `NONE` or `HEADERS` for security and performance).
         * - **`errorPeekInterceptor`**: A custom [Interceptor] designed to capture and log the response body
         *   specifically when a WebSocket handshake fails (i.e., HTTP status code is not 101).
         *   This is crucial for seeing detailed error messages from the server during connection setup.
         * - **Timeouts**: Configures connection, read, and write timeouts to prevent hangs.
         *
         * @return A singleton instance of [OkHttpClient].
         */
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            // Interceptor for logging HTTP request and response bodies.
            val bodyLogger = HttpLoggingInterceptor { msg ->
                Log.d("OkHttpBody", msg)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY // Logs headers and bodies.
            }

            // Custom interceptor to log the response body for failed WebSocket handshakes.
            // A successful WebSocket handshake returns HTTP 101 Switching Protocols.
            val errorPeekInterceptor = Interceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code != 101) { // If it's not a successful WebSocket handshake
                    // Use peekBody to read the response body without consuming it,
                    // so it can still be processed by other interceptors or the client.
                    val bodyText = response.peekBody(Long.MAX_VALUE).string()
                    Log.e(
                        "OkHttpPeek",
                        """
                ↓ HTTP ${response.code} ${response.message} -- ${response.request.url}
                $bodyText
                """.trimIndent()
                    )
                }
                response // Always return the original response.
            }

            return OkHttpClient.Builder()
                .addInterceptor(bodyLogger) // Add the body logger for all requests.
                .addNetworkInterceptor(errorPeekInterceptor) // Add the error peeking interceptor.
                // Configure various timeouts for robust network operations.
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}