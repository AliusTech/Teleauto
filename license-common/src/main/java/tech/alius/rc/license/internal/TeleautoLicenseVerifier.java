package tech.alius.rc.license.internal;

import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.nio.charset.*;
import java.security.*;
import java.security.interfaces.ECKey;
import java.security.spec.*;
import java.util.*;

/** Signature verification uses the exact ASCII base64url payload, not reserialized JSON. */
public final class TeleautoLicenseVerifier {
    public interface Codec { byte[] decode(String value); String encode(byte[] value); }
    public enum Error { VALID, NOT_INITIALIZED, LICENSE_NOT_FOUND, LICENSE_INVALID, KEY_NOT_CONFIGURED, UNKNOWN_KEY,
        UNSUPPORTED_VERSION, PACKAGE_MISMATCH, CERTIFICATE_MISMATCH, SIGNATURE_ERROR, VENDOR_MISMATCH,
        LICENSE_NOT_ACTIVE, LICENSE_EXPIRED, CLOCK_ROLLBACK, SECURITY_ERROR }
    public static final class Claims {
        public final String keyId,vendorId,packageName,licenseId; public final Set<String> certificates;
        public final long issuedAt,notBefore,expiresAt;
        public Claims(Map<String,Object> p){
            if(!p.keySet().equals(new HashSet<>(Arrays.asList("version","keyId","vendorId","packageName","certSha256","issuedAt","notBefore","expiresAt","licenseId"))))throw new IllegalArgumentException();
            keyId=text(p,"keyId");vendorId=text(p,"vendorId");packageName=text(p,"packageName");licenseId=text(p,"licenseId");
            if(!keyId.matches("[A-Za-z0-9_.-]{1,64}")||!packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))throw new IllegalArgumentException();
            issuedAt=number(p,"issuedAt");notBefore=number(p,"notBefore");expiresAt=number(p,"expiresAt");
            if(issuedAt<0||notBefore<0||expiresAt>253402300799L||expiresAt<=notBefore||issuedAt>=expiresAt)throw new IllegalArgumentException();
            Object cs=p.get("certSha256");if(!(cs instanceof List)||((List<?>)cs).isEmpty())throw new IllegalArgumentException();Set<String> certs=new HashSet<>();
            for(Object c:(List<?>)cs){if(!(c instanceof String))throw new IllegalArgumentException();certs.add(normalizeCertificate((String)c));}certificates=Collections.unmodifiableSet(certs);
        }
    }
    public static final class Result {
        public final Error error; public final Claims claims;
        public Result(Error error,Claims claims){this.error=error;this.claims=claims;}
        public static Result failure(Error error){return new Result(error,null);}
    }
    public static String text(Map<String,Object> p,String key){Object v=p.get(key);if(!(v instanceof String)||((String)v).trim().isEmpty()||((String)v).length()>256)throw new IllegalArgumentException();return (String)v;}
    public static long number(Map<String,Object> p,String key){Object v=p.get(key);if(!(v instanceof Long))throw new IllegalArgumentException();return (Long)v;}
    public static String normalizeCertificate(String value){String v=value.replace(":","").toUpperCase(Locale.ROOT);if(!v.matches("[0-9A-F]{64}"))throw new IllegalArgumentException();return v;}
    // Explicit secp256r1 parameters: Android 7 firmware may provide EC keys and
    // ECDSA signatures without an EC AlgorithmParameters provider.
    private static BigInteger hex(String value){return new BigInteger(value,16);}
    private static final ECParameterSpec P256 = new ECParameterSpec(
        new EllipticCurve(new ECFieldFp(hex("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF")),
            hex("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC"),
            hex("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B")),
        new ECPoint(hex("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296"),
            hex("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5")),
        hex("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551"),1);
    public static boolean p256(Key key) throws GeneralSecurityException {
        if(!(key instanceof ECKey))return false;ECParameterSpec expected=P256, actual=((ECKey)key).getParams();
        return actual!=null&&actual.getCurve().equals(expected.getCurve())&&actual.getGenerator().equals(expected.getGenerator())&&actual.getOrder().equals(expected.getOrder())&&actual.getCofactor()==expected.getCofactor();
    }
    private static byte[] canonical(String s,Codec codec){if(s.isEmpty()||s.length()>24000||!s.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException();byte[] b=codec.decode(s);if(!codec.encode(b).equals(s))throw new IllegalArgumentException();return b;}
    public static Result verify(String document,Map<String,PublicKey> keys,String hostPackage,Set<String> hostCerts,String vendor,long now,Codec codec){
        try {
            Map<String,Object> outer=TeleautoStrictJson.object(document);if(!outer.keySet().equals(new HashSet<>(Arrays.asList("payload","signature"))))return Result.failure(Error.LICENSE_INVALID);
            Object raw=outer.get("payload"),sig=outer.get("signature");if(!(raw instanceof String)||!(sig instanceof String))return Result.failure(Error.LICENSE_INVALID);
            String encoded=(String)raw;
            String payload=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(canonical(encoded,codec))).toString();
            Map<String,Object> p=TeleautoStrictJson.object(payload);String keyId=text(p,"keyId");PublicKey key=keys.get(keyId);if(key==null)return Result.failure(Error.UNKNOWN_KEY);
            if(!p256(key))return Result.failure(Error.SECURITY_ERROR);
            Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);verifier.update(encoded.getBytes(StandardCharsets.US_ASCII));
            if(!verifier.verify(canonical((String)sig,codec)))return Result.failure(Error.LICENSE_INVALID);
            if(number(p,"version")!=1)return Result.failure(Error.UNSUPPORTED_VERSION);
            Claims claims=new Claims(p);
            if(!claims.packageName.equals(hostPackage))return Result.failure(Error.PACKAGE_MISMATCH);
            if(hostCerts==null||hostCerts.isEmpty())return Result.failure(Error.SIGNATURE_ERROR);
            for(String cert:hostCerts)if(!claims.certificates.contains(normalizeCertificate(cert)))return Result.failure(Error.CERTIFICATE_MISMATCH);
            if(vendor!=null&&!claims.vendorId.equals(vendor))return Result.failure(Error.VENDOR_MISMATCH);
            if(now<claims.notBefore)return Result.failure(Error.LICENSE_NOT_ACTIVE);
            if(now>=claims.expiresAt)return Result.failure(Error.LICENSE_EXPIRED);
            return new Result(Error.VALID,claims);
        } catch(Exception failure){return Result.failure(Error.LICENSE_INVALID);}
    }
}
