package app.pwhs.dunebox.ui.screen

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.dunebox.sdk.DuneBox
import app.pwhs.dunebox.sdk.model.VirtualAppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddApp: () -> Unit,
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<VirtualAppInfo>>(emptyList()) }

    // Load installed virtual apps
    LaunchedEffect(Unit) {
        try {
            installedApps = DuneBox.getInstalledApps()
        } catch (_: Exception) {
            // SDK might not be initialized in preview
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DuneBox") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddApp,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add app") },
                text = { Text("Clone App") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    ) { padding ->
        if (installedApps.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🏜️",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Text(
                        text = "No cloned apps yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tap the button below to clone your first app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // App grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(installedApps) { appInfo ->
                    VirtualAppItem(
                        appInfo = appInfo,
                        onClick = {
                            DuneBox.launchApp(appInfo.packageName)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualAppItem(
    appInfo: VirtualAppInfo,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        // App icon
        val icon = appInfo.icon
        if (icon != null) {
            Image(
                bitmap = icon.toBitmap(48, 48).asImageBitmap(),
                contentDescription = appInfo.appName,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = appInfo.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = appInfo.appName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
