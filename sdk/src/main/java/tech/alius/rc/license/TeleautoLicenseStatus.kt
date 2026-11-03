package tech.alius.rc.license

/** Stable, non-throwing authorization result exposed to host applications. */
enum class TeleautoLicenseError {
    VALID, NOT_INITIALIZED, LICENSE_NOT_FOUND, LICENSE_INVALID, KEY_NOT_CONFIGURED, UNKNOWN_KEY,
    UNSUPPORTED_VERSION, PACKAGE_MISMATCH, CERTIFICATE_MISMATCH, SIGNATURE_ERROR, VENDOR_MISMATCH,
    LICENSE_NOT_ACTIVE, LICENSE_EXPIRED, CLOCK_ROLLBACK, SECURITY_ERROR
}
data class TeleautoLicenseStatus(
    val error: TeleautoLicenseError,
    val licenseId: String? = null,
    val vendorId: String? = null,
    val expiresAt: Long? = null,
) { val isValid: Boolean get() = error == TeleautoLicenseError.VALID }
fun interface TeleautoLicenseListener { fun onLicenseStatusChanged(status: TeleautoLicenseStatus) }
