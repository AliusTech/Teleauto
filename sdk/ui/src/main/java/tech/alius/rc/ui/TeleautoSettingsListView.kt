package tech.alius.rc.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView

/** Immutable display model for a row in a settings information list. */
data class TeleautoSettingsListItem(
    val id: String,
    val label: String,
    val value: String,
)

sealed interface TeleautoSettingsRow {
    val id: String
    val label: String

    data class Info(override val id: String, override val label: String, val value: String) : TeleautoSettingsRow
    data class Action(override val id: String, override val label: String, val value: String = "", val enabled: Boolean = true) : TeleautoSettingsRow
    data class Toggle(override val id: String, override val label: String, val checked: Boolean, val enabled: Boolean = true) : TeleautoSettingsRow
    data class Choice(override val id: String, override val label: String, val options: List<String>, val selectedIndex: Int, val enabled: Boolean = true) : TeleautoSettingsRow
    data class Danger(override val id: String, override val label: String, val value: String = "") : TeleautoSettingsRow
}

/** Shared, append-friendly settings list with stable label/value alignment. */
class TeleautoSettingsListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private var rows: List<TeleautoSettingsRow> = emptyList()
    var onAction: ((rowId: String) -> Unit)? = null
    var onToggle: ((rowId: String, checked: Boolean) -> Unit)? = null
    var onChoice: ((rowId: String, selectedIndex: Int) -> Unit)? = null

    init {
        orientation = VERTICAL
        background = TeleautoMaterialAssets.card(context)
    }

    fun submit(newItems: List<TeleautoSettingsListItem>) {
        submitRows(newItems.map { TeleautoSettingsRow.Info(it.id, it.label, it.value) })
    }

    fun submitRows(newRows: List<TeleautoSettingsRow>) {
        if (rows == newRows) return
        rows = newRows.toList()
        removeAllViews()
        rows.forEachIndexed { index, row ->
            if (index > 0) addView(View(context).apply {
                background = ColorDrawable(TeleautoUiTokens.LINE)
            }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.LIST_DIVIDER_HEIGHT).toInt()))
            addView(createRow(row), LayoutParams(
                LayoutParams.MATCH_PARENT,
                context.dp(TeleautoUiTokens.LIST_ROW_HEIGHT).toInt(),
            ))
        }
    }

    private fun createRow(item: TeleautoSettingsRow) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(TeleautoUiTokens.SPACE_4).toInt(), 0, context.dp(TeleautoUiTokens.SPACE_4).toInt(), 0)
        addView(TextView(context).apply {
            text = item.label
            setTextColor(if (item is TeleautoSettingsRow.Danger) TeleautoUiTokens.DANGER else TeleautoUiTokens.TEXT_SECONDARY)
            textSize = TeleautoUiTokens.TYPE_TITLE
            typeface = TeleautoUiTypography.medium(context)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        when (item) {
            is TeleautoSettingsRow.Info -> addValue(item.value)
            is TeleautoSettingsRow.Action -> {
                addValue(item.value)
                isEnabled = item.enabled; isFocusable = item.enabled; alpha = if (item.enabled) 1f else 0.38f
                setOnClickListener { onAction?.invoke(item.id) }
            }
            is TeleautoSettingsRow.Danger -> { addValue(item.value, TeleautoUiTokens.DANGER); isFocusable = true; setOnClickListener { onAction?.invoke(item.id) } }
            is TeleautoSettingsRow.Toggle -> addView(Switch(context).apply {
                isChecked = item.checked; isEnabled = item.enabled; isFocusable = item.enabled
                setOnCheckedChangeListener { _, checked -> onToggle?.invoke(item.id, checked) }
            })
            is TeleautoSettingsRow.Choice -> addView(Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, item.options)
                setSelection(item.selectedIndex.coerceIn(0, (item.options.size - 1).coerceAtLeast(0)), false)
                isEnabled = item.enabled; isFocusable = item.enabled
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { if (position != item.selectedIndex) onChoice?.invoke(item.id, position) }
                }
            }, LayoutParams(context.dp(TeleautoUiTokens.SETTINGS_VALUE_WIDTH).toInt(), LayoutParams.MATCH_PARENT))
        }
    }

    private fun LinearLayout.addValue(value: String, color: Int = TeleautoUiTokens.TEXT) {
        addView(TextView(context).apply {
            text = value; setTextColor(color); textSize = TeleautoUiTokens.TYPE_CARD_TITLE
            typeface = TeleautoUiTypography.numeric(context); gravity = Gravity.END or Gravity.CENTER_VERTICAL; maxLines = 1
        }, LayoutParams(context.dp(TeleautoUiTokens.SETTINGS_VALUE_WIDTH).toInt(), LayoutParams.MATCH_PARENT))
    }
}
