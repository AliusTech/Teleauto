import 'teleauto_panel_transition.dart';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'teleauto_controls.dart';
import 'teleauto_models.dart';
import 'teleauto_tokens.dart';
import 'teleauto_hid_connection_panel.dart';

/// One layout for RM500/RM700 and all device presentation modes.
class TeleautoConsole extends StatelessWidget {
  const TeleautoConsole({
    super.key,
    required this.state,
    required this.onKey,
    required this.onDial,
    required this.onLock,
    required this.onSettings,
    required this.onDevices,
    this.onFullscreen,
    this.onDisconnect,
    this.onConnect,
    this.onStick,
    this.onCenterDial,
    this.video,
  });
  final TeleautoConsoleState state;
  final void Function(String, bool) onKey;
  final ValueChanged<double> onDial;
  final VoidCallback onLock, onSettings, onDevices;
  final VoidCallback? onFullscreen, onDisconnect, onConnect;
  final void Function(bool, Offset)? onStick;
  final void Function(bool, double)? onCenterDial;
  final Widget? video;
  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, box) {
      final geometry = TeleautoConsoleGeometry(
        Size(box.maxWidth, box.maxHeight),
      );
      final size = geometry.canvas;
      final side = geometry.side;
      final top = TeleautoTokens.topHeight + 11 + geometry.extra;
      final stickTop = top + TeleautoTokens.dialHeight + 11 + geometry.extra;
      final keysTop = stickTop + side + TeleautoTokens.inset + geometry.extra;
      final footerBottom = size.height - TeleautoTokens.consoleBottomInset;
      final footerTop = footerBottom - TeleautoTokens.footerHeight;
      final videoTop =
          TeleautoTokens.topHeight + TeleautoTokens.consoleVideoTopGap;
      final centerX = TeleautoTokens.inset + side + TeleautoTokens.inset;
      final centerWidth = size.width - 2 * centerX;
      final enabled = state.enabled && !state.locked;
      final motion = TeleautoConsoleMotion.of(context);
      Widget place(double x, double y, double w, double h, Widget child) {
        final progress = motion?.progress ?? 0;
        final isHeader = y == 0;
        final left =
            x + w / 2 < size.width / 2 ||
            (x + w / 2 == size.width / 2 && y < size.height / 2);
        final distance = left ? -(x + w) : size.width - x;
        return Positioned(
          left: x,
          top: y,
          width: w,
          height: h,
          child: Transform.translate(
            offset: Offset(
              isHeader || motion?.reduced == true ? 0 : distance * progress,
              0,
            ),
            child: Opacity(opacity: 1 - progress, child: child),
          ),
        );
      }

      Widget key(String id, {bool round = false, String? icon, Color? tint}) =>
          TeleautoButton(
            label: id == 'GOHOME'
                ? '返航'
                : id == 'PAUSE'
                ? '暂停'
                : id == 'AUTH_LED'
                ? 'AUTH'
                : id,
            icon: icon,
            round: round,
            tint: tint,
            active: state.held.contains(id),
            enabled:
                enabled ||
                (state.enabled &&
                    state.locked &&
                    state.touchUnlock &&
                    ['C1', 'C2'].contains(id)),
            onHeldChanged: id == 'AUTH_LED' ? null : (down) => onKey(id, down),
            onPressed: id == 'AUTH_LED' ? () => onKey(id, false) : null,
          );
      Widget keyGroup(bool right) => LayoutBuilder(
        builder: (context, box) {
          const width = TeleautoTokens.keyWidth,
              height = TeleautoTokens.keyHeight;
          final left =
              (box.maxWidth - 2 * width - TeleautoTokens.keyColumnGap) / 2;
          final controls = right
              ? ['R1', 'C2', 'R2', 'C3', 'R3', 'PAUSE']
              : ['C1', 'L1', 'GOHOME', 'L2', 'AUTH_LED', 'L3'];
          return Stack(
            clipBehavior: Clip.none,
            children: [
              for (var i = 0; i < controls.length; i++)
                place(
                  left + (i % 2) * (width + TeleautoTokens.keyColumnGap),
                  i ~/ 2 * (height + TeleautoTokens.rowGap) -
                      (['GOHOME', 'AUTH_LED', 'PAUSE'].contains(controls[i])
                          ? (TeleautoTokens.roundKey - height) / 2
                          : 0),
                  width,
                  ['GOHOME', 'AUTH_LED', 'PAUSE'].contains(controls[i])
                      ? TeleautoTokens.roundKey
                      : height,
                  Center(
                    child: SizedBox(
                      width:
                          ['GOHOME', 'AUTH_LED', 'PAUSE'].contains(controls[i])
                          ? TeleautoTokens.roundKey
                          : width,
                      height:
                          ['GOHOME', 'AUTH_LED', 'PAUSE'].contains(controls[i])
                          ? TeleautoTokens.roundKey
                          : height,
                      child: key(
                        controls[i],
                        round: [
                          'GOHOME',
                          'AUTH_LED',
                          'PAUSE',
                        ].contains(controls[i]),
                        icon: switch (controls[i]) {
                          'GOHOME' => 'return_home',
                          'AUTH_LED' => 'auth',
                          'PAUSE' => 'pause',
                          _ => null,
                        },
                        tint: switch (controls[i]) {
                          'AUTH_LED' => TeleautoTokens.auth,
                          'PAUSE' => TeleautoTokens.pause,
                          _ => null,
                        },
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      );
      final canvas = Stack(
        children: [
          Positioned.fill(
            child: Image.asset(
              '${TeleautoTokens.assets}/png/background.png',
              package: TeleautoTokens.package,
              fit: BoxFit.cover,
              excludeFromSemantics: true,
            ),
          ),
          place(
            0,
            0,
            size.width,
            TeleautoTokens.topHeight,
            TeleautoTopBar(
              child: Row(
                children: [
                  TeleautoIcon(
                    'alius-logo',
                    color: TeleautoTokens.modeColor(state.mode),
                    size: TeleautoTokens.icon,
                  ),
                  const SizedBox(width: TeleautoTokens.gap),
                  Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        state.status,
                        style: TeleautoTokens.textStyle(bold: true),
                      ),
                      Text(
                        '${state.mode} · ${switch (state.mode) {
                          "S" => "运动模式",
                          "C" => "平稳模式",
                          _ => "稳定模式",
                        }}',
                        style: TeleautoTokens.textStyle(
                          size: TeleautoTokens.metaFont,
                          color: TeleautoTokens.modeColor(state.mode),
                        ),
                      ),
                    ],
                  ),
                  Expanded(
                    child: Text(
                      state.deviceName,
                      textAlign: TextAlign.center,
                      style: TeleautoTokens.textStyle(
                        size: TeleautoTokens.bodyFont,
                        bold: true,
                      ),
                    ),
                  ),
                  SizedBox(
                    width: TeleautoTokens.footerHeight,
                    height: TeleautoTokens.footerHeight,
                    child: TeleautoIconAction(
                      label: '设备',
                      icon: 'xbox',
                      enabled: enabled,
                      onPressed: onDevices,
                    ),
                  ),
                  const SizedBox(width: TeleautoTokens.deviceBatteryGap),
                  TeleautoBatterySelector(
                    enabled: enabled,
                    entries: state.batteries.isNotEmpty
                        ? state.batteries
                        : [
                            TeleautoBatteryEntry(
                              id: 'controller',
                              name: '遥控器电池',
                              level: state.battery,
                            ),
                          ],
                  ),
                  const SizedBox(width: TeleautoTokens.inset),
                  SizedBox(
                    width: TeleautoTokens.footerHeight,
                    height: TeleautoTokens.footerHeight,
                    child: TeleautoIconAction(
                      label: '控制中心',
                      icon: 'settings',
                      enabled: !state.locked,
                      onPressed: onSettings,
                    ),
                  ),
                ],
              ),
            ),
          ),
          for (final right in [false, true]) ...[
            place(
              right
                  ? size.width - TeleautoTokens.inset - side
                  : TeleautoTokens.inset,
              top,
              side,
              TeleautoTokens.dialHeight,
              TeleautoDial(
                value: state.axis(right ? 5 : 4),
                centered: true,
                centerOnRelease: onCenterDial != null,
                onChanged: enabled && onCenterDial != null
                    ? (value) => onCenterDial!(right, value)
                    : null,
              ),
            ),
            place(
              right
                  ? size.width - TeleautoTokens.inset - side
                  : TeleautoTokens.inset,
              stickTop,
              side,
              side,
              TeleautoJoystick(
                x: state.axis(right ? 2 : 0),
                y: state.axis(right ? 3 : 1),
                right: right,
                onChanged: enabled && onStick != null
                    ? (value) => onStick!(right, value)
                    : null,
              ),
            ),
            place(
              right
                  ? size.width - TeleautoTokens.inset - side
                  : TeleautoTokens.inset,
              keysTop,
              side,
              3 * (TeleautoTokens.keyHeight + TeleautoTokens.rowGap),
              keyGroup(right),
            ),
          ],
          place(
            centerX,
            videoTop,
            centerWidth,
            centerWidth / (840 / 473),
            _TeleautoPresentation(
              state: state,
              video: video,
              onDisconnect: enabled ? onDisconnect : null,
              onConnect: enabled ? onConnect : null,
              onFullscreen: enabled ? onFullscreen : null,
            ),
          ),
          place(
            centerX,
            videoTop + centerWidth / (840 / 473) + TeleautoTokens.inset,
            centerWidth,
            math.max(
              TeleautoTokens.iconLarge,
              footerBottom -
                  videoTop -
                  centerWidth / (840 / 473) -
                  TeleautoTokens.inset,
            ),
            DecoratedBox(
              decoration: BoxDecoration(
                color: TeleautoTokens.surface,
                border: Border.all(color: TeleautoTokens.border),
                borderRadius: BorderRadius.circular(TeleautoTokens.panelRadius),
              ),
              child: const Center(
                child: TeleautoIcon(
                  'alius-logo',
                  size: TeleautoTokens.iconLarge,
                  color: TeleautoTokens.secondary,
                ),
              ),
            ),
          ),
          place(
            TeleautoTokens.inset,
            footerTop,
            side,
            TeleautoTokens.footerHeight,
            TeleautoSlideLock(
              locked: state.locked,
              touchUnlock: state.touchUnlock,
              progress: state.unlockProgress,
              onLock: onLock,
              enabled: state.enabled && state.canLock,
            ),
          ),
          place(
            size.width - TeleautoTokens.inset - side,
            footerBottom - TeleautoTokens.dialHeight,
            side,
            TeleautoTokens.dialHeight,
            TeleautoDial(
              value: state.axis(6),
              centered: true,
              enabled: enabled,
              onChanged: onDial,
            ),
          ),
        ],
      );
      return ClipRect(
        child: OverflowBox(
          alignment: Alignment.topLeft,
          maxWidth: size.width,
          maxHeight: size.height,
          minWidth: size.width,
          minHeight: size.height,
          child: Transform.scale(
            scale: geometry.scale,
            alignment: Alignment.topLeft,
            child: SizedBox.fromSize(size: size, child: canvas),
          ),
        ),
      );
    },
  );
}

