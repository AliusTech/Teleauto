package tech.alius.rc.license

import java.security.Key
import java.security.KeyPairGenerator
import java.security.interfaces.ECKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import org.junit.Assert.*
import org.junit.Test
import tech.alius.rc.license.internal.TeleautoLicenseVerifier

class TeleautoP256CompatibilityTest {
    private fun parameters(name: String): ECParameterSpec =
        (KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(name)) }.generateKeyPair().public as ECKey).params
    private class TestKey(private val params: ECParameterSpec) : Key, ECKey {
        override fun getParams() = params
        override fun getAlgorithm() = "EC"
        override fun getFormat() = "X.509"
        override fun getEncoded() = byteArrayOf()
    }
    @Test fun `explicit parameters match provider P256 and reject different curves`() {
        val p = parameters("secp256r1")
        assertTrue(TeleautoLicenseVerifier.p256(TestKey(p)))
        assertFalse(TeleautoLicenseVerifier.p256(TestKey(parameters("secp384r1"))))
        assertFalse(TeleautoLicenseVerifier.p256(TestKey(ECParameterSpec(p.curve, p.generator, p.order.subtract(java.math.BigInteger.ONE), 1))))
        assertFalse(TeleautoLicenseVerifier.p256(TestKey(ECParameterSpec(p.curve, p.generator, p.order, 2))))
    }
}
