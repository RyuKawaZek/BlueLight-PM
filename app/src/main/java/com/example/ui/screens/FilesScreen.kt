package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.server.OperationProgress
import com.example.server.ServerFileItem
import com.example.server.ServerWorkspaceInfo
import com.example.ui.components.MetroChipShelf
import com.example.ui.components.MetroGroupedRow
import com.example.ui.components.MetroOperationBanner
import com.example.ui.components.MetroPillChip
import com.example.ui.components.MetroStoragePermissionCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.TextEditorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesScreen(
    workspaceInfo: ServerWorkspaceInfo,
    currentRelativePath: String,
    files: List<ServerFileItem>,
    operationProgress: OperationProgress,
    textEditorState: TextEditorState,
    onRequestStorageAccess: () -> Unit,
    onBrowseDirectory: (String) -> Unit,
    onNavigateUp: () -> Boolean,
    onRefresh: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onImportFileHere: () -> Unit,
    onOpenTextFile: (ServerFileItem) -> Unit,
    onExtractZip: (ServerFileItem) -> Unit,
    onExportFile: (ServerFileItem) -> Unit,
    onRenameEntry: (ServerFileItem, String) -> Unit,
    onDeleteEntry: (ServerFileItem) -> Unit,
    onUpdateEditorContent: (String) -> Unit,
    onSaveEditorFile: () -> Unit,
    onCloseEditor: () -> Unit,
    onDismissProgress: () -> Unit,
    onNavigateBackToDashboard: () -> Unit,
    bottomContentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (textEditorState is TextEditorState.Open) {
            onCloseEditor()
        } else if (!onNavigateUp()) {
            onNavigateBackToDashboard()
        }
    }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renamingItem by remember { mutableStateOf<ServerFileItem?>(null) }
    var deletingItem by remember { mutableStateOf<ServerFileItem?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!workspaceInfo.hasStorageAccess) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    MetroStoragePermissionCard(
                        workspacePath = workspaceInfo.workspacePath,
                        onRequestStorageAccess = onRequestStorageAccess
                    )
                }
            } else {
                // Path summary + Quick Actions ChipShelf (Metrolist style)
                val segments = remember(currentRelativePath) {
                    if (currentRelativePath.isEmpty()) emptyList()
                    else currentRelativePath.split('/')
                }

                MetroChipShelf {
                    if (currentRelativePath.isNotEmpty()) {
                        MetroPillChip(
                            label = "Up",
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            onClick = { onNavigateUp() },
                            modifier = Modifier.testTag("files_up_button")
                        )
                    }
                    MetroPillChip(
                        label = "PocketMine",
                        icon = Icons.Rounded.FolderOpen,
                        selected = currentRelativePath.isEmpty(),
                        onClick = { onBrowseDirectory("") }
                    )
                    segments.forEachIndexed { idx, segment ->
                        val subPath = segments.take(idx + 1).joinToString("/")
                        MetroPillChip(
                            label = segment,
                            selected = idx == segments.lastIndex,
                            onClick = { onBrowseDirectory(subPath) }
                        )
                    }
                }

                // Action Pill Shelf
                MetroChipShelf {
                    MetroPillChip(
                        label = "Import Here",
                        icon = Icons.Rounded.FileUpload,
                        selected = true,
                        onClick = onImportFileHere,
                        modifier = Modifier.testTag("files_import_button")
                    )
                    MetroPillChip(
                        label = "New Folder",
                        icon = Icons.Rounded.CreateNewFolder,
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.testTag("files_new_folder_button")
                    )
                    MetroPillChip(
                        label = "New File",
                        icon = Icons.Rounded.NoteAdd,
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.testTag("files_new_file_button")
                    )
                    MetroPillChip(
                        label = "Refresh",
                        icon = Icons.Rounded.Refresh,
                        onClick = onRefresh,
                        modifier = Modifier.testTag("files_refresh_button")
                    )
                }

                Text(
                    text = workspaceInfo.workspacePath +
                        if (currentRelativePath.isEmpty()) "" else "/$currentRelativePath",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                AnimatedVisibility(visible = operationProgress !is OperationProgress.Idle) {
                    MetroOperationBanner(
                        progress = operationProgress,
                        onDismiss = onDismissProgress,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (files.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "This folder is empty",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Use Import Here to add .phar plugins, server files, or .zip archives, or create a new folder/file.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("files_list"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = bottomContentPadding),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(
                            items = files,
                            key = { _, item -> item.absolutePath }
                        ) { index, item ->
                            SegmentedFileRow(
                                index = index,
                                count = files.size,
                                item = item,
                                onOpen = {
                                    if (item.isDirectory) {
                                        onBrowseDirectory(item.relativePath)
                                    } else if (item.isEditableText) {
                                        onOpenTextFile(item)
                                    } else if (item.isZipArchive) {
                                        onExtractZip(item)
                                    }
                                },
                                onEditText = { onOpenTextFile(item) },
                                onExtractZip = { onExtractZip(item) },
                                onExport = { onExportFile(item) },
                                onRename = { renamingItem = item },
                                onDelete = { deletingItem = item }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        SingleNameDialog(
            title = "Create Folder",
            label = "Folder name (e.g. plugins, worlds)",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                onCreateFolder(name)
            }
        )
    }

    if (showNewFileDialog) {
        SingleNameDialog(
            title = "Create File",
            label = "File name (e.g. ops.txt, server.properties)",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name ->
                showNewFileDialog = false
                onCreateFile(name)
            }
        )
    }

    renamingItem?.let { item ->
        SingleNameDialog(
            title = "Rename",
            label = "New name",
            initialValue = item.name,
            confirmLabel = "Rename",
            onDismiss = { renamingItem = null },
            onConfirm = { newName ->
                renamingItem = null
                onRenameEntry(item, newName)
            }
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete '${item.name}'?") },
            text = {
                Text(
                    if (item.isDirectory) {
                        "This will permanently delete the folder and all files inside it from ${workspaceInfo.workspacePath}."
                    } else {
                        "This will permanently delete '${item.name}' from ${workspaceInfo.workspacePath}."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingItem = null
                        onDeleteEntry(item)
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
                TextButton(onClick = { deletingItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (textEditorState is TextEditorState.Open) {
        TextFileEditorDialog(
            state = textEditorState,
            onContentChange = onUpdateEditorContent,
            onSave = onSaveEditorFile,
            onDismiss = onCloseEditor
        )
    }
}

@Composable
private fun SegmentedFileRow(
    index: Int,
    count: Int,
    item: ServerFileItem,
    onOpen: () -> Unit,
    onEditText: () -> Unit,
    onExtractZip: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val icon = when {
        item.isDirectory -> Icons.Rounded.Folder
        item.isZipArchive -> Icons.Rounded.Archive
        item.isEditableText -> Icons.Rounded.Description
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
    val iconTint = when {
        item.isDirectory -> MaterialTheme.colorScheme.onPrimaryContainer
        item.isZipArchive -> MaterialTheme.colorScheme.onTertiaryContainer
        item.isEditableText -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconBg = when {
        item.isDirectory -> MaterialTheme.colorScheme.primaryContainer
        item.isZipArchive -> MaterialTheme.colorScheme.tertiaryContainer
        item.isEditableText -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val subtitle = remember(item) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(item.lastModifiedMs))
        if (item.isDirectory) {
            "${item.childCount} items • $dateStr"
        } else {
            "${formatFileSize(item.sizeBytes)} • $dateStr"
        }
    }

    MetroGroupedRow(
        index = index,
        count = count,
        title = item.name,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        iconContainerColor = iconBg,
        onClick = onOpen,
        trailingContent = {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "File options for ${item.name}"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (item.isEditableText) {
                        DropdownMenuItem(
                            text = { Text("Edit Text") },
                            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditText()
                            }
                        )
                    }
                    if (item.isZipArchive) {
                        DropdownMenuItem(
                            text = { Text("Extract Here") },
                            leadingIcon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExtractZip()
                            }
                        )
                    }
                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Export / Save Copy") },
                            leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExport()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun SingleNameDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmLabel)
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
private fun TextFileEditorDialog(
    state: TextEditorState.Open,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.file.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.file.relativePath,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = onSave,
                            enabled = !state.isSaving,
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("editor_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.isSaving) "Saving..." else "Save")
                        }
                    }
                }

                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = state.content,
                    onValueChange = onContentChange,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("editor_text_field")
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
