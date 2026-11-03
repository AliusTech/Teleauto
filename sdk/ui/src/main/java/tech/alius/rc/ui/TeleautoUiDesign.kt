package tech.alius.rc.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import tech.alius.rc.model.TeleautoThreePositionSwitchMode
import tech.alius.rc.ui.internal.TeleautoConsoleBackgroundDrawable
import tech.alius.rc.ui.internal.TeleautoZoneBackgroundDrawable
import java.util.concurrent.ConcurrentHashMap

/** The single visual source shared by teleauto-ui and the demo application. */
object TeleautoUiTokens {
    // Figma DJI-RC / Frame 1 (1:2). 1920×1200 maps to the 768×480dp baseline.
    const val FIGMA_BASE_WIDTH = 768f
    const val FIGMA_BASE_HEIGHT = 480f
    const val FIGMA_PIXEL_TO_DP = 0.4f
    const val PRIMARY = 0xFFFFFFFF.toInt()
    const val PRIMARY_PRESSED = 0xFFD8D8D8.toInt()
    const val PRIMARY_SOFT = 0x2EFFFFFF
    const val PRIMARY_GLOW = 0x52FFFFFF
    const val BACKGROUND = 0xFF000000.toInt()
    const val BACKGROUND_ALT = 0xFF131313.toInt()
    const val BACKGROUND_DEEP = 0xFF000000.toInt()
    const val NAVIGATION_RAIL_BACKGROUND = 0xFF141616.toInt()
    const val SURFACE = 0xFF232323.toInt()
    const val SURFACE_RAISED = 0xFF3F3F3F.toInt()
    const val SURFACE_PRESSED = 0xFF242424.toInt()
    const val GLASS = 0xD91A1A1A.toInt()
    const val GLASS_STRONG = 0xFA1C1C1C.toInt()
    const val GLASS_HIGHLIGHT = 0x24FFFFFF
    const val GLASS_BORDER = 0xFF515151.toInt()
    const val GLASS_BORDER_ACTIVE = 0xA6FFFFFF.toInt()
    const val SHADOW_AMBIENT = 0x52000000
    const val SHADOW_DEEP = 0x8A000000.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xFFD0D0D0.toInt()
    const val TEXT_FAINT = 0xFF969696.toInt()
    const val LOGO_RECESSED_BODY = 0x78F3F6FA
    const val LOGO_RECESSED_HIGHLIGHT = 0x34FFFFFF
    const val LOGO_RECESSED_SHADOW = 0x68000000
    const val LINE = 0x24FFFFFF
    const val ACCENT = TEXT
    const val SUCCESS = TEXT
    const val WARNING = 0xFFE0E0E0.toInt()
    const val DANGER = 0xFFE45B5B.toInt()
    const val RECORD = 0xFFE24747.toInt()
    const val MODULE_AVAILABLE = 0xFF55C98A.toInt()
    const val MODULE_AVAILABLE_SURFACE = 0x2455C98A
    const val MODULE_PREPARING = 0xFFE0B45F.toInt()
    const val MODULE_PREPARING_SURFACE = 0x24E0B45F
    const val MODULE_UNAVAILABLE = 0xFF858B92.toInt()
    const val MODULE_UNAVAILABLE_SURFACE = 0x18858B92

