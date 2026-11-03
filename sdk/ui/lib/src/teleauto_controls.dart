import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'teleauto_tokens.dart';
import 'teleauto_models.dart';

class TeleautoIcon extends StatelessWidget {
  const TeleautoIcon(
    this.name, {
    super.key,
    this.size = TeleautoTokens.icon,
    this.color = TeleautoTokens.text,
  });
  final String name;
  final double size;
  final Color color;
  @override
  Widget build(BuildContext context) {
    if (name == 'cyberbrick') {
      return Image.asset(
        '${TeleautoTokens.assets}/png/cyberbrick.png',
        package: TeleautoTokens.package,
        width: size,
        height: size,
        color: color,
      );
    }
    final path =
        name.startsWith('device-') || name == 'auth' || name == 'alius-logo'
        ? 'src/main/assets/icons/$name.svg'
        : '${TeleautoTokens.assets}/svg/$name.svg';
    return SvgPicture.asset(
      path,
      package: TeleautoTokens.package,
      width: size,
      height: size,
      colorFilter: name == 'stick_pointer'
          ? null
          : ColorFilter.mode(color, BlendMode.srcIn),
    );
  }
}

class TeleautoMaterial extends StatelessWidget {
  const TeleautoMaterial(
    this.name, {
    super.key,
    required this.child,
    this.radius = TeleautoTokens.radius,
  });
  final String name;
  final Widget child;
  final double radius;
  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(radius),
    child: Stack(
      fit: StackFit.passthrough,
      children: [
        Positioned.fill(
          child: Image.asset(
            '${TeleautoTokens.assets}/png/$name.png',
            package: TeleautoTokens.package,
            fit: BoxFit.fill,
            excludeFromSemantics: true,
          ),
        ),
        child,
      ],
    ),
  );
}

class TeleautoButton extends StatefulWidget {
  const TeleautoButton({
    super.key,
    required this.label,
    this.icon,
    this.onPressed,
    this.onHeldChanged,
    this.active = false,
    this.round = false,
    this.usePanelSurface = false,
    this.tint,
    this.enabled = true,
    this.fontSize = TeleautoTokens.keyFont,
  });
  final double fontSize;
  final String label;
  final String? icon;
  final VoidCallback? onPressed;
  final ValueChanged<bool>? onHeldChanged;
  final bool active, round, enabled, usePanelSurface;
  final Color? tint;
  @override
  State<TeleautoButton> createState() => _TeleautoButtonState();
}

class _TeleautoButtonState extends State<TeleautoButton> {
  bool down = false, focused = false;
  void release() {
    if (down) {
      widget.onHeldChanged?.call(false);
      setState(() => down = false);
    }
  }

  @override
  void didUpdateWidget(covariant TeleautoButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!widget.enabled && down) {
      oldWidget.onHeldChanged?.call(false);
      down = false;
    }
  }

  @override
  void dispose() {
    if (down) widget.onHeldChanged?.call(false);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pressed = down || widget.active || focused;
    final shape = widget.round ? 'round' : 'key';
    final label = widget.icon != null
        ? TeleautoIcon(
            widget.icon!,
            color: pressed ? TeleautoTokens.text : TeleautoTokens.secondary,
          )
        : Text(
            widget.label,
            style: TeleautoTokens.textStyle(
              size: widget.fontSize,
              bold: true,
              color: pressed ? TeleautoTokens.text : TeleautoTokens.secondary,
            ),
          );
    final showHoldIndicator =
        widget.onHeldChanged != null && widget.icon != 'pause';
    Widget content = !showHoldIndicator
        ? Center(child: label)
        : Center(
            child: Padding(
              // Account for the material's lower shadow so the group sits on the face.
              padding: const EdgeInsets.only(bottom: TeleautoTokens.small),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  label,
                  if (showHoldIndicator) ...[
                    Transform.translate(
                      offset: const Offset(
                        0,
                        -TeleautoTokens.holdIndicatorLift,
                      ),
                      child: Container(
                        width: TeleautoTokens.holdIndicatorWidth,
                        height: TeleautoTokens.holdIndicatorThickness,
                        color: pressed
                            ? TeleautoTokens.text
                            : TeleautoTokens.faint,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          );
    if (widget.tint != null) {
      content = Stack(
        children: [
          Positioned.fill(
            child: Padding(
              padding: EdgeInsets.all(
                widget.round ? TeleautoTokens.roundFaceInset : 0,
              ),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: widget.tint!.withValues(alpha: .58),
                  borderRadius: BorderRadius.circular(
                    widget.round
                        ? TeleautoTokens.roundKey
                        : TeleautoTokens.radius,
                  ),
                ),
              ),
            ),
          ),
          Positioned.fill(child: content),
        ],
      );
    }
    return Semantics(
      button: true,
      label: widget.label,
      enabled: widget.enabled,
      child: FocusableActionDetector(
        enabled: widget.enabled,
        onFocusChange: (value) => setState(() => focused = value),
        actions: <Type, Action<Intent>>{
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              widget.onHeldChanged?.call(true);
              widget.onHeldChanged?.call(false);
              widget.onPressed?.call();
              return null;
            },
          ),
        },
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: widget.enabled
              ? (_) {
                  setState(() => down = true);
                  widget.onHeldChanged?.call(true);
                }
              : null,
          onTapUp: widget.enabled
              ? (_) {
                  release();
                  widget.onPressed?.call();
                }
              : null,
          onTapCancel: release,
          child: AnimatedOpacity(
            duration: TeleautoTokens.pressDuration,
            opacity: widget.enabled ? 1 : .55,
            child: widget.usePanelSurface
                ? TeleautoPanelSurface(active: pressed, child: content)
                : TeleautoMaterial(
                    '${shape}_${pressed ? 'pressed' : 'raised'}',
                    radius: widget.round
                        ? TeleautoTokens.roundKey
                        : TeleautoTokens.radius,
                    child: content,
                  ),
          ),
        ),
      ),
    );
  }
}

