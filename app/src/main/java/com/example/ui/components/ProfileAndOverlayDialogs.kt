package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.server.OverlayMacro
import com.example.server.ServerProfile
import com.example.server.ServerStatus
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ProfileManagerDialog(
    profiles: List<ServerProfile>,
    activeProfile: ServerProfile,
    profileCoreLabels: Map<String, String>,
    serverStatus: ServerStatus,
    detectCoreLabel: (ServerProfile) -> String,
    onSwitchProfile: (String) -> Unit,
    onCreateProfile: (name: String, copyProperties: Boolean) -> Unit,
    onRenameProfile: (profileId: String, newName: String) -> Unit,
    onDeleteProfile: (profileId: String, deleteFiles: Boolean) -> Unit,
    onRescanProfiles: () -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var deletingProfile by remember { mutableStateOf<ServerProfile?>(null) }

    val canSwitch = serverStatus == ServerStatus.STOPPED

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_manager_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Server Profiles",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (canSwitch) {
                                "Each profile keeps its own core, worlds & config"
                            } else {
                                "Stop the running server to switch profiles"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canSwitch) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    IconButton(
                        onClick = onRescanProfiles,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rescan_profiles_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Rescan storage for server profiles"
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(profiles, key = { _, p -> p.id }) { index, profile ->
                        val isActive = profile.id == activeProfile.id
                        val coreLabel = profileCoreLabels[profile.id] ?: detectCoreLabel(profile)
                        val pathLabel = formatProfilePathLabel(profile)
                        val dirSubtitle = "$pathLabel • $coreLabel"

                        MetroGroupedRow(
                            index = index,
                            count = profiles.size,
                            title = profile.name,
                            subtitle = dirSubtitle,
                            icon = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.Dns,
                            iconTint = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            enabled = canSwitch || isActive,
                            onClick = if (canSwitch && !isActive) {
                                {
                                    onSwitchProfile(profile.id)
                                    onDismiss()
                                }
                            } else {
                                null
                            },
                            trailingContent = {
                                if (!profile.isDefault) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { renamingProfile = profile },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DriveFileRenameOutline,
                                                contentDescription = "Rename ${profile.name}",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { deletingProfile = profile },
                                            enabled = !(isActive && !canSwitch),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete ${profile.name}",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("profile_row_${profile.id}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        shape = CircleShape,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("create_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("New Profile")
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var profileName by remember { mutableStateOf("") }
        var copyProps by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Server Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        singleLine = true,
                        label = { Text("Profile Name (e.g. Survival, Creative)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_profile_name_input")
                    )
                    val previewSlug = ServerProfile.sanitizeSlug(profileName.ifBlank { "survival" })
                    Text(
                        text = "Folder: /PocketMine-Profiles/$previewSlug",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = copyProps,
                            onCheckedChange = { copyProps = it }
                        )
                        Text(
                            text = "Copy current server.properties",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateProfile(profileName, copyProps)
                        showCreateDialog = false
                        onDismiss()
                    },
                    enabled = profileName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_profile_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    renamingProfile?.let { target ->
        var editedName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renamingProfile = null },
            title = { Text("Rename Profile") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameProfile(target.id, editedName)
                        renamingProfile = null
                    },
                    enabled = editedName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingProfile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    deletingProfile?.let { target ->
        var alsoDeleteFiles by remember(target.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text("Delete '${target.name}'?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove this profile from BlueLight's switcher.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = alsoDeleteFiles,
                            onCheckedChange = { alsoDeleteFiles = it }
                        )
                        Text(
                            text = "Also delete ${formatProfilePathLabel(target)} files",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProfile(target.id, alsoDeleteFiles)
                        deletingProfile = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProfile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun formatProfilePathLabel(profile: ServerProfile): String {
    return when {
        profile.isDefault -> "/PocketMine"
        profile.customPath.isNotEmpty() -> {
            val marker = "/storage/emulated/0"
            if (profile.customPath.startsWith(marker)) {
                profile.customPath.removePrefix(marker).ifEmpty { "/" }
            } else {
                profile.customPath
            }
        }
        else -> "/PocketMine-Profiles/${profile.directoryName}"
    }
}

@Composable
fun OverlayMacroEditDialog(
    initialMacro: OverlayMacro? = null,
    onDismiss: () -> Unit,
    onSave: (label: String, command: String) -> Unit
) {
    var label by remember(initialMacro?.id) { mutableStateOf(initialMacro?.label.orEmpty()) }
    var command by remember(initialMacro?.id) { mutableStateOf(initialMacro?.command.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialMacro == null) "Add 1-Tap Macro" else "Edit 1-Tap Macro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Button Label (optional, e.g. Day)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("macro_label_input")
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    singleLine = true,
                    label = { Text("Command (e.g. time set day)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("macro_command_input")
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Tapped macros run directly in the console & floating overlay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, command) },
                enabled = command.trim().removePrefix("/").isNotEmpty(),
                modifier = Modifier.testTag("save_macro_button")
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
