import 'package:flutter/material.dart';
import 'teleauto_tokens.dart';

/// Keeps the console (including its native video texture) mounted during navigation.
class TeleautoPanelTransition extends StatefulWidget {
  const TeleautoPanelTransition({
    super.key,
    required this.showSettings,
    required this.console,
    required this.settings,
  });
  final bool showSettings;
  final Widget console, settings;
  @override
  State<TeleautoPanelTransition> createState() =>
      _TeleautoPanelTransitionState();
}

class _TeleautoPanelTransitionState extends State<TeleautoPanelTransition>
    with SingleTickerProviderStateMixin {
  late final AnimationController controller = AnimationController(
    vsync: this,
    duration: TeleautoTokens.panelTransitionDuration,
    value: widget.showSettings ? 1 : 0,
  );
  @override
  void didUpdateWidget(covariant TeleautoPanelTransition oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.showSettings != widget.showSettings) {
      controller.animateTo(
        widget.showSettings ? 1 : 0,
        curve: TeleautoTokens.panelTransitionCurve,
      );
    }
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) {
      final progress = controller.value;
      final reduced = MediaQuery.disableAnimationsOf(context);
      return ClipRect(
        child: Stack(
          fit: StackFit.expand,
          children: [
            IgnorePointer(
              ignoring: widget.showSettings || controller.isAnimating,
              child: ExcludeSemantics(
                excluding: widget.showSettings,
                child: TeleautoConsoleMotion(
                  progress: progress,
                  reduced: reduced,
                  child: widget.console,
                ),
              ),
            ),
            if (progress > 0 || widget.showSettings)
              IgnorePointer(
                ignoring: !widget.showSettings || controller.isAnimating,
                child: Transform.translate(
                  offset: Offset(
                    reduced
                        ? 0
                        : TeleautoTokens.panelTransitionOffset * (1 - progress),
                    0,
                  ),
                  child: Opacity(opacity: progress, child: widget.settings),
                ),
              ),
          ],
        ),
      );
    },
  );
}

class TeleautoConsoleMotion extends InheritedWidget {
  const TeleautoConsoleMotion({
    super.key,
    required this.progress,
    required this.reduced,
    required super.child,
  });
  final double progress;
  final bool reduced;
  static TeleautoConsoleMotion? of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<TeleautoConsoleMotion>();
  @override
  bool updateShouldNotify(TeleautoConsoleMotion oldWidget) =>
      progress != oldWidget.progress || reduced != oldWidget.reduced;
}
