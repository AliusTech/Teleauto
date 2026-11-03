use std::{env, fs, path::PathBuf};
fn main() {
    let keys = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap()).join("keys");
    println!("cargo:rerun-if-changed={}", keys.display());
    let mut source = String::from("pub const EMBEDDED: &[(&str, &str)] = &[\n");
    if env::var_os("CARGO_FEATURE_EMBEDDED_KEYS").is_some() {
        let mut entries: Vec<_> = fs::read_dir(&keys)
            .unwrap()
            .map(|e| e.unwrap().path())
            .filter(|p| p.extension().and_then(|x| x.to_str()) == Some("json"))
            .collect();
        entries.sort();
        for entry in entries {
            let pem = entry.with_extension("pem");
            assert!(
                pem.exists(),
                "Each issuer manifest requires a matching private PEM"
            );
            source.push_str(&format!(
                "(include_str!({:?}), include_str!({:?})),\n",
                entry, pem
            ));
        }
    }
    source.push_str("];\n");
    fs::write(
        PathBuf::from(env::var("OUT_DIR").unwrap()).join("embedded_keys.rs"),
        source,
    )
    .unwrap();
}
