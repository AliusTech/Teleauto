use anyhow::{bail, ensure, Context, Result};
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use chrono::{DateTime, Utc};
use p256::{
    ecdsa::{
        signature::{Signer, Verifier},
        Signature, SigningKey, VerifyingKey,
    },
    pkcs8::{DecodePrivateKey, DecodePublicKey, EncodePrivateKey, EncodePublicKey, LineEnding},
};
use serde::{Deserialize, Serialize};
use std::{
    fs::{self, OpenOptions},
    io::Write,
    path::Path,
};

include!(concat!(env!("OUT_DIR"), "/embedded_keys.rs"));

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct KeyPolicy {
    pub key_id: String,
    pub sdk_from: String,
    pub sdk_until: Option<String>,
    pub public_key: String,
}
fn version(v: &str) -> Result<[u32; 3]> {
    let parts: Vec<_> = v.split('.').collect();
    ensure!(parts.len() == 3, "SDK version must be X.Y.Z");
    let mut out = [0; 3];
    for (i, p) in parts.iter().enumerate() {
        ensure!(
            !p.is_empty()
                && p.bytes().all(|c| c.is_ascii_digit())
                && (p.len() == 1 || !p.starts_with('0')),
            "SDK version must be X.Y.Z without leading zeros"
        );
        out[i] = p.parse()?;
        ensure!(out[i] <= i32::MAX as u32, "SDK version component too large");
    }
    Ok(out)
}
impl KeyPolicy {
    pub fn active(&self, sdk: &str) -> Result<bool> {
        let from = version(&self.sdk_from)?;
        let until = self.sdk_until.as_deref().map(version).transpose()?;
        ensure!(
            until.is_none_or(|v| v > from),
            "sdkUntil must exceed sdkFrom"
        );
        let current = version(sdk)?;
        Ok(current >= from && until.is_none_or(|v| current < v))
    }
    pub fn validate(&self) -> Result<()> {
        ensure!(
            !self.key_id.is_empty()
                && self.key_id.len() <= 64
                && self
                    .key_id
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b"_.-".contains(&b)),
            "Invalid key ID"
        );
        self.active(&self.sdk_from)?;
        VerifyingKey::from_public_key_der(&URL_SAFE_NO_PAD.decode(&self.public_key)?)?;
        Ok(())
    }
}
pub struct IssuerKey {
    pub policy: KeyPolicy,
    signing: SigningKey,
}
impl IssuerKey {
    pub fn parse(manifest: &str, pem: &str) -> Result<Self> {
        let policy: KeyPolicy = serde_json::from_str(manifest).context("Invalid key manifest")?;
        policy.validate()?;
        let signing =
            SigningKey::from_pkcs8_pem(pem).context("Invalid P-256 PKCS#8 private key")?;
        ensure!(
            URL_SAFE_NO_PAD.encode(signing.verifying_key().to_public_key_der()?.as_bytes())
                == policy.public_key,
            "Private key does not match public manifest"
        );
        Ok(Self { policy, signing })
    }
}
pub fn load_keys(dir: Option<&Path>) -> Result<Vec<IssuerKey>> {
    let mut keys = Vec::new();
    if let Some(dir) = dir {
        let mut files: Vec<_> = fs::read_dir(dir)?
            .map(|e| e.map(|v| v.path()))
            .collect::<std::io::Result<_>>()?;
        files.sort();
        for p in files
            .into_iter()
            .filter(|p| p.extension().and_then(|s| s.to_str()) == Some("json"))
        {
            keys.push(IssuerKey::parse(
                &fs::read_to_string(&p)?,
                &fs::read_to_string(p.with_extension("pem"))?,
            )?);
        }
    } else {
        for (manifest, pem) in EMBEDDED {
            keys.push(IssuerKey::parse(manifest, pem)?);
        }
    }
    let mut ids = std::collections::HashSet::new();
    for key in &keys {
        ensure!(ids.insert(&key.policy.key_id), "Duplicate key ID");
    }
    Ok(keys)
}
pub fn write_new(path: &Path, content: &[u8], secret: bool) -> Result<()> {
    if let Some(parent) = path.parent().filter(|p| !p.as_os_str().is_empty()) {
        fs::create_dir_all(parent)?;
    }
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(if secret { 0o600 } else { 0o644 });
    }
    #[cfg(not(unix))]
    let _ = secret;
    let mut f = options
        .open(path)
        .context("Output exists or cannot be created")?;
    f.write_all(content)?;
    f.sync_all()?;
    Ok(())
}
pub fn keygen(
    dir: &Path,
    key_id: String,
    from: String,
    until: Option<String>,
) -> Result<KeyPolicy> {
    let signing = SigningKey::random(&mut rand_core::OsRng);
    let policy = KeyPolicy {
        key_id,
        sdk_from: from,
        sdk_until: until,
        public_key: URL_SAFE_NO_PAD.encode(signing.verifying_key().to_public_key_der()?.as_bytes()),
    };
    policy.validate()?;
    let manifest = dir.join(format!("{}.json", policy.key_id));
    let private = manifest.with_extension("pem");
    // Both writes are exclusive: never replace an existing issuer key or its policy.
    ensure!(
        !manifest.exists() && !private.exists(),
        "Key already exists"
    );
    write_new(
        &private,
        signing.to_pkcs8_pem(LineEnding::LF)?.as_bytes(),
        true,
    )?;
    write_new(&manifest, &serde_json::to_vec_pretty(&policy)?, false)?;
    Ok(policy)
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct IssueRequest {
    pub key_id: String,
    pub sdk_version: String,
    pub vendor_id: String,
    pub package_name: String,
    pub cert_sha256: Vec<String>,
    pub not_before: Option<i64>,
    pub expires_at: i64,
    pub license_id: Option<String>,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Claims {
    pub version: u32,
    pub key_id: String,
    pub vendor_id: String,
    pub package_name: String,
    pub cert_sha256: Vec<String>,
    pub issued_at: i64,
    pub not_before: i64,
    pub expires_at: i64,
    pub license_id: String,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Envelope {
    pub payload: String,
    pub signature: String,
}
pub fn certificate(s: &str) -> Result<String> {
    let normalized = s.replace(':', "").to_ascii_uppercase();
    ensure!(
        normalized.len() == 64 && normalized.bytes().all(|b| b.is_ascii_hexdigit()),
        "Certificate must be a SHA-256 fingerprint (64 hexadecimal characters)"
    );
    Ok(normalized)
}
fn nonempty(s: &str) -> bool {
    !s.trim().is_empty() && s.encode_utf16().count() <= 256
}
impl Claims {
    fn validate(&self) -> Result<()> {
        ensure!(self.version == 1, "Unsupported license version");
        ensure!(
            nonempty(&self.vendor_id) && nonempty(&self.license_id) && nonempty(&self.package_name),
            "Empty or oversized claim"
        );
        let segments: Vec<_> = self.package_name.split('.').collect();
        ensure!(
            segments.len() >= 2
                && segments.iter().all(|s| !s.is_empty()
                    && s.as_bytes()[0].is_ascii_alphabetic()
                    && s.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')),
            "Invalid Android package name"
        );
        ensure!(
            !self.cert_sha256.is_empty() && self.cert_sha256.len() <= 32,
            "Provide 1 to 32 signing certificate fingerprints"
        );
        for c in &self.cert_sha256 {
            certificate(c)?;
        }
        ensure!(
            self.issued_at >= 0
                && self.not_before >= 0
                && self.expires_at > self.not_before
                && self.expires_at > self.issued_at
                && self.expires_at <= 253402300799,
            "Invalid validity interval"
        );
        Ok(())
    }
}
pub fn issue(keys: &[IssuerKey], request: IssueRequest, now: i64) -> Result<(Envelope, Claims)> {
    let key = keys
        .iter()
        .find(|k| k.policy.key_id == request.key_id)
        .context("Unknown issuer key ID")?;
    ensure!(
        key.policy.active(&request.sdk_version)?,
        "Key is not active for requested SDK version"
    );
    let claims = Claims {
        version: 1,
        key_id: request.key_id,
        vendor_id: request.vendor_id,
        package_name: request.package_name,
        cert_sha256: request
            .cert_sha256
            .iter()
            .map(|c| certificate(c))
            .collect::<Result<_>>()?,
        issued_at: now,
        not_before: request.not_before.unwrap_or(now),
        expires_at: request.expires_at,
        license_id: request
            .license_id
            .unwrap_or_else(|| uuid::Uuid::new_v4().to_string()),
    };
    claims.validate()?;
    let payload = URL_SAFE_NO_PAD.encode(serde_json::to_vec(&claims)?);
    let signature: Signature = key.signing.sign(payload.as_bytes());
    Ok((
        Envelope {
            payload,
            signature: URL_SAFE_NO_PAD.encode(signature.to_der().as_bytes()),
        },
        claims,
    ))
}
pub fn verify(
    envelope: &Envelope,
    policy: &KeyPolicy,
    sdk: &str,
    package: &str,
    certs: &[String],
    vendor: Option<&str>,
    now: i64,
) -> Result<Claims> {
    policy.validate()?;
    ensure!(policy.active(sdk)?, "Key is not active for SDK version");
    ensure!(
        envelope.payload.len() <= 24000 && envelope.signature.len() <= 24000,
        "Oversized license"
    );
    let decoded = URL_SAFE_NO_PAD.decode(&envelope.payload)?;
    ensure!(
        URL_SAFE_NO_PAD.encode(&decoded) == envelope.payload,
        "Noncanonical payload"
    );
    let signature = Signature::from_der(&URL_SAFE_NO_PAD.decode(&envelope.signature)?)?;
    let key = VerifyingKey::from_public_key_der(&URL_SAFE_NO_PAD.decode(&policy.public_key)?)?;
    key.verify(envelope.payload.as_bytes(), &signature)
        .context("Invalid license signature")?;
    let claims: Claims = serde_json::from_slice(&decoded)?;
    claims.validate()?;
    ensure!(claims.key_id == policy.key_id, "Key ID mismatch");
    ensure!(claims.package_name == package, "Package mismatch");
    ensure!(!certs.is_empty(), "Host certificate required");
    for cert in certs {
        ensure!(
            claims.cert_sha256.contains(&certificate(cert)?),
            "Certificate mismatch"
        );
    }
    ensure!(
        vendor.is_none_or(|v| v == claims.vendor_id),
        "Vendor mismatch"
    );
    ensure!(
        now >= claims.not_before && now < claims.expires_at,
        "License is not active or has expired"
    );
    Ok(claims)
}
pub fn timestamp(value: &str) -> Result<i64> {
    let t = DateTime::parse_from_rfc3339(value)
        .context("Use RFC3339 with timezone, e.g. 2027-01-01T00:00:00+08:00")?
        .timestamp();
    if t < 0 {
        bail!("Date must not precede 1970");
    }
    Ok(t)
}
pub fn now() -> i64 {
    Utc::now().timestamp()
}
