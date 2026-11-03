# SDK ownership

Maintain core SDK capabilities and all shared UI components here. Shared controls, layout, themes, interaction surfaces and tests live in `sdk/ui/`. Teleauto owns business orchestration and consumes SDK artifacts; never move shared components into Teleauto.

Current core Gradle module: `:sdk`; UI module: `:sdk:ui`. Validate with `./gradlew :sdk:testDebugUnitTest :sdk:ui:testDebugUnitTest :sdk:ui:verifyUiBoundary :sdk:ui:assembleRelease`. Publish both core and UI to Maven Local for application integration.

SDK namespace is `tech.alius.rc`. Owned public SDK types use the Teleauto prefix without vendor prefixes; preserve hardware identification constants. Preserve uncommitted work.

SDK UI namespace is `tech.alius.rc.ui` (including its R class). Desktop package/crate name is `teleauto-assistant`, Rust crate import is `teleauto_assistant`, and bundle identifier is `tech.alius.teleauto.assistant`.

Flutter UI lives in `sdk/ui/lib` and must not depend on core. The platform plugin in `sdk/flutter` consumes the packaged core AAR. Validate both Flutter packages with analyze/test.
