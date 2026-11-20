package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.console.AnsiParser
import com.example.server.ServerWorkspaceInfo
import com.example.ui.components.MetroExpressiveSwitch
import com.example.ui.components.MetroGroupedCustomBlock
import com.example.ui.components.MetroGroupedRow
import com.example.ui.components.MetroGroupedSection
import com.example.ui.components.MetroPillChip
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ConsoleThemeStyle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.toTokens
import kotlin.math.roundToInt

private val previewAnsiSample = listOf(
    "\u001B[1;33m[@]\u001B[0m \u001B[36mKyoto / BlueLight Multiversion\u001B[0m",
    "\u001B[31m[Ω] SERVER IS RUNNING IN OFFLINE/INSECURE MODE!\u001B[0m",
    "\u001B[1;32m[INFO]\u001B[0m Starting remote control listener on 0.0.0.0:19132"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    appThemeMode: AppThemeMode,
    pureBlack: Boolean,
    dynamicColor: Boolean,
    accentPreset: AccentPreset,
    floatingNavBar: Boolean,
    consoleThemeStyle: ConsoleThemeStyle,
    ansiEnabled: Boolean,
    fontSizeSp: Float,
    autoScroll: Boolean,
    workspaceInfo: ServerWorkspaceInfo,
    onSelectAppTheme: (AppThemeMode) -> Unit,
    onTogglePureBlack: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectAccentPreset: (AccentPreset) -> Unit,
    onToggleFloatingNavBar: (Boolean) -> Unit,
    onSelectConsoleTheme: (ConsoleThemeStyle) -> Unit,
    onToggleAnsi: (Boolean) -> Unit,
    onChangeFontSize: (Float) -> Unit,
    onToggleAutoScroll: (Boolean) -> Unit,
    onBackupLocal: () -> Unit,
    onBackupExport: () -> Unit,
    onDeleteServerData: () -> Unit,
    onNavigateBack: () -> Unit,
    bottomContentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val previewTokens = consoleThemeStyle.toTokens()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Appearance (Metrolist-style grouped cards)
            item {
                val appearanceCount = if (supportsDynamicColor) 5 else 4
                var idx = 0
                MetroGroupedSection(
                    title = "Appearance",
                    description = "Metrolist-inspired Material 3 surfaces, pure black OLED mode, and accent seeds"
                ) {
                    MetroGroupedCustomBlock(index = idx++, count = appearanceCount) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Dark Mode",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                MetroPillChip(
                                    label = mode.label,
                                    selected = appThemeMode == mode,
                                    onClick = { onSelectAppTheme(mode) }
                                )
                            }
                        }
                    }

                    MetroGroupedRow(
                        index = idx++,
                        count = appearanceCount,
                        title = "Pure Black (AMOLED)",
                        subtitle = "True #000000 surfaces when Dark mode is active",
                        icon = Icons.Rounded.Contrast,
                        checked = pureBlack,
                        onClick = { onTogglePureBlack(!pureBlack) },
                        trailingContent = {
                            MetroExpressiveSwitch(
                                checked = pureBlack,
                                onCheckedChange = null,
                                modifier = Modifier.testTag("pure_black_switch")
                            )
                        }
                    )

                    if (supportsDynamicColor) {
                        MetroGroupedRow(
                            index = idx++,
                            count = appearanceCount,
                            title = "Dynamic Color (Material You)",
                            subtitle = "Match Android 12+ wallpaper palette automatically",
                            icon = Icons.Rounded.AutoAwesome,
                            checked = dynamicColor,
                            onClick = { onToggleDynamicColor(!dynamicColor) },
                            trailingContent = {
                                MetroExpressiveSwitch(
                                    checked = dynamicColor,
                                    onCheckedChange = null,
                                    modifier = Modifier.testTag("dynamic_color_switch")
                                )
                            }
                        )
                    }

                    MetroGroupedCustomBlock(index = idx++, count = appearanceCount) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ColorLens,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accent Color",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (dynamicColor && supportsDynamicColor) {
                                        "Overridden while Dynamic Color is enabled"
                                    } else {
                                        accentPreset.label
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.alpha(if (dynamicColor && supportsDynamicColor) 0.45f else 1f)
                        ) {
                            AccentPreset.entries.forEach { preset ->
                                val selected = accentPreset == preset
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(preset.swatchColor)
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    width = 3.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = CircleShape
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(enabled = !(dynamicColor && supportsDynamicColor)) {
                                            onSelectAccentPreset(preset)
                                        }
                                        .semantics { contentDescription = "Accent ${preset.label}" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    MetroGroupedRow(
                        index = idx,
                        count = appearanceCount,
                        title = "Floating Navigation Bar",
                        subtitle = "Capsule bottom bar floating over scrolled content",
                        icon = Icons.Rounded.Navigation,
                        checked = floatingNavBar,
                        onClick = { onToggleFloatingNavBar(!floatingNavBar) },
                        trailingContent = {
                            MetroExpressiveSwitch(
                                checked = floatingNavBar,
                                onCheckedChange = null,
                                modifier = Modifier.testTag("floating_nav_switch")
                            )
                        }
                    )
                }
            }

            // Section 2: Console & Android 15 Contrast Protection
            item {
                MetroGroupedSection(
                    title = "Console & Contrast",
                    description = "Terminal colors stay contrast-locked independently of Android 15 system theme"
                ) {
                    MetroGroupedCustomBlock(index = 0, count = 4) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Console Theme",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ConsoleThemeStyle.entries.forEach { style ->
                                MetroPillChip(
                                    label = style.label,
                                    selected = consoleThemeStyle == style,
                                    onClick = { onSelectConsoleTheme(style) },
                                    modifier = Modifier.testTag("console_theme_${style.name.lowercase()}")
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(previewTokens.background)
                                .border(1.dp, previewTokens.border, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "LIVE CONSOLE PREVIEW (${consoleThemeStyle.label})",
                                style = MaterialTheme.typography.labelSmall,
                                color = previewTokens.secondaryText
                            )
                            previewAnsiSample.forEach { sample ->
                                Text(
                                    text = AnsiParser.parse(sample, ansiEnabled, previewTokens),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * 1.4f).sp
                                )
                            }
                        }
                    }

                    MetroGroupedRow(
                        index = 1,
                        count = 4,
                        title = "Enable ANSI Color Rendering",
                        subtitle = "Parses [1;33m, [31m, and 256-color codes cleanly",
                        icon = Icons.Rounded.FormatColorText,
                        checked = ansiEnabled,
                        onClick = { onToggleAnsi(!ansiEnabled) },
                        trailingContent = {
                            MetroExpressiveSwitch(
                                checked = ansiEnabled,
                                onCheckedChange = null,
                                modifier = Modifier.testTag("ansi_toggle_switch")
                            )
                        }
                    )

                    MetroGroupedRow(
                        index = 2,
                        count = 4,
                        title = "Auto-Scroll Logs",
                        subtitle = "Automatically jump to new server output",
                        icon = Icons.Rounded.VerticalAlignBottom,
                        checked = autoScroll,
                        onClick = { onToggleAutoScroll(!autoScroll) },
                        trailingContent = {
                            MetroExpressiveSwitch(
                                checked = autoScroll,
                                onCheckedChange = null,
                                modifier = Modifier.testTag("autoscroll_toggle_switch")
                            )
                        }
                    )

                    MetroGroupedCustomBlock(index = 3, count = 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Console Font Size",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${fontSizeSp.roundToInt()} sp",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = fontSizeSp,
                            onValueChange = onChangeFontSize,
                            valueRange = 10f..24f,
                            steps = 13,
                            modifier = Modifier.testTag("font_size_slider")
                        )
                    }
                }
            }

            // Section 3: Backups & Storage
            item {
                MetroGroupedSection(
                    title = "Backups & Storage",
                    description = "Workspace: ${workspaceInfo.workspacePath}"
                ) {
                    MetroGroupedRow(
                        index = 0,
                        count = 3,
                        title = stringResource(R.string.action_backup_server),
                        subtitle = "Local snapshots stored in PocketMine-Backups: ${workspaceInfo.backupCount}",
                        icon = Icons.Rounded.Archive,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onBackupLocal,
                        modifier = Modifier.testTag("backup_local_button")
                    )
                    MetroGroupedRow(
                        index = 1,
                        count = 3,
                        title = "Export Backup .zip",
                        subtitle = "Save a portable .zip archive via Android file picker",
                        icon = Icons.Rounded.FileUpload,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = onBackupExport,
                        modifier = Modifier.testTag("backup_export_button")
                    )
                    MetroGroupedRow(
                        index = 2,
                        count = 3,
                        title = stringResource(R.string.action_delete_server),
                        subtitle = "Stops server and wipes workspace files (backups kept)",
                        icon = Icons.Rounded.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_server_button")
                    )
                }
            }

            // Section 4: About
            item {
                MetroGroupedSection(title = "About") {
                    MetroGroupedRow(
                        index = 0,
                        count = 1,
                        title = "BlueLight for Android",
                        subtitle = "v2.0.0 • PocketMine-MP Android runner • Credits: BlueLightJapan/BlueLight, haniokasai/BlueLightAPP, FENGberd/MinecraftPEServer",
                        icon = Icons.Rounded.Info
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteServerData()
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
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
