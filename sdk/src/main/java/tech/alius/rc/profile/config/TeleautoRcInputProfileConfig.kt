package tech.alius.rc.profile.config

import android.content.Context
import org.json.JSONObject

internal data class TeleautoRcInputProfileConfig(
    val id: String,
    val eventDevice: String,
    val eventCommand: String,
    val logcatCommand: String,
    val logTag: String,
    val deadzonePerMille: Int,
    val invertAxisMask: Int,
    val axes: Map<String, String>,
    val evdevKeys: Map<String, String>,
    val privateActions: Map<String, String>,
)

internal object TeleautoRcInputProfiles {
    private const val ASSET_NAME = "dji_rc_profiles.json"
    @Volatile private var cache: Map<String, TeleautoRcInputProfileConfig>? = null

    fun get(context: Context, id: String): TeleautoRcInputProfileConfig =
        all(context)[id] ?: all(context)["rm500"] ?: error("missing RC input profile: $id")

    fun all(context: Context): Map<String, TeleautoRcInputProfileConfig> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
                .also { cache = it }
        }
    }

    fun parse(json: String): Map<String, TeleautoRcInputProfileConfig> {
        val root = JSONObject(json)
        val profiles = root.getJSONArray("profiles")
        val out = linkedMapOf<String, TeleautoRcInputProfileConfig>()
        for (i in 0 until profiles.length()) {
            val obj = profiles.getJSONObject(i)
            out[obj.getString("id")] = TeleautoRcInputProfileConfig(
                id = obj.getString("id"),
                eventDevice = obj.getString("event_device"),
                eventCommand = obj.getString("event_command"),
                logcatCommand = obj.getString("logcat_command"),
                logTag = obj.getString("log_tag"),
                deadzonePerMille = obj.getInt("deadzone_per_mille"),
                invertAxisMask = obj.getInt("invert_axis_mask"),
                axes = obj.getJSONObject("axes").toStringMap(),
                evdevKeys = obj.getJSONObject("evdev_keys").toStringMap(),
                privateActions = obj.getJSONObject("private_actions").toStringMap(),
            )
        }
        return out
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = getString(key)
        }
        return result
    }
}
