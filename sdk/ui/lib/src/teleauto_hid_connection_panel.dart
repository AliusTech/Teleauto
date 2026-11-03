import 'package:flutter/material.dart';
import 'teleauto_controls.dart';
import 'teleauto_models.dart';
import 'teleauto_tokens.dart';

/// PC pairing controls shared by the gamepad and joystick presentations.
class TeleautoHidConnectionPanel extends StatelessWidget {
  const TeleautoHidConnectionPanel({
    super.key,
    required this.state,
    this.onConnect,
    this.onDisconnect,
  });

  final TeleautoConsoleState state;
  final VoidCallback? onConnect, onDisconnect;

  @override
  Widget build(BuildContext context) {
    final connected = state.hidState == 'CONNECTED';
    final waiting = state.hidState == 'ADVERTISING';
    final pairing = state.hidState == 'PAIRING';
    final active = connected || waiting || pairing;
    final title = switch (state.hidState) {
      'CONNECTED' => 'PC 已连接',
      'ADVERTISING' => '等待 PC 配对 / 连接',
      'PAIRING' => '正在建立手柄连接',
      'ERROR' => '连接失败，请重试',
      _ => 'PC 未连接',
    };
    final name = state.hidDeviceName ?? state.deviceName;
    final hint = connected
        ? '${state.hidHostName ?? 'PC'} · 已就绪'
        : pairing
        ? '请在 PC 和遥控器上确认蓝牙配对，等待手柄连接完成。'
        : waiting
        ? '在 PC 蓝牙设置中添加“$name”。已配对的 PC 可重新连接；遥控器需保持此页面在前台。'
        : '点击“配对 / 连接 PC”，再到 PC 蓝牙设置中选择“$name”。';
    final color = connected ? TeleautoTokens.normal : TeleautoTokens.secondary;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(TeleautoTokens.inset),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Semantics(
              label: connected ? '手柄与 PC 已连接' : '手柄与 PC 未连接',
              child: ExcludeSemantics(
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    TeleautoIcon(
                      state.presentation == 'joystick' ? 'joystick' : 'xbox',
                      size: TeleautoTokens.iconLarge,
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: TeleautoTokens.inset,
                      ),
                      child: Icon(
                        connected ? Icons.link : Icons.link_off,
                        color: color,
                        size: TeleautoTokens.keyHeight,
                      ),
                    ),
                    Icon(
                      Icons.desktop_windows_outlined,
                      color: color,
                      size: TeleautoTokens.iconLarge,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: TeleautoTokens.gap),
            Text(
              state.presentation == 'joystick'
                  ? 'Joystick 摇杆 → PC'
                  : 'Xbox 手柄 → PC',
              style: TeleautoTokens.textStyle(bold: true),
            ),
            const SizedBox(height: TeleautoTokens.small),
            Semantics(
              liveRegion: true,
              child: Text(
                title,
                style: TeleautoTokens.textStyle(color: color, bold: true),
              ),
            ),
            const SizedBox(height: TeleautoTokens.gap),
            Text(
              hint,
              textAlign: TextAlign.center,
              style: TeleautoTokens.textStyle(
                color: TeleautoTokens.secondary,
                size: TeleautoTokens.bodyFont,
              ),
            ),
            const SizedBox(height: TeleautoTokens.inset),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: TeleautoTokens.gap,
              runSpacing: TeleautoTokens.gap,
              children: [
                if (!connected && !pairing)
                  SizedBox(
                    width: TeleautoTokens.sideWidth,
                    height: TeleautoTokens.footerHeight,
                    child: TeleautoButton(
                      label: waiting ? '重新等待连接' : '配对 / 连接 PC',
                      usePanelSurface: true,
                      fontSize: TeleautoTokens.bodyFont,
                      onPressed: state.enabled ? onConnect : null,
                    ),
                  ),
                if (active)
                  SizedBox(
                    width: TeleautoTokens.sideWidth,
                    height: TeleautoTokens.footerHeight,
                    child: TeleautoButton(
                      label: connected ? '断开 PC 连接' : '取消连接',
                      usePanelSurface: true,
                      fontSize: TeleautoTokens.bodyFont,
                      onPressed: state.enabled ? onDisconnect : null,
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
