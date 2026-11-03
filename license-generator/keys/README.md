# Issuer keys

This private SDK repository stores issuer private keys by explicit project-owner policy.
Each key has a `<keyId>.pem` (P-256 PKCS#8) and `<keyId>.json` (public identity and SDK version window).

The Rust tool embeds these pairs at build time by default. The resulting executable is an issuer credential and belongs only on authorized operator machines and the license server. It is not an SDK download or a customer artifact. SDK Gradle source sets never include this directory.

Generate keys through `keygen`; do not overwrite or repurpose an existing key ID. Rebuild the CLI after adding a key. Export the public registry separately into the SDK. `--no-default-features` builds a tool without embedded keys for deployments that use an external `--key-dir`.
