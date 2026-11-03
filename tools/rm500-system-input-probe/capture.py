#!/usr/bin/env python3
"""Capture one labelled, bounded RM500 operation without changing device settings."""
import argparse
import datetime
import json
from pathlib import Path
import selectors
import subprocess
import time

LABELS = ("baseline", "c1", "c2", "pause", "c3", "rth", "c1_c2", "switch", "sticks_dials")


def adb_call(adb, serial, *args):
    result = subprocess.run([adb, "-s", serial, *args], capture_output=True, text=True, timeout=20)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--source", choices=("protocol", "kernel"), default="protocol",
                        help="kernel reads every input node using the existing shell permissions")
    parser.add_argument("--label", choices=LABELS, required=True)
    parser.add_argument("--repeat", type=int, choices=(1, 2, 3), default=1)
    parser.add_argument("--seconds", type=int, default=20)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not 5 <= args.seconds <= 180:
        parser.error("--seconds must be 5..180")
    if args.output.exists():
        parser.error("output already exists; choose a new directory to preserve earlier evidence")
    if adb_call(args.adb, args.serial, "get-state") != "device":
        parser.error("device is not ready")
    model = adb_call(args.adb, args.serial, "shell", "getprop", "ro.product.name")
    if model.lower() not in ("ag501", "rm500"):
        parser.error("target is not RM500/AG501")
    metadata = {
        "serial": args.serial, "model": model, "label": args.label, "repeat": args.repeat,
        "source": args.source,
        "started_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "duration_requested_seconds": args.seconds,
        "firmware": adb_call(args.adb, args.serial, "shell", "getprop", "ro.build.fingerprint"),
        "device_uptime_start": adb_call(args.adb, args.serial, "shell", "cat", "/proc/uptime"),
        "status": "capturing", "physical_operation_confirmed": False,
        "note": "Label identifies requested operation; confirm execution separately. No mapping is inferred automatically.",
    }
    args.output.mkdir(parents=True)
    (args.output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
    # A remote PTY makes getevent line-buffered. With a pipe its final partial
    # stdio buffer can be lost when timeout terminates it, hiding short presses.
    command = (["shell", "-tt", "timeout", str(args.seconds + 2), "getevent", "-lt"]
               if args.source == "kernel" else
               ["logcat", "-T", "1", "-v", "monotonic", "TeleautoInputProbe:I", "*:S"])
    proc = subprocess.Popen([args.adb, "-s", args.serial, *command],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    selector = selectors.DefaultSelector()
    selector.register(proc.stdout, selectors.EVENT_READ, args.source)
    selector.register(proc.stderr, selectors.EVENT_READ, "stderr")
    start = time.monotonic()
    try:
        with (args.output / "capture.jsonl").open("w") as stream:
            print("CAPTURE_STARTED", args.label, args.repeat, flush=True)
            while time.monotonic() - start < args.seconds:
                if proc.poll() is not None:
                    metadata["status"] = "transport_ended"
                    metadata["exit_code"] = proc.returncode
                    break
                for key, _ in selector.select(timeout=0.25):
                    import os
                    chunk = os.read(key.fileobj.fileno(), 65536)
                    if chunk:
                        stream.write(json.dumps({"host_elapsed_s": time.monotonic() - start,
                                                 "source": key.data,
                                                 "text": chunk.decode("utf-8", errors="replace")},
                                                ensure_ascii=False) + "\n")
                        stream.flush()
            else:
                metadata["status"] = "captured"
    except KeyboardInterrupt:
        metadata["status"] = "interrupted"
    finally:
        selector.close()
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
        metadata["duration_actual_seconds"] = time.monotonic() - start
        metadata["ended_utc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        (args.output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
    print(json.dumps(metadata, ensure_ascii=False, indent=2))
    if metadata["status"] != "captured":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
