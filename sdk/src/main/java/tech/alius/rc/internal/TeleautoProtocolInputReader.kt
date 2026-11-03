package tech.alius.rc.internal

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.lang.reflect.Proxy

/** Firmware-owned API; reflection keeps vendor classes out of the distributed AAR. */
internal class TeleautoProtocolInputReader(private val onInput: (TeleautoProtocolInput) -> Unit) {
    @Volatile private var active = false
    private var manager: Any? = null
    private var listener: Any? = null
    private var listenerClass: Class<*>? = null

    fun start() {
        active = true
        try {
            val managerType = Class.forName("com.dji.protocol.ProtocolManager")
            val filterType = Class.forName("com.dji.protocol.PackFilter")
            val ruleType = Class.forName("com.dji.protocol.PackRule")
            val builderType = Class.forName("com.dji.protocol.PackRule\$Builder")
            val packType = Class.forName("com.dji.protocol.Pack")
            val callbackType = Class.forName(DESCRIPTOR)
            val dataField = packType.getDeclaredField("data")
            val creator = packType.getDeclaredField("CREATOR").get(null) as Parcelable.Creator<*>
            fun receive(pack: Any?) {
                if (!active || pack == null) return
                val input = TeleautoProtocolInput.parse(dataField.get(pack) as? ByteArray) ?: return
                onInput(input)
            }
            val binder = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code == IBinder.INTERFACE_TRANSACTION) {
                        reply?.writeString(DESCRIPTOR)
                        return true
                    }
                    if (code != 1 && code != 2) return super.onTransact(code, data, reply, flags)
                    data.enforceInterface(DESCRIPTOR)
                    if (code == 1) receive(if (data.readInt() != 0) creator.createFromParcel(data) else null)
                    else Log.w(TAG, "Firmware input callback failed")
                    if (flags and IBinder.FLAG_ONEWAY == 0) reply?.writeNoException()
                    return true
                }
            }
            val proxy = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { proxy, method, args ->
                when (method.name) {
                    "asBinder" -> binder
                    "onSuccess" -> { receive(args?.get(0)); null }
                    "onFailure" -> { Log.w(TAG, "Firmware input callback failed"); null }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    "toString" -> TAG
                    else -> null
                }
            }
            binder.attachInterface(proxy as IInterface, DESCRIPTOR)
            val instance = managerType.getDeclaredMethod("getDefault").invoke(null)
            val filter = filterType.getDeclaredMethod("obtain").invoke(null)
            val builder = builderType.getDeclaredConstructor().newInstance()
            builderType.getDeclaredMethod("cmd", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(builder, 6, 0xAE)
            val rule = builderType.getDeclaredMethod("build").invoke(builder)
            filterType.getDeclaredMethod("addRule", ruleType).invoke(filter, rule)
            manager = instance
            listener = proxy
            listenerClass = callbackType
            managerType.getDeclaredMethod("addPackListener", filterType, callbackType).invoke(instance, filter, proxy)
        } catch (error: Exception) {
            Log.w(TAG, "Firmware input unavailable; retaining evdev/TCP fallback", error)
            stop()
        }
    }

    fun stop() {
        active = false
        val instance = manager
        val callback = listener
        val type = listenerClass
        if (instance != null && callback != null && type != null) {
            runCatching { instance.javaClass.getDeclaredMethod("removePackListener", type).invoke(instance, callback) }
                .onFailure { Log.w(TAG, "Could not remove firmware listener", it) }
        }
        manager = null
        listener = null
        listenerClass = null
    }

    private companion object {
        const val DESCRIPTOR = "com.dji.protocol.IPackListener"
        const val TAG = "TeleautoProtocolInput"
    }
}

/** 0x06/0xAE payload: u32 buttons, u8 extension, six little-endian u16 axes. */
internal data class TeleautoProtocolInput(val buttons: Int, val leftDial: Float, val rightDial: Float,
    val leftX: Float = 0f, val leftY: Float = 0f,
    val rightX: Float = 0f, val rightY: Float = 0f,
) {
    companion object {
        fun parse(data: ByteArray?): TeleautoProtocolInput? {
            if (data == null || data.size < 17) return null
            fun u8(offset: Int) = data[offset].toInt() and 0xff
            fun axis(offset: Int) = ((u8(offset) + (u8(offset + 1) shl 8) - 1024) / 660f).coerceIn(-1f, 1f)
            val buttons = u8(0) or (u8(1) shl 8) or (u8(2) shl 16) or (u8(3) shl 24)
            return TeleautoProtocolInput(buttons, axis(13), axis(15), axis(11), axis(9), axis(5), axis(7))
        }
    }
}
