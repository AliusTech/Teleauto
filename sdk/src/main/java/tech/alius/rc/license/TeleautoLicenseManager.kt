package tech.alius.rc.license

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import tech.alius.rc.BuildConfig
import tech.alius.rc.license.internal.TeleautoKeyPolicy
import tech.alius.rc.license.internal.TeleautoLicenseTimeGuard
import tech.alius.rc.license.internal.TeleautoLicenseClock
import tech.alius.rc.license.internal.TeleautoLicenseVerifier
import tech.alius.rc.license.internal.TeleautoTrustedLicenseKeys
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Read-only authorization state. Only the SDK lifecycle can establish a verified session. */
object TeleautoLicenseManager {
    val status: TeleautoLicenseStatus get() = TeleautoLicenseRuntime.status
}

internal object TeleautoLicenseRuntime {
    @Volatile var status = TeleautoLicenseStatus(TeleautoLicenseError.NOT_INITIALIZED)
        private set
    @Volatile var generation = 0L
        private set
    private val lock = Any()
    private var clock: TeleautoClockStore? = null
    private var anchor: TeleautoLicenseClock.State? = null
    private var timeGuard: TeleautoLicenseTimeGuard? = null
    private var boot = -1L
    private var monitor: ScheduledExecutorService? = null
    private var onInvalid: ((Long) -> Unit)? = null
    private var onStatus: ((TeleautoLicenseStatus) -> Unit)? = null
    private var savedAt = 0L
    private val codec = object : TeleautoLicenseVerifier.Codec {
        override fun decode(value: String): ByteArray = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        override fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun initialize(context: Context, vendor: String?, listener: (TeleautoLicenseStatus) -> Unit, invalidated: (Long) -> Unit): Boolean {
        stop()
        synchronized(lock) {
            onStatus = listener
            onInvalid = invalidated
            val verified = try {
                val document = try {
                    context.assets.open("sdk/license.json").use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            check(output.size() + n <= 32768)
                            output.write(buffer, 0, n)
                        }
                        output.toString("UTF-8")
                    }
                } catch (_: java.io.FileNotFoundException) {
                    return failInitialization(TeleautoLicenseError.LICENSE_NOT_FOUND)
                }
                val keys = TeleautoKeyPolicy.resolve(TeleautoTrustedLicenseKeys.records(), BuildConfig.VERSION, codec)
                if (keys.isEmpty()) return failInitialization(TeleautoLicenseError.KEY_NOT_CONFIGURED)
                TeleautoLicenseVerifier.verify(document, keys, context.packageName, certificates(context), vendor, System.currentTimeMillis() / 1000, codec)
            } catch (error: Exception) {
                Log.e("TeleautoLicense", "License verification setup failed", error)
                return failInitialization(TeleautoLicenseError.SECURITY_ERROR)
            }
            if (verified.error != TeleautoLicenseVerifier.Error.VALID) return failInitialization(TeleautoLicenseError.valueOf(verified.error.name))
            val claims = verified.claims
            try {
                clock = TeleautoClockStore(context.applicationContext)
                boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).toLong()
                val elapsed = SystemClock.elapsedRealtime()
                val effective = try {
                    TeleautoLicenseClock.effectiveTime(clock!!.read(), System.currentTimeMillis()/1000, elapsed, boot)
                } catch (_: IllegalArgumentException) {
                    return failInitialization(TeleautoLicenseError.CLOCK_ROLLBACK)
                }
                if (effective >= claims.expiresAt) return failInitialization(TeleautoLicenseError.LICENSE_EXPIRED)
                anchor = TeleautoLicenseClock.State(effective, elapsed, boot)
                clock!!.write(anchor!!)
                timeGuard = TeleautoLicenseTimeGuard(effective, elapsed, claims.expiresAt)
                savedAt = elapsed
            } catch (error: Exception) {
                Log.e("TeleautoLicense", "License clock initialization failed", error)
                return failInitialization(TeleautoLicenseError.SECURITY_ERROR)
            }
            status = TeleautoLicenseStatus(TeleautoLicenseError.VALID, claims.licenseId, claims.vendorId, claims.expiresAt)
            notifyStatus(status)
            val session = generation
            monitor = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "teleauto-license").apply { isDaemon = true } }.also {
                it.scheduleWithFixedDelay({ tick(session) }, 1, 1, TimeUnit.SECONDS)
            }
            return true
        }
    }

    /** No file I/O on the input/transport hot path. A revoked session cannot be reopened by callers. */
    fun isUsable(): Boolean {
        if (!status.isValid) return false
        synchronized(lock) {
            if (!status.isValid) return false
            val check = timeGuard?.check(System.currentTimeMillis()/1000, SystemClock.elapsedRealtime())
                ?: TeleautoLicenseVerifier.Error.SECURITY_ERROR
            if (check != TeleautoLicenseVerifier.Error.VALID) {
                invalidate(TeleautoLicenseError.valueOf(check.name))
                return false
            }
            return true
        }
    }

    private fun tick(session: Long) {
        synchronized(lock) {
            if (generation != session || !isUsable()) return
            try {
                val elapsed = SystemClock.elapsedRealtime()
                if (elapsed - savedAt >= 30_000) {
                    val effective = timeGuard!!.time(System.currentTimeMillis()/1000, elapsed)
                    clock!!.write(TeleautoLicenseClock.State(effective, elapsed, boot))
                    savedAt = elapsed
                }
            } catch (_: Exception) { invalidate(TeleautoLicenseError.SECURITY_ERROR) }
        }
    }

    // These callbacks are enqueued by the facade; they never acquire lifecycle/input locks inline.
    private fun notifyStatus(value: TeleautoLicenseStatus) { runCatching { onStatus?.invoke(value) } }
    private fun invalidate(error: TeleautoLicenseError) {
        if (!status.isValid) return
        status = status.copy(error = error)
        notifyStatus(status)
        runCatching { onInvalid?.invoke(generation) }
    }
    private fun failInitialization(error: TeleautoLicenseError): Boolean {
        status = TeleautoLicenseStatus(error)
        clock = null; anchor = null; timeGuard = null
        notifyStatus(status)
        return false
    }
    fun stop(preserveFailure: Boolean = false) {
        synchronized(lock) {
            generation++
            monitor?.shutdownNow(); monitor = null
            clock = null; anchor = null; timeGuard = null; onInvalid = null; onStatus = null
            if (!preserveFailure || status.isValid) status = TeleautoLicenseStatus(TeleautoLicenseError.NOT_INITIALIZED)
        }
    }
    @Suppress("DEPRECATION")
    private fun certificates(context: Context): Set<String> {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02X".format(it.toInt() and 255) }
        }.toSet()
    }
}
