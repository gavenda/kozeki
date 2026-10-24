package dev.gavenda.kozeki.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.ContentWarning
import dev.gavenda.kozeki.data.comic.repo.AddRepoResult
import dev.gavenda.kozeki.data.comic.repo.InstallError
import dev.gavenda.kozeki.data.comic.repo.InstallStep
import dev.gavenda.kozeki.data.comic.repo.RepoError
import dev.gavenda.kozeki.data.comic.repo.RepoState
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.OptionColor
import dev.gavenda.kozeki.ui.components.appBarIconButtonColors
import dev.gavenda.kozeki.ui.components.narrowIconButton
import dev.gavenda.kozeki.ui.components.wideIconButton
import dev.gavenda.kozeki.ui.extensionReposViewModel
import dev.gavenda.kozeki.ui.koinViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What can be done from the screen of extension repositories. */
data class ExtensionReposActions(
    val onRefresh: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onAddRepo: (String) -> Unit = {},
    val onResetAdd: () -> Unit = {},
    val onRemoveRepo: (String) -> Unit = {},
    val onInstall: (RepoExtension) -> Unit = {},
    val onUninstall: (RepoExtension) -> Unit = {},
)

/** The repositories of Mihon extensions, and the extensions they offer to install, a screen of its own. */
@Composable
fun ExtensionReposScreen(
    onBack: () -> Unit,
    addUrl: String? = null,
    viewModel: ExtensionReposViewModel = koinViewModel { extensionReposViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ExtensionReposContent(
        state = state,
        onBack = onBack,
        addUrl = addUrl,
        actions = remember(viewModel, context) {
            ExtensionReposActions(
                onRefresh = viewModel::refresh,
                onQuery = viewModel::setQuery,
                onAddRepo = viewModel::addRepo,
                onResetAdd = viewModel::resetAdd,
                onRemoveRepo = viewModel::removeRepo,
                onInstall = viewModel::install,
                // The system asks before it removes an app, so this asks nothing itself.
                onUninstall = { item ->
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", item.extension.packageName, null)),
                        )
                    } catch (_: ActivityNotFoundException) {
                    }
                },
            )
        },
    )
}

