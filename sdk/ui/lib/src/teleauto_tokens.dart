import 'package:flutter/material.dart';

/// Ported from the accepted native UI tokens; all consumers share these values.
abstract final class TeleautoTokens {
  static const panelTransitionDuration = Duration(milliseconds: 260);
  static const panelTransitionCurve = Cubic(0.23, 1, 0.32, 1);
  static const panelTransitionOffset = 24.0;

  static const package = 'teleauto_ui';
  static const assets = 'src/main/assets/materials/crater-v2';
  static const background = Color(0xFF131313);
  static const surface = Color(0xFF232323);
  static const raised = Color(0xFF3F3F3F);
  static const pressed = Color(0xFF242424);
  static const text = Color(0xFFFFFFFF);
  static const secondary = Color(0xFFD0D0D0);
  static const faint = Color(0xFF969696);
  static const border = Color(0xFF515151);
  static const line = Color(0x24FFFFFF);
  static const black = Color(0xFF000000);
  static const auth = Color(0xFFE45B5B);
  static const pause = Color(0xFFF27642);
  static const sport = Color(0xFF70A8D8);
  static const cine = Color(0xFFD6B56A);
  static const normal = Color(0xFF70C998);
  static const small = 4.0, gap = 8.0, medium = 12.0, inset = 16.0;
  static const radius = 6.0, cardRadius = 12.0, panelRadius = 20.0;
  static const keyWidth = 73.44, keyHeight = 36.0, roundKey = 46.0;
  static const rowGap = 12.0, keyColumnGap = 20.56 * .6;
  static const holdIndicatorWidth = 10.0;
  static const buttonLineHeight = 1.0;
  static const holdIndicatorLift = 3.0, holdIndicatorThickness = 1.8;
  static const keyFont = 18.0,
      titleFont = 18.0,
      bodyFont = 12.0,
      metaFont = 9.0;
  static const batteryWidth = 34.0,
      batteryHeight = 16.0,
      batteryTerminal = 2.0,
      batteryRadius = 2.0,
      hairline = .7;
  static const centerSidebarWidth = 164.0;
  static const centerNavigationHeight = 32.0;
  static const panelButtonTop = Color(0xFF383A3A);
  static const panelButtonBottom = Color(0xFF242626);
  static const panelShadow = Color(0x99000000);
  static const panelShadowBlur = 3.0, panelShadowOffset = 2.0;
  static const consoleVideoTopGap = 24.0, consoleBottomInset = 21.0;
  static const deviceBatteryGap = 2.0;
  static const deviceGridColumns = 4;
  static const deviceCardNameHeight = 36.0;
  static const navigationHeight = 30.0;
  static const topHeight = 46.0, footerHeight = 36.0, sideWidth = 168.0;
  static const icon = 22.0, iconLarge = 40.0;
  static const baseWidth = 768.0, minAspect = 16 / 9;
  static const pressDuration = Duration(milliseconds: 90);
  static const roundFaceInset = 4.0;
  static const dialTickIntervals = 10;
  static const dialTickRatio = .35, dialCenterTickRatio = .6;
  static const dialTick = Color(0xFF393B3B);
  static const batteryFillInset = 2.0, batteryFillRadius = 1.0;
  static const batteryFill = Color(0xFF606262);
  static const dialProgress = Color(0xFF555959);
  static const dialFillInset = 2.0;
  static const thumbAspect = 46 / 20;
  static const stickDesignSide = 354.0;
  static const stickTrackEdge = 26.0, stickTrackThickness = 16.0;
  static const stickHorizontalInset = 59.0;
  static const stickVerticalStart = 53.0, stickVerticalEndInset = 60.0;
  static const sliderThumb = 18.0, dialHeight = 14.0;
  static const buttonPadding = EdgeInsets.symmetric(
    horizontal: inset,
    vertical: medium,
  );
  static Color modeColor(String mode) => switch (mode) {
    'S' => sport,
    'C' => cine,
    _ => normal,
  };
  static TextStyle textStyle({
    double size = bodyFont,
    bool bold = false,
    Color color = text,
  }) => TextStyle(
    fontFamily: 'Inter',
    package: package,
    fontFamilyFallback: const ['SourceHanSans'],
    fontSize: size,
    fontWeight: bold ? FontWeight.w700 : FontWeight.w400,
    color: color,
  );
  static ThemeData get theme => ThemeData(
    brightness: Brightness.dark,
    scaffoldBackgroundColor: background,
    colorScheme: const ColorScheme.dark(
      primary: text,
      surface: surface,
      error: auth,
    ),
    textTheme: ThemeData.dark().textTheme.apply(
      fontFamily: 'packages/teleauto_ui/SourceHanSans',
      bodyColor: text,
      displayColor: text,
    ),
    tooltipTheme: TooltipThemeData(textStyle: textStyle()),
  );
}