    const val SPACE_1 = 4f
    const val SPACE_2 = 8f
    const val SPACE_3 = 12f
    const val SPACE_4 = 16f
    const val SPACE_6 = 24f
    const val CONTROL_HEIGHT = 40f
    const val MODULE_STATUS_HEIGHT = 58f
    const val LIST_ROW_HEIGHT = 48f
    const val LIST_DIVIDER_HEIGHT = 1f
    const val SETTINGS_VALUE_WIDTH = 180f
    const val POPUP_WIDTH = 250f
    const val POPUP_VERTICAL_OFFSET = 46f
    const val TYPE_TITLE = 12f
    const val TYPE_APP_HEADER = TYPE_TITLE
    const val DIAL_TICK_COLOR = SURFACE
    const val DIAL_TICK_INTERVALS = 10
    const val DIAL_TICK_HEIGHT_RATIO = 0.35f
    const val DIAL_CENTER_TICK_HEIGHT_RATIO = 0.6f
    const val TYPE_BODY = 10f
    const val TYPE_LABEL_SMALL = 8f
    const val TYPE_META_SMALL = 7f
    const val RADIUS_SM = 6f
    const val RADIUS_CONTROL = 8f // Figma 20px
    const val RADIUS_CARD = 12f // Figma 50px
    const val RADIUS_DIALOG = 18f
    const val PAGE_INSET = SPACE_4
    const val COLUMN_GAP = SPACE_4
    const val CARD_INSET = SPACE_4
    const val FIVE_WAY_CELL = 24f
    const val FIVE_WAY_GAP = SPACE_1
    const val JOYSTICK_SIDE_TRACK_THICKNESS = 9.6f // Figma 24px

    const val FIGMA_SIDE_COLUMN_WIDTH = 168f
    const val FIGMA_CENTER_COLUMN_WIDTH = 368f
    const val FIGMA_JOYSTICK_SIZE = 168f
    const val FIGMA_DIAL_WIDTH = 168f
    const val FIGMA_DIAL_HEIGHT = 9.6f
    const val FIGMA_VIDEO_ASPECT = 840f / 473f
    const val HARDWARE_PANEL_HEIGHT = 196f
    // Shared dashboard geometry; XML spacing tokens carry the same base values.
    const val DASHBOARD_MIN_ASPECT = 16f / 9f
    const val DASHBOARD_TOP_INSET = 11f
    const val DASHBOARD_BOTTOM_INSET = 21f
    const val DASHBOARD_DIAL_TO_STICK_GAP = 11f
    const val CENTERING_DIAL_HEIGHT = 14f
    const val FIGMA_KEY_WIDTH = 73.44f
    const val FIGMA_KEY_HEIGHT = 36f
    const val FIGMA_KEY_COLUMN_GAP = 20.56f
    const val FIGMA_KEY_ROW_GAP = 16f
    const val COMPACT_KEY_ROW_GAP = FIGMA_KEY_ROW_GAP * .75f
    const val COMPACT_KEY_COLUMN_GAP_RATIO = .60f
    const val SCREEN_LOCK_TINT = SURFACE_RAISED
    const val SCREEN_LOCK_SURFACE = SURFACE
    const val SCREEN_UNLOCK_HOLD_MS = 2000L
    const val FIGMA_KEY_TEXT_SIZE = 18f
    const val HARDWARE_KEY_DEPTH = SPACE_1
    const val HARDWARE_KEY_EDGE = LIST_DIVIDER_HEIGHT
    const val ROUND_KEY_DIAMETER = CONTROL_HEIGHT + RADIUS_SM
    const val AUTH_KEY_COLOR = DANGER
    const val PAUSE_KEY_COLOR = 0xFFF27642.toInt()
    const val MATTE_KEY_SHADE = 0.58f
    const val MATTE_KEY_PRESSED_SHADE = 0.50f
    const val MATTE_KEY_LIGHT = 0.08f
    const val MATTE_KEY_EDGE_LIGHT = 0.05f
    const val FIGMA_CENTER_SHELL_RADIUS = 20f
    const val FIGMA_VIDEO_RADIUS = 20f

    const val TOP_BAR_BATTERY_SETTINGS_GAP = SPACE_4
    const val TOP_BAR_HEIGHT = 40f
    const val TOP_BAR_HORIZONTAL_INSET = 8.8f
    const val TOP_BAR_LOGO_HEIGHT = 22f
    const val TOP_BAR_LOGO_ASPECT = 72f / 108f
    const val TOP_BAR_BACKGROUND = 0xFF050505.toInt()

