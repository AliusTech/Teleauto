package tech.alius.rc.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.text.TextUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.TextView
import tech.alius.rc.device.TeleautoDeviceCapability
import tech.alius.rc.device.TeleautoDeviceCategory
import tech.alius.rc.device.TeleautoDeviceSnapshot
import tech.alius.rc.device.TeleautoDeviceType
import tech.alius.rc.model.TeleautoTransmitModuleInfo
import tech.alius.rc.video.TeleautoVideoTransmissionModuleInfo
import tech.alius.rc.video.TeleautoVideoReceiverConfig
import tech.alius.rc.video.TeleautoVideoStats

data class TeleautoDeviceCreationSelection(
    val type: TeleautoDeviceType,
    val capabilities: Set<TeleautoDeviceCapability>,
    val localId: String? = null,
)

data class TeleautoManagedDeviceEntry(
    val localId: String,
    val type: TeleautoDeviceType,
    val capabilities: Set<TeleautoDeviceCapability>,
    val displayName: String = type.displayName,
    val connectionState: tech.alius.rc.device.TeleautoDeviceConnectionState = tech.alius.rc.device.TeleautoDeviceConnectionState.DISCONNECTED,
    val active: Boolean = false,
)

/** Full-screen device-management surface with a stable left navigation rail. */
class TeleautoDeviceManagementView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var onComplete: ((TeleautoDeviceCreationSelection) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onDeviceActivated: ((String) -> Unit)? = null
    var onRemoveDevice: ((String) -> Unit)? = null
    var onMediaStorageSelected: ((external: Boolean) -> Unit)? = null
    var onVideoReceiverConfigChanged: ((TeleautoVideoReceiverConfig) -> Unit)? = null
    var onVideoTestChanged: ((Boolean) -> Unit)? = null

    private enum class Page { MANAGEMENT, ADD, MODULE_TRANSMIT, MODULE_VIDEO, SETTINGS_STORAGE, SETTINGS_VERSION }
    private var currentPage = Page.MANAGEMENT

    private val managementNav = navigationButton("设备管理") { showPage(Page.MANAGEMENT) }
    private var modulesExpanded = false
    private val transmitNav = navigationButton("发射模块") { showPage(Page.MODULE_TRANSMIT) }
    private val videoNav = navigationButton("图传模块") { showPage(Page.MODULE_VIDEO) }
    private val modulesNav = navigationButton("模块管理") {
        modulesExpanded = !modulesExpanded
        updateNavigationHierarchy()
        if (modulesExpanded && currentPage != Page.MODULE_TRANSMIT && currentPage != Page.MODULE_VIDEO) {
            showPage(Page.MODULE_TRANSMIT)
        } else if (!modulesExpanded) {
            post(::focusCurrentPage)
        }
    }
    private var settingsExpanded = false
    private val storageNav = navigationButton("存储管理") { showPage(Page.SETTINGS_STORAGE) }
    private val versionNav = navigationButton("版本信息") { showPage(Page.SETTINGS_VERSION) }
    private val settingsNav = navigationButton("系统设置") {
        settingsExpanded = !settingsExpanded
        updateNavigationHierarchy()
        if (settingsExpanded && currentPage != Page.SETTINGS_STORAGE && currentPage != Page.SETTINGS_VERSION) {
            showPage(Page.SETTINGS_STORAGE)
        } else if (!settingsExpanded) {
            post(::focusCurrentPage)
        }
    }
    private val returnNav = navigationButton("返回控制台") { onCancel?.invoke() }
    var onMediaStorageRefreshRequested: (() -> Unit)? = null
    private var storagePopup: android.widget.PopupWindow? = null
    private var storageOptions = emptyList<TeleautoStorageChoice>()
    private val mediaStorageSelector = Button(context).apply {
        background = TeleautoMaterialAssets.keycapSelector(context)
        setTextColor(TeleautoUiTokens.TEXT)
        isAllCaps = false
        isFocusable = true
        setOnClickListener { showStorageAlternatives() }
    }
    private fun showStorageAlternatives() {
        if (storageOptions.isEmpty()) return
        storagePopup?.dismiss()
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(TeleautoUiTokens.SPACE_1).toInt(), context.dp(TeleautoUiTokens.SPACE_1).toInt(),
                context.dp(TeleautoUiTokens.SPACE_1).toInt(), context.dp(TeleautoUiTokens.SPACE_1).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(TeleautoUiTokens.SURFACE)
                cornerRadius = context.dp(TeleautoUiTokens.RADIUS_SM)
            }
        }
        storageOptions.forEach { option ->
            rows.addView(Button(context).apply {
                text = option.label
                isAllCaps = false
                isEnabled = option.enabled
                setTextColor(if (option.enabled) TeleautoUiTokens.TEXT else TeleautoUiTokens.TEXT_FAINT)
                background = TeleautoMaterialAssets.keycapSelector(context)
                setOnClickListener { storagePopup?.dismiss(); onMediaStorageSelected?.invoke(option.external) }
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt()))
        }
        storagePopup = android.widget.PopupWindow(rows, mediaStorageSelector.width, LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            showAsDropDown(mediaStorageSelector, 0, context.dp(TeleautoUiTokens.SPACE_1).toInt())
        }
    }
    private val externalStorageUsage = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progressTintList = android.content.res.ColorStateList.valueOf(TeleautoUiTokens.TEXT_FAINT)
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(TeleautoUiTokens.SURFACE_PRESSED)
    }
    private val managementContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(30f).toInt(), context.dp(24f).toInt(), context.dp(30f).toInt(), context.dp(24f).toInt())
    }
    private val creationContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(30f).toInt(), context.dp(20f).toInt(), context.dp(30f).toInt(), context.dp(20f).toInt())
    }
    private val settingsContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(30f).toInt(), context.dp(24f).toInt(), context.dp(30f).toInt(), context.dp(24f).toInt())
    }
    private val transmitModuleContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(30f).toInt(), context.dp(24f).toInt(), context.dp(30f).toInt(), context.dp(24f).toInt())
    }
    private val videoSettingsContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(30f).toInt(), context.dp(20f).toInt(), context.dp(30f).toInt(), context.dp(20f).toInt())
    }
    private val versionSettingsContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(30f).toInt(), context.dp(24f).toInt(), context.dp(30f).toInt(), context.dp(24f).toInt())
    }
    private val transmitModuleStatus = TeleautoTransmitModuleStatusView(context)
    private val videoModuleStatus = TeleautoVideoModuleStatusView(context)
    private val videoTransmissionSettings = TeleautoVideoTransmissionSettingsView(context).apply {
        onConfigChanged = { onVideoReceiverConfigChanged?.invoke(it) }
        onTestChanged = { onVideoTestChanged?.invoke(it) }
    }
    private val versionList = TeleautoSettingsListView(context)
    private val mediaStorageValue = TextView(context).apply {
        text = "系统图库 · DCIM/Teleauto"
        setTextColor(TeleautoUiTokens.TEXT_SECONDARY)
        textSize = 11f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }
    private var managedDevices: List<TeleautoManagedDeviceEntry> = emptyList()
    private var editingExisting = false
    private var editingLocalId: String? = null

    private val categoryRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val typeGrid = GridLayout(context).apply {
        columnCount = TeleautoUiTokens.DEVICE_GRID_COLUMNS
        alignmentMode = GridLayout.ALIGN_BOUNDS
        useDefaultMargins = false
    }
    private val typeScroller = ScrollView(context).apply {
        isFillViewport = true
        addView(typeGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
    private val videoCheck = CheckBox(context).apply {
        text = "此设备携带图传"
        setTextColor(TeleautoUiTokens.TEXT)
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        buttonTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(TeleautoUiTokens.ACCENT, TeleautoUiTokens.TEXT_SECONDARY),
        )
    }
    private val complete = Button(context).apply {
        text = "完成创建"
        isAllCaps = false
        setTextColor(TeleautoUiTokens.TEXT)
        textSize = 14f
        isEnabled = false
        alpha = 0.38f
        stateListAnimator = null
    }
    private val formBack = Button(context).apply {
        text = "返回设备管理"
        isAllCaps = false
        setTextColor(TeleautoUiTokens.TEXT_SECONDARY)
        textSize = 14f
        stateListAnimator = null
    }
    private val cards = linkedMapOf<TeleautoDeviceType, TeleautoDeviceCardView>()
    private val categoryButtons = linkedMapOf<TeleautoDeviceCategory, android.widget.TextView>()
    private var availableTypes: List<TeleautoDeviceType> = emptyList()
    private var selectedCategory: TeleautoDeviceCategory? = null
    private var selectedType: TeleautoDeviceType? = null
    private val dialogOverlay = TeleautoOverlayDialogView(context)
    private val inputHandler = Handler(Looper.getMainLooper())
    private var centerPressedView: View? = null
    private var centerLongPerformed = false
    private val centerLongPress = Runnable {
        val target = centerPressedView ?: return@Runnable
        centerLongPerformed = target.performLongClick()
    }

    private val controllerTitle = TextView(context).apply {
        text = "遥控器"
        setTextColor(TeleautoUiTokens.TEXT)
        textSize = TeleautoUiTokens.TYPE_APP_HEADER
        gravity = Gravity.CENTER
        typeface = TeleautoUiTypography.bold(context)
    }
    fun setControllerName(name: String) { controllerTitle.text = name }

    init {
        // The host console background remains visible so settings and dashboard share one texture layer.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        isFocusable = false
        val shell = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val rail = TeleautoSettingsNavigationView(context)
        rail.addView(managementNav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(44f).toInt()))
        rail.addView(modulesNav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(44f).toInt()).apply { topMargin = context.dp(6f).toInt() })
        rail.addView(transmitNav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(38f).toInt()).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_1).toInt() })
        rail.addView(videoNav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(38f).toInt()).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_1).toInt() })
        rail.addView(settingsNav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(44f).toInt()).apply { topMargin = context.dp(6f).toInt() })
        rail.addView(storageNav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(38f).toInt()).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_1).toInt() })
        rail.addView(versionNav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(38f).toInt()).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_1).toInt() })
        rail.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        rail.addView(returnNav.apply {
            setTextColor(TeleautoUiTokens.TEXT)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_arrow_back, 0, 0, 0)
            compoundDrawablePadding = context.dp(8f).toInt()
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(TeleautoUiTokens.TEXT)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(44f).toInt()))

        buildCreationContent()
        buildSettingsContent()
        buildTransmitModuleContent()
        buildVideoSettingsContent()
        buildVersionSettingsContent()
        val contentHost = FrameLayout(context).apply {
            addView(managementContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(creationContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(settingsContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(transmitModuleContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(videoSettingsContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(versionSettingsContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        shell.addView(rail, LinearLayout.LayoutParams(context.dp(176f).toInt(), LinearLayout.LayoutParams.MATCH_PARENT))
        shell.addView(contentHost, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        val pageRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(settingsTopBar(), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dp(TeleautoUiTokens.TOP_BAR_HEIGHT).toInt(),
            ))
            addView(shell, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(pageRoot, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(dialogOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        formBack.background = buttonBackground(false)
        complete.background = buttonBackground(true)
        formBack.setOnClickListener { showPage(Page.MANAGEMENT) }
        complete.setOnClickListener {
            val type = selectedType ?: return@setOnClickListener
            onComplete?.invoke(TeleautoDeviceCreationSelection(type, if (videoCheck.isChecked) setOf(TeleautoDeviceCapability.VIDEO_TRANSMISSION) else emptySet(), editingLocalId))
        }
        setDeviceTypes(TeleautoDeviceType.selectable())
        updateNavigationHierarchy()
        applyTypography(this)
    }

    private fun settingsTopBar() = FrameLayout(context).apply {
        background = TeleautoMaterialAssets.topBar(context)
        val left = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(
                context.dp(TeleautoUiTokens.TOP_BAR_HORIZONTAL_INSET).toInt(),
                0,
                context.dp(TeleautoUiTokens.TOP_BAR_HORIZONTAL_INSET).toInt(),
                0,
            )
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_dji_wordmark)
                imageTintList = android.content.res.ColorStateList.valueOf(TeleautoUiTokens.TEXT)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(
                context.dp(TeleautoUiTokens.TOP_BAR_LOGO_HEIGHT * TeleautoUiTokens.TOP_BAR_LOGO_ASPECT).toInt(),
                context.dp(TeleautoUiTokens.TOP_BAR_LOGO_HEIGHT).toInt(),
            ))
            addView(TextView(context).apply {
                text = "控制中心"
                setTextColor(TeleautoUiTokens.TEXT)
                textSize = TeleautoUiTokens.TYPE_APP_HEADER
                typeface = TeleautoUiTypography.bold(context)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(10f).toInt()
            })
            setOnClickListener { onCancel?.invoke() }
        }
        addView(left, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START))
        addView(controllerTitle, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    private fun buildCreationContent() {
        creationContent.addView(pageHeader("新增设备", R.drawable.ic_device_ground_rover).apply {
            findViewWithTag<TextView>("pageTitle")?.apply {
            tag = "formTitle"
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(52f).toInt()))
        creationContent.addView(TextView(context).apply {
            text = "选择设备类型并配置其硬件能力"
            setTextColor(TeleautoUiTokens.TEXT_SECONDARY)
            textSize = 11f
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(5f).toInt(); bottomMargin = context.dp(16f).toInt()
        })
        creationContent.addView(categoryRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(44f).toInt()))
        creationContent.addView(TextView(context).apply {
            text = "选择具体类型"; setTextColor(TeleautoUiTokens.TEXT_SECONDARY); textSize = 10f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(30f).toInt()).apply {
            topMargin = context.dp(6f).toInt()
        })
        creationContent.addView(typeScroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        creationContent.addView(videoCheck, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(42f).toInt()).apply { topMargin = context.dp(8f).toInt() })
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(formBack, LinearLayout.LayoutParams(context.dp(146f).toInt(), context.dp(42f).toInt()).apply { marginEnd = context.dp(10f).toInt() })
        actions.addView(complete, LinearLayout.LayoutParams(context.dp(156f).toInt(), context.dp(44f).toInt()))
        creationContent.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END; topMargin = context.dp(8f).toInt()
        })
    }

    private fun buildSettingsContent() {
        transmitModuleStatus.submit(emptyList())
        videoModuleStatus.submit(emptyList())
        settingsContent.addView(pageHeader("存储管理", R.drawable.ic_settings), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(52f).toInt()))
        settingsContent.addView(TextView(context).apply {
            text = "选择照片与 MP4 录像的保存位置"; setTextColor(TeleautoUiTokens.TEXT_SECONDARY); textSize = 11f
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(5f).toInt(); bottomMargin = context.dp(14f).toInt()
        })
        settingsContent.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(20f).toInt(), 0, context.dp(14f).toInt(), 0)
            background = panelBackground()
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "存储位置选择"; setTextColor(TeleautoUiTokens.TEXT); textSize = 15f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
                addView(mediaStorageValue, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = context.dp(TeleautoUiTokens.SPACE_1).toInt()
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(externalStorageUsage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(4f).toInt()).apply {
                    bottomMargin = context.dp(TeleautoUiTokens.SPACE_2).toInt()
                })
                addView(mediaStorageSelector, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt()))
            }, LinearLayout.LayoutParams(context.dp(300f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(TeleautoUiTokens.SPACE_2).toInt()
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(78f).toInt()).apply {
            topMargin = context.dp(TeleautoUiTokens.SPACE_3).toInt()
        })
    }

    private fun buildVideoSettingsContent() {
        videoSettingsContent.addView(pageHeader("图传模块", R.drawable.ic_settings), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, context.dp(52f).toInt(),
        ))
        videoSettingsContent.addView(videoTransmissionSettings, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_2).toInt() })
    }

    private fun buildTransmitModuleContent() {
        transmitModuleStatus.submit(emptyList())
        transmitModuleContent.addView(pageHeader("发射模块", R.drawable.ic_settings), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, context.dp(52f).toInt(),
        ))
        transmitModuleContent.addView(transmitModuleStatus, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.MODULE_STATUS_HEIGHT).toInt(),
        ).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_2).toInt() })
    }

    private fun buildVersionSettingsContent() {
        versionSettingsContent.addView(pageHeader("版本信息", R.drawable.ic_settings), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, context.dp(52f).toInt(),
        ))
        versionSettingsContent.addView(versionList, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = context.dp(TeleautoUiTokens.SPACE_3).toInt()
        })
    }

    fun setDeviceTypes(types: List<TeleautoDeviceType>) {
        availableTypes = types.filter { it != TeleautoDeviceType.REMOTE_CONTROLLER && it.category != null }
        categoryRow.removeAllViews()
        categoryButtons.clear()
        cards.clear()
        TeleautoDeviceCategory.entries.filter { category -> availableTypes.any { it.category == category } }.forEach { category ->
            val button = android.widget.TextView(context).apply {
                text = category.displayName
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setTextColor(TeleautoUiTokens.TEXT)
                isClickable = true
                // 直接设置背景,不依赖 Button 的内部状态
                background = TeleautoMaterialAssets.keycap(context)
                setPadding(0, context.dp(10f).toInt(), 0, context.dp(10f).toInt())
                setOnClickListener { selectCategory(category) }
            }
            categoryButtons[category] = button
            categoryRow.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = context.dp(4f).toInt(); marginEnd = context.dp(4f).toInt()
            })
        }
        selectedCategory = null
        typeGrid.removeAllViews()
        refreshCategories()
    }

    fun submit(snapshot: TeleautoDeviceSnapshot?) {
        submitManagedDevices(snapshot?.let { listOf(it.toManagedEntry(active = true)) }.orEmpty())
    }

    fun updateManagedDevice(snapshot: TeleautoDeviceSnapshot?) {
        updateManagedDevices(snapshot?.let { listOf(it.toManagedEntry(active = true)) }.orEmpty())
    }

    fun showManagement(snapshot: TeleautoDeviceSnapshot?) {
        managedDevices = snapshot?.let { listOf(it.toManagedEntry(active = true)) }.orEmpty()
        renderManagement()
        showPage(Page.MANAGEMENT)
    }

    fun submitManagedDevices(devices: List<TeleautoManagedDeviceEntry>) {
        managedDevices = devices.toList()
        renderManagement()
        showPage(Page.MANAGEMENT)
    }

    fun updateManagedDevices(devices: List<TeleautoManagedDeviceEntry>) {
        managedDevices = devices.toList()
        renderManagement()
    }

    fun showManagedDevices(devices: List<TeleautoManagedDeviceEntry>) {
        managedDevices = devices.toList()
        renderManagement()
        showPage(Page.MANAGEMENT)
    }

    fun showSettings(appVersion: String, sdkVersion: String) {
        versionList.submit(listOf(
            TeleautoSettingsListItem("app_version", "App 版本", appVersion),
            TeleautoSettingsListItem("sdk_version", "SDK 版本", sdkVersion),
        ))
        settingsExpanded = true
        updateNavigationHierarchy()
        showPage(Page.SETTINGS_STORAGE)
    }

    fun submitVideoTransmissionModules(modules: List<TeleautoVideoTransmissionModuleInfo>) {
        videoModuleStatus.submit(modules)
        videoTransmissionSettings.submitModules(modules)
    }

    fun submitTransmitModules(modules: List<TeleautoTransmitModuleInfo>) {
        transmitModuleStatus.submit(modules)
    }

    fun submitMediaStorage(label: String) {
        mediaStorageValue.text = label
    }

    fun submitMediaStorageOptions(
        internal: String,
        external: String,
        selectedExternal: Boolean,
        externalUsedPercent: Int?,
        internalEnabled: Boolean,
        externalEnabled: Boolean,
        externalPresent: Boolean = externalEnabled,
    ) {
        storagePopup?.dismiss()
        val currentExternal = selectedExternal && externalPresent
        storageOptions = TeleautoStorageChoice.alternatives(currentExternal, externalPresent, internalEnabled, externalEnabled, internal, external)
        mediaStorageSelector.text = if (currentExternal) external else internal
        val arrow = if (storageOptions.isEmpty()) null else context.getDrawable(R.drawable.ic_nav_expand)
        arrow?.setBounds(0, 0, context.dp(TeleautoUiTokens.SPACE_4).toInt(), context.dp(TeleautoUiTokens.SPACE_4).toInt())
        mediaStorageSelector.setCompoundDrawables(null, null, arrow, null)
        mediaStorageSelector.isClickable = storageOptions.isNotEmpty()
        externalStorageUsage.visibility = if (currentExternal && externalUsedPercent != null) View.VISIBLE else View.GONE
        externalStorageUsage.progress = externalUsedPercent?.coerceIn(0, 100) ?: 0
    }

    fun submitVideoReceiverConfig(config: TeleautoVideoReceiverConfig) = videoTransmissionSettings.submitConfig(config)

    fun submitVideoTestStats(stats: TeleautoVideoStats) = videoTransmissionSettings.submitStats(stats)

    fun stopVideoTest() = videoTransmissionSettings.stopTest()

    fun dismissOverlay(): Boolean {
        if (storagePopup?.isShowing == true) { storagePopup?.dismiss(); return true }
        return dialogOverlay.dismissIfShowing()
    }

    /** Restores the stable entry focus after page transitions or dynamic updates. */
    fun focusCurrentPage() {
        // The control centre is normally opened by touch. A plain requestFocus()
        // is rejected while Android remains in touch mode because the navigation
        // buttons deliberately are not focusable-in-touch-mode (that preserves
        // one-tap touch activation). Hardware navigation must explicitly leave
        // touch mode before establishing its deterministic entry focus.
        currentNavigation().requestFocusFromTouch()
    }

    /** Entry point for the remote controller's physical 5D navigation events. */
    fun dispatchFiveWayKeyEvent(event: KeyEvent): Boolean {
        val focused = findFocus()
        if (focused == null || !isDescendant(focused)) {
            focusCurrentPage()
        }
        return dispatchKeyEvent(event)
    }

    private fun beginAdd() = beginForm(null)

    private fun beginForm(initial: TeleautoDeviceCreationSelection?) {
        editingExisting = initial != null
        editingLocalId = initial?.localId
        creationContent.findViewWithTag<TextView>("formTitle")?.text = if (editingExisting) "编辑设备" else "新增设备"
        complete.text = if (editingExisting) "保存配置" else "完成创建"
        videoCheck.isChecked = TeleautoDeviceCapability.VIDEO_TRANSMISSION in initial?.capabilities.orEmpty()
        videoCheck.visibility = View.VISIBLE
        videoCheck.isEnabled = true
        selectedType = null
        selectedCategory = null
        complete.isEnabled = false
        complete.alpha = 0.38f
        val initialType = initial?.type?.takeIf { it in availableTypes }
        val initialCategory = initialType?.category
        if (initialCategory != null) {
            selectCategory(initialCategory)
            select(initialType)
        } else {
            // 新建设备:自动选中第一个分类(航空设备),直接展示具体类型列表
            categoryButtons.keys.firstOrNull()?.let { selectCategory(it) }
                ?: refreshCategories()
        }
        showPage(Page.ADD)
    }

    private var renderedDevices: List<TeleautoManagedDeviceEntry>? = null
    private val managementCards = linkedMapOf<String, TeleautoDeviceCardView>()

    private fun bindManagedCard(card: TeleautoDeviceCardView, device: TeleautoManagedDeviceEntry) {
        val capabilityLabel = when {
            device.type == TeleautoDeviceType.CYBERBRICK -> "蓝牙接收端"
            device.type.isVirtualHid -> "虚拟 HID"
            TeleautoDeviceCapability.VIDEO_TRANSMISSION in device.capabilities -> "已配置图传"
            else -> "未配置图传"
        }
        card.bindManagement(
            type = device.type,
            title = device.displayName,
            meta = "${device.type.category?.displayName ?: "设备"} · $capabilityLabel",
            status = "${connectionLabel(device.connectionState)}${if (device.active) " · 当前设备" else ""}",
            active = device.active,
            onOpen = { openManagedDevice(device) },
            onLongPress = { showManagedDeviceActions(device) },
        )
    }

    private fun renderManagement() {
        val previous = renderedDevices
        if (previous == managedDevices) return
        if (previous != null && previous.map { it.localId } == managedDevices.map { it.localId }) {
            managedDevices.forEachIndexed { index, device ->
                if (device != previous[index]) managementCards[device.localId]?.let { bindManagedCard(it, device) }
            }
            renderedDevices = managedDevices.toList()
            return
        }
        renderedDevices = managedDevices.toList()
        managementCards.clear()
        val previousFocusTag = findFocus()?.tag
        managementContent.removeAllViews()
        managementContent.addView(pageHeader("设备管理", R.drawable.ic_device_ground_rover), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(52f).toInt()))
        managementContent.addView(android.view.View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(18f).toInt()))  // 顶部间距

        val grid = createDeviceGrid()
        managedDevices.forEachIndexed { index, device ->
            val card = TeleautoDeviceCardView(context)
            bindManagedCard(card, device)
            managementCards[device.localId] = card
            card.tag = "device:${device.localId}"
            grid.addView(card, deviceGridLayoutParams(index))
        }
        val addIndex = managedDevices.size
        grid.addView(TeleautoDeviceCardView(context).apply {
            tag = "device:add"
            bindAdd(::beginAdd)
        }, deviceGridLayoutParams(addIndex))
        fillDeviceGridRow(grid, addIndex + 1)
        managementContent.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        if (currentPage == Page.MANAGEMENT && previousFocusTag != null) {
            grid.findViewWithTag<View>(previousFocusTag)?.requestFocus()
        }
    }

    private fun openManagedDevice(device: TeleautoManagedDeviceEntry) {
        if (device.active) {
            onCancel?.invoke()
            return
        }
        dialogOverlay.showConfirmation(
            title = "切换到${device.displayName}？",
            message = "当前设备将断开，确定后连接所选设备并返回控制台。",
            confirmLabel = "切换",
        ) { onDeviceActivated?.invoke(device.localId) }
    }

    private fun showManagedDeviceActions(device: TeleautoManagedDeviceEntry) {
        dialogOverlay.showActions(
            title = device.displayName,
            message = "选择要执行的设备管理操作。",
            editLabel = "编辑设备",
            removeLabel = "移除设备",
            onEdit = { beginForm(TeleautoDeviceCreationSelection(device.type, device.capabilities, device.localId)) },
            onRemove = { confirmRemove(device) },
        )
    }

    private fun confirmRemove(device: TeleautoManagedDeviceEntry) {
        dialogOverlay.showConfirmation(
            title = "移除${device.displayName}？",
            message = "设备配置将从当前遥控器删除。",
            confirmLabel = "移除",
            danger = true,
        ) { onRemoveDevice?.invoke(device.localId) }
    }

    private fun connectionLabel(state: tech.alius.rc.device.TeleautoDeviceConnectionState) = when (state) {
        tech.alius.rc.device.TeleautoDeviceConnectionState.CONNECTED -> "已连接"
        tech.alius.rc.device.TeleautoDeviceConnectionState.CONNECTING -> "正在连接"
        tech.alius.rc.device.TeleautoDeviceConnectionState.ERROR -> "连接异常"
        else -> "未连接"
    }

    private fun TeleautoDeviceSnapshot.toManagedEntry(active: Boolean) = TeleautoManagedDeviceEntry(
        localId = id.value,
        type = type,
        capabilities = capabilities,
        displayName = info.displayName,
        connectionState = connectionState,
        active = active,
    )

    private fun showPage(page: Page) {
        storagePopup?.dismiss()
        if (page == Page.SETTINGS_STORAGE) onMediaStorageRefreshRequested?.invoke()
        dialogOverlay.dismissIfShowing()
        if (currentPage == Page.MODULE_VIDEO && page != Page.MODULE_VIDEO) videoTransmissionSettings.stopTest()
        currentPage = page
        managementContent.visibility = if (page == Page.MANAGEMENT) View.VISIBLE else View.GONE
        creationContent.visibility = if (page == Page.ADD) View.VISIBLE else View.GONE
        transmitModuleContent.visibility = if (page == Page.MODULE_TRANSMIT) View.VISIBLE else View.GONE
        settingsContent.visibility = if (page == Page.SETTINGS_STORAGE) View.VISIBLE else View.GONE
        videoSettingsContent.visibility = if (page == Page.MODULE_VIDEO) View.VISIBLE else View.GONE
        versionSettingsContent.visibility = if (page == Page.SETTINGS_VERSION) View.VISIBLE else View.GONE
        updateNavigationSelection()
        applyTypography(currentContent())
        if (!isInTouchMode) focusCurrentPage()
    }

    private fun navigationButton(label: String, click: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; textSize = 12f; gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(context.dp(14f).toInt(), 0, context.dp(10f).toInt(), 0); setTextColor(TeleautoUiTokens.TEXT_SECONDARY)
        stateListAnimator = null
        isFocusable = true
        isFocusableInTouchMode = false
        background = TeleautoMaterialAssets.navigationItem(context)
        setOnClickListener { click() }
    }

    /** Only the visible entry representing the current page gets selected fill. */
    private fun updateNavigationSelection() {
        val active = currentNavigation()
        navigationViews().forEach { styleNavigation(it, it === active) }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) findFocus()?.clearFocus()
        return super.dispatchTouchEvent(event)
    }

    private fun styleNavigation(button: Button, active: Boolean) {
        button.setTextColor(if (active) TeleautoUiTokens.TEXT else TeleautoUiTokens.TEXT_SECONDARY)
        button.isSelected = active
    }

    private fun panelBackground() = TeleautoMaterialAssets.card(context)

    private fun selectCategory(category: TeleautoDeviceCategory) {
        if (selectedCategory == category) return  // 已选中,不重复处理
        selectedCategory = category
        selectedType = null
        videoCheck.visibility = if (category == TeleautoDeviceCategory.VIRTUAL) View.GONE else View.VISIBLE
        complete.isEnabled = false
        complete.alpha = 0.38f
        refreshCategories()      // 切换所有分类按钮视觉
        rebuildTypeGrid(category) // 刷新底部类型列表
        typeGrid.invalidate()
    }

    private fun refreshCategories() {
        categoryButtons.forEach { (category, button) ->
            button.background = if (category == selectedCategory)
                TeleautoMaterialAssets.keycapPressed(context)
            else
                TeleautoMaterialAssets.keycap(context)
        }
    }

    private fun rebuildTypeGrid(category: TeleautoDeviceCategory) {
        typeGrid.removeAllViews()
        cards.clear()
        val types = availableTypes.filter { it.category == category }
        types.forEachIndexed { index, type ->
            val card = TeleautoDeviceCardView(context).apply { bindSelection(type) { select(type) } }
            cards[type] = card
            typeGrid.addView(card, deviceGridLayoutParams(index))
        }
        fillDeviceGridRow(typeGrid, types.size)
    }

    private fun select(type: TeleautoDeviceType) {
        selectedType = type
        val supportsVideoCapability = !type.isBluetoothTarget
        videoCheck.visibility = if (supportsVideoCapability) View.VISIBLE else View.GONE
        videoCheck.isEnabled = supportsVideoCapability
        if (!supportsVideoCapability) videoCheck.isChecked = false
        cards.forEach { (candidate, view) -> view.chosen = candidate == type }
        complete.isEnabled = true
        complete.alpha = 1f
    }

    private fun buttonBackground(primary: Boolean) = TeleautoMaterialAssets.keycapSelector(context)

    private fun pageHeader(title: String, iconRes: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(TeleautoUiTokens.TEXT)
        }, LinearLayout.LayoutParams(context.dp(24f).toInt(), context.dp(24f).toInt()).apply {
            marginEnd = context.dp(12f).toInt()
        })
        addView(TextView(context).apply {
            tag = "pageTitle"
            text = title
            setTextColor(TeleautoUiTokens.TEXT)
            textSize = 23f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun createDeviceGrid() = GridLayout(context).apply {
        columnCount = TeleautoUiTokens.DEVICE_GRID_COLUMNS
        alignmentMode = GridLayout.ALIGN_BOUNDS
        useDefaultMargins = false
    }

    private fun deviceGridLayoutParams(index: Int) = GridLayout.LayoutParams(
        GridLayout.spec(index / TeleautoUiTokens.DEVICE_GRID_COLUMNS),
        GridLayout.spec(index % TeleautoUiTokens.DEVICE_GRID_COLUMNS, 1f),
    ).apply {
        width = 0
        height = context.dp(TeleautoUiTokens.DEVICE_CARD_HEIGHT).toInt()
        val margin = context.dp(TeleautoUiTokens.DEVICE_GRID_CELL_MARGIN).toInt()
        setMargins(margin, margin, margin, margin)
    }

    private fun fillDeviceGridRow(grid: GridLayout, itemCount: Int) {
        val missing = (TeleautoUiTokens.DEVICE_GRID_COLUMNS - itemCount % TeleautoUiTokens.DEVICE_GRID_COLUMNS) %
            TeleautoUiTokens.DEVICE_GRID_COLUMNS
        repeat(missing) { offset ->
            grid.addView(View(context).apply { visibility = View.INVISIBLE }, deviceGridLayoutParams(itemCount + offset))
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isCenterKey = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (isCenterKey && centerPressedView != null) {
            return handleCenterKey(event)
        }
        if (dialogOverlay.isShowing) return dialogOverlay.handleKeyEvent(event)
        if (isCenterKey) return handleCenterKey(event)
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val focused = findFocus()
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (focused in navigationViews()) {
                    firstFocusable(currentContent())?.requestFocus() ?: true
                } else moveFocusWithFallback(View.FOCUS_RIGHT, 1)
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (focused !in navigationViews()) currentNavigation().requestFocus() else false
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (focused in navigationViews()) moveNavigation(-1) else moveFocusWithFallback(View.FOCUS_UP, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (focused in navigationViews()) moveNavigation(1) else moveFocusWithFallback(View.FOCUS_DOWN, 1)
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun handleCenterKey(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (centerPressedView == null) {
                    centerPressedView = findFocus()
                    centerLongPerformed = false
                    inputHandler.postDelayed(centerLongPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                inputHandler.removeCallbacks(centerLongPress)
                val target = centerPressedView
                val wasLongPress = centerLongPerformed
                centerPressedView = null
                centerLongPerformed = false
                if (wasLongPress) true else target?.performClick() == true
            }
            else -> true
        }
    }

    private fun moveFocus(direction: Int): Boolean {
        val current = findFocus() ?: return false
        val next = current.focusSearch(direction) ?: return false
        if (next === this || !isDescendant(next)) return false
        return next.requestFocus()
    }

    private fun moveFocusWithFallback(direction: Int, step: Int): Boolean {
        if (moveFocus(direction)) return true
        val items = focusableContentViews()
        val index = items.indexOf(findFocus())
        if (index < 0 || items.isEmpty()) return false
        val next = (index + step).coerceIn(0, items.lastIndex)
        return next != index && items[next].requestFocus()
    }

    private fun focusableContentViews(): List<View> {
        val result = mutableListOf<View>()
        fun collect(view: View) {
            if (view.visibility != View.VISIBLE || !view.isEnabled) return
            if (view.isFocusable) result += view
            if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
        collect(currentContent())
        return result
    }

    private fun moveNavigation(step: Int): Boolean {
        val items = navigationViews()
        val index = items.indexOf(findFocus())
        if (index < 0) return false
        val next = index + step
        if (next !in items.indices) return false
        return items[next].requestFocus()
    }

    private fun currentNavigation(): Button = when (currentPage) {
        Page.MANAGEMENT, Page.ADD -> managementNav
        Page.MODULE_TRANSMIT -> if (modulesExpanded) transmitNav else modulesNav
        Page.MODULE_VIDEO -> if (modulesExpanded) videoNav else modulesNav
        Page.SETTINGS_STORAGE -> if (settingsExpanded) storageNav else settingsNav
        Page.SETTINGS_VERSION -> if (settingsExpanded) versionNav else settingsNav
    }

    private fun currentContent(): ViewGroup = when (currentPage) {
        Page.MANAGEMENT -> managementContent
        Page.ADD -> creationContent
        Page.MODULE_TRANSMIT -> transmitModuleContent
        Page.MODULE_VIDEO -> videoSettingsContent
        Page.SETTINGS_STORAGE -> settingsContent
        Page.SETTINGS_VERSION -> versionSettingsContent
    }

    private fun navigationViews() = listOf(managementNav, modulesNav, transmitNav, videoNav, settingsNav, storageNav, versionNav, returnNav)
        .filter { it.visibility == View.VISIBLE }

    private fun setNavigationChevron(button: Button, expanded: Boolean) {
        val icon = context.getDrawable(if (expanded) R.drawable.ic_nav_expand else R.drawable.ic_nav_collapse)!!.mutate()
        val size = context.dp(TeleautoUiTokens.SPACE_4).toInt()
        icon.setBounds(0, 0, size, size)
        button.setCompoundDrawablesRelative(null, null, icon, null)
    }

    private fun updateNavigationHierarchy() {
        setNavigationChevron(modulesNav, modulesExpanded)
        setNavigationChevron(settingsNav, settingsExpanded)
        val modulesVisibility = if (modulesExpanded) View.VISIBLE else View.GONE
        transmitNav.visibility = modulesVisibility
        videoNav.visibility = modulesVisibility
        val settingsVisibility = if (settingsExpanded) View.VISIBLE else View.GONE
        storageNav.visibility = settingsVisibility
        versionNav.visibility = settingsVisibility
        val indent = context.dp(TeleautoUiTokens.SPACE_6).toInt()
        transmitNav.setPadding(indent, 0, context.dp(TeleautoUiTokens.SPACE_2).toInt(), 0)
        videoNav.setPadding(indent, 0, context.dp(TeleautoUiTokens.SPACE_2).toInt(), 0)
        storageNav.setPadding(indent, 0, context.dp(TeleautoUiTokens.SPACE_2).toInt(), 0)
        versionNav.setPadding(indent, 0, context.dp(TeleautoUiTokens.SPACE_2).toInt(), 0)
        updateNavigationSelection()
    }

    private fun isDescendant(view: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === this) return true
            parent = parent.parent
        }
        return false
    }

    private fun firstFocusable(root: ViewGroup): View? {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child.visibility != View.VISIBLE || !child.isEnabled) continue
            if (child.isFocusable) return child
            if (child is ViewGroup) firstFocusable(child)?.let { return it }
        }
        return null
    }

    override fun onDetachedFromWindow() {
        storagePopup?.dismiss()
        inputHandler.removeCallbacks(centerLongPress)
        centerPressedView = null
        centerLongPerformed = false
        super.onDetachedFromWindow()
    }

    private fun applyTypography(root: View) {
        if (root is TextView) {
            root.typeface = if (root.typeface?.isBold == true) TeleautoUiTypography.bold(context) else TeleautoUiTypography.regular(context)
        }
        if (root is ViewGroup) for (index in 0 until root.childCount) applyTypography(root.getChildAt(index))
    }
}

/** Non-video target cover occupying the same 16:9 product area as the viewfinder. */

internal data class TeleautoStorageChoice(val external: Boolean, val label: String, val enabled: Boolean) {
    companion object {
        fun alternatives(selectedExternal: Boolean, cardPresent: Boolean, internalEnabled: Boolean,
            externalEnabled: Boolean, internal: String = "内部存储", external: String = "存储卡"): List<TeleautoStorageChoice> = when {
            !cardPresent -> emptyList()
            selectedExternal -> listOf(TeleautoStorageChoice(false, if (internalEnabled) internal else "$internal（不可用）", internalEnabled))
            else -> listOf(TeleautoStorageChoice(true, if (externalEnabled) external else "$external（不可用）", externalEnabled))
        }
    }
}
