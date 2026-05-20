import 'package:flutter/services.dart';
import 'src/models.dart';
export 'src/models.dart';

class SecondaryDisplay {
  // Singleton instance
  static final SecondaryDisplay _instance = SecondaryDisplay._internal();
  static SecondaryDisplay get instance => _instance;

  SecondaryDisplay._internal();

  final MethodChannel _channel = const MethodChannel('secondary_display');

  SecondaryDisplayConfig? _currentConfig;

  /// Initializes the plugin with optional configuration and themes.
  /// Must be called before other operations if custom themes are desired.
  Future<void> initialize({SecondaryDisplayConfig? config}) async {
    _currentConfig = config;
    try {
      await _channel.invokeMethod('initialize', config?.toMap() ?? {});
    } on PlatformException catch (e) {
      print("Failed to initialize secondary display: '\${e.message}'.");
    }
  }

  /// Gets the resolution of the secondary screen.
  Future<Map<String, int>?> getScreenResolution() async {
    try {
      final Map? result = await _channel.invokeMethod('getScreenResolution');
      if (result != null) {
        return {
          'width': result['width'] as int,
          'height': result['height'] as int,
        };
      }
    } on PlatformException catch (e) {
      print("Failed to get screen resolution: '\${e.message}'.");
    }
    return null;
  }

  /// Power on or off the secondary screen.
  /// Uses the SDK manager's power() method when available (official QuickStart pattern).
  /// Falls back to show()/dismiss() via the Presentation API.
  Future<bool> power(bool on) async {
    try {
      final bool? success = await _channel.invokeMethod('power', {'on': on});
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to set power: '\${e.message}'.");
      return false;
    }
  }

  /// Sets the screen brightness (0-100).
  /// Uses the SDK manager when available (official QuickStart sets 100 after init).
  /// Returns false when using Presentation API (not supported in that mode).
  Future<bool> setBrightness(int value) async {
    try {
      final bool? success = await _channel.invokeMethod('setBrightness', {'value': value});
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to set brightness: '\${e.message}'.");
      return false;
    }
  }

  /// Displays the default wallpaper (or custom if configured).
  /// Alias: [showIdleScreen] for semantic clarity.
  Future<bool> showWallpaper() async {
    try {
      final bool? success = await _channel.invokeMethod('showWallpaper');
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to show wallpaper: '\${e.message}'.");
      return false;
    }
  }

  /// Shows the idle/resting screen — equivalent to `second_default.xml` in the
  /// official QuickStart SDK. Call this after a transaction completes or when
  /// the terminal returns to idle state (e.g. after 30s timeout).
  /// 
  /// Official reference: TransInitActivity.java — idleTimer.onFinish() → showCover(true)
  Future<bool> showIdleScreen() async {
    try {
      final bool? success = await _channel.invokeMethod('showIdleScreen');
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to show idle screen: '\${e.message}'.");
      return false;
    }
  }

  /// Displays the custom PLATCO welcome screen.
  Future<bool> showWelcome() async {
    try {
      final bool? success = await _channel.invokeMethod('showWelcome');
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to show welcome screen: '\${e.message}'.");
      return false;
    }
  }

  /// Updates the input value displayed on the secondary screen (Amount, CI, etc.).
  Future<bool> updateInput(String value, {String title = '', String currency = ''}) async {
    try {
      final bool? success = await _channel.invokeMethod('showAmount', {
        'amount': value,
        'title': title,
        'currency': currency,
      });
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to update input: '\${e.message}'.");
      return false;
    }
  }

  /// Shows a status or instruction message on the secondary screen.
  Future<bool> showStatus(String title, {String subtitle = ''}) async {
    try {
      final bool? success = await _channel.invokeMethod('showStatus', {
        'title': title, 
        'subtitle': subtitle
      });
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to show status: '\${e.message}'.");
      return false;
    }
  }

  /// Displays the animated MP4/GIF video for card reading.
  Future<bool> showReadCard() async {
    try {
      final bool? success = await _channel.invokeMethod('showReadCard');
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to show read card: '\${e.message}'.");
      return false;
    }
  }

  /// Shows animated green APROBADO screen on the secondary display.
  Future<bool> showApproved() async {
    try {
      final bool? success = await _channel.invokeMethod('showApproved');
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to show approved: '\${e.message}'.");
      return false;
    }
  }

  /// Shows animated red DENEGADO screen on the secondary display.
  Future<bool> showRejected({String message = ''}) async {
    try {
      final bool? success = await _channel.invokeMethod('showRejected', {
        'message': message,
      });
      return success ?? false;
    } on PlatformException catch (e) {
      print("Failed to show rejected: '\${e.message}'.");
      return false;
    }
  }

  /// Call BEFORE starting card reading.
  /// Temporarily stops the Kozen SDK background polling that competes
  /// with the card reader hardware bus (com.pos.service race condition).
  Future<void> pauseForCardRead() async {
    try {
      await _channel.invokeMethod('pauseForCardRead');
    } on PlatformException catch (e) {
      print("pauseForCardRead failed: '${e.message}'.");
    }
  }

  /// Call AFTER card reading completes (success OR failure).
  /// Re-initializes the Kozen SDK so the secondary display works again.
  Future<void> resumeAfterCardRead() async {
    try {
      await _channel.invokeMethod('resumeAfterCardRead');
    } on PlatformException catch (e) {
      print("resumeAfterCardRead failed: '${e.message}'.");
    }
  }
}
