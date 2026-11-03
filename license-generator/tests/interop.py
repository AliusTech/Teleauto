"""CLI → Java SDK verifier integration, isolated ephemeral keys. No third-party Python dependencies."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
BIN = ROOT / "license-generator/target/debug/teleauto-license-generator"

def cli(*args, stdin=None, good=True):
    p = subprocess.run([str(BIN), *map(str, args), "--json"], input=stdin, text=True, capture_output=True, timeout=20)
    result = json.loads(p.stdout)
    assert (p.returncode == 0) == good, (p.returncode, result)
    assert result["ok"] == good, result
    assert "PRIVATE KEY" not in p.stdout + p.stderr
    return result.get("result", result)

with tempfile.TemporaryDirectory(prefix="teleauto-interop-") as work:
    temp = Path(work)
    cli("keygen", "--key-id", "test", "--sdk-from", "0.3.0", "--sdk-until", "0.5.0", "--output-dir", temp)
    cli("keygen", "--key-id", "test", "--sdk-from", "0.3.0", "--output-dir", temp, good=False)
    public = temp / "public.properties"
    cli("--key-dir", temp, "export-public", "--output", public)
    request = {"keyId":"test", "sdkVersion":"0.3.0", "vendorId":"developer-1", "packageName":"tech.alius.teleauto", "certSha256":["AB"*32], "expiresAt":int(time.time())+86400, "licenseId":"interop"}
    envelope = cli("--key-dir", temp, "issue", "--request", "-", stdin=json.dumps(request))["license"]
    license_path = temp / "license.txt"
    license_path.write_text(json.dumps(envelope))
    cli("verify", "--public-manifest", temp / "test.json", "--license", license_path, "--sdk-version", "0.3.0", "--package", request["packageName"], "--cert", "AB"*32)
    cli("--key-dir", temp, "issue", "--request", "-", stdin="{bad", good=False)
    cli("--key-dir", temp, "issue", "--request", "-", stdin=" "*32769, good=False)
    cli("--key-dir", temp, "issue", "--request", "-", "--package", "other.app", stdin=json.dumps(request), good=False)
    cli("--key-dir", temp, "issue", "--request", "-", "--output", license_path, stdin=json.dumps(request), good=False)
    cli("issue", "--not-an-option", good=False)
    request["sdkVersion"]="0.5.0"
    cli("--key-dir", temp, "issue", "--request", "-", stdin=json.dumps(request), good=False)
    source = temp / "Interop.java"
    source.write_text('''
import java.nio.file.*; import java.util.*; import tech.alius.rc.license.internal.*;
public class Interop {
 public static void main(String[] a) throws Exception {
  TeleautoLicenseVerifier.Codec codec=new TeleautoLicenseVerifier.Codec(){public byte[] decode(String s){return Base64.getUrlDecoder().decode(s);}public String encode(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}};
  Properties p=new Properties();try(var in=Files.newInputStream(Path.of(a[0]))){p.load(in);}
  Map<String,String> records=new HashMap<>();for(String k:p.stringPropertyNames())records.put(k,p.getProperty(k));
  var keys=TeleautoKeyPolicy.resolve(records,a[2],codec);
  var result=TeleautoLicenseVerifier.verify(Files.readString(Path.of(a[1])),keys,a[3],Set.of(a[4]),"developer-1",Long.parseLong(a[5]),codec);
  System.out.println(result.error);
 }
}
''')
    subprocess.run(["javac", "-d", work, *map(str, (ROOT / "license-common/src/main/java").rglob("*.java")), str(source)], check=True, capture_output=True)
    def java(expected, sdk="0.3.0", pkg="tech.alius.teleauto", cert="AB"*32, at=None):
        p=subprocess.run(["java", "-cp", work, "Interop", str(public), str(license_path), sdk, pkg, cert, str(at or int(time.time()))],capture_output=True,text=True,check=True)
        assert p.stdout.strip()==expected,p.stdout
    java("VALID")
    java("UNKNOWN_KEY",sdk="0.2.9")
    java("UNKNOWN_KEY",sdk="0.5.0")
    java("PACKAGE_MISMATCH",pkg="tech.other.app")
    java("CERTIFICATE_MISMATCH",cert="CD"*32)
    java("LICENSE_EXPIRED",at=request["expiresAt"])
    java("LICENSE_NOT_ACTIVE",at=1)
    envelope["payload"]="A"+envelope["payload"][1:]
    license_path.write_text(json.dumps(envelope))
    java("LICENSE_INVALID")
print("PASS: Rust CLI contract, exclusive writes, SDK version boundaries, and Java signature/identity/time verification")