    const val DEVICE_GRID_COLUMNS = 4
    const val DEVICE_GRID_CELL_MARGIN = SPACE_1
    const val DEVICE_CARD_HEIGHT = 132f
    const val DEVICE_CARD_ICON_SIZE = 34f
    const val DEVICE_CARD_ACTION_HEIGHT = 28f
    const val TYPE_CARD_TITLE = 13f
    const val TYPE_LABEL = 11f
    const val TYPE_META = 9f
    const val DIALOG_SCRIM = 0xB3000000.toInt()
    const val DIALOG_WIDTH = 360f
    const val DIALOG_ACTION_HEIGHT = CONTROL_HEIGHT

    const val MOTION_PRESS_IN = 120L
    const val MOTION_PRESS_OUT = 90L
    const val MOTION_STATE = 160L
    const val MOTION_OVERLAY_IN = 210L
    const val MOTION_OVERLAY_OUT = 140L

    // Console-specific monochrome roles.  They live here so the App and UI AAR
    // cannot drift into separate themes as additional devices are added.
    const val BRAND_50 = GLASS_STRONG
    const val BRAND_100 = SURFACE_RAISED
    const val BRAND_200 = 0xFF363636.toInt()
    const val BRAND_300 = 0xFFF2F2F2.toInt()
    const val BRAND_400 = 0xFFE6E6E6.toInt()
    const val BRAND_600 = PRIMARY
    const val BRAND_700 = PRIMARY_PRESSED
    const val MODE_CINE = 0xFFD6B56A.toInt()
    const val MODE_NORMAL = 0xFF70C998.toInt()
    const val MODE_SPORT = 0xFF70A8D8.toInt()

    /** Shared C/N/S identity mapping; UNKNOWN intentionally follows the safe Normal state. */
    fun modeColor(mode: TeleautoThreePositionSwitchMode): Int = when (mode) {
        TeleautoThreePositionSwitchMode.CINE -> MODE_CINE
        TeleautoThreePositionSwitchMode.NORMAL -> MODE_NORMAL
        TeleautoThreePositionSwitchMode.SPORT -> MODE_SPORT
        TeleautoThreePositionSwitchMode.UNKNOWN -> MODE_NORMAL
    }
    const val SCREEN_BG = 0xFF050505.toInt()
    const val SCREEN_EDGE = 0xFF4A4A4A.toInt()
    const val SCREEN_LINE = 0x2EFFFFFF
    const val CONSOLE_SCREEN2 = 0xFF111111.toInt()
    const val CONSOLE_FG_DIM = 0xA6F3F6FA.toInt()
    const val JOYSTICK_TOP = 0xE61C1C1C.toInt()
    const val JOYSTICK_BOTTOM = 0xE60B0B0B.toInt()
    const val JOYSTICK_GRID = 0x70FFFFFF
    const val STICK_HEAD = 0xFF777777.toInt()
    const val TRIM_BG = 0xFF343434.toInt()
    const val TRACK_BASE = 0xFF424242.toInt()
    const val ACTION_BAR_BG = 0xF0222222.toInt()
    const val CENTER_TOP = 0xCC242424.toInt()
    const val CENTER_BOTTOM = 0xCC111111.toInt()
    const val KEY_HIGHLIGHT = 0x20FFFFFF
    const val STICK_R1 = 0xFFB8B8B8.toInt()
    const val STICK_R2 = 0xFF606060.toInt()
    const val STICK_R3 = 0xFF171717.toInt()
    const val STICK_R4 = 0xFF383838.toInt()

    const val CONSOLE_MATTE_TOP = 0xFF1B1D1D.toInt()
    const val CONSOLE_MATTE_BOTTOM = 0xFF111313.toInt()
    const val VIDEO_HUD_INSET = SPACE_4
    const val VIDEO_HUD_BUTTON_SIZE = 25f
    const val VIDEO_HUD_TOUCH_SIZE = 52f
    const val VIDEO_FRAME_STROKE = 1f
    const val VIDEO_FRAME_EDGE = 0xFF343636.toInt()
    const val CONSOLE_MEDIA_TOP_INSET = 20f
    const val CRATER_EDGE_TOP = 0xFF101111.toInt()
    const val CRATER_EDGE_BOTTOM = 0xFF373939.toInt()
    const val CRATER_SHOULDER = 0xFF292B2B.toInt()
    const val CRATER_INNER = 0xFF080909.toInt()
    const val CRATER_FLOOR = 0xFF202222.toInt()

