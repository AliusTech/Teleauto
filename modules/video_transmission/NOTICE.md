# OpenIPC WFB receiver attribution

This module contains adapted source from [OpenIPC/PixelPilot](https://github.com/OpenIPC/PixelPilot),
including its WFB-NG Android integration, the OpenIPC `devourer` userspace RTL8812 driver,
and the `svpcom/wfb-ng` receiver. The imported source remains licensed under GPL-3.0;
see `LICENSE`, `src/main/cpp/devourer/LICENSE`, and `src/main/cpp/wfb-ng/LICENSE.txt`.

Imported revisions:

- PixelPilot: source snapshot downloaded 2026-08-27
- devourer: `bb03774c9968c1fbe3430d3d830b5091f7a47022`
- wfb-ng: revision recorded by the PixelPilot submodule at import time

Alius adaptations make the key and USB lock paths application-relative, expose a Context-only
Java wrapper, use arm64-v8a, and operate the radio in receive-only mode for lower downlink loss.
