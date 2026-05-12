import 'package:flutter/material.dart';
import 'package:secondary_display/secondary_display.dart';

/// ─────────────────────────────────────────────────────────────────────────────
/// Example App — secondary_display plugin demo
///
/// Demonstrates:
///   1. Default initialization (Intelipunto branding / built-in defaults).
///   2. Custom-theme initialization (override colors, labels, logos).
///   3. All available display screens.
/// ─────────────────────────────────────────────────────────────────────────────

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ExampleApp());
}

class ExampleApp extends StatelessWidget {
  const ExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Secondary Display Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      home: const DemoPage(),
    );
  }
}

class DemoPage extends StatefulWidget {
  const DemoPage({super.key});

  @override
  State<DemoPage> createState() => _DemoPageState();
}

class _DemoPageState extends State<DemoPage> {
  bool _useCustomTheme = false;
  String _lastAction = '—';

  SecondaryDisplay get _display => SecondaryDisplay.instance;

  @override
  void initState() {
    super.initState();
    _initDefault();
  }

  // ── 1. Default init — uses built-in Intelipunto assets + green/red ────────
  Future<void> _initDefault() async {
    await _display.initialize();
    _setAction('Initialized with DEFAULT theme (Intelipunto)');
  }

  // ── 2. Custom init — override colors and labels ───────────────────────────
  Future<void> _initCustomTheme() async {
    await _display.initialize(
      config: const SecondaryDisplayConfig(
        theme: ScreenTheme(
          // Approved screen: indigo palette instead of green
          approvedColorTopHex: '#1565C0',
          approvedColorBottomHex: '#0D47A1',
          approvedLabel: 'APROBADA',
          // Rejected screen: deep orange instead of red
          rejectedColorTopHex: '#E65100',
          rejectedColorBottomHex: '#BF360C',
          rejectedLabel: 'RECHAZADA',
          // Uncomment to supply an on-device custom logo:
          // wallpaperLogoPath: '/storage/emulated/0/my_bank_logo.png',
        ),
      ),
    );
    _setAction('Initialized with CUSTOM theme (Bank X)');
  }

  // ── Helpers ───────────────────────────────────────────────────────────────
  void _setAction(String msg) => setState(() => _lastAction = msg);

  Future<void> _run(String label, Future<bool> Function() fn) async {
    final ok = await fn();
    _setAction('$label → ${ok ? "✅ OK" : "❌ FAILED"}');
  }

  // ── Build ─────────────────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Secondary Display Demo'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Theme toggle
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  children: [
                    const Text('Theme', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    SwitchListTile(
                      title: Text(_useCustomTheme ? 'Custom (Bank X)' : 'Default (Intelipunto)'),
                      value: _useCustomTheme,
                      onChanged: (val) async {
                        setState(() => _useCustomTheme = val);
                        if (val) {
                          await _initCustomTheme();
                        } else {
                          await _initDefault();
                        }
                      },
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),

            // Screen buttons
            const Text('Screens', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 8),
            _btn('💤 Wallpaper', () => _run('showWallpaper', _display.showWallpaper)),
            _btn('👋 Welcome', () => _run('showWelcome', _display.showWelcome)),
            _btn('💵 Amount',
                () => _run('showAmount', () => _display.updateInput('1.234,56', title: 'MONTO', currency: 'USD'))),
            _btn('⏳ Status',
                () => _run('showStatus', () => _display.showStatus('PROCESANDO', subtitle: 'Por favor espere'))),
            _btn('💳 Read Card', () => _run('showReadCard', _display.showReadCard)),
            _btn('✅ Approved', () => _run('showApproved', _display.showApproved), color: Colors.green),
            _btn('❌ Rejected',
                () => _run('showRejected', () => _display.showRejected(message: 'FONDOS INSUFICIENTES')),
                color: Colors.red),

            const SizedBox(height: 24),

            // Last action log
            Card(
              color: Colors.grey.shade100,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    const Text('Last action', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    Text(_lastAction, textAlign: TextAlign.center),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _btn(String label, VoidCallback onTap, {Color? color}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: ElevatedButton(
        style: ElevatedButton.styleFrom(
          backgroundColor: color,
          foregroundColor: color != null ? Colors.white : null,
          padding: const EdgeInsets.symmetric(vertical: 14),
        ),
        onPressed: onTap,
        child: Text(label),
      ),
    );
  }
}
