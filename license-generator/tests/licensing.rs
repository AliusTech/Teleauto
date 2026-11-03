use std::{fs, path::PathBuf};
use teleauto_license_generator::*;

struct Fixture(PathBuf);
impl Fixture {
    fn new() -> Self {
        Self(std::env::temp_dir().join(format!("teleauto-license-test-{}", uuid::Uuid::new_v4())))
    }
    fn keys(&self) -> Vec<IssuerKey> {
        keygen(
            &self.0,
            "test-key".into(),
            "0.3.0".into(),
            Some("0.5.0".into()),
        )
        .unwrap();
        load_keys(Some(&self.0)).unwrap()
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}
fn request() -> IssueRequest {
    IssueRequest {
        key_id: "test-key".into(),
        sdk_version: "0.3.0".into(),
        vendor_id: "developer-1".into(),
        package_name: "tech.alius.teleauto".into(),
        cert_sha256: vec!["AB".repeat(32)],
        not_before: Some(1000),
        expires_at: 2000,
        license_id: Some("license-1".into()),
    }
}
#[test]
fn sign_verify_time_and_identity() {
    let f = Fixture::new();
    let keys = f.keys();
    let (doc, _) = issue(&keys, request(), 1000).unwrap();
    let check =
        |sdk, pkg, cert, vendor, now| verify(&doc, &keys[0].policy, sdk, pkg, &[cert], vendor, now);
    assert!(check(
        "0.3.0",
        "tech.alius.teleauto",
        "ab".repeat(32),
        Some("developer-1"),
        1000
    )
    .is_ok());
    assert!(check("0.4.99", "tech.alius.teleauto", "AB".repeat(32), None, 1999).is_ok());
    assert!(check("0.2.99", "tech.alius.teleauto", "AB".repeat(32), None, 1000).is_err());
    assert!(check("0.5.0", "tech.alius.teleauto", "AB".repeat(32), None, 1000).is_err());
    assert!(check("0.3.0", "tech.other.app", "AB".repeat(32), None, 1000).is_err());
    assert!(check("0.3.0", "tech.alius.teleauto", "CD".repeat(32), None, 1000).is_err());
    assert!(check(
        "0.3.0",
        "tech.alius.teleauto",
        "AB".repeat(32),
        Some("other"),
        1000
    )
    .is_err());
    assert!(check("0.3.0", "tech.alius.teleauto", "AB".repeat(32), None, 999).is_err());
    assert!(check("0.3.0", "tech.alius.teleauto", "AB".repeat(32), None, 2000).is_err());
}
#[test]
fn tampering_and_different_key_fail() {
    let f = Fixture::new();
    let keys = f.keys();
    let (mut doc, _) = issue(&keys, request(), 1000).unwrap();
    doc.payload.replace_range(0..1, "A");
    assert!(verify(
        &doc,
        &keys[0].policy,
        "0.3.0",
        "tech.alius.teleauto",
        &["AB".repeat(32)],
        None,
        1000
    )
    .is_err());
    let other = Fixture::new();
    let other_keys = other.keys();
    let (doc, _) = issue(&keys, request(), 1000).unwrap();
    assert!(verify(
        &doc,
        &other_keys[0].policy,
        "0.3.0",
        "tech.alius.teleauto",
        &["AB".repeat(32)],
        None,
        1000
    )
    .is_err());
}
#[test]
fn validate_request_and_sdk_boundaries() {
    let f = Fixture::new();
    let keys = f.keys();
    for sdk in [
        "0.2.9",
        "0.5.0",
        "01.3.0",
        "0.3",
        "0.3.0-beta",
        "2147483648.0.0",
    ] {
        let mut r = request();
        r.sdk_version = sdk.into();
        assert!(issue(&keys, r, 1000).is_err());
    }
    for pkg in ["bad", "tech..app", "tech.5app", "tech.app;echo secret"] {
        let mut r = request();
        r.package_name = pkg.into();
        assert!(issue(&keys, r, 1000).is_err());
    }
    let mut r = request();
    r.cert_sha256.clear();
    assert!(issue(&keys, r, 1000).is_err());
    let mut r = request();
    r.expires_at = 1000;
    assert!(issue(&keys, r, 1000).is_err());
    let mut r = request();
    r.key_id = "unknown".into();
    assert!(issue(&keys, r, 1000).is_err());
}
#[test]
fn key_output_is_exclusive_and_manifest_matches() {
    let f = Fixture::new();
    let keys = f.keys();
    assert!(keygen(&f.0, "test-key".into(), "0.3.0".into(), None).is_err());
    let pem = fs::read_to_string(f.0.join("test-key.pem")).unwrap();
    let mut policy = keys[0].policy.clone();
    policy.sdk_until = Some("0.3.0".into());
    assert!(IssuerKey::parse(&serde_json::to_string(&policy).unwrap(), &pem).is_err());
    let other = Fixture::new();
    let _ = other.keys();
    let other_pem = fs::read_to_string(other.0.join("test-key.pem")).unwrap();
    assert!(
        IssuerKey::parse(&serde_json::to_string(&keys[0].policy).unwrap(), &other_pem).is_err()
    );
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        assert_eq!(
            fs::metadata(f.0.join("test-key.pem"))
                .unwrap()
                .permissions()
                .mode()
                & 0o777,
            0o600
        );
    }
}
#[test]
fn schema_and_dates_are_strict() {
    let mut value = serde_json::to_value(request()).unwrap();
    value["isAdmin"] = true.into();
    assert!(serde_json::from_value::<IssueRequest>(value).is_err());
    assert!(timestamp("2027-01-01T00:00:00").is_err());
    assert_eq!(
        timestamp("2027-01-01T08:00:00+08:00").unwrap(),
        timestamp("2027-01-01T00:00:00Z").unwrap()
    );
}
