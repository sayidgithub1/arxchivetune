/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.about.AboutContributor
import moe.rukamori.archivetune.about.AboutContributorCollection
import moe.rukamori.archivetune.about.AboutDependencyLicense
import moe.rukamori.archivetune.about.AboutDependencyLicenseCollection
import moe.rukamori.archivetune.about.AboutTranslationContributor
import moe.rukamori.archivetune.about.AboutTranslationContributorCollection
import moe.rukamori.archivetune.about.FetchAboutContributorsUseCase
import moe.rukamori.archivetune.about.FetchAboutDependencyLicensesUseCase
import moe.rukamori.archivetune.about.FetchAboutTranslationContributorsUseCase
import moe.rukamori.archivetune.currentBuildHash
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

sealed interface AboutScreenState {
    data object Loading : AboutScreenState

    data class Success(
        val model: AboutUiModel,
    ) : AboutScreenState

    data object Empty : AboutScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutScreenState
}

@Immutable
data class AboutUiModel(
    @StringRes val appNameResId: Int,
    val versionName: String,
    val buildHash: String?,
    val buildVariant: String,
    val primaryLinks: AboutLinkCollection,
    val leadDeveloper: TeamMember,
    val collaborators: TeamMemberCollection,
    val respecters: TeamMemberCollection,
    val contributorsState: AboutContributorsUiState,
    val contributorsReadMoreUrl: String,
    val activeDialog: AboutDialog,
    val translationContributorsState: AboutTranslationContributorsUiState,
    val dependencyLicensesState: AboutDependencyLicensesUiState,
)

@Immutable
data class TeamMember(
    val avatarUrl: String,
    val name: String,
    @StringRes val positionResId: Int,
    val profileUrl: String?,
    val links: AboutLinkCollection,
)

@Immutable
data class TeamMemberCollection private constructor(
    private val values: List<TeamMember>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): TeamMember = values[index]

    companion object {
        fun of(vararg values: TeamMember): TeamMemberCollection = TeamMemberCollection(values.toList())
    }
}

@Immutable
data class AboutLinkUiModel(
    val id: String,
    @DrawableRes val iconResId: Int,
    @StringRes val labelResId: Int,
    val url: String,
)

@Immutable
data class AboutLinkCollection private constructor(
    private val values: List<AboutLinkUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): AboutLinkUiModel = values[index]

    fun forEach(action: (AboutLinkUiModel) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutLinkCollection(emptyList())

        fun of(vararg values: AboutLinkUiModel): AboutLinkCollection = AboutLinkCollection(values.toList())
    }
}

sealed interface AboutContributorsUiState {
    data object Loading : AboutContributorsUiState

    data class Success(
        val contributors: AboutContributorUiCollection,
    ) : AboutContributorsUiState

    data object Empty : AboutContributorsUiState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutContributorsUiState
}

enum class AboutDialog {
    NONE,
    TRANSLATION_CONTRIBUTORS,
    DEPENDENCY_LICENSES,
}

sealed interface AboutTranslationContributorsUiState {
    data object Loading : AboutTranslationContributorsUiState

    data class Success(
        val contributors: AboutTranslationContributorUiCollection,
    ) : AboutTranslationContributorsUiState

    data object Empty : AboutTranslationContributorsUiState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutTranslationContributorsUiState
}

@Immutable
data class AboutTranslationContributorUiModel(
    val language: String,
    val contributors: String?,
)

@Immutable
data class AboutTranslationContributorUiCollection private constructor(
    private val values: List<AboutTranslationContributorUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): AboutTranslationContributorUiModel = values[index]

    companion object {
        fun from(values: List<AboutTranslationContributorUiModel>): AboutTranslationContributorUiCollection =
            AboutTranslationContributorUiCollection(values.toList())
    }
}

sealed interface AboutDependencyLicensesUiState {
    data object Loading : AboutDependencyLicensesUiState

    data class Success(
        val licenses: AboutDependencyLicenseUiCollection,
    ) : AboutDependencyLicensesUiState

    data object Empty : AboutDependencyLicensesUiState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutDependencyLicensesUiState
}

@Immutable
data class AboutDependencyLicenseUiModel(
    val name: String,
    val version: String?,
    val licenses: String?,
)

@Immutable
data class AboutDependencyLicenseUiCollection private constructor(
    private val values: List<AboutDependencyLicenseUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): AboutDependencyLicenseUiModel = values[index]

    companion object {
        fun from(values: List<AboutDependencyLicenseUiModel>): AboutDependencyLicenseUiCollection =
            AboutDependencyLicenseUiCollection(values.toList())
    }
}

