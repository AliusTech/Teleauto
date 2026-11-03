# Teleauto shared Flutter UI

`lib/src/teleauto_tokens.dart` is the source of truth for colors, typography, spacing, corner radii and control geometry. Tokens carry forward the accepted matte crater design. `navigationHeight` is 30 logical units so expanded navigation remains visible at the 1080-pixel baseline. Pages compose SDK components and receive state/callbacks; they do not import core, platform channels, storage or hardware managers.

TeleautoViewport paints the background edge to edge while preserving safe insets for controls. Battery and icon actions use the shared status-bar components. Rectangular controls retain keyHeight, independent of roundKey.

Console-to-control-center navigation uses the shared 260 ms panel transition. Console controls exit laterally while the control center enters from the right over 24 logical pixels; reverse navigation follows the same path. Reduced-motion mode keeps the crossfade and removes positional movement, and the console remains mounted so native video textures are preserved.

The console uses one layout for all output types and controller models. Extra height is distributed across four gaps. Android sticks and centering dials are display-only; the continuous dial permits dragging. iOS opts into touch callbacks for both sticks and centering dials, including release/cancel recentering. Stick rim indicators follow their respective axes. The status panel retains the original Alius logo, a flat surface and one thin border. Locked controls are gray; Android unlocks with physical C1+C2 hold progress; iOS permits the two on-screen C1+C2 controls while locked and unlocks only when both are down simultaneously.

Device SVGs are derived from the matching native vector assets. Xbox, Joystick and CyberBrick remain distinct virtual-device identities. Fonts are loaded explicitly during screenshot tests; screenshots are written to `build/visuals` at 1920×1080, 1920×1200 and 1920×1280.

Validation: `flutter analyze` and `flutter test` from this package. Native UI remains temporarily available to legacy consumers; Flutter is the mobile application's shared UI implementation.

### Dial progress and axis direction

Flutter joystick Y follows the native console: -1 is up and +1 is down. Touch callbacks, pointer position and vertical rim slider use the same convention; release/cancel returns touch sticks to zero. Continuous rollers fill from the left; centering dials fill from the center. Progress stays inside the recessed track and has no numeric label. All round control keys share the same material bounds; color overlays are inset to the material face. The status bar uses plain device/settings icons and an outlined percentage battery. The lower center status frame remains visible in every presentation mode.

Hold indicators are centered immediately below their labels as a single group inside the button face. `buttonLineHeight` (1.0) keeps label metrics compact; the material shadow is excluded from visual centering. Pause has no hold underline.

Reference styling: dial scales use 10 intervals, minor tick ratio .35 and center tick ratio .6 inherited from native UI. The recessed progress is subdued gray. Battery fill is inset 2 units with a 1-unit radius and tracks the clamped level; unknown level stays empty. Resting button labels/icons use secondary gray-white and become white when active.

Hold indicator refinement: remove the extra label gap, lift the line 3 logical units to compensate for font descent, and use a 1.8-unit stroke and a 10-unit width to balance bold button labels. Pressed-state behavior remains unchanged.

Dial fill occupies the inner groove with a 2-unit rim inset and rounded clipping; ticks render above it. TeleautoDialThumb is shared by dials and joystick rim sliders, preserving the 46:20 asset aspect ratio. Rim positions use the native 354-unit template: edge 26, thickness 16, horizontal inset 59, vertical bounds 53 to height-60. Travel accounts for thumb length to keep endpoints inside the slot.

Add-device cards use four equal columns with shared gaps. Names reserve 36 logical units for up to two centered lines, keeping card rows aligned across controller resolutions.
