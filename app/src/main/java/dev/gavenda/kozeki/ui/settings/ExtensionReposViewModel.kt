package dev.gavenda.kozeki.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.comic.ComicExtension
import dev.gavenda.kozeki.data.comic.ComicSources
import dev.gavenda.kozeki.data.comic.ExtensionLoader
import dev.gavenda.kozeki.data.comic.repo.AddRepoResult
import dev.gavenda.kozeki.data.comic.repo.AvailableExtension
import dev.gavenda.kozeki.data.comic.repo.ExtensionInstaller
import dev.gavenda.kozeki.data.comic.repo.ExtensionRepos
import dev.gavenda.kozeki.data.comic.repo.InstallStep
import dev.gavenda.kozeki.data.comic.repo.RepoState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An extension a repository offers, with whether it is installed and how its install is going. */
data class RepoExtension(
    val extension: AvailableExtension,
    /** The key of the repository it came from, which its APK has to be signed with. */
    val signingKey: String,
    val installed: ComicExtension?,
    val step: InstallStep?,
) {
    val hasUpdate: Boolean get() = installed != null && installed.versionCode < extension.versionCode
}

/** Where adding a repository has got to. */
sealed interface AddRepoStatus {
    data object Idle : AddRepoStatus
    data object Working : AddRepoStatus
    data object Done : AddRepoStatus
    data class Error(val result: AddRepoResult) : AddRepoStatus
}

data class ExtensionReposUiState(
    val repos: List<RepoState> = emptyList(),
    val updates: List<RepoExtension> = emptyList(),
    val installed: List<RepoExtension> = emptyList(),
    val available: List<RepoExtension> = emptyList(),
    val query: String = "",
    val addStatus: AddRepoStatus = AddRepoStatus.Idle,
) {
    val loading: Boolean get() = repos.any { it is RepoState.Loading }
    val hasExtensions: Boolean get() = repos.any { it is RepoState.Loaded && it.store.extensions.isNotEmpty() }
}

private data class Filters(val query: String, val addStatus: AddRepoStatus)

class ExtensionReposViewModel(
    private val repos: ExtensionRepos,
    private val installer: ExtensionInstaller,
    private val comicSources: ComicSources,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val addStatus = MutableStateFlow<AddRepoStatus>(AddRepoStatus.Idle)

    val uiState: StateFlow<ExtensionReposUiState> = combine(
        repos.repos,
        comicSources.extensions,
        installer.steps,
        combine(query, addStatus, ::Filters),
    ) { repoStates, installedList, steps, filters ->
        val installed = installedList.orEmpty().associateBy { it.packageName }
        // A package offered by two repositories is taken from the first one added.
        val all = repoStates.filterIsInstance<RepoState.Loaded>()
            .flatMap { repo -> repo.store.extensions.map { it to repo.store.signingKey } }
            .filter { (ext, _) -> ext.libVersion == ExtensionLoader.SUPPORTED_LIB_VERSION }
            .distinctBy { (ext, _) -> ext.packageName }
            .map { (ext, key) -> RepoExtension(ext, key, installed[ext.packageName], steps[ext.packageName]) }
            .filter { it.matches(filters.query) }
            .sortedBy { it.extension.name.lowercase() }
        val (onDevice, notOnDevice) = all.partition { it.installed != null }
        val (updates, upToDate) = onDevice.partition { it.hasUpdate }
        ExtensionReposUiState(
            repos = repoStates,
            updates = updates,
            installed = upToDate,
            available = notOnDevice,
            query = filters.query,
            addStatus = filters.addStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExtensionReposUiState())

    init {
        repos.refresh()
        viewModelScope.launch { comicSources.ensureLoaded() }
    }

    fun refresh() = repos.refresh(force = true)

    fun setQuery(text: String) {
        query.value = text
    }

    fun addRepo(url: String) {
        if (addStatus.value == AddRepoStatus.Working) return
        addStatus.value = AddRepoStatus.Working
        viewModelScope.launch {
            addStatus.value = when (val result = repos.add(url)) {
                AddRepoResult.Added -> AddRepoStatus.Done
                else -> AddRepoStatus.Error(result)
            }
        }
    }

    /** The add dialog closed, or its error was read. */
    fun resetAdd() {
        addStatus.value = AddRepoStatus.Idle
    }

    fun removeRepo(url: String) = repos.remove(url)

    fun install(item: RepoExtension) = installer.install(item.extension, item.signingKey)

    fun dismissFailure(item: RepoExtension) = installer.dismiss(item.extension.packageName)

    private fun RepoExtension.matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return extension.name.contains(q, ignoreCase = true) ||
            extension.sourceNames.any { it.contains(q, ignoreCase = true) }
    }
}
