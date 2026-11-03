"""Assert issuer credentials/tools are absent from customer AAR/APK/JAR artifacts."""
import base64
import io
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
secrets = []
for pem in (ROOT / 'license-generator/keys').glob('*.pem'):
    body = ''.join(line for line in pem.read_text().splitlines() if not line.startswith('---'))
    secrets.extend([body.encode(), base64.b64decode(body)])

def inspect(blob, label):
    assert b'-----BEGIN PRIVATE KEY-----' not in blob, f'Private PEM in {label}'
    assert b'-----BEGIN EC PRIVATE KEY-----' not in blob, f'Private PEM in {label}'
    for secret in secrets:
        assert secret not in blob, f'Issuer private key in {label}'
    if zipfile.is_zipfile(io.BytesIO(blob)):
        with zipfile.ZipFile(io.BytesIO(blob)) as archive:
            for item in archive.infolist():
                if item.is_dir(): continue
                assert 'license-generator' not in item.filename, f'Issuer tool in {label}'
                inspect(archive.read(item), f'{label}/{item.filename}')

assert len(sys.argv) > 1, 'Pass one or more built AAR/APK/JAR files'
for name in sys.argv[1:]:
    path = Path(name)
    inspect(path.read_bytes(), path.name)
    print(f'PASS: {path.name} contains no issuer key or generator')
