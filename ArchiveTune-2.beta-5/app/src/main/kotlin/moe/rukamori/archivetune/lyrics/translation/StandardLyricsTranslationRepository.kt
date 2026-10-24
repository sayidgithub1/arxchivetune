/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics.translation

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class StandardLyricsTranslationException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

@Singleton
class StandardLyricsTranslationRepository
    @Inject
    constructor() {
        private val client =
            HttpClient(OkHttp) {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = RequestTimeoutMillis
                    connectTimeoutMillis = ConnectTimeoutMillis
                    socketTimeoutMillis = RequestTimeoutMillis
                }
                engine {
                    config {
                        retryOnConnectionFailure(true)
                    }
                }
            }

        // ====================================================================
        // SISTEM AUTO-RETRY DAN FALLBACK ENDPOINT DITAMBAHKAN DI SINI
        // ====================================================================
        suspend fun translate(
            text: String,
            targetLanguageCode: String,
        ): String =
            withContext(Dispatchers.IO) {
                val maxAttempts = 3
                var currentAttempt = 0
                var lastException: Exception? = null

                // Daftar server yang akan dicoba secara bergantian jika salah satu error/diblokir
                val endpoints = listOf(
                    "https://translate.googleapis.com/translate_a/single",
                    "https://clients5.google.com/translate_a/t" // Fallback Endpoint
                )

                while (currentAttempt < maxAttempts) {
                    try {
                        val endpointToUse = endpoints[currentAttempt % endpoints.size]
                        
                        val response =
                            client.submitForm(
                                url = endpointToUse,
                                formParameters =
                                    parameters {
                                        append("client", AndroidClientId)
                                        append("dt", "t")
                                        append("ie", "UTF-8")
                                        append("oe", "UTF-8")
                                        append("sl", "auto")
                                        append("tl", targetLanguageCode)
                                        append("q", text)
                                    },
                            ) {
                                header(HttpHeaders.UserAgent, UserAgent)
                            }
                            
                        val responseBody = response.bodyAsText()
                        
                        if (response.status.value in 200..299) {
                            return@withContext parseTranslatedText(responseBody)
                        } else {
                            throw StandardLyricsTranslationException(
                                "Translation service returned HTTP ${response.status.value}"
                            )
                        }
                    } catch (e: Exception) {
                        lastException = e
                        currentAttempt++
                        
                        if (currentAttempt < maxAttempts) {
                            // Jeda sebelum mencoba lagi (Backoff: 1 detik, lalu 2 detik)
                            delay(currentAttempt * 1000L)
                        }
                    }
                }

                // Jika sudah 3 kali mencoba dan tetap gagal, barulah lemparkan error ke layar
                throw StandardLyricsTranslationException(
                    "Gagal menerjemahkan lirik setelah $maxAttempts kali percobaan. Server sedang sibuk.", 
                    lastException
                )
            }

        private fun parseTranslatedText(responseBody: String): String {
            val translatedSegments =
                try {
                    // Penanganan format JSON yang mungkin berbeda antara endpoint 1 dan 2
                    val jsonArray = JSONArray(responseBody)
                    // Terkadang format clients5 mengembalikan array langsung, terkadang bersarang
                    if (jsonArray.optJSONArray(0) != null) {
                        jsonArray.optJSONArray(0)
                    } else {
                        jsonArray
                    }
                } catch (exception: Exception) {
                    throw StandardLyricsTranslationException("Translation service returned malformed data", exception)
                } ?: throw StandardLyricsTranslationException("Translation service returned no translated text")

            val translatedText =
                buildString {
                    for (index in 0 until translatedSegments.length()) {
                        val segment = translatedSegments.optJSONArray(index) ?: continue
                        // Mengambil string teks hasil terjemahan
                        append(segment.optString(0))
                    }
                }
            if (translatedText.isBlank()) {
                throw StandardLyricsTranslationException("Translation service returned empty translated text")
            }
            return translatedText
        }

        private companion object {
            // AndroidClientId default bawaan Google Translate API bypass
            const val AndroidClientId = "at" 
            const val UserAgent = "ArchiveTune Android"
            const val ConnectTimeoutMillis = 15_000L
            const val RequestTimeoutMillis = 30_000L
        }
    }
