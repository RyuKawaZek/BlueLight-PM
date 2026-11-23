package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.compose.material.icons.rounded.Layers
import com.example.server.OperationProgress
import com.example.server.ServerProfile
import com.example.server.ServerPropertiesConfig
import com.example.server.ServerStatus
import com.example.server.ServerWorkspaceInfo
import com.example.ui.components.MetroChipShelf
import com.example.ui.components.MetroExpressiveSwitch
import com.example.ui.components.MetroGroupedRow
import com.example.ui.components.MetroGroupedSection
import com.example.ui.components.MetroOperationBanner
import com.example.ui.components.MetroPillChip
import com.example.ui.components.MetroStoragePermissionCard
import com.example.ui.components.ProfileManagerDialog
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.Rose400

@Composable
fun DashboardScreen(
    serverStatus: ServerStatus,
    workspaceInfo: ServerWorkspaceInfo,
    serverProperties: ServerPropertiesConfig,
    operationProgress: OperationProgress,
    profiles: List<ServerProfile>,
    activeProfile: ServerProfile,
    profileCoreLabels: Map<String, String>,
    detectCoreLabel: (ServerProfile) -> String,
    isOverlayActive: Boolean,
    canDrawOverlays: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onForceKillServer: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenFiles: () -> Unit,
    onRequestStorageAccess: () -> Unit,
    onImportCore: () -> Unit,
    onDownloadBlueLight: (String?) -> Unit,
    onRefreshWorkspace: () -> Unit,
    onSaveProperties: (ServerPropertiesConfig) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onCreateProfile: (String, Boolean) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onDeleteProfile: (String, Boolean) -> Unit,
    onRescanProfiles: () -> Unit,
    onDismissProgress: () -> Unit,
    bottomContentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    var showCustomDownloadDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showProfilesDialog by remember { mutableStateOf(false) }

    val isBusy = serverStatus == ServerStatus.RUNNING ||
        serverStatus == ServerStatus.STARTING ||
        operationProgress is OperationProgress.Running

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Metrolist-style horizontal quick-action chip shelf
            item {
                MetroChipShelf {
                    MetroPillChip(
                        label = stringResource(R.string.action_download_bluelight),
                        icon = Icons.Rounded.CloudDownload,
                        onClick = { onDownloadBlueLight(null) },
                        enabled = !isBusy,
                        selected = true,
                        modifier = Modifier.testTag("download_core_button")
                    )
                    MetroPillChip(
                        label = stringResource(R.string.action_import_core),
                        icon = Icons.Rounded.FolderOpen,
                        onClick = onImportCore,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("import_core_button")
                    )
                    MetroPillChip(
                        label = "Custom URL",
                        icon = Icons.Rounded.Link,
                        onClick = { showCustomDownloadDialog = true },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("custom_url_download_button")
                    )
                    MetroPillChip(
                        label = stringResource(R.string.action_manage_files),
                        icon = Icons.Rounded.Storage,
                        onClick = onOpenFiles,
                        modifier = Modifier.testTag("manage_files_button")
                    )
                    MetroPillChip(
                        label = "Config",
                        icon = Icons.Rounded.Tune,
                        onClick = { showPropertiesDialog = true }
                    )
                    MetroPillChip(
                        label = "Profiles (${profiles.size})",
                        icon = Icons.Rounded.Layers,
                        onClick = { showProfilesDialog = true },
                        modifier = Modifier.testTag("profiles_chip_button")
                    )
                }
            }

            // Hero Server Control Card
            item {
                ServerHeroCard(
                    serverStatus = serverStatus,
                    workspaceInfo = workspaceInfo,
                    activeProfileName = activeProfile.name,
                    port = serverProperties.port,
                    onStartServer = onStartServer,
                    onStopServer = onStopServer,
                    onForceKillServer = onForceKillServer,
                    onOpenConsole = onOpenConsole,
                    onOpenProfiles = { showProfilesDialog = true },
                    onRefreshWorkspace = onRefreshWorkspace,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            if (!workspaceInfo.hasStorageAccess) {
                item {
                    MetroStoragePermissionCard(
                        workspacePath = workspaceInfo.workspacePath,
                        onRequestStorageAccess = onRequestStorageAccess,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            item {
                AnimatedVisibility(visible = operationProgress !is OperationProgress.Idle) {
                    MetroOperationBanner(
                        progress = operationProgress,
                        onDismiss = onDismissProgress,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // Grouped Section 1: Server Runtime & Floating Overlay
            item {
                MetroGroupedSection(
                    title = "Runtime & Overlay",
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    MetroGroupedRow(
                        index = 0,
                        count = 5,
                        title = "Server Profile",
                        subtitle = "${activeProfile.name} • ${profiles.size} profile(s)",
                        icon = Icons.Rounded.Layers,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { showProfilesDialog = true },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("server_profile_row")
                    )
                    MetroGroupedRow(
                        index = 1,
                        count = 5,
                        title = "Floating Console Bubble",
                        subtitle = when {
                            !canDrawOverlays -> "Grant Display over other apps to float over Minecraft"
                            isOverlayActive -> "Active on screen • Drag circle or tap to open mini console"
                            else -> "Show draggable console circle over Minecraft & other apps"
                        },
                        icon = Icons.Rounded.PictureInPictureAlt,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checked = isOverlayActive,
                        onClick = { onToggleOverlay(!isOverlayActive) },
                        trailingContent = {
                            MetroExpressiveSwitch(
                                checked = isOverlayActive,
                                onCheckedChange = null,
                                modifier = Modifier.testTag("floating_overlay_switch")
                            )
                        }
                    )
                    MetroGroupedRow(
                        index = 2,
                        count = 5,
                        title = "Detected Core",
                        subtitle = workspaceInfo.detectedCoreLabel,
                        icon = Icons.Rounded.Code,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = if (!isBusy) onImportCore else null
                    )
                    MetroGroupedRow(
                        index = 3,
                        count = 5,
                        title = "PHP Runtime Engine",
                        subtitle = if (workspaceInfo.phpBinaryAvailable) {
                            "Ready (arm64-v8a static binary)"
                        } else {
                            "Missing (${workspaceInfo.deviceAbis})"
                        },
                        icon = Icons.Rounded.Memory,
                        iconTint = if (workspaceInfo.phpBinaryAvailable) Emerald400 else Rose400
                    )
                    MetroGroupedRow(
                        index = 4,
                        count = 5,
                        title = "Shared Workspace Folder",
                        subtitle = workspaceInfo.workspacePath,
                        icon = Icons.Rounded.FolderOpen,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenFiles,
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            // Grouped Section 2: server.properties
            item {
                MetroGroupedSection(
                    title = "server.properties",
                    description = "Tap any row to edit server configuration",
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    MetroGroupedRow(
                        index = 0,
                        count = 3,
                        title = "Server MOTD",
                        subtitle = serverProperties.motd,
                        icon = Icons.Rounded.Dns,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { showPropertiesDialog = true },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit server properties",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag("edit_properties_button")
                            )
                        }
                    )
                    MetroGroupedRow(
                        index = 1,
                        count = 3,
                        title = "Port & Player Slots",
                        subtitle = "UDP :${serverProperties.port} • Max ${serverProperties.maxPlayers} players • Gamemode ${serverProperties.gamemode}",
                        icon = Icons.Rounded.SettingsEthernet,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { showPropertiesDialog = true }
                    )
                    MetroGroupedRow(
                        index = 2,
                        count = 3,
                        title = "Xbox / Online Authentication",
                        subtitle = if (serverProperties.onlineMode) {
                            "Enabled (online-mode=on)"
                        } else {
                            "Disabled for LAN / offline play (online-mode=off)"
                        },
                        icon = Icons.Rounded.Public,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        checked = serverProperties.onlineMode,
                        onClick = {
                            onSaveProperties(serverProperties.copy(onlineMode = !serverProperties.onlineMode))
                        },
                        trailingContent = {
                            MetroExpressiveSwitch(
                                checked = serverProperties.onlineMode,
                                onCheckedChange = null
                            )
                        }
                    )
                }
            }
        }
    }

    if (showCustomDownloadDialog) {
        CustomDownloadUrlDialog(
            onDismiss = { showCustomDownloadDialog = false },
            onConfirm = { url ->
                showCustomDownloadDialog = false
                onDownloadBlueLight(url)
            }
        )
    }

    if (showPropertiesDialog) {
        ServerPropertiesDialog(
            initial = serverProperties,
            onDismiss = { showPropertiesDialog = false },
            onSave = { updated ->
                showPropertiesDialog = false
                onSaveProperties(updated)
            }
        )
    }

    if (showProfilesDialog) {
        ProfileManagerDialog(
            profiles = profiles,
            activeProfile = activeProfile,
            profileCoreLabels = profileCoreLabels,
            serverStatus = serverStatus,
            detectCoreLabel = detectCoreLabel,
            onSwitchProfile = onSwitchProfile,
            onCreateProfile = onCreateProfile,
            onRenameProfile = onRenameProfile,
            onDeleteProfile = onDeleteProfile,
            onRescanProfiles = onRescanProfiles,
            onDismiss = { showProfilesDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerHeroCard(
    serverStatus: ServerStatus,
    workspaceInfo: ServerWorkspaceInfo,
    activeProfileName: String,
    port: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onForceKillServer: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenProfiles: () -> Unit,
    onRefreshWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (serverStatus) {
        ServerStatus.RUNNING -> Emerald400
        ServerStatus.STARTING, ServerStatus.STOPPING -> Amber400
        ServerStatus.STOPPED -> Rose400
    }
    val statusText = when (serverStatus) {
        ServerStatus.RUNNING -> stringResource(R.string.server_status_running)
        ServerStatus.STOPPED -> stringResource(R.string.server_status_stopped)
        ServerStatus.STARTING -> stringResource(R.string.server_status_starting)
        ServerStatus.STOPPING -> stringResource(R.string.server_status_stopping)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = "$statusText • :$port",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Text(
                            text = activeProfileName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = workspaceInfo.detectedCoreLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        TextButton(
                            onClick = onOpenProfiles,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .testTag("hero_switch_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Switch Profile", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    IconButton(
                        onClick = onRefreshWorkspace,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                            .testTag("refresh_workspace_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh server workspace",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartServer,
                        enabled = serverStatus == ServerStatus.STOPPED,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("start_server_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.action_start_server), style = MaterialTheme.typography.labelLarge)
                    }

                    FilledTonalButton(
                        onClick = onStopServer,
                        enabled = serverStatus == ServerStatus.RUNNING,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("stop_server_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.action_stop_server), style = MaterialTheme.typography.labelLarge)
                    }

                    if (serverStatus != ServerStatus.STOPPED) {
                        OutlinedButton(
                            onClick = onForceKillServer,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("kill_server_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dangerous,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.action_kill_server), style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenConsole,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("open_console_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.action_open_console), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomDownloadUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember {
        mutableStateOf("https://github.com/BlueLightJapan/BlueLight/archive/refs/heads/master.zip")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Server Archive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a direct .phar or .zip URL:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Archive URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url) },
                enabled = url.startsWith("http://") || url.startsWith("https://")
            ) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ServerPropertiesDialog(
    initial: ServerPropertiesConfig,
    onDismiss: () -> Unit,
    onSave: (ServerPropertiesConfig) -> Unit
) {
    var motd by remember { mutableStateOf(initial.motd) }
    var port by remember { mutableStateOf(initial.port) }
    var maxPlayers by remember { mutableStateOf(initial.maxPlayers) }
    var gamemode by remember { mutableStateOf(initial.gamemode) }
    var onlineMode by remember { mutableStateOf(initial.onlineMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit server.properties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = motd,
                    onValueChange = { motd = it },
                    label = { Text("Server MOTD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxPlayers,
                        onValueChange = { maxPlayers = it.filter { c -> c.isDigit() } },
                        label = { Text("Max Players") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = gamemode,
                    onValueChange = { gamemode = it.filter { c -> c.isDigit() } },
                    label = { Text("Default Gamemode (0=Survival, 1=Creative)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Online / Xbox Auth", style = MaterialTheme.typography.bodyMedium)
                    MetroExpressiveSwitch(
                        checked = onlineMode,
                        onCheckedChange = { onlineMode = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ServerPropertiesConfig(
                            motd = motd,
                            port = port.ifBlank { "19132" },
                            maxPlayers = maxPlayers.ifBlank { "20" },
                            gamemode = gamemode.ifBlank { "0" },
                            onlineMode = onlineMode
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
