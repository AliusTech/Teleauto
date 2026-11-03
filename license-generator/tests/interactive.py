"""PTY smoke test for the actual Chinese wizard, including cancellation."""
import json
import os
from pathlib import Path
import pty
import select
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
BIN = ROOT / 'license-generator/target/debug/teleauto-license-generator'
with tempfile.TemporaryDirectory(prefix='teleauto-wizard-') as directory:
    output = Path(directory) / 'license.json'
    def wizard(confirm, target):
        master, slave = pty.openpty()
        process = subprocess.Popen([str(BIN)], stdin=slave, stdout=slave, stderr=slave)
        os.close(slave)
        answers = ['', '1', 'test-organization', 'tech.test.controller', 'AB'*32, '', '1', 'test-wizard', str(target), confirm]
        os.write(master, ('\n'.join(answers)+'\n').encode())
        transcript = bytearray()
        deadline = time.monotonic()+15
        while process.poll() is None and time.monotonic() < deadline:
            if select.select([master], [], [], 0.2)[0]:
                try: transcript.extend(os.read(master, 4096))
                except OSError: break
        try: code = process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            process.kill(); process.wait(); raise AssertionError('Wizard did not finish')
        finally: os.close(master)
        assert b'PRIVATE KEY' not in transcript
        return code
    assert wizard('yes', output) == 0
    license_data = json.loads(output.read_text())
    assert set(license_data) == {'payload', 'signature'}
    cancelled = Path(directory) / 'cancelled.json'
    assert wizard('no', cancelled) != 0
    assert not cancelled.exists()
print('PASS: interactive PTY issue and cancel')