    fun ring(alpha: Int): Int = (alpha shl 24) or (PRIMARY and 0xFFFFFF)
}

/** Original flat glass surfaces. Kept behind one factory so all SDK pages stay consistent. */
object TeleautoMaterialAssets {
    private val bitmaps = ConcurrentHashMap<Int, Bitmap>()

    private fun surface(
        context: Context,
        color: Int,
        border: Int,
        radiusDp: Float,
    ): Drawable = GradientDrawable().apply {
        val density = context.resources.displayMetrics.density
        shape = GradientDrawable.RECTANGLE
        colors = intArrayOf(color, color)
        cornerRadius = radiusDp * density
        setStroke(density.toInt().coerceAtLeast(1), border)
    }

    fun panelRaised(context: Context) = surface(context, TeleautoUiTokens.SURFACE_RAISED, TeleautoUiTokens.GLASS_BORDER, TeleautoUiTokens.RADIUS_CARD)
    fun panelRecessed(context: Context) = surface(context, TeleautoUiTokens.PRIMARY_SOFT, TeleautoUiTokens.GLASS_BORDER_ACTIVE, TeleautoUiTokens.RADIUS_CARD)
    /** Placeholder shown in the exact live-video slot; it must share the video's corner geometry. */
    fun centralFrame(context: Context): Drawable = TeleautoCentralSurface(context, raised = false)
    fun centralPlatform(context: Context): Drawable = TeleautoCentralSurface(context, raised = true)
    fun videoPlaceholder(context: Context): Drawable = android.graphics.drawable.LayerDrawable(arrayOf(
        GradientDrawable().apply {
            setColor(TeleautoUiTokens.BACKGROUND)
            cornerRadius = TeleautoUiTokens.FIGMA_VIDEO_RADIUS * context.resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context)
        }, centralFrame(context),
    ))
    fun card(context: Context) = surface(context, TeleautoUiTokens.GLASS, TeleautoUiTokens.GLASS_BORDER, TeleautoUiTokens.RADIUS_CARD)
    fun moduleStatus(context: Context, state: TeleautoModuleAvailabilityState): Drawable = when (state) {
        TeleautoModuleAvailabilityState.AVAILABLE -> surface(
            context, TeleautoUiTokens.MODULE_AVAILABLE_SURFACE, TeleautoUiTokens.MODULE_AVAILABLE, TeleautoUiTokens.RADIUS_CARD,
        )
        TeleautoModuleAvailabilityState.PREPARING -> surface(
            context, TeleautoUiTokens.MODULE_PREPARING_SURFACE, TeleautoUiTokens.MODULE_PREPARING, TeleautoUiTokens.RADIUS_CARD,
        )
        TeleautoModuleAvailabilityState.UNAVAILABLE -> surface(
            context, TeleautoUiTokens.MODULE_UNAVAILABLE_SURFACE, TeleautoUiTokens.MODULE_UNAVAILABLE, TeleautoUiTokens.RADIUS_CARD,
        )
    }
    fun track(context: Context) = surface(context, TeleautoUiTokens.TRACK_BASE, TeleautoUiTokens.LINE, TeleautoUiTokens.RADIUS_SM)
    fun keycap(context: Context): Drawable = (surface(context, TeleautoUiTokens.SURFACE_RAISED, TeleautoUiTokens.GLASS_BORDER, TeleautoUiTokens.RADIUS_CONTROL) as GradientDrawable).apply {
        colors = intArrayOf(TeleautoUiTokens.SURFACE_RAISED, TeleautoUiTokens.SURFACE)
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
    }
    fun keycapPressed(context: Context) = surface(context, TeleautoUiTokens.PRIMARY_SOFT, TeleautoUiTokens.GLASS_BORDER_ACTIVE, TeleautoUiTokens.RADIUS_CONTROL)
    fun topBar(context: Context): Drawable = context.getDrawable(R.drawable.djirc_v2_top_bar)!!.mutate()

    /** Square, borderless state surface shared by touch and 5D navigation. */
    fun navigationItem(context: Context): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), keycap(context))
        addState(intArrayOf(android.R.attr.state_selected), keycapPressed(context))
        addState(intArrayOf(android.R.attr.state_focused), surface(context, android.graphics.Color.TRANSPARENT, TeleautoUiTokens.GLASS_BORDER_ACTIVE, TeleautoUiTokens.RADIUS_CONTROL))
        addState(intArrayOf(), ColorDrawable(android.graphics.Color.TRANSPARENT))
    }

    fun keycapSelector(context: Context): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), keycapPressed(context))
        addState(intArrayOf(android.R.attr.state_focused), keycapPressed(context))
        addState(intArrayOf(android.R.attr.state_selected), keycapPressed(context))
        addState(intArrayOf(), keycap(context))
    }

    fun joystickCrater(context: Context): Bitmap = bitmap(context, R.drawable.joystick_crater)
    fun joystickCap(context: Context): Bitmap = bitmap(context, R.drawable.djirc_ui_joystick_cap)
    fun joystickTrimKnob(context: Context): Bitmap = bitmap(context, R.drawable.joystick_trim_knob)

    private fun bitmap(context: Context, id: Int): Bitmap = bitmaps.getOrPut(id) {
        requireNotNull(BitmapFactory.decodeResource(context.resources, id)).apply { setHasAlpha(true) }
    }

    private class TopBarDrawable(density: Float) : Drawable() {
        private val fill = Paint().apply { color = TeleautoUiTokens.TOP_BAR_BACKGROUND }
        private val divider = Paint().apply {
            color = TeleautoUiTokens.GLASS_BORDER
            strokeWidth = density.coerceAtLeast(1f)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRect(bounds, fill)
            canvas.drawLine(
                bounds.left.toFloat(),
                bounds.bottom - divider.strokeWidth / 2f,
                bounds.right.toFloat(),
                bounds.bottom - divider.strokeWidth / 2f,
                divider,
            )
        }

        override fun setAlpha(alpha: Int) { fill.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            fill.colorFilter = colorFilter
            divider.colorFilter = colorFilter
        }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}

