use anyhow::{ensure, Context, Result};
use clap::{Args, Parser, Subcommand};
use serde_json::{json, Value};
use std::{
    io::{self, IsTerminal, Read, Write},
    path::{Path, PathBuf},
};
use teleauto_license_generator::*;

#[derive(Parser)]
#[command(
    version,
    about = "Teleauto SDK issuer CLI. No command starts the interactive wizard."
)]
struct Cli {
    /// Machine-readable success/error output; never prints private key material.
    #[arg(long, global = true)]
    json: bool,
    /// Use external key directory instead of the keys embedded at build time.
    #[arg(long, global = true)]
    key_dir: Option<PathBuf>,
    #[command(subcommand)]
    command: Option<Command>,
}
#[derive(Subcommand)]
enum Command {
    /// Guided license creation; requires a terminal.
    Interactive,
    /// Generate a P-256 key with a mandatory SDK version policy; rebuild to embed it.
    Keygen {
        #[arg(long)]
        key_id: String,
        #[arg(long)]
        sdk_from: String,
        #[arg(long)]
        sdk_until: Option<String>,
        #[arg(long)]
        output_dir: PathBuf,
    },
    /// Show public metadata of available signing keys.
    Keys,
    /// Export public trust anchors for SDK builds. Never contains a private key.
    ExportPublic {
        #[arg(long)]
        output: PathBuf,
    },
    /// Noninteractive issuance, suitable for FastAPI subprocess calls.
    Issue(IssueArgs),
    /// Verify with a public manifest; no private key is needed.
    Verify {
        #[arg(long)]
        public_manifest: PathBuf,
        #[arg(long)]
        license: PathBuf,
        #[arg(long)]
        sdk_version: String,
        #[arg(long)]
        package: String,
        #[arg(long, required = true, value_delimiter = ',')]
        cert: Vec<String>,
        #[arg(long)]
        vendor: Option<String>,
    },
}
#[derive(Args)]
struct IssueArgs {
    /// JSON request path, or '-' to read stdin. Cannot be mixed with claim flags.
    #[arg(long)]
    request: Option<PathBuf>,
    #[arg(long)]
    key_id: Option<String>,
    #[arg(long)]
    sdk_version: Option<String>,
    #[arg(long)]
    vendor: Option<String>,
    #[arg(long)]
    package: Option<String>,
    #[arg(long, value_delimiter = ',')]
    cert: Vec<String>,
    /// RFC3339 timestamp including timezone; defaults to now.
    #[arg(long)]
    start: Option<String>,
    #[arg(long, conflicts_with = "days")]
    expire: Option<String>,
    /// Duration in 24-hour days from start (exclusive expiration).
    #[arg(long, conflicts_with = "expire")]
    days: Option<u32>,
    #[arg(long)]
    license_id: Option<String>,
    /// Optional license file destination. Exclusive create, never overwrites.
    #[arg(long)]
    output: Option<PathBuf>,
}
fn bounded_read(mut reader: impl Read) -> Result<String> {
    let mut bytes = Vec::new();
    reader.by_ref().take(32769).read_to_end(&mut bytes)?;
    ensure!(bytes.len() <= 32768, "Request exceeds 32 KiB");
    Ok(String::from_utf8(bytes)?)
}
fn read(path: &Path) -> Result<String> {
    bounded_read(std::fs::File::open(path)?)
}
fn required(value: Option<String>, flag: &str) -> Result<String> {
    value.with_context(|| format!("Missing --{flag}"))
}
fn prompt(label: &str, default: Option<&str>) -> Result<String> {
    eprint!(
        "{label}{}：",
        default.map(|s| format!(" [{s}]")).unwrap_or_default()
    );
    io::stderr().flush()?;
    let mut line = String::new();
    ensure!(
        io::stdin().read_line(&mut line)? > 0,
        "Input ended; cancelled"
    );
    let value = line.trim();
    Ok(if value.is_empty() {
        default.unwrap_or("").to_owned()
    } else {
        value.to_owned()
    })
}
fn wizard(keys: &[IssuerKey]) -> Result<(IssueRequest, PathBuf)> {
    ensure!(
        io::stdin().is_terminal(),
        "Interactive mode requires a terminal; use issue --request - --json for servers"
    );
    ensure!(
        !keys.is_empty(),
        "No embedded keys; generate a key and rebuild, or pass --key-dir"
    );
    eprintln!("Teleauto SDK 授权文件生成向导");
    let sdk = prompt("目标 SDK 版本", Some(include_str!("../../VERSION").trim()))?;
    let available = keys
        .iter()
        .filter(|k| k.policy.active(&sdk).unwrap_or(false))
        .collect::<Vec<_>>();
    ensure!(
        !available.is_empty(),
        "No key is active for this SDK version"
    );
    for (i, k) in available.iter().enumerate() {
        eprintln!(
            "{}. {}（{} ≤ SDK < {}）",
            i + 1,
            k.policy.key_id,
            k.policy.sdk_from,
            k.policy.sdk_until.as_deref().unwrap_or("不限")
        );
    }
    let index: usize = prompt("选择签名密钥", Some("1"))?
        .parse()
        .context("Enter a key number")?;
    let key = available
        .get(index.wrapping_sub(1))
        .context("Invalid key selection")?;
    let vendor = prompt("开发者/组织 ID（服务端分配）", None)?;
    let package = prompt("Android 包名", Some("tech.alius.teleauto"))?;
    let certs = prompt("APK 签名证书 SHA-256（多个用逗号分隔）", None)?
        .split(',')
        .map(|s| s.trim().to_owned())
        .collect();
    let start = prompt("生效时间（RFC3339，留空立即生效）", None)?;
    let start = if start.is_empty() {
        now()
    } else {
        timestamp(&start)?
    };
    eprintln!("有效期：1. 30 天  2. 90 天  3. 365 天  4. 自定义截止时间");
    let expire = match prompt("选择有效期", Some("1"))?.as_str() {
        "1" => start + 30 * 86400,
        "2" => start + 90 * 86400,
        "3" => start + 365 * 86400,
        "4" => timestamp(&prompt("截止时间（RFC3339）", None)?)?,
        _ => anyhow::bail!("Invalid duration selection"),
    };
    let license_id = prompt("授权 ID（留空自动生成）", None)?;
    let output = PathBuf::from(prompt("保存路径", Some("license.json"))?);
    eprintln!(
        "包名：{package}\n组织：{vendor}\nSDK：{sdk}\n生效：{start}\n到期：{expire}\n保存：{}",
        output.display()
    );
    ensure!(
        prompt("确认签发？输入 yes", Some("no"))? == "yes",
        "Cancelled"
    );
    Ok((
        IssueRequest {
            key_id: key.policy.key_id.clone(),
            sdk_version: sdk,
            vendor_id: vendor,
            package_name: package,
            cert_sha256: certs,
            not_before: Some(start),
            expires_at: expire,
            license_id: (!license_id.is_empty()).then_some(license_id),
        },
        output,
    ))
}
fn issued(keys: &[IssuerKey], request: IssueRequest, output: Option<PathBuf>) -> Result<Value> {
    let sdk = request.sdk_version.clone();
    let (envelope, claims) = issue(keys, request, now())?;
    if let Some(path) = &output {
        write_new(path, &serde_json::to_vec_pretty(&envelope)?, false)?;
    }
    Ok(json!({"license": envelope, "claims": claims, "sdkVersion": sdk, "output": output}))
}
fn run(cli: &Cli) -> Result<Value> {
    match cli.command.as_ref() {
        Some(Command::Keygen {
            key_id,
            sdk_from,
            sdk_until,
            output_dir,
        }) => Ok(
            json!({"key": keygen(output_dir, key_id.clone(), sdk_from.clone(), sdk_until.clone())?, "rebuildRequired": true}),
        ),
        Some(Command::Verify {
            public_manifest,
            license,
            sdk_version,
            package,
            cert,
            vendor,
        }) => {
            let policy: KeyPolicy = serde_json::from_str(&read(public_manifest)?)?;
            let envelope: Envelope = serde_json::from_str(&read(license)?)?;
            Ok(
                json!({"claims": verify(&envelope, &policy, sdk_version, package, cert, vendor.as_deref(), now())?}),
            )
        }
        command => {
            let keys = load_keys(cli.key_dir.as_deref())?;
            match command {
                Some(Command::Keys) => {
                    Ok(json!({"keys": keys.iter().map(|k| &k.policy).collect::<Vec<_>>()}))
                }
                Some(Command::ExportPublic { output }) => {
                    ensure!(!keys.is_empty(), "No signing keys available");
                    let mut registry = String::from("# Teleauto SDK: keyId=sdkFromInclusive|sdkUntilExclusiveOrStar|base64urlSPKI\n");
                    for k in &keys {
                        registry.push_str(&format!(
                            "{}={}|{}|{}\n",
                            k.policy.key_id,
                            k.policy.sdk_from,
                            k.policy.sdk_until.as_deref().unwrap_or("*"),
                            k.policy.public_key
                        ));
                    }
                    write_new(output, registry.as_bytes(), false)?;
                    Ok(json!({"output": output, "keys": keys.len()}))
                }
                Some(Command::Issue(args)) => {
                    let request = if let Some(path) = &args.request {
                        ensure!(
                            args.key_id.is_none()
                                && args.sdk_version.is_none()
                                && args.vendor.is_none()
                                && args.package.is_none()
                                && args.cert.is_empty()
                                && args.start.is_none()
                                && args.expire.is_none()
                                && args.days.is_none()
                                && args.license_id.is_none(),
                            "--request cannot be mixed with claim flags"
                        );
                        let document = if path == Path::new("-") {
                            bounded_read(io::stdin())?
                        } else {
                            read(path)?
                        };
                        serde_json::from_str(&document).context("Invalid issue request JSON")?
                    } else {
                        let start = args
                            .start
                            .as_deref()
                            .map(timestamp)
                            .transpose()?
                            .unwrap_or_else(now);
                        let expires = match (&args.expire, args.days) {
                            (Some(expire), None) => timestamp(expire)?,
                            (None, Some(days)) => {
                                ensure!(days > 0, "--days must be positive");
                                start
                                    .checked_add(i64::from(days) * 86400)
                                    .context("Expiry overflow")?
                            }
                            _ => anyhow::bail!("Provide --days or --expire"),
                        };
                        IssueRequest {
                            key_id: required(args.key_id.clone(), "key-id")?,
                            sdk_version: required(args.sdk_version.clone(), "sdk-version")?,
                            vendor_id: required(args.vendor.clone(), "vendor")?,
                            package_name: required(args.package.clone(), "package")?,
                            cert_sha256: args.cert.clone(),
                            not_before: Some(start),
                            expires_at: expires,
                            license_id: args.license_id.clone(),
                        }
                    };
                    issued(&keys, request, args.output.clone())
                }
                None | Some(Command::Interactive) => {
                    let (request, output) = wizard(&keys)?;
                    issued(&keys, request, Some(output))
                }
                _ => unreachable!(),
            }
        }
    }
}
fn main() {
    let cli = match Cli::try_parse() {
        Ok(cli) => cli,
        Err(error) => {
            if std::env::args().any(|s| s == "--json") && error.use_stderr() {
                println!(
                    "{}",
                    json!({"ok":false,"error":{"code":"INVALID_ARGUMENTS","message":"Invalid command arguments; see --help"}})
                );
                std::process::exit(2);
            }
            error.exit();
        }
    };
    match run(&cli) {
        Ok(result) => println!(
            "{}",
            if cli.json {
                json!({"ok":true,"result":result}).to_string()
            } else {
                serde_json::to_string_pretty(&result).unwrap()
            }
        ),
        Err(error) => {
            if cli.json {
                println!(
                    "{}",
                    json!({"ok":false,"error":{"code":"LICENSE_OPERATION_FAILED","message":error.to_string()}})
                );
            } else {
                eprintln!("License Generator: {error}");
            }
            std::process::exit(2);
        }
    }
}
