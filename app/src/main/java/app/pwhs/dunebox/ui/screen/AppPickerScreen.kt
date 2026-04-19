package app.pwhs.dunebox.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.dunebox.sdk.DuneBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    onAppSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var installingPackage by remember { mutableStateOf<String?>(null) }

    // Load installed apps
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    // Filter out system apps and DuneBox itself
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                            appInfo.packageName != context.packageName
                }
                .map { appInfo ->
                    InstalledApp(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null },
                    )
                }
                .sortedBy { it.appName.lowercase() }
            apps = installed
            isLoading = false
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select App to Clone") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                singleLine = true,
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppListItem(
                            app = app,
                            isInstalling = installingPackage == app.packageName,
                            onClick = {
                                installingPackage = app.packageName
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        DuneBox.installPackage(app.packageName)
                                    }
                                    installingPackage = null
                                    if (result.isSuccess) {
                                        onAppSelected(app.packageName)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: InstalledApp,
    isInstalling: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(app.appName) },
        supportingContent = {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            val icon = app.icon
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap(40, 40).asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(app.appName.take(1).uppercase())
                    }
                }
            }
        },
        trailingContent = {
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        modifier = Modifier.clickable(enabled = !isInstalling, onClick = onClick)
    )
}
