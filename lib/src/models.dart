/// Represents the complete visual theme for the secondary display.
///
/// All parameters are **optional**. When a parameter is `null`, the native layer
/// will use its built-in default (e.g. green palette + Intelipunto logo).
///
/// Example — basic brand override for Bank X:
/// ```dart
/// SecondaryDisplay.instance.initialize(
///   config: SecondaryDisplayConfig(
///     theme: ScreenTheme(
///       approvedColorTopHex: '#1565C0',
///       approvedColorBottomHex: '#0D47A1',
///       approvedLabel: 'APROBADA',
///       rejectedLabel: 'RECHAZADA',
///     ),
///   ),
/// );
/// ```
class ScreenTheme {
  // ── Approved screen palette (defaults: green) ──
  /// Top gradient color for the APPROVED screen. Hex format, e.g. "#00C853".
  final String? approvedColorTopHex;

  /// Bottom gradient color for the APPROVED screen. Hex format, e.g. "#1B5E20".
  final String? approvedColorBottomHex;

  // ── Rejected screen palette (defaults: red) ──
  /// Top gradient color for the REJECTED screen. Hex format, e.g. "#D32F2F".
  final String? rejectedColorTopHex;

  /// Bottom gradient color for the REJECTED screen. Hex format, e.g. "#7F0000".
  final String? rejectedColorBottomHex;

  // ── Labels (defaults: "APROBADO" / "DENEGADO") ──
  /// Text shown on the approved result screen.
  final String? approvedLabel;

  /// Text shown on the rejected result screen.
  final String? rejectedLabel;

  // ── Asset overrides ──
  /// Absolute path (on device) to a custom logo image for the wallpaper screen.
  /// When null, the built-in Intelipunto logo is used.
  final String? wallpaperLogoPath;

  /// Absolute path (on device) to a custom GIF for the read-card animation.
  /// When null, the built-in GIF is used.
  final String? readCardGifPath;

  const ScreenTheme({
    this.approvedColorTopHex,
    this.approvedColorBottomHex,
    this.rejectedColorTopHex,
    this.rejectedColorBottomHex,
    this.approvedLabel,
    this.rejectedLabel,
    this.wallpaperLogoPath,
    this.readCardGifPath,
  });

  /// Serializes the theme to a map that matches what the native plugin expects.
  Map<String, dynamic> toMap() {
    return {
      if (approvedColorTopHex != null) 'primaryColorHex': approvedColorTopHex,
      if (approvedColorBottomHex != null) 'secondaryColorHex': approvedColorBottomHex,
      if (rejectedColorTopHex != null) 'rejectedColorTopHex': rejectedColorTopHex,
      if (rejectedColorBottomHex != null) 'rejectedColorBottomHex': rejectedColorBottomHex,
      if (approvedLabel != null) 'approvedLabel': approvedLabel,
      if (rejectedLabel != null) 'rejectedLabel': rejectedLabel,
      if (wallpaperLogoPath != null) 'wallpaperLogoPath': wallpaperLogoPath,
      if (readCardGifPath != null) 'readCardGifPath': readCardGifPath,
    };
  }
}

/// Root configuration object for [SecondaryDisplay.initialize].
class SecondaryDisplayConfig {
  /// Optional visual theme. When null, all defaults are preserved.
  final ScreenTheme? theme;

  const SecondaryDisplayConfig({this.theme});

  Map<String, dynamic> toMap() {
    return {
      if (theme != null) 'theme': theme!.toMap(),
    };
  }
}