class TeleautoDial extends StatelessWidget {
  const TeleautoDial({
    super.key,
    required this.value,
    this.onChanged,
    this.enabled = true,
    this.centerOnRelease = false,
    this.centered = false,
  });
  final double value;
  final ValueChanged<double>? onChanged;
  final bool enabled, centerOnRelease, centered;
  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, box) {
      final position =
          ((value.clamp(-1.0, 1.0) + 1) / 2) *
          (box.maxWidth - TeleautoTokens.sliderThumb);
      void update(Offset p) {
        onChanged?.call(
          ((p.dx - TeleautoTokens.sliderThumb / 2) /
                      (box.maxWidth - TeleautoTokens.sliderThumb) *
                      2 -
                  1)
              .clamp(-1.0, 1.0),
        );
      }

      return Semantics(
        label: centered ? '回中拨轮' : '连续滚轮',
        value: '${((value.clamp(-1.0, 1.0) + 1) * 50).round()}%',
        slider: onChanged != null,
        child: GestureDetector(
          onHorizontalDragStart: enabled && onChanged != null
              ? (d) => update(d.localPosition)
              : null,
          onHorizontalDragUpdate: enabled && onChanged != null
              ? (d) => update(d.localPosition)
              : null,
          onHorizontalDragEnd: centerOnRelease && enabled
              ? (_) => onChanged?.call(0)
              : null,
          onHorizontalDragCancel: centerOnRelease && enabled
              ? () => onChanged?.call(0)
              : null,
          child: TeleautoMaterial(
            'continuous_track',
            radius: TeleautoTokens.radius,
            child: Stack(
              children: [
                Positioned.fill(
                  child: CustomPaint(
                    painter: _TeleautoDialProgress(value, centered),
                  ),
                ),
                Positioned(
                  left: position,
                  top: 0,
                  bottom: 0,
                  width: TeleautoTokens.sliderThumb,
                  child: const TeleautoDialThumb(),
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
}

class TeleautoDialThumb extends StatelessWidget {
  const TeleautoDialThumb({super.key, this.vertical = false});
  final bool vertical;
  @override
  Widget build(BuildContext context) => RotatedBox(
    quarterTurns: vertical ? 1 : 0,
    child: Image.asset(
      '${TeleautoTokens.assets}/png/dial_thumb.png',
      package: TeleautoTokens.package,
      fit: BoxFit.contain,
    ),
  );
}

// Fill clips to the inside of the recessed rim; ticks and thumb stay above it.
class _TeleautoDialProgress extends CustomPainter {
  const _TeleautoDialProgress(this.value, this.centered);
  final double value;
  final bool centered;
  @override
  void paint(Canvas canvas, Size size) {
    final inset = TeleautoTokens.sliderThumb / 2;
    final travel = math.max(0.0, size.width - inset * 2);
    final normalized = value.clamp(-1.0, 1.0);
    final end = inset + (normalized + 1) / 2 * travel;
    final inner = Rect.fromLTWH(
      TeleautoTokens.dialFillInset,
      TeleautoTokens.dialFillInset,
      math.max(0, size.width - 2 * TeleautoTokens.dialFillInset),
      math.max(0, size.height - 2 * TeleautoTokens.dialFillInset),
    );
    final start = centered ? size.width / 2 : inner.left;
    final fillEnd = normalized == 1
        ? inner.right
        : normalized == -1
        ? inner.left
        : end;
    canvas.save();
    canvas.clipRRect(
      RRect.fromRectAndRadius(inner, Radius.circular(inner.height / 2)),
    );
    canvas.drawRect(
      Rect.fromLTRB(
        math.min(start, fillEnd),
        inner.top,
        math.max(start, fillEnd),
        inner.bottom,
      ),
      Paint()..color = TeleautoTokens.dialProgress,
    );
    canvas.restore();
    final ticks = Paint()
      ..color = TeleautoTokens.dialTick
      ..strokeWidth = TeleautoTokens.hairline;
    for (var i = 0; i <= TeleautoTokens.dialTickIntervals; i++) {
      final x = inset + travel * i / TeleautoTokens.dialTickIntervals;
      final ratio = i == TeleautoTokens.dialTickIntervals / 2
          ? TeleautoTokens.dialCenterTickRatio
          : TeleautoTokens.dialTickRatio;
      final half = size.height * ratio / 2;
      canvas.drawLine(
        Offset(x, size.height / 2 - half),
        Offset(x, size.height / 2 + half),
        ticks,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _TeleautoDialProgress oldDelegate) =>
      oldDelegate.value != value || oldDelegate.centered != centered;
}

/// SDK axes follow screen coordinates: negative Y is up, positive Y is down.
class TeleautoJoystick extends StatelessWidget {
  const TeleautoJoystick({
    super.key,
    required this.x,
    required this.y,
    this.right = false,
    this.onChanged,
  });
  final double x, y;
  final ValueChanged<Offset>? onChanged;
  final bool right;
  @override
  Widget build(BuildContext context) => Semantics(
    label: right ? '右物理摇杆' : '左物理摇杆',
    child: IgnorePointer(
      ignoring: onChanged == null,
      child: LayoutBuilder(
        builder: (context, box) {
          final inner = box.maxWidth * .70;
          final unit = box.maxWidth / TeleautoTokens.stickDesignSide;
          final trackEdge = TeleautoTokens.stickTrackEdge * unit;
          final trackThickness = TeleautoTokens.stickTrackThickness * unit;
          final thumbLength = trackThickness * TeleautoTokens.thumbAspect;
          final horizontalStart = TeleautoTokens.stickHorizontalInset * unit;
          final horizontalEnd = box.maxWidth - horizontalStart;
          final verticalStart = TeleautoTokens.stickVerticalStart * unit;
          final verticalEnd =
              box.maxHeight - TeleautoTokens.stickVerticalEndInset * unit;
          void update(Offset p) => onChanged?.call(
            Offset(
              ((p.dx - box.maxWidth / 2) / (inner / 2)).clamp(-1.0, 1.0),
              ((p.dy - box.maxHeight / 2) / (inner / 2)).clamp(-1.0, 1.0),
            ),
          );
          return GestureDetector(
            behavior: HitTestBehavior.opaque,
            onPanStart: (d) => update(d.localPosition),
            onPanUpdate: (d) => update(d.localPosition),
            onPanEnd: (_) => onChanged?.call(Offset.zero),
            onPanCancel: () => onChanged?.call(Offset.zero),
            child: Stack(
              children: [
                Positioned.fill(
                  child: Image.asset(
                    '${TeleautoTokens.assets}/png/joystick_well_${right ? 'right' : 'left'}.png',
                    package: TeleautoTokens.package,
                    fit: BoxFit.contain,
                  ),
                ),
                Positioned(
                  left:
                      horizontalStart +
                      (x.clamp(-1.0, 1.0) + 1) /
                          2 *
                          (horizontalEnd - horizontalStart - thumbLength),
                  top: box.maxHeight - trackEdge - trackThickness,
                  width: thumbLength,
                  height: trackThickness,
                  child: const TeleautoDialThumb(),
                ),
                Positioned(
                  left: right ? null : trackEdge,
                  right: right ? trackEdge : null,
                  top:
                      verticalStart +
                      (y.clamp(-1.0, 1.0) + 1) /
                          2 *
                          (verticalEnd - verticalStart - thumbLength),
                  width: trackThickness,
                  height: thumbLength,
                  child: const TeleautoDialThumb(vertical: true),
                ),
                Positioned.fill(
                  child: Padding(
                    padding: EdgeInsets.all(box.maxWidth * .15),
                    child: CustomPaint(painter: _TeleautoStickGrid(x, y)),
                  ),
                ),
                Positioned(
                  left:
                      box.maxWidth / 2 +
                      x.clamp(-1.0, 1.0) * inner / 2 -
                      TeleautoTokens.gap,
                  top:
                      box.maxHeight / 2 +
                      y.clamp(-1.0, 1.0) * inner / 2 -
                      TeleautoTokens.gap,
                  width: TeleautoTokens.inset,
                  height: TeleautoTokens.inset,
                  child: const TeleautoIcon(
                    'stick_pointer',
                    size: TeleautoTokens.inset,
                  ),
                ),
              ],
            ),
          );
        },
      ),
    ),
  );
}

class _TeleautoStickGrid extends CustomPainter {
  _TeleautoStickGrid(this.x, this.y);
  final double x, y;
  @override
  void paint(Canvas canvas, Size size) {
    final faint = Paint()
      ..color = TeleautoTokens.line
      ..strokeWidth = .5;
    final axis = Paint()
      ..color = TeleautoTokens.secondary
      ..strokeWidth = .75;
    for (var i = 0; i <= 8; i++) {
      final p = i / 8;
      canvas.drawLine(
        Offset(size.width * p, 0),
        Offset(size.width * p, size.height),
        i == 4 ? axis : faint,
      );
      canvas.drawLine(
        Offset(0, size.height * p),
        Offset(size.width, size.height * p),
        i == 4 ? axis : faint,
      );
    }
  }

  @override
  bool shouldRepaint(_TeleautoStickGrid oldDelegate) => false;
}

class TeleautoSlideLock extends StatefulWidget {
  const TeleautoSlideLock({
    super.key,
    required this.locked,
    required this.progress,
    required this.onLock,
    this.touchUnlock = false,
    this.enabled = true,
  });
  final bool locked, enabled, touchUnlock;
  final double progress;
  final VoidCallback onLock;
  @override
  State<TeleautoSlideLock> createState() => _TeleautoSlideLockState();
}

class _TeleautoSlideLockState extends State<TeleautoSlideLock> {
  double drag = 0;
  @override
  void didUpdateWidget(covariant TeleautoSlideLock oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.locked != oldWidget.locked) drag = 0;
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, box) {
      final travel = math.max(1.0, box.maxWidth - TeleautoTokens.footerHeight);
      return Semantics(
        label: widget.locked
            ? (widget.touchUnlock
                  ? '同时按下屏幕 C1 和 C2 解锁'
                  : '已锁定，物理 C1 和 C2 同时长按解锁')
            : '滑动锁定',
        child: ClipRRect(
          borderRadius: BorderRadius.circular(TeleautoTokens.radius),
          child: ColoredBox(
            color: TeleautoTokens.surface,
            child: GestureDetector(
              onHorizontalDragUpdate: !widget.locked && widget.enabled
                  ? (d) => setState(
                      () => drag = (drag + d.delta.dx).clamp(0.0, travel),
                    )
                  : null,
              onHorizontalDragEnd: !widget.locked && widget.enabled
                  ? (_) {
                      if (drag >= travel * .95) widget.onLock();
                      setState(() => drag = 0);
                    }
                  : null,
              onHorizontalDragCancel: () => setState(() => drag = 0),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  border: Border.all(color: TeleautoTokens.border),
                  borderRadius: BorderRadius.circular(TeleautoTokens.radius),
                ),
                child: Stack(
                  children: [
                    if (widget.locked)
                      Positioned(
                        left: 0,
                        top: 0,
                        bottom: 0,
                        width: box.maxWidth * widget.progress.clamp(0, 1),
                        child: const ColoredBox(color: TeleautoTokens.raised),
                      ),
                    Center(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          left: TeleautoTokens.footerHeight,
                        ),
                        child: Text(
                          widget.locked
                              ? (widget.touchUnlock
                                    ? 'C1 + C2 同时按下解锁'
                                    : 'C1 + C2 长按解锁')
                              : '滑动锁定  ›',
                          style: TeleautoTokens.textStyle(),
                        ),
                      ),
                    ),
                    Positioned(
                      left: widget.locked ? 0 : drag,
                      top: 0,
                      bottom: 0,
                      width: TeleautoTokens.footerHeight,
                      child: TeleautoMaterial(
                        'lock_thumb_small',
                        child: Center(
                          child: TeleautoIcon(
                            widget.locked ? 'lock' : 'unlock',
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    },
  );
}

/// Paint reaches screen edges; controls keep device-specific safe insets.
class TeleautoViewport extends StatelessWidget {
  const TeleautoViewport({super.key, required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => Stack(
    fit: StackFit.expand,
    children: [
      Image.asset(
        '${TeleautoTokens.assets}/png/background.png',
        package: TeleautoTokens.package,
        fit: BoxFit.cover,
        excludeFromSemantics: true,
      ),
      SafeArea(child: child),
    ],
  );
}

class TeleautoIconAction extends StatelessWidget {
  const TeleautoIconAction({
    super.key,
    required this.label,
    required this.icon,
    this.onPressed,
    this.enabled = true,
  });
  final String label, icon;
  final VoidCallback? onPressed;
  final bool enabled;
  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: label,
    style: IconButton.styleFrom(
      backgroundColor: Colors.transparent,
      shadowColor: Colors.transparent,
      elevation: 0,
    ),
    onPressed: enabled ? onPressed : null,
    padding: EdgeInsets.zero,
    icon: TeleautoIcon(
      icon,
      color: enabled ? TeleautoTokens.text : TeleautoTokens.faint,
    ),
  );
}

class TeleautoBatteryIndicator extends StatelessWidget {
  const TeleautoBatteryIndicator({super.key, this.level});
  final int? level;
  @override
  Widget build(BuildContext context) => Semantics(
    label: level == null ? '电量未知' : '电量 $level%',
    child: SizedBox(
      width: TeleautoTokens.batteryWidth,
      height: TeleautoTokens.batteryHeight,
      child: Stack(
        children: [
          Positioned(
            left: 0,
            top: 0,
            bottom: 0,
            right: TeleautoTokens.batteryTerminal,
            child: DecoratedBox(
              decoration: BoxDecoration(
                border: Border.all(
                  color: TeleautoTokens.secondary,
                  width: TeleautoTokens.hairline,
                ),
                borderRadius: BorderRadius.circular(
                  TeleautoTokens.batteryRadius,
                ),
              ),
              child: Stack(
                children: [
                  Positioned.fill(
                    child: Padding(
                      padding: const EdgeInsets.all(
                        TeleautoTokens.batteryFillInset,
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(
                          TeleautoTokens.batteryFillRadius,
                        ),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: FractionallySizedBox(
                            widthFactor: level == null
                                ? 0
                                : level!.clamp(0, 100) / 100,
                            heightFactor: 1,
                            child: const ColoredBox(
                              color: TeleautoTokens.batteryFill,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  Center(
                    child: Text(
                      level == null ? '—' : '${level!.clamp(0, 100)}%',
                      style: TeleautoTokens.textStyle(
                        size: TeleautoTokens.metaFont,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          Positioned(
            right: 0,
            top: TeleautoTokens.batteryHeight / 3,
            bottom: TeleautoTokens.batteryHeight / 3,
            width: TeleautoTokens.batteryTerminal,
            child: const ColoredBox(color: TeleautoTokens.secondary),
          ),
        ],
      ),
    ),
  );
}

/// Selection stays local to presentation; hardware identity comes from the SDK.
class TeleautoBatterySelector extends StatefulWidget {
  const TeleautoBatterySelector({
    super.key,
    required this.entries,
    this.enabled = true,
  });
  final List<TeleautoBatteryEntry> entries;
  final bool enabled;
  @override
  State<TeleautoBatterySelector> createState() =>
      _TeleautoBatterySelectorState();
}

class _TeleautoBatterySelectorState extends State<TeleautoBatterySelector> {
  String? selectedId;
  final controller = MenuController();
  List<TeleautoBatteryEntry> get available =>
      widget.entries.where((e) => e.present).toList();
  @override
  void didUpdateWidget(covariant TeleautoBatterySelector oldWidget) {
    super.didUpdateWidget(oldWidget);
    final oldIds = oldWidget.entries
        .where((e) => e.present)
        .map((e) => e.id)
        .join('|');
    if (!widget.enabled || oldIds != available.map((e) => e.id).join('|')) {
      if (controller.isOpen) controller.close();
    }
    if (!available.any((e) => e.id == selectedId)) selectedId = null;
  }

  @override
  Widget build(BuildContext context) {
    final entries = available;
    final selected =
        entries.where((e) => e.id == selectedId).firstOrNull ??
        entries.firstOrNull;
    return MenuAnchor(
      controller: controller,
      style: MenuStyle(
        backgroundColor: const WidgetStatePropertyAll(TeleautoTokens.surface),
        surfaceTintColor: const WidgetStatePropertyAll(Colors.transparent),
        shape: WidgetStatePropertyAll(
          RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(TeleautoTokens.radius),
            side: const BorderSide(color: TeleautoTokens.border),
          ),
        ),
      ),
      menuChildren: [
        for (final entry in entries)
          MenuItemButton(
            onPressed: () => setState(() => selectedId = entry.id),
            leadingIcon: Icon(
              entry.id == selected?.id ? Icons.check : Icons.battery_std,
              size: TeleautoTokens.icon,
              color: TeleautoTokens.secondary,
            ),
            trailingIcon: TeleautoBatteryIndicator(level: entry.level),
            child: Text(entry.name, style: TeleautoTokens.textStyle()),
          ),
      ],
      builder: (context, controller, child) => Semantics(
        button: true,
        label: '电量：${selected?.name ?? '未检测到电池'}',
        child: Tooltip(
          message: selected?.name ?? '未检测到电池',
          child: InkWell(
            onTap: widget.enabled && entries.isNotEmpty
                ? () =>
                      controller.isOpen ? controller.close() : controller.open()
                : null,
            borderRadius: BorderRadius.circular(TeleautoTokens.radius),
            child: SizedBox(
              height: TeleautoTokens.footerHeight,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [TeleautoBatteryIndicator(level: selected?.level)],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Resizable matte surface: corners and shadows never stretch with button width.
class TeleautoPanelSurface extends StatelessWidget {
  const TeleautoPanelSurface({
    super.key,
    required this.child,
    this.active = false,
  });
  final Widget child;
  final bool active;
  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(TeleautoTokens.radius),
      gradient: LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: active
            ? [TeleautoTokens.pressed, TeleautoTokens.surface]
            : [TeleautoTokens.panelButtonTop, TeleautoTokens.panelButtonBottom],
      ),
      border: Border.all(
        color: active ? TeleautoTokens.secondary : TeleautoTokens.border,
        width: TeleautoTokens.hairline,
      ),
      boxShadow: [
        if (!active)
          const BoxShadow(
            color: TeleautoTokens.panelShadow,
            blurRadius: TeleautoTokens.panelShadowBlur,
            offset: Offset(0, TeleautoTokens.panelShadowOffset),
          ),
      ],
    ),
    child: child,
  );
}

class TeleautoNavigationItem extends StatelessWidget {
  const TeleautoNavigationItem({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
    this.active = false,
    this.expanded,
  });
  final String label;
  final Widget icon;
  final VoidCallback onPressed;
  final bool active;
  final bool? expanded;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onPressed,
    borderRadius: BorderRadius.circular(TeleautoTokens.radius),
    child: Container(
      height: TeleautoTokens.centerNavigationHeight,
      padding: const EdgeInsets.symmetric(horizontal: TeleautoTokens.gap),
      decoration: BoxDecoration(
        color: active ? TeleautoTokens.surface : Colors.transparent,
        borderRadius: BorderRadius.circular(TeleautoTokens.radius),
        border: active
            ? Border.all(
                color: TeleautoTokens.secondary,
                width: TeleautoTokens.hairline,
              )
            : null,
      ),
      child: Row(
        children: [
          icon,
          const SizedBox(width: TeleautoTokens.gap),
          Expanded(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TeleautoTokens.textStyle(bold: active),
            ),
          ),
          if (expanded != null)
            Icon(
              expanded! ? Icons.expand_more : Icons.chevron_right,
              size: TeleautoTokens.inset,
              color: TeleautoTokens.secondary,
            ),
        ],
      ),
    ),
  );
}

/// Shared top-bar surface, spacing and height for console and control center.
class TeleautoTopBar extends StatelessWidget {
  const TeleautoTopBar({super.key, required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => SizedBox(
    height: TeleautoTokens.topHeight,
    child: TeleautoMaterial(
      'top_bar',
      radius: 0,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: TeleautoTokens.inset),
        child: child,
      ),
    ),
  );
}
