package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.server.OverlayBubbleSize
import com.example.server.OverlayMacro
import com.example.server.ServerFileItem
import com.example.server.ServerProfile
import com.example.server.ServerStatus
import com.example.service.FloatingConsoleService
import com.example.ui.components.MetroDockedBottomBar
import com.example.ui.components.MetroFloatingBottomBar
import com.example.ui.components.MetroFloatingTopAppBar
import com.example.ui.components.MetroMiniServerBar
import com.example.ui.components.MetroNavDestination
import com.example.ui.components.MetroTopBarIconButton
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.Amber400
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.BlueLightTheme
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose400
import com.example.ui.theme.toTokens
import com.example.viewmodel.AppTab
import com.example.viewmodel.BlueLightViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BlueLightViewModel = viewModel()
            val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
            val appThemeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()
            val pureBlack by viewModel.pureBlack.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val accentPreset by viewModel.accentPreset.collectAsStateWithLifecycle()
            val consoleThemeStyle by viewModel.consoleThemeStyle.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val isDarkApp = when (appThemeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }

            // Ensure status/nav bars match either console contrast theme or Metrolist app theme
            val effectiveLightStatusBars = if (selectedTab == AppTab.CONSOLE) {
                !consoleThemeStyle.toTokens().isDark
            } else {
                !isDarkApp
            }

            SideEffect {
                val statusStyle = if (effectiveLightStatusBars) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                val navStyle = if (isDarkApp) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = statusStyle,
                    navigationBarStyle = navStyle
                )
            }

            BlueLightTheme(
                themeMode = appThemeMode,
                pureBlack = pureBlack,
                dynamicColor = dynamicColor,
                accentPreset = accentPreset
            ) {
                BlueLightAppShell(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun BlueLightAppShell(viewModel: BlueLightViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val consoleLines by viewModel.consoleLines.collectAsStateWithLifecycle()
    val workspaceInfo by viewModel.workspaceInfo.collectAsStateWithLifecycle()
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
    val serverProperties by viewModel.serverProperties.collectAsStateWithLifecycle()
    val currentRelativePath by viewModel.currentRelativePath.collectAsStateWithLifecycle()
    val browserFiles by viewModel.browserFiles.collectAsStateWithLifecycle()
    val textEditorState by viewModel.textEditorState.collectAsStateWithLifecycle()
    val ansiEnabled by viewModel.ansiEnabled.collectAsStateWithLifecycle()
    val fontSizeSp by viewModel.fontSizeSp.collectAsStateWithLifecycle()
    val autoScroll by viewModel.autoScroll.collectAsStateWithLifecycle()
    val appThemeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()
    val pureBlack by viewModel.pureBlack.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val accentPreset by viewModel.accentPreset.collectAsStateWithLifecycle()
    val floatingNavBar by viewModel.floatingNavBar.collectAsStateWithLifecycle()
    val consoleThemeStyle by viewModel.consoleThemeStyle.collectAsStateWithLifecycle()
    val commandHistory by viewModel.commandHistory.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val overlayBubbleSize by viewModel.overlayBubbleSize.collectAsStateWithLifecycle()
    val overlayOpacityPercent by viewModel.overlayOpacityPercent.collectAsStateWithLifecycle()
    val overlayMacros by viewModel.overlayMacros.collectAsStateWithLifecycle()
    val isOverlayActive by FloatingConsoleService.isOverlayActive.collectAsStateWithLifecycle()

    var pendingExportItem by remember { mutableStateOf<ServerFileItem?>(null) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pendingOverlayEnable by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshWorkspace()
                val overlayNow = Settings.canDrawOverlays(context)
                canDrawOverlays = overlayNow
                if (pendingOverlayEnable && overlayNow) {
                    pendingOverlayEnable = false
                    FloatingConsoleService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val importCoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importServerPackage(uri)
        }
    }

    val importFileHereLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFileIntoCurrentDir(uri)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.backupServer(uri)
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val item = pendingExportItem
        pendingExportItem = null
        if (uri != null && item != null) {
            viewModel.exportFileToUri(item, uri)
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshWorkspace()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.startServer()
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            runCatching { context.startActivity(appIntent) }
                .onFailure { runCatching { context.startActivity(fallbackIntent) } }
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    fun requestOverlayPermission() {
        pendingOverlayEnable = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(intent) }
    }

    fun setOverlayEnabled(enable: Boolean) {
        if (!enable) {
            pendingOverlayEnable = false
            FloatingConsoleService.stop(context)
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            requestOverlayPermission()
            return
        }
        FloatingConsoleService.start(context)
    }

    fun startServerWithPermissionCheck() {
        if (!workspaceInfo.hasStorageAccess) {
            requestStorageAccess()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.startServer()
    }

    val navDestinations = listOf(
        MetroNavDestination(
            route = AppTab.DASHBOARD,
            label = stringResource(R.string.nav_dashboard),
            selectedIcon = Icons.Rounded.Dns,
            unselectedIcon = Icons.Outlined.Dns,
            testTag = "nav_tab_dashboard"
        ),
        MetroNavDestination(
            route = AppTab.CONSOLE,
            label = stringResource(R.string.nav_console),
            selectedIcon = Icons.Rounded.Terminal,
            unselectedIcon = Icons.Outlined.Terminal,
            testTag = "nav_tab_console"
        ),
        MetroNavDestination(
            route = AppTab.FILES,
            label = stringResource(R.string.nav_files),
            selectedIcon = Icons.Rounded.Folder,
            unselectedIcon = Icons.Outlined.Folder,
            testTag = "nav_tab_files"
        ),
        MetroNavDestination(
            route = AppTab.SETTINGS,
            label = stringResource(R.string.nav_settings),
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_tab_settings"
        )
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Spacer(Modifier.size(8.dp))
                    navDestinations.forEach { dest ->
                        val selected = selectedTab == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label
                                )
                            },
                            label = { Text(dest.label) },
                            modifier = Modifier.testTag("nav_rail_${dest.route.name.lowercase()}")
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (selectedTab != AppTab.CONSOLE) {
                        ShellFloatingTopBar(
                            selectedTab = selectedTab,
                            serverStatus = serverStatus,
                            activeProfileName = activeProfile.name,
                            isOverlayActive = isOverlayActive,
                            onToggleOverlay = { setOverlayEnabled(!isOverlayActive) },
                            onRefresh = { viewModel.refreshWorkspace() }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ScreenContentSwitch(
                            selectedTab = selectedTab,
                            bottomContentPadding = if (selectedTab == AppTab.CONSOLE) 8.dp else 16.dp,
                            viewModel = viewModel,
                            serverStatus = serverStatus,
                            consoleLines = consoleLines,
                            workspaceInfo = workspaceInfo,
                            operationProgress = operationProgress,
                            serverProperties = serverProperties,
                            currentRelativePath = currentRelativePath,
                            browserFiles = browserFiles,
                            textEditorState = textEditorState,
                            ansiEnabled = ansiEnabled,
                            fontSizeSp = fontSizeSp,
                            autoScroll = autoScroll,
                            appThemeMode = appThemeMode,
                            pureBlack = pureBlack,
                            dynamicColor = dynamicColor,
                            accentPreset = accentPreset,
                            floatingNavBar = floatingNavBar,
                            consoleThemeStyle = consoleThemeStyle,
                            commandHistory = commandHistory,
                            profiles = profiles,
                            activeProfile = activeProfile,
                            overlayBubbleSize = overlayBubbleSize,
                            overlayOpacityPercent = overlayOpacityPercent,
                            overlayMacros = overlayMacros,
                            isOverlayActive = isOverlayActive,
                            canDrawOverlays = canDrawOverlays,
                            onToggleOverlay = { setOverlayEnabled(it) },
                            onStartServer = { startServerWithPermissionCheck() },
                            onRequestStorageAccess = { requestStorageAccess() },
                            onImportCore = {
                                if (!workspaceInfo.hasStorageAccess) {
                                    requestStorageAccess()
                                } else {
                                    importCoreLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                }
                            },
                            onImportFileHere = { importFileHereLauncher.launch(arrayOf("*/*")) },
                            onExportFile = { item ->
                                pendingExportItem = item
                                exportFileLauncher.launch(item.name)
                            },
                            onBackupExport = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportBackupLauncher.launch("bluelight_backup_$stamp.zip")
                            }
                        )
                    }

                    if (selectedTab != AppTab.CONSOLE) {
                        MetroMiniServerBar(
                            serverStatus = serverStatus,
                            coreLabel = workspaceInfo.detectedCoreLabel,
                            port = serverProperties.port,
                            onOpenConsole = { viewModel.selectTab(AppTab.CONSOLE) },
                            onStartServer = { startServerWithPermissionCheck() },
                            onStopServer = { viewModel.stopServer() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        } else if (selectedTab == AppTab.CONSOLE) {
            // Dedicated Console layout so IME keyboard & terminal dock stay unobstructed
            val consoleTokens = consoleThemeStyle.toTokens()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(consoleTokens.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ScreenContentSwitch(
                        selectedTab = selectedTab,
                        bottomContentPadding = 8.dp,
                        viewModel = viewModel,
                        serverStatus = serverStatus,
                        consoleLines = consoleLines,
                        workspaceInfo = workspaceInfo,
                        operationProgress = operationProgress,
                        serverProperties = serverProperties,
                        currentRelativePath = currentRelativePath,
                        browserFiles = browserFiles,
                        textEditorState = textEditorState,
                        ansiEnabled = ansiEnabled,
                        fontSizeSp = fontSizeSp,
                        autoScroll = autoScroll,
                        appThemeMode = appThemeMode,
                        pureBlack = pureBlack,
                        dynamicColor = dynamicColor,
                        accentPreset = accentPreset,
                        floatingNavBar = floatingNavBar,
                        consoleThemeStyle = consoleThemeStyle,
                        commandHistory = commandHistory,
                        profiles = profiles,
                        activeProfile = activeProfile,
                        overlayBubbleSize = overlayBubbleSize,
                        overlayOpacityPercent = overlayOpacityPercent,
                        overlayMacros = overlayMacros,
                        isOverlayActive = isOverlayActive,
                        canDrawOverlays = canDrawOverlays,
                        onToggleOverlay = { setOverlayEnabled(it) },
                        onStartServer = { startServerWithPermissionCheck() },
                        onRequestStorageAccess = { requestStorageAccess() },
                        onImportCore = {},
                        onImportFileHere = {},
                        onExportFile = {},
                        onBackupExport = {}
                    )
                }

                if (floatingNavBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(consoleTokens.background)
                    ) {
                        MetroFloatingBottomBar(
                            items = navDestinations,
                            selectedRoute = selectedTab,
                            onSelect = { viewModel.selectTab(it) }
                        )
                    }
                } else {
                    MetroDockedBottomBar(
                        items = navDestinations,
                        selectedRoute = selectedTab,
                        onSelect = { viewModel.selectTab(it) }
                    )
                }
            }
        } else {
            // Metrolist Floating Top Bar + Content + Floating MiniServerBar + Floating/Docked Bottom Bar
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ShellFloatingTopBar(
                        selectedTab = selectedTab,
                        serverStatus = serverStatus,
                        activeProfileName = activeProfile.name,
                        isOverlayActive = isOverlayActive,
                        onToggleOverlay = { setOverlayEnabled(!isOverlayActive) },
                        onRefresh = { viewModel.refreshWorkspace() }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val bottomContentInset = if (floatingNavBar) 172.dp else 20.dp
                        ScreenContentSwitch(
                            selectedTab = selectedTab,
                            bottomContentPadding = bottomContentInset,
                            viewModel = viewModel,
                            serverStatus = serverStatus,
                            consoleLines = consoleLines,
                            workspaceInfo = workspaceInfo,
                            operationProgress = operationProgress,
                            serverProperties = serverProperties,
                            currentRelativePath = currentRelativePath,
                            browserFiles = browserFiles,
                            textEditorState = textEditorState,
                            ansiEnabled = ansiEnabled,
                            fontSizeSp = fontSizeSp,
                            autoScroll = autoScroll,
                            appThemeMode = appThemeMode,
                            pureBlack = pureBlack,
                            dynamicColor = dynamicColor,
                            accentPreset = accentPreset,
                            floatingNavBar = floatingNavBar,
                            consoleThemeStyle = consoleThemeStyle,
                            commandHistory = commandHistory,
                            profiles = profiles,
                            activeProfile = activeProfile,
                            overlayBubbleSize = overlayBubbleSize,
                            overlayOpacityPercent = overlayOpacityPercent,
                            overlayMacros = overlayMacros,
                            isOverlayActive = isOverlayActive,
                            canDrawOverlays = canDrawOverlays,
                            onToggleOverlay = { setOverlayEnabled(it) },
                            onStartServer = { startServerWithPermissionCheck() },
                            onRequestStorageAccess = { requestStorageAccess() },
                            onImportCore = {
                                if (!workspaceInfo.hasStorageAccess) {
                                    requestStorageAccess()
                                } else {
                                    importCoreLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                }
                            },
                            onImportFileHere = { importFileHereLauncher.launch(arrayOf("*/*")) },
                            onExportFile = { item ->
                                pendingExportItem = item
                                exportFileLauncher.launch(item.name)
                            },
                            onBackupExport = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportBackupLauncher.launch("bluelight_backup_$stamp.zip")
                            }
                        )
                    }

                    if (!floatingNavBar) {
                        MetroMiniServerBar(
                            serverStatus = serverStatus,
                            coreLabel = workspaceInfo.detectedCoreLabel,
                            port = serverProperties.port,
                            onOpenConsole = { viewModel.selectTab(AppTab.CONSOLE) },
                            onStartServer = { startServerWithPermissionCheck() },
                            onStopServer = { viewModel.stopServer() },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        MetroDockedBottomBar(
                            items = navDestinations,
                            selectedRoute = selectedTab,
                            onSelect = { viewModel.selectTab(it) }
                        )
                    }
                }

                if (floatingNavBar) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        MetroMiniServerBar(
                            serverStatus = serverStatus,
                            coreLabel = workspaceInfo.detectedCoreLabel,
                            port = serverProperties.port,
                            onOpenConsole = { viewModel.selectTab(AppTab.CONSOLE) },
                            onStartServer = { startServerWithPermissionCheck() },
                            onStopServer = { viewModel.stopServer() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        MetroFloatingBottomBar(
                            items = navDestinations,
                            selectedRoute = selectedTab,
                            onSelect = { viewModel.selectTab(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellFloatingTopBar(
    selectedTab: AppTab,
    serverStatus: ServerStatus,
    activeProfileName: String,
    isOverlayActive: Boolean,
    onToggleOverlay: () -> Unit,
    onRefresh: () -> Unit
) {
    val dotColor = when (serverStatus) {
        ServerStatus.RUNNING -> Emerald400
        ServerStatus.STARTING, ServerStatus.STOPPING -> Amber400
        ServerStatus.STOPPED -> Rose400
    }
    val titleText = when (selectedTab) {
        AppTab.DASHBOARD -> stringResource(R.string.app_name)
        AppTab.CONSOLE -> stringResource(R.string.nav_console)
        AppTab.FILES -> stringResource(R.string.nav_files)
        AppTab.SETTINGS -> stringResource(R.string.nav_settings)
    }
    val subtitleText = when (selectedTab) {
        AppTab.DASHBOARD -> {
            val statusWord = when (serverStatus) {
                ServerStatus.RUNNING -> "Online"
                ServerStatus.STARTING -> "Starting"
                ServerStatus.STOPPING -> "Stopping"
                ServerStatus.STOPPED -> "Offline"
            }
            "$statusWord • $activeProfileName"
        }
        AppTab.FILES -> activeProfileName
        AppTab.SETTINGS -> "Theme, Overlay & Profiles"
        AppTab.CONSOLE -> ""
    }

    MetroFloatingTopAppBar(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        },
        title = {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        },
        subtitle = {
            if (subtitleText.isNotEmpty()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        },
        actions = {
            MetroTopBarIconButton(
                onClick = onToggleOverlay,
                contentDescription = if (isOverlayActive) {
                    stringResource(R.string.action_disable_overlay)
                } else {
                    stringResource(R.string.action_enable_overlay)
                },
                icon = Icons.Rounded.PictureInPictureAlt,
                active = isOverlayActive
            )
            MetroTopBarIconButton(
                onClick = onRefresh,
                contentDescription = "Refresh",
                icon = Icons.Rounded.Refresh
            )
        }
    )
}

@Composable
private fun ScreenContentSwitch(
    selectedTab: AppTab,
    bottomContentPadding: Dp,
    viewModel: BlueLightViewModel,
    serverStatus: ServerStatus,
    consoleLines: List<com.example.server.ConsoleLine>,
    workspaceInfo: com.example.server.ServerWorkspaceInfo,
    operationProgress: com.example.server.OperationProgress,
    serverProperties: com.example.server.ServerPropertiesConfig,
    currentRelativePath: String,
    browserFiles: List<ServerFileItem>,
    textEditorState: com.example.viewmodel.TextEditorState,
    ansiEnabled: Boolean,
    fontSizeSp: Float,
    autoScroll: Boolean,
    appThemeMode: AppThemeMode,
    pureBlack: Boolean,
    dynamicColor: Boolean,
    accentPreset: com.example.ui.theme.AccentPreset,
    floatingNavBar: Boolean,
    consoleThemeStyle: com.example.ui.theme.ConsoleThemeStyle,
    commandHistory: List<String>,
    profiles: List<ServerProfile>,
    activeProfile: ServerProfile,
    overlayBubbleSize: OverlayBubbleSize,
    overlayOpacityPercent: Int,
    overlayMacros: List<OverlayMacro>,
    isOverlayActive: Boolean,
    canDrawOverlays: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    onStartServer: () -> Unit,
    onRequestStorageAccess: () -> Unit,
    onImportCore: () -> Unit,
    onImportFileHere: () -> Unit,
    onExportFile: (ServerFileItem) -> Unit,
    onBackupExport: () -> Unit
) {
    Crossfade(targetState = selectedTab, label = "tabCrossfade") { tab ->
        when (tab) {
            AppTab.DASHBOARD -> DashboardScreen(
                serverStatus = serverStatus,
                workspaceInfo = workspaceInfo,
                serverProperties = serverProperties,
                operationProgress = operationProgress,
                profiles = profiles,
                activeProfile = activeProfile,
                detectCoreLabel = { viewModel.detectCoreLabelForProfile(it) },
                isOverlayActive = isOverlayActive,
                canDrawOverlays = canDrawOverlays,
                onToggleOverlay = onToggleOverlay,
                onStartServer = onStartServer,
                onStopServer = { viewModel.stopServer() },
                onForceKillServer = { viewModel.forceKillServer() },
                onOpenConsole = { viewModel.selectTab(AppTab.CONSOLE) },
                onOpenFiles = { viewModel.selectTab(AppTab.FILES) },
                onRequestStorageAccess = onRequestStorageAccess,
                onImportCore = onImportCore,
                onDownloadBlueLight = { customUrl ->
                    if (!workspaceInfo.hasStorageAccess) {
                        onRequestStorageAccess()
                    } else {
                        viewModel.downloadBlueLightCore(customUrl)
                    }
                },
                onRefreshWorkspace = { viewModel.refreshWorkspace() },
                onSaveProperties = { updated -> viewModel.saveServerProperties(updated) },
                onSwitchProfile = { viewModel.switchProfile(it) },
                onCreateProfile = { name, copyProps -> viewModel.createProfile(name, copyProps) },
                onRenameProfile = { id, newName -> viewModel.renameProfile(id, newName) },
                onDeleteProfile = { id, deleteFiles -> viewModel.deleteProfile(id, deleteFiles) },
                onDismissProgress = { viewModel.dismissOperationProgress() },
                bottomContentPadding = bottomContentPadding
            )

            AppTab.CONSOLE -> ConsoleScreen(
                consoleLines = consoleLines,
                serverStatus = serverStatus,
                ansiEnabled = ansiEnabled,
                fontSizeSp = fontSizeSp,
                autoScroll = autoScroll,
                consoleThemeStyle = consoleThemeStyle,
                commandHistory = commandHistory,
                quickMacros = overlayMacros,
                isOverlayActive = isOverlayActive,
                onToggleOverlay = { onToggleOverlay(!isOverlayActive) },
                onSendCommand = { viewModel.sendCommand(it) },
                onClearLogs = { viewModel.clearConsole() },
                onToggleAutoScroll = { viewModel.setAutoScroll(it) },
                onCycleConsoleTheme = { viewModel.cycleConsoleTheme() },
                onGetPlainLogText = { viewModel.getPlainLogText() },
                onNavigateBack = { viewModel.selectTab(AppTab.DASHBOARD) }
            )

            AppTab.FILES -> FilesScreen(
                workspaceInfo = workspaceInfo,
                currentRelativePath = currentRelativePath,
                files = browserFiles,
                operationProgress = operationProgress,
                textEditorState = textEditorState,
                onRequestStorageAccess = onRequestStorageAccess,
                onBrowseDirectory = { viewModel.browseDirectory(it) },
                onNavigateUp = { viewModel.navigateUpDirectory() },
                onRefresh = { viewModel.refreshWorkspace() },
                onCreateFolder = { viewModel.createFolder(it) },
                onCreateFile = { viewModel.createFile(it) },
                onImportFileHere = onImportFileHere,
                onOpenTextFile = { viewModel.openTextFile(it) },
                onExtractZip = { viewModel.extractZipEntry(it) },
                onExportFile = onExportFile,
                onRenameEntry = { item, newName -> viewModel.renameEntry(item, newName) },
                onDeleteEntry = { viewModel.deleteEntry(it) },
                onUpdateEditorContent = { viewModel.updateEditorContent(it) },
                onSaveEditorFile = { viewModel.saveOpenTextFile() },
                onCloseEditor = { viewModel.closeTextEditor() },
                onDismissProgress = { viewModel.dismissOperationProgress() },
                onNavigateBackToDashboard = { viewModel.selectTab(AppTab.DASHBOARD) },
                bottomContentPadding = bottomContentPadding
            )

            AppTab.SETTINGS -> SettingsScreen(
                appThemeMode = appThemeMode,
                pureBlack = pureBlack,
                dynamicColor = dynamicColor,
                accentPreset = accentPreset,
                floatingNavBar = floatingNavBar,
                consoleThemeStyle = consoleThemeStyle,
                ansiEnabled = ansiEnabled,
                fontSizeSp = fontSizeSp,
                autoScroll = autoScroll,
                overlayBubbleSize = overlayBubbleSize,
                overlayOpacityPercent = overlayOpacityPercent,
                overlayMacros = overlayMacros,
                profiles = profiles,
                activeProfile = activeProfile,
                serverStatus = serverStatus,
                detectCoreLabel = { viewModel.detectCoreLabelForProfile(it) },
                workspaceInfo = workspaceInfo,
                onSelectAppTheme = { viewModel.setAppThemeMode(it) },
                onTogglePureBlack = { viewModel.setPureBlack(it) },
                onToggleDynamicColor = { viewModel.setDynamicColor(it) },
                onSelectAccentPreset = { viewModel.setAccentPreset(it) },
                onToggleFloatingNavBar = { viewModel.setFloatingNavBar(it) },
                onSelectConsoleTheme = { viewModel.setConsoleThemeStyle(it) },
                onToggleAnsi = { viewModel.setAnsiEnabled(it) },
                onChangeFontSize = { viewModel.setFontSizeSp(it) },
                onToggleAutoScroll = { viewModel.setAutoScroll(it) },
                onSelectOverlayBubbleSize = { viewModel.setOverlayBubbleSize(it) },
                onChangeOverlayOpacity = { viewModel.setOverlayOpacityPercent(it) },
                onAddOverlayMacro = { label, command -> viewModel.addOverlayMacro(label, command) },
                onUpdateOverlayMacro = { id, label, command -> viewModel.updateOverlayMacro(id, label, command) },
                onRemoveOverlayMacro = { viewModel.removeOverlayMacro(it) },
                onResetOverlayMacros = { viewModel.resetOverlayMacros() },
                onSwitchProfile = { viewModel.switchProfile(it) },
                onCreateProfile = { name, copyProps -> viewModel.createProfile(name, copyProps) },
                onRenameProfile = { id, newName -> viewModel.renameProfile(id, newName) },
                onDeleteProfile = { id, deleteFiles -> viewModel.deleteProfile(id, deleteFiles) },
                onBackupLocal = { viewModel.backupServer(null) },
                onBackupExport = onBackupExport,
                onDeleteServerData = { viewModel.deleteServerData() },
                onNavigateBack = { viewModel.selectTab(AppTab.DASHBOARD) },
                bottomContentPadding = bottomContentPadding
            )
        }
    }
}
