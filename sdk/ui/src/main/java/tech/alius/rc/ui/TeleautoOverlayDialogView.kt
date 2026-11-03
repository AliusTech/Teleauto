package tech.alius.rc.ui

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Controller-styled modal surface shared by confirmation and device action flows. */
internal class TeleautoOverlayDialogView(context: Context) : FrameLayout(context) {
    private val titleView = TextView(context).apply {
        setTextColor(TeleautoUiTokens.TEXT)
        textSize = 18f
        typeface = TeleautoUiTypography.bold(context)
        gravity = Gravity.START
    }
    private val messageView = TextView(context).apply {
        setTextColor(TeleautoUiTokens.TEXT_SECONDARY)
        textSize = 12f
        typeface = TeleautoUiTypography.regular(context)
        gravity = Gravity.START
    }
    private val actions = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    val isShowing: Boolean
        get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        setBackgroundColor(TeleautoUiTokens.DIALOG_SCRIM)
        setOnClickListener { /* Modal scrim deliberately consumes outside taps. */ }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                context.dp(TeleautoUiTokens.SPACE_6).toInt(),
                context.dp(TeleautoUiTokens.SPACE_6).toInt(),
                context.dp(TeleautoUiTokens.SPACE_6).toInt(),
                context.dp(TeleautoUiTokens.SPACE_4).toInt(),
            )
            background = TeleautoMaterialAssets.panelRaised(context)
            isClickable = true
            addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(messageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(TeleautoUiTokens.SPACE_2).toInt()
            })
            addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.DIALOG_ACTION_HEIGHT).toInt()).apply {
                topMargin = context.dp(TeleautoUiTokens.SPACE_6).toInt()
            })
        }
        addView(card, LayoutParams(
            context.dp(TeleautoUiTokens.DIALOG_WIDTH).toInt(),
            LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
    }

    fun showConfirmation(
        title: String,
        message: String,
        confirmLabel: String,
        danger: Boolean = false,
        onConfirm: () -> Unit,
    ) {
        show(
            title = title,
            message = message,
            entries = listOf(
                ActionEntry("取消", false, {}),
                ActionEntry(confirmLabel, danger, onConfirm),
            ),
            initialFocus = 0,
        )
    }

    fun showActions(
        title: String,
        message: String,
        editLabel: String,
        removeLabel: String,
        onEdit: () -> Unit,
        onRemove: () -> Unit,
    ) {
        show(
            title = title,
            message = message,
            entries = listOf(
                ActionEntry(editLabel, false, onEdit),
                ActionEntry(removeLabel, true, onRemove),
                ActionEntry("取消", false, {}),
            ),
            initialFocus = 0,
        )
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        visibility = View.GONE
        actions.removeAllViews()
        return true
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isShowing) return false
        if (event.action == KeyEvent.ACTION_UP) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> findFocus()?.performClick() == true
                else -> true
            }
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> dismissIfShowing()
            KeyEvent.KEYCODE_DPAD_LEFT -> moveFocus(View.FOCUS_LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocus(View.FOCUS_RIGHT)
            KeyEvent.KEYCODE_DPAD_UP -> moveFocus(View.FOCUS_UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveFocus(View.FOCUS_DOWN)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true
            else -> true
        }
    }

    private fun show(title: String, message: String, entries: List<ActionEntry>, initialFocus: Int) {
        titleView.text = title
        messageView.text = message
        actions.removeAllViews()
        entries.forEachIndexed { index, entry ->
            actions.addView(dialogButton(entry), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (index > 0) marginStart = context.dp(TeleautoUiTokens.SPACE_2).toInt()
            })
        }
        visibility = View.VISIBLE
        bringToFront()
        contentDescription = "$title。$message"
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        actions.getChildAt(initialFocus.coerceIn(0, entries.lastIndex))?.requestFocus()
    }

    private fun dialogButton(entry: ActionEntry) = Button(context).apply {
        text = entry.label
        isAllCaps = false
        textSize = 12f
        typeface = TeleautoUiTypography.medium(context)
        setTextColor(if (entry.danger) TeleautoUiTokens.DANGER else TeleautoUiTokens.TEXT)
        stateListAnimator = null
        background = TeleautoMaterialAssets.keycapSelector(context)
        isFocusable = true
        isFocusableInTouchMode = false
        setOnFocusChangeListener { view, hasFocus -> view.isSelected = hasFocus }
        setOnClickListener {
            dismissIfShowing()
            entry.action()
        }
    }

    private fun moveFocus(direction: Int): Boolean {
        val current = findFocus() ?: return false
        val next = current.focusSearch(direction) ?: return false
        var parent = next.parent
        var belongsToOverlay = next === this
        while (!belongsToOverlay && parent is View) {
            belongsToOverlay = parent === this
            parent = parent.parent
        }
        if (!belongsToOverlay) return false
        return next.requestFocus()
    }

    private data class ActionEntry(
        val label: String,
        val danger: Boolean,
        val action: () -> Unit,
    )
}