@Immutable
data class AboutContributorUiModel(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

@Immutable
data class AboutContributorUiCollection private constructor(
    private val values: List<AboutContributorUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): AboutContributorUiModel = values[index]

    fun forEach(action: (AboutContributorUiModel) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutContributorUiCollection(emptyList())

        fun from(values: List<AboutContributorUiModel>): AboutContributorUiCollection = AboutContributorUiCollection(values.toList())
    }
}

sealed interface AboutScreenEffect {
    data class OpenUri(
        val uri: String,
    ) : AboutScreenEffect
}

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val fetchAboutContributors: FetchAboutContributorsUseCase,
        private val fetchTranslationContributors: FetchAboutTranslationContributorsUseCase,
        private val fetchDependencyLicenses: FetchAboutDependencyLicensesUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<AboutScreenState>(AboutScreenState.Loading)
        val state: StateFlow<AboutScreenState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<AboutScreenEffect>(extraBufferCapacity = 1)
        val effects = _effects.asSharedFlow()

        private var activeDialog = AboutDialog.NONE
        
        // =======================================================
        // SISTEM CACHE OFFLINE (PENYIMPANAN LOKAL)
        // =======================================================
        private val sharedPrefs = context.getSharedPreferences("ArchiveTuneAboutPrefs", Context.MODE_PRIVATE)

        // Variabel penampung state UI utama
        private var currentLeadDeveloper: TeamMember
        private var currentPrimaryLinks: AboutLinkCollection

        private fun createLinkModel(url: String): AboutLinkUiModel {
            val isGithub = url.contains("github", ignoreCase = true)
            val isTelegram = url.contains("t.me", ignoreCase = true) || url.contains("telegram", ignoreCase = true)
            val isDiscord = url.contains("discord", ignoreCase = true)
            val isDonate = url.contains("donate", ignoreCase = true) || url.contains("saweria", ignoreCase = true)
            
            val iconRes = when {
                isGithub -> R.drawable.github
                isTelegram -> R.drawable.telegram
                isDiscord -> R.drawable.discord
                isDonate -> R.drawable.coffee
                else -> R.drawable.website
            }
            
            val labelRes = when {
                isGithub -> R.string.about_content_desc_github
                isTelegram -> R.string.about_content_desc_telegram
                isDiscord -> R.string.discord
                isDonate -> R.string.about_content_desc_donate
                else -> R.string.about_content_desc_website
            }
            
            return AboutLinkUiModel(
                id = url,
                iconResId = iconRes,
                labelResId = labelRes,
                url = url
            )
        }

        private fun loadCachedLinks(): AboutLinkCollection {
            val cachedLinksStr = sharedPrefs.getString("links", null)
            val linksList = mutableListOf<AboutLinkUiModel>()
            
            if (cachedLinksStr != null) {
                try {
                    val array = JSONArray(cachedLinksStr)
                    for (i in 0 until array.length()) {
                        linksList.add(createLinkModel(array.getString(i)))
                    }
                } catch (e: Exception) {}
            } else {
                linksList.add(createLinkModel("https://github.com/SayidGithub"))
                linksList.add(createLinkModel("https://wahid-archivetune.vercel.app"))
            }
            
            return if (linksList.isNotEmpty()) AboutLinkCollection.of(*linksList.toTypedArray()) else AboutLinkCollection.Empty
        }

        init {
            // Memuat dari Cache saat aplikasi pertama kali dibuka (Tanpa Internet)
            val cachedAvatar = sharedPrefs.getString("avatarUrl", "https://avatars.githubusercontent.com/u/171119166?v=4") ?: ""
            val cachedName = sharedPrefs.getString("name", "Wahid") ?: "Wahid"
            val cachedProfile = sharedPrefs.getString("profileUrl", "https://github.com/SayidGithub") ?: ""
            
            currentLeadDeveloper = TeamMember(
                avatarUrl = cachedAvatar,
                name = cachedName,
                positionResId = R.string.about_position_lead_dev,
                profileUrl = cachedProfile,
                links = loadCachedLinks()
            )
            currentPrimaryLinks = loadCachedLinks()

            // Segera render tampilan menggunakan data cache
            updateState()
            
            // Lakukan sinkronisasi data profil secara diam-diam (Silent Sync)
            fetchPanelData(isManualRefresh = false)
        }

        // =======================================================
        // MENGAMBIL DATA AVATAR & PROFIL DARI PANEL PTERODACTYL
        // =======================================================
        fun fetchPanelData(isManualRefresh: Boolean = false) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val client = HttpClient()
                    // URL endpoint yang sesuai dengan backend panel Python Anda
                    val response = client.get("http://de3.bot-hosting.net:21013/api/archivetune/about?t=${System.currentTimeMillis()}").bodyAsText()
                    val json = JSONObject(response)

                    if (json.has("leadDeveloper")) {
                        val ldJson = json.getJSONObject("leadDeveloper")
                        val linksArray = ldJson.optJSONArray("links")
                        
                        val parsedLinks = mutableListOf<AboutLinkUiModel>()
                        val rawLinks = mutableListOf<String>()
                        
                        if (linksArray != null) {
                            for (i in 0 until linksArray.length()) {
                                val url = linksArray.getString(i)
                                parsedLinks.add(createLinkModel(url))
                                rawLinks.add(url)
                            }
                        }
                        
                        // Timpa data cache dengan yang terbaru dari panel
                        val newAvatar = ldJson.optString("avatarUrl", currentLeadDeveloper.avatarUrl)
                        val newName = ldJson.optString("name", currentLeadDeveloper.name)
                        val newProfile = ldJson.optString("profileUrl", currentLeadDeveloper.profileUrl ?: "")

                        sharedPrefs.edit()
                            .putString("avatarUrl", newAvatar)
                            .putString("name", newName)
                            .putString("profileUrl", newProfile)
                            .putString("links", JSONArray(rawLinks).toString())
                            .apply()
                        
                        val newLinksCollection = if (parsedLinks.isNotEmpty()) AboutLinkCollection.of(*parsedLinks.toTypedArray()) else currentLeadDeveloper.links

                        // Terapkan ke state agar UI langsung berubah
                        currentLeadDeveloper = TeamMember(
                            avatarUrl = newAvatar,
                            name = newName,
                            positionResId = R.string.about_position_lead_dev,
                            profileUrl = newProfile,
                            links = newLinksCollection
                        )
                        currentPrimaryLinks = newLinksCollection
                        
                        // Memicu rendering ulang layar
                        updateState()
                    }

                    if (isManualRefresh) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Profil berhasil diperbarui dari server!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    if (isManualRefresh) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Gagal menyegarkan profil. Periksa koneksi internet Anda.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        fun retryContributors() {
            // Sengaja dikosongkan karena fitur Github Contributors bawaan kita matikan
        }

        fun openTranslationContributors() {
            activeDialog = AboutDialog.TRANSLATION_CONTRIBUTORS
            updateState()
        }

        fun openDependencyLicenses() {
            activeDialog = AboutDialog.DEPENDENCY_LICENSES
            updateState()
        }

        fun dismissDialog() {
            activeDialog = AboutDialog.NONE
            updateState()
        }

        fun retryTranslationContributors() {
             // Sengaja dikosongkan karena fitur translation bawaan kita matikan
        }

        fun retryDependencyLicenses() {
             // Sengaja dikosongkan karena fitur license bawaan kita matikan
        }

        fun openUri(uri: String) {
            if (uri.isBlank()) return
            _effects.tryEmit(AboutScreenEffect.OpenUri(uri))
        }

        private fun updateState() {
            _state.value = AboutScreenState.Success(buildUiModel())
        }

        private fun buildUiModel(): AboutUiModel =
            AboutUiModel(
                appNameResId = R.string.app_name,
                versionName = BuildConfig.VERSION_NAME,
                buildHash = currentBuildHash,
                buildVariant = if (BuildConfig.DEBUG) DebugBuildBadge else BuildConfig.ARCHITECTURE.uppercase(),
                
                // Menerapkan data dari cache / panel
                primaryLinks = currentPrimaryLinks,
                leadDeveloper = currentLeadDeveloper,
                
                // Menghilangkan menu tidak penting dari UI
                collaborators = TeamMemberCollection.of(),
                respecters = TeamMemberCollection.of(),
                contributorsState = AboutContributorsUiState.Empty,
                contributorsReadMoreUrl = "https://github.com/SayidGithub/ArchiveTune/graphs/contributors",
                activeDialog = activeDialog,
                translationContributorsState = AboutTranslationContributorsUiState.Empty,
                dependencyLicensesState = AboutDependencyLicensesUiState.Empty,
            )

        private companion object {
            const val DebugBuildBadge = "DEBUG"
        }
    }
