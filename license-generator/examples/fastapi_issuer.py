"""Async signer adapter for a future FastAPI service (Python 3.11+).

Call only after authentication, ownership, entitlement and idempotency checks.
This module deliberately defines no public HTTP endpoint or user database.
"""
import asyncio
import json
from pathlib import Path


class IssuerError(RuntimeError):
    pass


class LicenseIssuer:
    def __init__(self, executable: Path, concurrency: int = 4):
        self.executable = executable.resolve(strict=True)
        self._slots = asyncio.Semaphore(concurrency)

    async def issue(self, request: dict) -> dict:
        # The service constructs this object from an approved registration.
        # Never accept executable paths, output paths or extra CLI flags from clients.
        payload = json.dumps(request, separators=(",", ":")).encode()
        if len(payload) > 32768:
            raise IssuerError("Request is too large")
        async with self._slots:
            process = await asyncio.create_subprocess_exec(
                str(self.executable), "issue", "--request", "-", "--json",
                stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            try:
                stdout, _ = await asyncio.wait_for(process.communicate(payload), timeout=15)
            except BaseException:
                if process.returncode is None:
                    try:
                        process.kill()
                    except ProcessLookupError:
                        pass
                await process.wait()
                raise
            try:
                response = json.loads(stdout)
            except (ValueError, UnicodeDecodeError) as exc:
                raise IssuerError("Invalid signer response") from exc
            if process.returncode != 0 or response.get("ok") is not True:
                # Do not expose server paths or internal issuer diagnostics to API clients.
                raise IssuerError("License issuance failed")
            return response["result"]