class TeleautoConsoleGeometry {
  TeleautoConsoleGeometry(Size viewport) {
    const sideFraction = 168 / (2 * 168 + 368);
    const fixed = TeleautoTokens.topHeight + 11 + 21 + 14 + 11 + 16 + 196 + 4;
    final referenceHeight = math.min(
      viewport.height,
      viewport.width / TeleautoTokens.minAspect,
    );
    scale = math
        .min(
          viewport.width / TeleautoTokens.baseWidth,
          (referenceHeight - viewport.width * sideFraction) /
              (fixed - 64 * sideFraction),
        )
        .clamp(.1, 4.0);
    canvas = viewport / scale;
    side = (canvas.width - 64) * sideFraction;
    extra =
        math.max(
          0.0,
          viewport.height - viewport.width / TeleautoTokens.minAspect,
        ) /
        scale /
        4;
  }
  late final double scale, side, extra;
  late final Size canvas;
}

class _TeleautoPresentation extends StatelessWidget {
  const _TeleautoPresentation({
    required this.state,
    this.video,
    this.onFullscreen,
    this.onDisconnect,
    this.onConnect,
  });
  final TeleautoConsoleState state;
  final Widget? video;
  final VoidCallback? onFullscreen, onDisconnect, onConnect;
  @override
  Widget build(BuildContext context) {
    final isVideo = state.presentation == 'video';
    return DecoratedBox(
      decoration: BoxDecoration(
        color: TeleautoTokens.black,
        border: Border.all(color: TeleautoTokens.border),
        borderRadius: BorderRadius.circular(TeleautoTokens.radius),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(TeleautoTokens.radius),
        child: Stack(
          children: [
            if (isVideo && video != null) Positioned.fill(child: video!),
            if (state.presentation == 'xbox' ||
                state.presentation == 'joystick')
              Positioned.fill(
                child: TeleautoHidConnectionPanel(
                  state: state,
                  onConnect: onConnect,
                  onDisconnect: onDisconnect,
                ),
              )
            else if (!isVideo || video == null)
              Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (!isVideo)
                      TeleautoIcon(
                        state.presentation == 'cyberbrick'
                            ? 'cyberbrick'
                            : state.presentation == 'joystick'
                            ? 'joystick'
                            : 'xbox',
                        size: TeleautoTokens.iconLarge,
                      ),
                    const SizedBox(height: TeleautoTokens.inset),
                    Text(
                      isVideo
                          ? '请插入图传接收端'
                          : switch (state.presentation) {
                              'cyberbrick' => 'CyberBrick',
                              'joystick' => 'Joystick 摇杆',
                              _ => 'Xbox 手柄',
                            },
                      style: TeleautoTokens.textStyle(bold: true),
                    ),
                    if (!isVideo)
                      Text(
                        state.connectionLabel,
                        style: TeleautoTokens.textStyle(
                          color: TeleautoTokens.secondary,
                        ),
                      ),
                    if (!isVideo && onDisconnect != null)
                      Padding(
                        padding: const EdgeInsets.only(
                          top: TeleautoTokens.inset,
                        ),
                        child: SizedBox(
                          width: TeleautoTokens.sideWidth,
                          height: TeleautoTokens.footerHeight,
                          child: TeleautoButton(
                            label: '断开连接',
                            usePanelSurface: true,
                            fontSize: TeleautoTokens.bodyFont,
                            onPressed: onDisconnect,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            if (isVideo)
              Positioned(
                left: TeleautoTokens.gap,
                top: TeleautoTokens.gap,
                child: Text(
                  video == null ? 'VIDEO · STANDBY' : 'VIDEO · LIVE',
                  style: TeleautoTokens.textStyle(
                    size: TeleautoTokens.metaFont,
                    bold: true,
                  ),
                ),
              ),
            if (isVideo)
              Positioned(
                right: TeleautoTokens.gap,
                top: TeleautoTokens.gap,
                width: TeleautoTokens.icon,
                height: TeleautoTokens.icon,
                child: TeleautoButton(
                  label: '放大图传',
                  icon: 'fullscreen',
                  onPressed: onFullscreen,
                  enabled: onFullscreen != null,
                ),
              ),
          ],
        ),
      ),
    );
  }
}
