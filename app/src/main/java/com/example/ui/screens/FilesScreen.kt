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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.example.viewmodel.FileClipboardMode
import com.example.viewmodel.FileClipboardState
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
    fileClipboard: FileClipboardState?,
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
    onDeleteEntries: (List<ServerFileItem>) -> Unit,
    onStageClipboard: (FileClipboardMode, List<ServerFileItem>) -> Unit,
    onPasteClipboardHere: () -> Unit,
    onClearClipboard: () -> Unit,
    onMoveEntriesTo: (List<ServerFileItem>, String) -> Unit,
    onCopyEntriesTo: (List<ServerFileItem>, String) -> Unit,
    onZipEntries: (List<ServerFileItem>, String) -> Unit,
    onListWorkspaceDirectories: () -> List<String>,
    onUpdateEditorContent: (String) -> Unit,
    onSaveEditorFile: () -> Unit,
    onCloseEditor: () -> Unit,
    onDismissProgress: () -> Unit,
    onNavigateBackToDashboard: () -> Unit,
    bottomContentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    var selectionModeExplicit by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(currentRelativePath) {
        selectedPaths = emptySet()
        selectionModeExplicit = false
    }

    LaunchedEffect(files) {
        val validPaths = files.mapTo(HashSet()) { it.absolutePath }
        val filtered = selectedPaths.filterTo(HashSet()) { it in validPaths }
        if (filtered.size != selectedPaths.size) {
            selectedPaths = filtered
        }
    }

    val selectionMode = selectionModeExplicit || selectedPaths.isNotEmpty()
    val selectedItems = remember(files, selectedPaths) {
        files.filter { it.absolutePath in selectedPaths }
    }

    fun clearSelection() {
        selectedPaths = emptySet()
        selectionModeExplicit = false
    }

    fun toggleSelection(item: ServerFileItem) {
        selectedPaths = if (item.absolutePath in selectedPaths) {
            selectedPaths - item.absolutePath
        } else {
            selectedPaths + item.absolutePath
        }
        if (selectedPaths.isEmpty()) {
            selectionModeExplicit = false
        }
    }

    BackHandler {
        when {
            textEditorState is TextEditorState.Open -> onCloseEditor()
            selectionMode -> clearSelection()
            !onNavigateUp() -> onNavigateBackToDashboard()
        }
    }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renamingItem by remember { mutableStateOf<ServerFileItem?>(null) }
    var deletingItem by remember { mutableStateOf<ServerFileItem?>(null) }
    var deletingBatchItems by remember { mutableStateOf<List<ServerFileItem>?>(null) }
    var zippingItems by remember { mutableStateOf<List<ServerFileItem>?>(null) }
    var folderPickerRequest by remember { mutableStateOf<Pair<FileClipboardMode, List<ServerFileItem>>?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!workspaceInfo.hasStorageAccess) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    MetroStoragePermissionCard(
                        workspacePath = workspaceInfo.workspacePath,
                        onRequestStorageAccess = onRequestStorageAccess
                    )
                }
            } else {
                // Path breadcrumbs chip shelf
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
                        label = "Import",
                        icon = Icons.Rounded.FileUpload,
                        selected = true,
                        onClick = onImportFileHere,
                        modifier = Modifier.testTag("files_import_button")
                    )
                    MetroPillChip(
                        label = if (selectionMode) "Cancel Select" else "Select",
                        icon = if (selectionMode) Icons.Rounded.Close else Icons.Rounded.CheckBox,
                        selected = selectionMode,
                        enabled = files.isNotEmpty() || selectionMode,
                        onClick = {
                            if (selectionMode) {
                                clearSelection()
                            } else {
                                selectionModeExplicit = true
                            }
                        },
                        modifier = Modifier.testTag("files_select_mode_button")
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
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Multi-Select Batch Action Bar
                AnimatedVisibility(visible = selectionMode) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .testTag("files_selection_bar")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedItems.size} selected",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val allSelected = files.isNotEmpty() && selectedItems.size == files.size
                                    MetroPillChip(
                                        label = if (allSelected) "None" else "All",
                                        icon = Icons.Rounded.DoneAll,
                                        selected = allSelected,
                                        onClick = {
                                            selectedPaths = if (allSelected) {
                                                emptySet()
                                            } else {
                                                files.mapTo(HashSet()) { it.absolutePath }
                                            }
                                        },
                                        modifier = Modifier.testTag("files_select_all_button")
                                    )
                                    MetroPillChip(
                                        label = "Clear",
                                        icon = Icons.Rounded.Close,
                                        onClick = { clearSelection() },
                                        modifier = Modifier.testTag("files_clear_selection_button")
                                    )
                                }
                            }

                            MetroChipShelf(contentPadding = PaddingValues(0.dp)) {
                                MetroPillChip(
                                    label = "Move",
                                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                                    enabled = selectedItems.isNotEmpty(),
                                    onClick = {
                                        onStageClipboard(FileClipboardMode.MOVE, selectedItems)
                                        clearSelection()
                                    },
                                    modifier = Modifier.testTag("files_batch_move_button")
                                )
                                MetroPillChip(
                                    label = "Copy",
                                    icon = Icons.Rounded.ContentCopy,
                                    enabled = selectedItems.isNotEmpty(),
                                    onClick = {
                                        onStageClipboard(FileClipboardMode.COPY, selectedItems)
                                        clearSelection()
                                    },
                                    modifier = Modifier.testTag("files_batch_copy_button")
                                )
                                MetroPillChip(
                                    label = "Zip",
                                    icon = Icons.Rounded.Archive,
                                    enabled = selectedItems.isNotEmpty(),
                                    onClick = { zippingItems = selectedItems },
                                    modifier = Modifier.testTag("files_batch_zip_button")
                                )
                                MetroPillChip(
                                    label = "Delete",
                                    icon = Icons.Rounded.DeleteOutline,
                                    enabled = selectedItems.isNotEmpty(),
                                    onClick = { deletingBatchItems = selectedItems },
                                    modifier = Modifier.testTag("files_batch_delete_button")
                                )
                            }
                        }
                    }
                }

                // Pending Move / Copy Clipboard Bar
                AnimatedVisibility(visible = fileClipboard != null) {
                    fileClipboard?.let { clip ->
                        val isMove = clip.mode == FileClipboardMode.MOVE
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .testTag("files_clipboard_bar")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isMove) {
                                                "Move ${clip.items.size} item(s)"
                                            } else {
                                                "Copy ${clip.items.size} item(s)"
                                            },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "Target: /PocketMine" +
                                                if (currentRelativePath.isEmpty()) "" else "/$currentRelativePath",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = onClearClipboard,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(36.dp)
                                            .testTag("files_clipboard_cancel_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Cancel pending file operation",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onPasteClipboardHere,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 38.dp)
                                            .testTag("files_paste_here_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentPaste,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isMove) "Move Here" else "Copy Here",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = { folderPickerRequest = clip.mode to clip.items },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 38.dp)
                                            .testTag("files_pick_folder_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Pick Folder", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = operationProgress !is OperationProgress.Idle) {
                    MetroOperationBanner(
                        progress = operationProgress,
                        onDismiss = onDismissProgress,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                if (files.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "This folder is empty",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap Import to add plugins/files, or move staged items here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .testTag("files_list"),
                        contentPadding = PaddingValues(top = 2.dp, bottom = bottomContentPadding),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            items = files,
                            key = { _, item -> item.absolutePath }
                        ) { index, item ->
                            val isSelected = item.absolutePath in selectedPaths
                            SegmentedFileRow(
                                index = index,
                                count = files.size,
                                item = item,
                                selectionMode = selectionMode,
                                selected = isSelected,
                                onToggleSelect = { toggleSelection(item) },
                                onLongPress = {
                                    selectionModeExplicit = true
                                    if (!isSelected) {
                                        selectedPaths = selectedPaths + item.absolutePath
                                    }
                                },
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
                                onMove = { onStageClipboard(FileClipboardMode.MOVE, listOf(item)) },
                                onCopy = { onStageClipboard(FileClipboardMode.COPY, listOf(item)) },
                                onZip = { zippingItems = listOf(item) },
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

    zippingItems?.let { items ->
        val defaultName = if (items.size == 1) {
            items.first().name.substringBeforeLast('.')
        } else {
            "archive"
        }
        SingleNameDialog(
            title = "Create .zip Archive",
            label = "Archive name (${items.size} item(s))",
            initialValue = defaultName,
            confirmLabel = "Zip",
            onDismiss = { zippingItems = null },
            onConfirm = { archiveName ->
                zippingItems = null
                onZipEntries(items, archiveName)
                clearSelection()
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

    deletingBatchItems?.let { items ->
        AlertDialog(
            onDismissRequest = { deletingBatchItems = null },
            title = { Text("Delete ${items.size} selected item(s)?") },
            text = {
                val preview = items.take(4).joinToString(", ") { it.name } +
                    if (items.size > 4) " +${items.size - 4} more" else ""
                Text("This permanently deletes $preview from ${workspaceInfo.workspacePath}.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingBatchItems = null
                        onDeleteEntries(items)
                        clearSelection()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_batch_delete_button")
                ) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBatchItems = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    folderPickerRequest?.let { (mode, items) ->
        val directories = remember(workspaceInfo.workspacePath) {
            onListWorkspaceDirectories()
        }
        FolderPickerDialog(
            mode = mode,
            itemCount = items.size,
            directories = directories,
            currentRelativePath = currentRelativePath,
            onDismiss = { folderPickerRequest = null },
            onSelectFolder = { destRel ->
                folderPickerRequest = null
                if (mode == FileClipboardMode.MOVE) {
                    onMoveEntriesTo(items, destRel)
                } else {
                    onCopyEntriesTo(items, destRel)
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
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onOpen: () -> Unit,
    onEditText: () -> Unit,
    onExtractZip: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onZip: () -> Unit,
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

    val subtitle = item.formattedSubtitle.ifEmpty {
        if (item.isDirectory) "${item.childCount} items" else formatFileSize(item.sizeBytes)
    }

    MetroGroupedRow(
        index = index,
        count = count,
        title = item.name,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        iconContainerColor = iconBg,
        selected = selected,
        onClick = if (selectionMode) onToggleSelect else onOpen,
        onLongClick = onLongPress,
        trailingContent = {
            if (selectionMode) {
                IconButton(
                    onClick = onToggleSelect,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = if (selected) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = if (selected) {
                            "Deselect ${item.name}"
                        } else {
                            "Select ${item.name}"
                        },
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "File options for ${item.name}",
                            modifier = Modifier.size(18.dp)
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
                        DropdownMenuItem(
                            text = { Text("Move To...") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy To...") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Compress (.zip)") },
                            leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onZip()
                            }
                        )
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
        }
    )
}

@Composable
private fun FolderPickerDialog(
    mode: FileClipboardMode,
    itemCount: Int,
    directories: List<String>,
    currentRelativePath: String,
    onDismiss: () -> Unit,
    onSelectFolder: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("folder_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (mode == FileClipboardMode.MOVE) {
                        "Move $itemCount item(s) to"
                    } else {
                        "Copy $itemCount item(s) to"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(directories) { idx, relPath ->
                        val label = if (relPath.isEmpty()) "/PocketMine (Root)" else "/$relPath"
                        MetroGroupedRow(
                            index = idx,
                            count = directories.size,
                            title = label,
                            subtitle = if (relPath == currentRelativePath) "Current folder" else null,
                            icon = Icons.Rounded.Folder,
                            iconTint = MaterialTheme.colorScheme.primary,
                            selected = relPath == currentRelativePath,
                            onClick = { onSelectFolder(relPath) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
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
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
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
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 42.dp)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = onSave,
                            enabled = !state.isSaving,
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 42.dp)
                                .testTag("editor_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
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
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("editor_text_field")
                )
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
