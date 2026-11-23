package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.console.AnsiParser
import com.example.console.ConsoleLogLevel
import com.example.console.ConsoleTools
import com.example.server.BlueLightPreferencesCodec
import com.example.server.ConsoleLine
import com.example.server.OverlayMacro
import com.example.server.ServerStatus
import com.example.ui.theme.Amber400
import com.example.ui.theme.ConsoleColorTokens
import com.example.ui.theme.ConsoleThemeStyle
import com.example.ui.theme.Emerald400
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.Rose400
import com.example.ui.theme.toTokens
import kotlinx.coroutines.launch

@Composable
fun ConsoleScreen(
    consoleLines: List<ConsoleLine>,
    serverStatus: ServerStatus,
    ansiEnabled: Boolean,
    fontSizeSp: Float,
    autoScroll: Boolean,
    consoleThemeStyle: ConsoleThemeStyle,
    commandHistory: List<String>,
    quickMacros: List<OverlayMacro>,
    isOverlayActive: Boolean,
    onToggleOverlay: () -> Unit,
    onSendCommand: (String) -> Unit,
    onClearLogs: () -> Unit,
    onToggleAutoScroll: (Boolean) -> Unit,
    onCycleConsoleTheme: () -> Unit,
    onGetPlainLogText: () -> String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val tokens = consoleThemeStyle.toTokens()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var commandValue by remember { mutableStateOf(TextFieldValue("")) }
    var historyCursor by remember { mutableIntStateOf(-1) }

    var selectedLogLevel by remember { mutableStateOf(ConsoleLogLevel.ALL) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLines by remember(consoleLines, selectedLogLevel, searchQuery) {
        derivedStateOf {
            ConsoleTools.filterConsoleLines(consoleLines, selectedLogLevel, searchQuery)
        }
    }

    val commandSuggestions by remember(commandValue.text, commandHistory) {
        derivedStateOf {
            ConsoleTools.suggestCommands(commandValue.text, commandHistory)
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount == 0 || lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(filteredLines.lastOrNull()?.id, autoScroll) {
        if (autoScroll && filteredLines.isNotEmpty()) {
            listState.scrollToItem(filteredLines.lastIndex)
        }
    }

    val statusDotColor = when (serverStatus) {
        ServerStatus.RUNNING -> Emerald400
        ServerStatus.STARTING, ServerStatus.STOPPING -> Amber400
        ServerStatus.STOPPED -> Rose400
    }

    // Contrast-locked outer container so Android 15 system colors can never bleach terminal logs
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Metrolist-style Floating Pill Header + Circular Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = CircleShape,
                color = tokens.toolbarBackground,
                contentColor = tokens.toolbarContent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Console",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.toolbarContent,
                            maxLines = 1
                        )
                        val countLabel = if (selectedLogLevel != ConsoleLogLevel.ALL || searchQuery.isNotBlank()) {
                            "${filteredLines.size}/${consoleLines.size} lines"
                        } else {
                            "${consoleThemeStyle.label} • ${consoleLines.size} lines"
                        }
                        Text(
                            text = countLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            ConsoleCircleAction(
                icon = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.action_search_logs),
                tokens = tokens,
                active = showSearchBar || searchQuery.isNotBlank(),
                onClick = {
                    if (showSearchBar && searchQuery.isNotEmpty()) {
                        searchQuery = ""
                    } else {
                        showSearchBar = !showSearchBar
                    }
                },
                modifier = Modifier.testTag("console_search_toggle_button")
            )
            ConsoleCircleAction(
                icon = Icons.Rounded.PictureInPictureAlt,
                contentDescription = if (isOverlayActive) {
                    stringResource(R.string.action_disable_overlay)
                } else {
                    stringResource(R.string.action_enable_overlay)
                },
                tokens = tokens,
                active = isOverlayActive,
                onClick = onToggleOverlay,
                modifier = Modifier.testTag("console_overlay_button")
            )
            ConsoleCircleAction(
                icon = Icons.Rounded.Palette,
                contentDescription = stringResource(R.string.action_cycle_console_theme),
                tokens = tokens,
                onClick = onCycleConsoleTheme,
                modifier = Modifier.testTag("console_theme_button")
            )
            ConsoleCircleAction(
                icon = Icons.Rounded.VerticalAlignBottom,
                contentDescription = stringResource(R.string.action_toggle_autoscroll),
                tokens = tokens,
                active = autoScroll,
                onClick = { onToggleAutoScroll(!autoScroll) },
                modifier = Modifier.testTag("console_autoscroll_button")
            )
            ConsoleCircleAction(
                icon = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.action_copy_logs),
                tokens = tokens,
                onClick = {
                    val plain = if (selectedLogLevel == ConsoleLogLevel.ALL && searchQuery.isBlank()) {
                        onGetPlainLogText()
                    } else {
                        filteredLines.joinToString("\n") { AnsiParser.stripAnsi(it.rawText) }
                    }
                    clipboard.setText(AnnotatedString(plain))
                    Toast.makeText(context, R.string.message_copied, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("console_copy_button")
            )
            ConsoleCircleAction(
                icon = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.action_clear_logs),
                tokens = tokens,
                onClick = onClearLogs,
                modifier = Modifier.testTag("console_clear_button")
            )
        }

        // Collapsible Search Bar
        AnimatedVisibility(visible = showSearchBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(tokens.inputContainer)
                    .border(1.dp, tokens.border, CircleShape)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = tokens.promptAccent,
                    modifier = Modifier.size(18.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Filter past logs by keyword...",
                            color = tokens.inputPlaceholder,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = tokens.inputText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(tokens.promptAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("console_search_input")
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = tokens.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Log Level Filter Chip Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConsoleLogLevel.entries.forEach { level ->
                val selected = selectedLogLevel == level
                val levelAccent = when (level) {
                    ConsoleLogLevel.ERROR -> Rose400
                    ConsoleLogLevel.WARN -> Amber400
                    ConsoleLogLevel.CHAT -> Emerald400
                    else -> tokens.promptAccent
                }
                Surface(
                    onClick = { selectedLogLevel = level },
                    shape = CircleShape,
                    color = if (selected) levelAccent.copy(alpha = 0.2f) else tokens.toolbarBackground,
                    contentColor = if (selected) levelAccent else tokens.secondaryText,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (selected) levelAccent else tokens.border,
                            shape = CircleShape
                        )
                        .testTag("console_filter_${level.name.lowercase()}")
                ) {
                    Text(
                        text = level.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Main Framed Terminal Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.toolbarBackground.copy(alpha = 0.35f))
                .border(1.dp, tokens.border, RoundedCornerShape(16.dp))
        ) {
            if (filteredLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyMsg = if (consoleLines.isEmpty()) {
                        "Console output is empty. Start the server or enter a command below."
                    } else {
                        "No logs match ${selectedLogLevel.label}${if (searchQuery.isNotBlank()) " • \"$searchQuery\"" else ""}."
                    }
                    Text(
                        text = emptyMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = tokens.secondaryText
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("console_log_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = filteredLines,
                            key = { it.id }
                        ) { line ->
                            val parsed = remember(line.id, ansiEnabled, consoleThemeStyle) {
                                AnsiParser.parse(
                                    rawLine = line.rawText,
                                    ansiEnabled = ansiEnabled,
                                    tokens = tokens
                                )
                            }
                            Text(
                                text = parsed,
                                color = tokens.defaultText,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.42f).sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            this@Column.AnimatedVisibility(
                visible = filteredLines.isNotEmpty() && !isAtBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            if (filteredLines.isNotEmpty()) {
                                listState.animateScrollToItem(filteredLines.lastIndex)
                            }
                        }
                    },
                    shape = CircleShape,
                    containerColor = tokens.toolbarBackground,
                    contentColor = tokens.promptAccent,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = "Scroll to latest logs"
                    )
                }
            }
        }

        // `/` Command Autocomplete Suggestions Popup Card
        AnimatedVisibility(visible = commandSuggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = tokens.toolbarBackground,
                contentColor = tokens.toolbarContent,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.border, RoundedCornerShape(18.dp))
                    .testTag("console_autocomplete_list")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    commandSuggestions.forEachIndexed { idx, suggestion ->
                        if (idx > 0) {
                            HorizontalDivider(color = tokens.border.copy(alpha = 0.5f))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val filled = if (suggestion.takesArgs) {
                                        "${suggestion.command} "
                                    } else {
                                        suggestion.command
                                    }
                                    commandValue = TextFieldValue(
                                        text = filled,
                                        selection = TextRange(filled.length)
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (suggestion.fromHistory) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = tokens.promptAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = suggestion.syntax,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.promptAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = suggestion.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Quick Command / Macro Pill Shelf
        val macrosToShow = if (quickMacros.isEmpty()) {
            BlueLightPreferencesCodec.defaultOverlayMacros()
        } else {
            quickMacros
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            macrosToShow.forEach { macro ->
                Surface(
                    onClick = { onSendCommand(macro.command) },
                    color = tokens.toolbarBackground,
                    contentColor = tokens.promptAccent,
                    shape = CircleShape,
                    modifier = Modifier.border(1.dp, tokens.border, CircleShape)
                ) {
                    Text(
                        text = macro.label,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Rounded Pill Command Input Dock
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (commandHistory.isNotEmpty()) {
                ConsoleCircleAction(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Previous command",
                    tokens = tokens,
                    onClick = {
                        val nextIndex = if (historyCursor < 0) {
                            commandHistory.lastIndex
                        } else {
                            (historyCursor - 1).coerceAtLeast(0)
                        }
                        historyCursor = nextIndex
                        val hist = commandHistory[nextIndex]
                        commandValue = TextFieldValue(hist, TextRange(hist.length))
                    }
                )
                ConsoleCircleAction(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Next command",
                    tokens = tokens,
                    onClick = {
                        if (historyCursor >= 0) {
                            if (historyCursor >= commandHistory.lastIndex) {
                                historyCursor = -1
                                commandValue = TextFieldValue("")
                            } else {
                                historyCursor += 1
                                val hist = commandHistory[historyCursor]
                                commandValue = TextFieldValue(hist, TextRange(hist.length))
                            }
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(CircleShape)
                    .background(tokens.inputContainer)
                    .border(1.dp, tokens.border, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "> ",
                    color = tokens.promptAccent,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (commandValue.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.command_input_hint),
                            color = tokens.inputPlaceholder,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = commandValue,
                        onValueChange = { commandValue = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = tokens.inputText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(tokens.promptAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (commandValue.text.isNotBlank()) {
                                    onSendCommand(commandValue.text)
                                    commandValue = TextFieldValue("")
                                    historyCursor = -1
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("console_command_input")
                    )
                }
            }

            Button(
                onClick = {
                    if (commandValue.text.isNotBlank()) {
                        onSendCommand(commandValue.text)
                        commandValue = TextFieldValue("")
                        historyCursor = -1
                    }
                },
                enabled = commandValue.text.isNotBlank(),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.promptAccent,
                    contentColor = if (tokens.isDark) tokens.background else tokens.inputContainer
                ),
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("console_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.action_send),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun ConsoleCircleAction(
    icon: ImageVector,
    contentDescription: String,
    tokens: ConsoleColorTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) {
                tokens.promptAccent.copy(alpha = 0.18f)
            } else {
                tokens.toolbarBackground
            },
            contentColor = if (active) tokens.promptAccent else tokens.toolbarContent
        ),
        modifier = modifier.size(38.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp)
        )
    }
}