/** Original compact system typography; no bundled font decode is required. */
object TeleautoUiTypography {
    @Volatile private var regularFace: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    @Volatile private var mediumFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    @Volatile private var boldFace: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    val body: Typeface get() = regularFace
    val label: Typeface get() = mediumFace
    val numeric: Typeface get() = regularFace
    val numericStrong: Typeface get() = mediumFace

    fun initialize(context: Context) {
        runCatching {
            regularFace = Typeface.createFromAsset(context.assets, "fonts/inter_regular.otf")
            mediumFace = Typeface.createFromAsset(context.assets, "fonts/inter_medium.otf")
            boldFace = Typeface.createFromAsset(context.assets, "fonts/inter_bold.otf")
        }
    }
    fun regular(@Suppress("UNUSED_PARAMETER") context: Context): Typeface = regularFace
    fun medium(@Suppress("UNUSED_PARAMETER") context: Context): Typeface = mediumFace
    fun bold(@Suppress("UNUSED_PARAMETER") context: Context): Typeface = boldFace
    fun numeric(@Suppress("UNUSED_PARAMETER") context: Context): Typeface = regularFace
}

fun View.applyAbsRaisedSurface() { background = TeleautoMaterialAssets.panelRaised(context) }
fun View.applyAbsRecessedSurface() { background = TeleautoMaterialAssets.panelRecessed(context) }
fun View.applyAbsCard() { background = TeleautoMaterialAssets.card(context) }

/** Visual surface installation stays in teleauto-ui; host pages only compose regions. */
object TeleautoUiSurfaces {
    fun applyConsoleBackground(view: View) {
        view.background = view.context.getDrawable(R.drawable.djirc_v2_background)
    }

    fun applyControlRegion(view: View) {
        view.background = null
    }
}