@Composable
fun ExtensionReposContent(
    state: ExtensionReposUiState,
    onBack: () -> Unit,
    actions: ExtensionReposActions,
    modifier: Modifier = Modifier,
    addUrl: String? = null,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // A link's repository is asked about once; after that the dialog opens empty.
    var linkUrl by rememberSaveable { mutableStateOf(addUrl) }
    var adding by rememberSaveable { mutableStateOf(addUrl != null) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.settings_extension_repos_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_48px),
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    if (state.repos.isNotEmpty()) {
                        IconButton(onClick = actions.onRefresh, enabled = !state.loading) {
                            Icon(
                                painterResource(R.drawable.refresh_48px),
                                contentDescription = stringResource(R.string.extension_repos_refresh),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = { adding = true },
                        shapes = IconButtonDefaults.shapes(),
                        colors = OptionColor.VIOLET.appBarIconButtonColors(),
                        modifier = Modifier.wideIconButton(),
                    ) {
                        Icon(
                            painterResource(R.drawable.add_48px),
                            contentDescription = stringResource(R.string.extension_repos_add),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.repos.isEmpty()) {
            Box(Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    expression = Expression.CURIOUS,
                    icon = painterResource(R.drawable.extension_48px),
                    title = stringResource(R.string.extension_repos_empty_title),
                    message = stringResource(R.string.extension_repos_empty_message),
                    action = {
                        Button(onClick = { adding = true }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.extension_repos_add))
                        }
                    },
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding(),
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    item(key = "repos") {
                        Section(title = stringResource(R.string.extension_repos_section)) {
                            state.repos.forEach { repo -> RepoRow(repo, onRemove = { actions.onRemoveRepo(repo.url) }) }
                        }
                    }
                    if (state.hasExtensions) {
                        item(key = "search") {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = actions.onQuery,
                                label = { Text(stringResource(R.string.extension_repos_search)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.search_48px), contentDescription = null, modifier = Modifier.size(24.dp))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                        extensionGroup(R.string.extension_repos_updates, state.updates, actions)
                        extensionGroup(R.string.extension_repos_installed, state.installed, actions)
                        extensionGroup(R.string.extension_repos_available, state.available, actions)
                        if (state.updates.isEmpty() && state.installed.isEmpty() && state.available.isEmpty()) {
                            item(key = "no-match") {
                                Text(
                                    stringResource(R.string.extension_repos_no_match, state.query.trim()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddRepoDialog(
            initialUrl = linkUrl.orEmpty(),
            status = state.addStatus,
            onAdd = actions.onAddRepo,
            onEdited = { if (state.addStatus is AddRepoStatus.Error) actions.onResetAdd() },
            onDismiss = {
                adding = false
                linkUrl = null
                actions.onResetAdd()
            },
        )
    }
}

/** A titled run of extensions, one lazy item each, as there can be a great many. */
private fun LazyListScope.extensionGroup(title: Int, items: List<RepoExtension>, actions: ExtensionReposActions) {
    if (items.isEmpty()) return
    item(key = "title-$title") {
        Box(Modifier.padding(top = 16.dp, bottom = 4.dp)) { SectionTitle(stringResource(title)) }
    }
    items(items, key = { "$title-${it.extension.packageName}" }) { item ->
        ExtensionRow(item, actions, Modifier.animateItem())
    }
}

@Composable
private fun RepoRow(repo: RepoState, onRemove: () -> Unit) {
    val host = remember(repo.url) { repo.url.toHttpUrlOrNull()?.host ?: repo.url }
    SettingRow(
        icon = painterResource(R.drawable.public_48px),
        title = (repo as? RepoState.Loaded)?.store?.name ?: host,
        subtitle = when (repo) {
            is RepoState.Loading -> stringResource(R.string.extension_repos_loading)
            is RepoState.Loaded -> repo.store.extensions.size.let {
                "$host · " + pluralStringResource(R.plurals.extension_repos_extension_count, it, it)
            }
            is RepoState.Failed -> repoErrorText(repo.error)
        },
        subtitleColor = if (repo is RepoState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        DeleteButton(stringResource(R.string.extension_repos_remove), onRemove)
    }
}

@Composable
private fun ExtensionRow(item: RepoExtension, actions: ExtensionReposActions, modifier: Modifier = Modifier) {
    val ext = item.extension
    val step = item.step
    val failed = step as? InstallStep.Failed
    SettingRow(
        leading = {
            AsyncImage(model = ext.iconUrl, contentDescription = null, modifier = Modifier.size(24.dp))
        },
        title = ext.name,
        subtitle = if (failed != null) {
            installErrorText(failed.error)
        } else {
            listOfNotNull(
                if (item.hasUpdate) "${item.installed?.versionName} → ${ext.versionName}" else ext.versionName,
                languageName(ext.language),
                stringResource(R.string.settings_comic_nsfw).takeIf { ext.contentWarning == ContentWarning.NSFW },
            ).joinToString(" · ")
        },
        subtitleColor = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        horizontalPadding = 8.dp,
        modifier = modifier,
    ) {
        when {
            step != null && failed == null -> {
                val working = stringResource(R.string.extension_repos_working)
                Box(Modifier.size(48.dp).semantics { contentDescription = working }, contentAlignment = Alignment.Center) {
                    HaloLoadingIndicator(Modifier.size(24.dp))
                }
            }
            failed != null -> FilledTonalButton(onClick = { actions.onInstall(item) }) {
                Text(stringResource(R.string.extension_repos_retry))
            }
            item.hasUpdate -> FilledTonalButton(onClick = { actions.onInstall(item) }) {
                Text(stringResource(R.string.extension_repos_update))
            }
            item.installed != null ->
                DeleteButton(stringResource(R.string.extension_repos_uninstall)) { actions.onUninstall(item) }
            else -> FilledTonalButton(onClick = { actions.onInstall(item) }) {
                Text(stringResource(R.string.extension_repos_install))
            }
        }
    }
}

/** Removing a repository or an extension: filled in the error colour, at the narrow width. */
@Composable
private fun DeleteButton(label: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = Modifier.narrowIconButton(),
    ) {
        Icon(painterResource(R.drawable.delete_48px), contentDescription = label, modifier = Modifier.size(24.dp))
    }
}

/** Asks for the URL of a repository's index, and stays open with the reason when it cannot be added. */
@Composable
private fun AddRepoDialog(
    initialUrl: String,
    status: AddRepoStatus,
    onAdd: (String) -> Unit,
    onEdited: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(status) { if (status == AddRepoStatus.Done) onDismiss() }
    val working = status == AddRepoStatus.Working
    val error = (status as? AddRepoStatus.Error)?.result

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extension_repos_add)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    onEdited()
                },
                label = { Text(stringResource(R.string.extension_repos_add_hint)) },
                supportingText = {
                    Text(if (error != null) addErrorText(error) else stringResource(R.string.extension_repos_add_supporting))
                },
                isError = error != null,
                enabled = !working,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank() && !working) onAdd(url) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            if (working) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { HaloLoadingIndicator(Modifier.size(24.dp)) }
            } else {
                Button(onClick = { onAdd(url) }, enabled = url.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.extension_repos_add_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

@Composable
private fun repoErrorText(error: RepoError): String = when (error) {
    RepoError.Offline -> stringResource(R.string.extension_repos_error_offline)
    is RepoError.Refused -> stringResource(R.string.extension_repos_error_refused, error.code)
    RepoError.Unreadable -> stringResource(R.string.extension_repos_error_unreadable)
}

@Composable
private fun addErrorText(result: AddRepoResult): String = when (result) {
    AddRepoResult.InvalidUrl -> stringResource(R.string.extension_repos_error_invalid)
    AddRepoResult.AlreadyAdded -> stringResource(R.string.extension_repos_error_already)
    is AddRepoResult.Failed -> repoErrorText(result.error)
    AddRepoResult.Added -> ""
}

@Composable
private fun installErrorText(error: InstallError): String = when (error) {
    InstallError.Offline -> stringResource(R.string.extension_install_offline)
    is InstallError.Refused -> stringResource(R.string.extension_install_refused, error.code)
    InstallError.WrongSignature -> stringResource(R.string.extension_install_signature)
    InstallError.NotAnApk -> stringResource(R.string.extension_install_not_apk)
    is InstallError.System ->
        error.message?.let { stringResource(R.string.extension_install_system, it) }
            ?: stringResource(R.string.extension_install_system_unknown)
}
