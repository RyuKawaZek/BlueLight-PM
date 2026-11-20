package com.example.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.MainActivity
import com.example.R
import com.example.console.AnsiParser
import com.example.server.BlueLightPreferencesCodec
import com.example.server.ConsoleLine
import com.example.server.OverlayBubbleSize
import com.example.server.OverlayMacro
import com.example.server.ServerRepository
import com.example.server.ServerStatus
import com.example.ui.theme.ConsoleThemeStyle
import com.example.ui.theme.toTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingConsoleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var repository: ServerRepository
    private lateinit var prefs: SharedPreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var rootContainer: FrameLayout? = null
    private var bubbleView: FrameLayout? = null
    private var bubbleIconView: ImageView? = null
    private var panelView: LinearLayout? = null
    private var statusDotBubble: View? = null
    private var statusDotPanel: View? = null
    private var serverToggleBtn: TextView? = null
    private var logScrollView: ScrollView? = null
    private var logTextView: TextView? = null
    private var commandInput: EditText? = null
    private var macrosRow: LinearLayout? = null
    private var quickControlsRow: LinearLayout? = null
    private var sizeCycleBtn: TextView? = null
    private var opacityCycleBtn: TextView? = null
    private var panelLayoutParams: WindowManager.LayoutParams? = null

    private var isExpanded = false
    private var isLargePanel = false
    private var showQuickControls = false

    private var bubbleX = 32
    private var bubbleY = 260
    private var panelX = 24
    private var panelY = 140

    private var currentConsoleTokens = ConsoleThemeStyle.DARK_TERMINAL.toTokens()
    private var currentAnsiEnabled = true
    private var currentFontSizeSp = 12f
    private var bubbleSize = OverlayBubbleSize.STANDARD
    private var opacityPercent = 92
    private var macros: List<OverlayMacro> = BlueLightPreferencesCodec.defaultOverlayMacros()
    private var latestLines: List<ConsoleLine> = emptyList()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_OVERLAY_BUBBLE_SIZE ||
            key == KEY_OVERLAY_OPACITY ||
            key == KEY_OVERLAY_MACROS ||
            key == "ansi_enabled" ||
            key == "font_size_sp" ||
            key == "console_theme_style"
        ) {
            loadPrefs()
            applyOverlayAppearance()
            rebuildMacroChips()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        repository = ServerRepository.getInstance(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(ServerRepository.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        loadPrefs()
        createNotificationChannel()
        startForegroundNotification()
        setupOverlayViews()
        applyOverlayAppearance()
        rebuildMacroChips()
        observeState()
        _isOverlayActive.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_EXPAND_OVERLAY -> {
                if (rootContainer != null && !isExpanded) {
                    setExpanded(true)
                }
            }
            ACTION_REFRESH_CONFIG -> {
                loadPrefs()
                applyOverlayAppearance()
                rebuildMacroChips()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        savePositions()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
        _isOverlayActive.value = false
        serviceScope.cancel()
        rootContainer?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        rootContainer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadPrefs() {
        currentAnsiEnabled = prefs.getBoolean("ansi_enabled", true)
        currentFontSizeSp = prefs.getFloat("font_size_sp", 13f).coerceIn(10f, 18f)
        val styleIdx = prefs.getInt("console_theme_style", 0)
        val style = ConsoleThemeStyle.entries.getOrElse(styleIdx) { ConsoleThemeStyle.DARK_TERMINAL }
        currentConsoleTokens = style.toTokens()

        val sizeIdx = prefs.getInt(KEY_OVERLAY_BUBBLE_SIZE, OverlayBubbleSize.STANDARD.ordinal)
        bubbleSize = OverlayBubbleSize.entries.getOrElse(sizeIdx) { OverlayBubbleSize.STANDARD }
        opacityPercent = prefs.getInt(KEY_OVERLAY_OPACITY, 92).coerceIn(40, 100)
        macros = BlueLightPreferencesCodec.decodeOverlayMacros(prefs.getString(KEY_OVERLAY_MACROS, null))

        val dm = resources.displayMetrics
        val maxX = (dm.widthPixels - dp(bubbleSize.sizeDp)).coerceAtLeast(0)
        val maxY = (dm.heightPixels - dp(bubbleSize.sizeDp)).coerceAtLeast(0)
        bubbleX = prefs.getInt(KEY_BUBBLE_X, 32).coerceIn(0, maxX)
        bubbleY = prefs.getInt(KEY_BUBBLE_Y, 260).coerceIn(0, maxY)
        panelX = prefs.getInt(KEY_PANEL_X, 24).coerceIn(0, (dm.widthPixels - dp(220)).coerceAtLeast(0))
        panelY = prefs.getInt(KEY_PANEL_Y, 140).coerceIn(0, (dm.heightPixels - dp(220)).coerceAtLeast(0))
    }

    private fun savePositions() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putInt(KEY_BUBBLE_X, bubbleX)
            .putInt(KEY_BUBBLE_Y, bubbleY)
            .putInt(KEY_PANEL_X, panelX)
            .putInt(KEY_PANEL_Y, panelY)
            .apply()
    }

    private fun applyOverlayAppearance() {
        val alphaFloat = (opacityPercent / 100f).coerceIn(0.4f, 1.0f)
        val sizePx = dp(bubbleSize.sizeDp)
        val iconPx = dp(bubbleSize.iconDp)
        val dotPx = when (bubbleSize) {
            OverlayBubbleSize.COMPACT -> dp(10)
            OverlayBubbleSize.STANDARD -> dp(13)
            OverlayBubbleSize.LARGE -> dp(15)
        }

        bubbleView?.let { bubble ->
            bubble.alpha = alphaFloat
            bubble.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        }
        bubbleIconView?.let { icon ->
            icon.layoutParams = FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER)
        }
        statusDotBubble?.let { dot ->
            dot.layoutParams = FrameLayout.LayoutParams(dotPx, dotPx, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(3)
                marginEnd = dp(3)
            }
        }
        panelView?.alpha = alphaFloat
        sizeCycleBtn?.text = "Size: ${bubbleSize.label}"
        opacityCycleBtn?.text = "Opacity: $opacityPercent%"

        val params = panelLayoutParams
        val root = rootContainer
        if (params != null && root != null) {
            runCatching { windowManager.updateViewLayout(root, params) }
        }
    }

    private fun startForegroundNotification() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeOverlayIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, FloatingConsoleService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_overlay_title))
            .setContentText(getString(R.string.notification_overlay_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(closeOverlayIntent)
            .addAction(0, "Open App", openAppIntent)
            .addAction(0, "Hide Bubble", closeOverlayIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Console Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the draggable floating server console bubble visible over other apps"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayViews() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }
        panelLayoutParams = params

        val container = FrameLayout(this)
        rootContainer = container

        val bubble = buildBubbleView(params)
        val panel = buildExpandedPanelView(params)
        panel.visibility = View.GONE

        bubbleView = bubble
        panelView = panel

        container.addView(bubble)
        container.addView(panel)

        windowManager.addView(container, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBubbleView(params: WindowManager.LayoutParams): FrameLayout {
        val sizePx = dp(bubbleSize.sizeDp)
        val bubble = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0B1528"))
                setStroke(dp(2), Color.parseColor("#38BDF8"))
            }
            elevation = dp(8).toFloat()
        }

        val iconView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(bubbleSize.iconDp), dp(bubbleSize.iconDp), Gravity.CENTER)
            setImageResource(R.drawable.ic_launcher_fg_art)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }
        bubbleIconView = iconView
        bubble.addView(iconView)

        val dot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(13), dp(13), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(4)
                marginEnd = dp(4)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FB7185"))
                setStroke(dp(2), Color.parseColor("#0B1528"))
            }
        }
        statusDotBubble = dot
        bubble.addView(dot)

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                    }
                    val dm = resources.displayMetrics
                    val maxX = (dm.widthPixels - dp(bubbleSize.sizeDp)).coerceAtLeast(0)
                    val maxY = (dm.heightPixels - dp(bubbleSize.sizeDp)).coerceAtLeast(0)
                    params.x = (startX + dx).coerceIn(0, maxX)
                    params.y = (startY + dy).coerceIn(0, maxY)
                    bubbleX = params.x
                    bubbleY = params.y
                    runCatching { windowManager.updateViewLayout(rootContainer, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        savePositions()
                    } else {
                        setExpanded(true)
                    }
                    true
                }
                else -> false
            }
        }

        return bubble
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildExpandedPanelView(params: WindowManager.LayoutParams): LinearLayout {
        val monoTypeface = runCatching {
            ResourcesCompat.getFont(this, R.font.jetbrains_mono)
        }.getOrNull() ?: Typeface.MONOSPACE

        val bgHex = if (currentConsoleTokens.isDark) "#EE0D1117" else "#F5F8FAFC"
        val toolbarHex = if (currentConsoleTokens.isDark) "#F5161B22" else "#F5E2E8F0"
        val borderHex = if (currentConsoleTokens.isDark) "#30363D" else "#CBD5E1"
        val textHex = if (currentConsoleTokens.isDark) "#E6EDF3" else "#0F172A"
        val hintHex = if (currentConsoleTokens.isDark) "#7D8590" else "#64748B"
        val accentHex = if (currentConsoleTokens.isDark) "#38BDF8" else "#1D4ED8"

        val panelWidth = dp(334)
        val panelHeight = dp(330)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(panelWidth, panelHeight)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor(bgHex))
                setStroke(dp(1), Color.parseColor(borderHex))
            }
            elevation = dp(12).toFloat()
        }

        // Draggable Header Bar
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(), dp(16).toFloat(),
                    dp(16).toFloat(), dp(16).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setColor(Color.parseColor(toolbarHex))
            }
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                marginEnd = dp(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FB7185"))
            }
        }
        statusDotPanel = dot
        header.addView(dot)

        val titleText = TextView(this).apply {
            text = "Console"
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleText)

        fun headerActionButton(label: String, bgColor: String? = null, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                setTextColor(Color.parseColor(textHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minWidth = dp(32)
                minHeight = dp(30)
                setPadding(dp(7), dp(4), dp(7), dp(4))
                if (bgColor != null) {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(Color.parseColor(bgColor))
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(4)
                }
                setOnClickListener { onClick() }
            }
        }

        val toggleServerBtn = headerActionButton("Start", accentHex) {
            when (repository.status.value) {
                ServerStatus.STOPPED -> repository.startServer(currentAnsiEnabled)
                ServerStatus.RUNNING -> repository.stopServer()
                else -> {}
            }
        }.apply {
            setTextColor(if (currentConsoleTokens.isDark) Color.parseColor("#0B1528") else Color.WHITE)
        }
        serverToggleBtn = toggleServerBtn
        header.addView(toggleServerBtn)

        val tuneBtn = headerActionButton("⚙") {
            showQuickControls = !showQuickControls
            quickControlsRow?.visibility = if (showQuickControls) View.VISIBLE else View.GONE
        }
        header.addView(tuneBtn)

        val resizeBtn = headerActionButton("⤢") {
            isLargePanel = !isLargePanel
            val newW = if (isLargePanel) dp(364) else dp(334)
            val newH = if (isLargePanel) dp(440) else dp(330)
            panel.layoutParams = FrameLayout.LayoutParams(newW, newH)
            runCatching { windowManager.updateViewLayout(rootContainer, params) }
        }
        header.addView(resizeBtn)

        val copyBtn = headerActionButton("Copy") {
            val plain = latestLines.joinToString("\n") { AnsiParser.stripAnsi(it.rawText) }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BlueLight Console", plain))
            Toast.makeText(this, R.string.message_copied, Toast.LENGTH_SHORT).show()
        }
        header.addView(copyBtn)

        val minimizeBtn = headerActionButton("—") {
            setExpanded(false)
        }
        header.addView(minimizeBtn)

        val closeBtn = headerActionButton("✕") {
            stopSelf()
        }
        header.addView(closeBtn)

        // Drag panel by header and persist position
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    val dm = resources.displayMetrics
                    params.x = (startX + dx).coerceIn(0, (dm.widthPixels - dp(120)).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(0, (dm.heightPixels - dp(120)).coerceAtLeast(0))
                    panelX = params.x
                    panelY = params.y
                    runCatching { windowManager.updateViewLayout(rootContainer, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    savePositions()
                    true
                }
                else -> false
            }
        }
        panel.addView(header)

        // Mini Size & Opacity Controls Drawer (collapsible via ⚙ button)
        val controlsDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(toolbarHex))
            setPadding(dp(10), dp(4), dp(10), dp(6))
            visibility = View.GONE
        }

        val sizeBtn = TextView(this).apply {
            text = "Size: ${bubbleSize.label}"
            setTextColor(Color.parseColor(accentHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor(bgHex))
                setStroke(dp(1), Color.parseColor(borderHex))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                val nextOrdinal = (bubbleSize.ordinal + 1) % OverlayBubbleSize.entries.size
                prefs.edit().putInt(KEY_OVERLAY_BUBBLE_SIZE, nextOrdinal).apply()
            }
        }
        sizeCycleBtn = sizeBtn
        controlsDrawer.addView(sizeBtn)

        val opacityBtn = TextView(this).apply {
            text = "Opacity: $opacityPercent%"
            setTextColor(Color.parseColor(accentHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor(bgHex))
                setStroke(dp(1), Color.parseColor(borderHex))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val steps = listOf(100, 85, 70, 55)
                val nextVal = steps.firstOrNull { it < opacityPercent } ?: 100
                prefs.edit().putInt(KEY_OVERLAY_OPACITY, nextVal).apply()
            }
        }
        opacityCycleBtn = opacityBtn
        controlsDrawer.addView(opacityBtn)

        quickControlsRow = controlsDrawer
        panel.addView(controlsDrawer)

        // Scrollable Console Log Area
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val logTv = TextView(this).apply {
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSizeSp)
            setTextColor(Color.parseColor(textHex))
            setTextIsSelectable(true)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(logTv)
        logScrollView = scroll
        logTextView = logTv
        panel.addView(scroll)

        // Customizable 1-tap Command Macros Strip
        val chipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor(toolbarHex))
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        macrosRow = chipsRow
        chipsScroll.addView(chipsRow)
        panel.addView(chipsScroll)

        // Command Input + Send Row
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            setBackgroundColor(Color.parseColor(toolbarHex))
        }

        val input = EditText(this).apply {
            hint = "Command..."
            setHintTextColor(Color.parseColor(hintHex))
            setTextColor(Color.parseColor(textHex))
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor(bgHex))
                setStroke(dp(1), Color.parseColor(borderHex))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        commandInput = input
        inputRow.addView(input)

        val sendBtn = Button(this).apply {
            text = "Send"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (currentConsoleTokens.isDark) Color.parseColor("#0B1528") else Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor(accentHex))
            }
            minWidth = dp(56)
            minHeight = dp(38)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        fun submitCommand() {
            val text = input.text?.toString().orEmpty().trim()
            if (text.isNotEmpty()) {
                repository.writeCommand(text)
                input.setText("")
            }
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submitCommand()
                true
            } else {
                false
            }
        }
        sendBtn.setOnClickListener { submitCommand() }
        inputRow.addView(sendBtn)
        panel.addView(inputRow)

        return panel
    }

    private fun rebuildMacroChips() {
        val row = macrosRow ?: return
        row.removeAllViews()

        val monoTypeface = runCatching {
            ResourcesCompat.getFont(this, R.font.jetbrains_mono)
        }.getOrNull() ?: Typeface.MONOSPACE
        val bgHex = if (currentConsoleTokens.isDark) "#EE0D1117" else "#F5F8FAFC"
        val borderHex = if (currentConsoleTokens.isDark) "#30363D" else "#CBD5E1"
        val accentHex = if (currentConsoleTokens.isDark) "#38BDF8" else "#1D4ED8"

        val items = if (macros.isEmpty()) BlueLightPreferencesCodec.defaultOverlayMacros() else macros
        items.forEach { macro ->
            val chip = TextView(this).apply {
                text = macro.label
                typeface = monoTypeface
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(Color.parseColor(accentHex))
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor(bgHex))
                    setStroke(dp(1), Color.parseColor(borderHex))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    repository.writeCommand(macro.command)
                }
            }
            row.addView(chip)
        }
    }

    private fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        val params = panelLayoutParams ?: return
        val bubble = bubbleView ?: return
        val panel = panelView ?: return

        if (expanded) {
            bubble.visibility = View.GONE
            panel.visibility = View.VISIBLE
            params.x = panelX
            params.y = panelY
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            renderLogs(latestLines)
        } else {
            hideKeyboard()
            panel.visibility = View.GONE
            bubble.visibility = View.VISIBLE
            params.x = bubbleX
            params.y = bubbleY
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        runCatching { windowManager.updateViewLayout(rootContainer, params) }
    }

    private fun hideKeyboard() {
        val input = commandInput ?: return
        input.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun observeState() {
        serviceScope.launch {
            repository.consoleLines.collectLatest { lines ->
                latestLines = lines
                if (isExpanded) {
                    renderLogs(lines)
                }
            }
        }

        serviceScope.launch {
            repository.status.collectLatest { status ->
                val color = when (status) {
                    ServerStatus.RUNNING -> Color.parseColor("#34D399")
                    ServerStatus.STARTING, ServerStatus.STOPPING -> Color.parseColor("#FBBF24")
                    ServerStatus.STOPPED -> Color.parseColor("#FB7185")
                }
                (statusDotBubble?.background as? GradientDrawable)?.setColor(color)
                (statusDotPanel?.background as? GradientDrawable)?.setColor(color)

                serverToggleBtn?.apply {
                    text = when (status) {
                        ServerStatus.RUNNING -> "Stop"
                        ServerStatus.STARTING -> "..."
                        ServerStatus.STOPPING -> "..."
                        ServerStatus.STOPPED -> "Start"
                    }
                    isEnabled = status == ServerStatus.STOPPED || status == ServerStatus.RUNNING
                }
            }
        }
    }

    private fun renderLogs(lines: List<ConsoleLine>) {
        val tv = logTextView ?: return
        val scroll = logScrollView ?: return
        val tail = if (lines.size > MAX_OVERLAY_LINES) lines.takeLast(MAX_OVERLAY_LINES) else lines
        val ssb = SpannableStringBuilder()
        tail.forEachIndexed { index, line ->
            ssb.append(AnsiParser.parseToSpanned(line.rawText, currentAnsiEnabled, currentConsoleTokens))
            if (index < tail.lastIndex) {
                ssb.append("\n")
            }
        }
        tv.text = ssb
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        const val CHANNEL_ID = "bluelight_overlay_channel"
        const val NOTIFICATION_ID = 1338
        const val MAX_OVERLAY_LINES = 250
        const val ACTION_STOP_OVERLAY = "com.example.service.ACTION_STOP_OVERLAY"
        const val ACTION_EXPAND_OVERLAY = "com.example.service.ACTION_EXPAND_OVERLAY"
        const val ACTION_REFRESH_CONFIG = "com.example.service.ACTION_REFRESH_CONFIG"

        const val KEY_BUBBLE_X = "overlay_bubble_x"
        const val KEY_BUBBLE_Y = "overlay_bubble_y"
        const val KEY_PANEL_X = "overlay_panel_x"
        const val KEY_PANEL_Y = "overlay_panel_y"
        const val KEY_OVERLAY_BUBBLE_SIZE = "overlay_bubble_size"
        const val KEY_OVERLAY_OPACITY = "overlay_opacity_percent"
        const val KEY_OVERLAY_MACROS = "overlay_macros_json"

        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

        fun start(context: Context, expandImmediately: Boolean = false) {
            val intent = Intent(context, FloatingConsoleService::class.java).apply {
                if (expandImmediately) action = ACTION_EXPAND_OVERLAY
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingConsoleService::class.java))
        }

        fun refreshConfig(context: Context) {
            if (!_isOverlayActive.value) return
            val intent = Intent(context, FloatingConsoleService::class.java).apply {
                action = ACTION_REFRESH_CONFIG
            }
            context.startService(intent)
        }
    }
}
