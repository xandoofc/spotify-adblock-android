import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Color(0xFF121212),
      systemNavigationBarIconBrightness: Brightness.light,
    ),
  );
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'xanSpot',
      theme: ThemeData(
        brightness: Brightness.dark,
        primaryColor: const Color(0xFFEF5350),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFEF5350),
          secondary: Color(0xFFEF5350),
          surface: Color(0xFF1E1E1E),
        ),
        scaffoldBackgroundColor: const Color(0xFF121212),
        cardColor: const Color(0xFF1E1E1E),
        textTheme: const TextTheme(
          headlineLarge: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
          bodyLarge: TextStyle(
            fontSize: 16,
            color: Colors.white70,
          ),
          labelLarge: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w600,
            color: Colors.white,
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            foregroundColor: Colors.white,
            backgroundColor: const Color(0xFFEF5350),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
            textStyle: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        snackBarTheme: const SnackBarThemeData(
          backgroundColor: Color(0xFF1E1E1E),
          contentTextStyle: TextStyle(color: Colors.white),
          actionTextColor: Color(0xFFEF5350),
        ),
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> with SingleTickerProviderStateMixin {
  static const platform = MethodChannel('com.xanspot.net/vpn');
  bool _isVpnActive = false;
  bool _hasPromptedBattery = false; // Track if battery prompt was shown
  late AnimationController _animationController;
  late Animation<double> _scaleAnimation;
  bool _isMiui = false; // Detect MIUI

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
    );
    _scaleAnimation = Tween<double>(begin: 1.0, end: 0.95).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeInOut),
    );
    _checkMiui(); // Check if running on MIUI
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  Future<void> _checkMiui() async {
    final deviceInfo = DeviceInfoPlugin();
    final androidInfo = await deviceInfo.androidInfo;
    // MIUI detection via build properties
    if (androidInfo.manufacturer.toLowerCase().contains('xiaomi') ||
        androidInfo.brand.toLowerCase().contains('miui')) {
      setState(() {
        _isMiui = true;
      });
    }
  }

  Future<void> _toggleVpn() async {
    await _animationController.forward();
    await _animationController.reverse();

    try {
      // Request battery optimization permission only once (optional)
      if (!_hasPromptedBattery) {
        var batteryStatus = await Permission.ignoreBatteryOptimizations.request();
        setState(() {
          _hasPromptedBattery = true;
        });
        if (!batteryStatus.isGranted && _isMiui) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text(
                'MIUI detectado. Ative "Iniciar automaticamente" e desative a otimização de bateria nas configurações do app.',
              ),
              duration: const Duration(seconds: 5),
              action: SnackBarAction(
                label: 'Abrir Configurações',
                onPressed: () => openAppSettings(),
              ),
            ),
          );
        }
      }

      // Toggle VPN
      if (_isVpnActive) {
        final result = await platform.invokeMethod<bool>('stopVpn');
        if (result == true) {
          setState(() {
            _isVpnActive = false;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Adblock desativado'),
              duration: Duration(seconds: 2),
            ),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Falha ao desativar o adblock'),
              action: SnackBarAction(
                label: 'Tentar novamente',
                onPressed: _toggleVpn,
              ),
            ),
          );
        }
      } else {
        final result = await platform.invokeMethod<bool>('startVpn');
        if (result == true) {
          setState(() {
            _isVpnActive = true;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Adblock ativado'),
              duration: Duration(seconds: 2),
            ),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Permissão de VPN negada. Ative a permissão.'),
              action: SnackBarAction(
                label: 'Abrir Configurações',
                onPressed: () => openAppSettings(),
              ),
            ),
          );
        }
      }
    } on PlatformException catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Erro: ${e.message ?? 'Desconhecido'}'),
          action: SnackBarAction(
            label: 'Tentar novamente',
            onPressed: _toggleVpn,
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Card(
              elevation: 8,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16),
              ),
              child: Padding(
                padding: const EdgeInsets.all(32.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Image.asset(
                      'assets/app_icon.png',
                      width: 100,
                      height: 100,
                      errorBuilder: (context, error, stackTrace) => const Icon(
                        Icons.block,
                        size: 100,
                        color: Color(0xFFEF5350),
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'xanSpot',
                      style: TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: _isVpnActive ? Colors.green : Colors.red,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          _isVpnActive ? 'Adblock Ativado' : 'Adblock Desativado',
                          style: const TextStyle(
                            fontSize: 16,
                            color: Colors.white70,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 24),
                    ScaleTransition(
                      scale: _scaleAnimation,
                      child: ElevatedButton(
                        onPressed: _toggleVpn,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFFEF5350),
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 32,
                            vertical: 16,
                          ),
                          elevation: 4,
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              _isVpnActive ? Icons.stop : Icons.play_arrow,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            Text(
                              _isVpnActive ? 'Desativar Adblock' : 'Ativar Adblock',
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Bloqueie anúncios no Spotify de forma simples e eficaz. // @xandoofc on github',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.white54,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}