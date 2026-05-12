# Secondary Display Kozen 📱

[![Flutter](https://img.shields.io/badge/Flutter-Package-blue.svg)](https://flutter.dev)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Plugin modular de Flutter para el control total de la **pantalla secundaria (customer-facing)** en terminales POS de la marca **Kozen**.

Este paquete extrae y encapsula toda la complejidad del SDK nativo de Kozen, proporcionando una interfaz sencilla, tipada y reactiva para desarrolladores Flutter.

## ✨ Características Principales

- 🎨 **Tematización Dinámica**: Personaliza colores, etiquetas y estilos según la marca del banco o comercio.
- ⚡ **Estados de Transacción Ready-to-use**:
  - **Bienvenida**: Pantalla de espera personalizable con imagen o GIF.
  - **Monto**: Visualización clara del total y tipo de moneda.
  - **Lectura de Tarjeta**: Instrucciones visuales para el cliente.
  - **Resultados Animados**: Pantallas de aprobación y rechazo con animaciones fluidas basadas en Canvas.
- 🖼️ **Gestión de Wallpapers**: Cambia el fondo de pantalla del terminal para promociones o branding.
- 🚀 **Desacoplamiento Total**: Diseñado para ser inyectado en cualquier proyecto sin dependencias de la lógica de negocio.

## 📦 Instalación

Añade este paquete a tu `pubspec.yaml`:

```yaml
dependencies:
  secondary_display:
    git:
      url: https://github.com/gginteligensa/packages-secondary_display.git
      ref: main
```

*(O usa una ruta local si estás en desarrollo)*

## 🚀 Uso Rápido

### 1. Inicialización

Configura el estilo visual una sola vez al inicio de la aplicación:

```dart
import 'package:secondary_display/secondary_display.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  await SecondaryDisplay.instance.initialize(
    theme: ScreenTheme(
      primaryColorHex: "#00529b", // Color institucional
      approvedLabel: "TRANSACCIÓN EXITOSA",
      rejectedLabel: "PAGO RECHAZADO",
    ),
  );
  
  runApp(MyApp());
}
```

### 2. Flujo de Transacción

```dart
// Mostrar bienvenida
await SecondaryDisplay.instance.showWelcome();

// Mostrar monto a pagar
await SecondaryDisplay.instance.showAmount(
  amount: "2,500.00", 
  currency: "DOP"
);

// Indicar lectura de tarjeta
await SecondaryDisplay.instance.showReadCard();

// Mostrar éxito con subtexto (ej. número de aprobación)
await SecondaryDisplay.instance.showApproved(subtext: "AUT: 987654");
```

## 🛠️ Requisitos Técnicos

- **Android SDK**: Requiere Kozen Component SDK (Librerías JAR/AAR incluidas en el plugin).
- **Android Min SDK**: 24 (Android 7.0).
- **Compile SDK**: 36.
- **Flutter**: >= 3.3.0.

## 🤝 Contribución

Si encuentras un error o tienes una sugerencia de mejora, por favor abre un *Issue* o envía un *Pull Request*.

---
Desarrollado con ❤️ para el ecosistema POS de Kozen.
