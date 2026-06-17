# Secondary Display Kozen 📱

[![Flutter](https://img.shields.io/badge/Flutter-Package-blue.svg)](https://flutter.dev)
[![Kozen SDK](https://img.shields.io/badge/Kozen_SDK-1.3.0-green.svg)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Plugin modular de Flutter para el control total de la **pantalla secundaria (customer-facing)** en terminales POS de la marca **Kozen**.

Este paquete extrae y encapsula toda la complejidad del SDK nativo de Kozen, proporcionando una interfaz sencilla, tipada y reactiva para desarrolladores Flutter.

---

## ✨ Características Principales

- 🎨 **Tematización Dinámica**: Personaliza colores, etiquetas y estilos según la marca del banco o comercio.
- ⚡ **Estados de Transacción Ready-to-use**:
  - **Bienvenida**: Pantalla de espera personalizable con imagen o GIF.
  - **Monto**: Visualización clara del total y tipo de moneda.
  - **Lectura de Tarjeta**: Instrucciones visuales para el cliente.
  - **Resultados Animados**: Pantallas de aprobación y rechazo con animaciones fluidas basadas en Canvas.
- 🖼️ **Gestión de Wallpapers**: Cambia el fondo de pantalla del terminal para promociones o branding.
- 💡 **Control de Hardware**: Encendido/apagado y brillo de la pantalla secundaria.
- 🚀 **Desacoplamiento Total**: Diseñado para ser inyectado en cualquier proyecto sin dependencias de la lógica de negocio.

---

## 📦 Instalación

### Opción 1: Desde Git (recomendado para producción)

```yaml
dependencies:
  secondary_display:
    git:
      url: https://github.com/gginteligensa/packages-secondary_display.git
      ref: main
```

### Opción 2: Ruta local (para desarrollo)

```yaml
dependencies:
  secondary_display:
    path: ../packages-secondary_display
```

Luego ejecuta:

```bash
flutter pub get
```

---

## ⚙️ Configuración Nativa (Android)

### Requisito: Kozen Component SDK 1.3.0

Este plugin depende del **Kozen Component SDK** para comunicarse con el hardware de la pantalla secundaria. El SDK tiene **dos componentes** que deben estar en la **misma versión**:

| Componente | Tipo | Ubicación | Versión actual |
|---|---|---|---|
| **ComponentSDK** (APK) | Servicio del sistema | Instalado en el POS | **1.3.0** |
| **ComponentLib** (AAR) | Librería cliente | Incluida en este plugin (`android/libs/`) | **1.3.0** |

> ⚠️ **IMPORTANTE**: Si las versiones no coinciden, se produce el **error -10004** (version mismatch). Ambas versiones deben ser idénticas.

### Librerías incluidas en `android/libs/`

| Archivo | Versión | Descripción |
|---|---|---|
| `ComponentLib_1.3.0_release.aar` | 1.3.0 | SDK cliente para la pantalla secundaria y teclado |
| `FinancialLib_1.2.2_release.aar` | 1.2.2 | SDK de servicios financieros |
| `TerminalManagerLib_1.1.0_release.aar` | 1.1.0 | SDK de gestión del terminal |

### Configuración del `build.gradle` del consumidor

Si tu app Flutter consume este plugin, añade la referencia al AAR en tu `android/app/build.gradle`:

```gradle
dependencies {
    // ... otras dependencias

    // ComponentLib 1.3.0: matches device service 1.3.0
    implementation files('../../../packages-secondary_display/android/libs/ComponentLib_1.3.0_release.aar')
}
```

> 💡 La ruta es relativa desde `android/app/` hacia la carpeta `libs/` del plugin. Ajústala según la ubicación de tu proyecto.

### Actualización del servicio en el POS

Para instalar o actualizar el servicio ComponentSDK en el terminal POS:

```bash
# Verificar que el dispositivo está conectado
adb devices

# Instalar el APK del servicio (usar -r para reemplazar)
adb install -r ComponentSDK_1.3.0_release.apk
```

---

## 🚀 Uso

### 1. Inicialización

Configura el plugin al inicio de la aplicación. La inicialización es **opcional** si quieres usar los valores por defecto:

```dart
import 'package:secondary_display/secondary_display.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Inicialización con tema personalizado
  await SecondaryDisplay.instance.initialize(
    config: SecondaryDisplayConfig(
      theme: ScreenTheme(
        approvedColorTopHex: '#00C853',
        approvedColorBottomHex: '#1B5E20',
        rejectedColorTopHex: '#D32F2F',
        rejectedColorBottomHex: '#7F0000',
        approvedLabel: 'APROBADA',
        rejectedLabel: 'RECHAZADA',
      ),
    ),
  );

  runApp(MyApp());
}
```

### 2. Habilitar/Deshabilitar

Puedes activar o desactivar la pantalla secundaria globalmente:

```dart
// Deshabilitar (todas las llamadas UI serán ignoradas)
SecondaryDisplay.instance.isEnabled = false;

// Habilitar
SecondaryDisplay.instance.isEnabled = true;
```

### 3. Control de Hardware

```dart
// Encender la pantalla secundaria
await SecondaryDisplay.instance.power(true);

// Apagar la pantalla secundaria
await SecondaryDisplay.instance.power(false);

// Ajustar brillo (0-100)
await SecondaryDisplay.instance.setBrightness(60);

// Obtener resolución de la pantalla
final resolution = await SecondaryDisplay.instance.getScreenResolution();
// resolution = {'width': 378, 'height': 172}
```

### 4. Pantallas de Estado (Flujo de Transacción)

```dart
// 1️⃣ Mostrar pantalla de bienvenida
await SecondaryDisplay.instance.showWelcome();

// 2️⃣ Mostrar monto a pagar
await SecondaryDisplay.instance.updateInput(
  '2,500.00',
  title: 'MONTO',
  currency: 'DOP',
);

// 3️⃣ Indicar lectura de tarjeta (muestra GIF animado)
await SecondaryDisplay.instance.showReadCard();

// 4️⃣ Mostrar mensaje de estado personalizado
await SecondaryDisplay.instance.showStatus(
  'PROCESANDO',
  subtitle: 'Espere un momento...',
);

// 5️⃣ Resultado: Aprobado (animación verde)
await SecondaryDisplay.instance.showApproved();

// 5️⃣ Resultado: Rechazado (animación roja)
await SecondaryDisplay.instance.showRejected(message: 'Fondos insuficientes');

// 6️⃣ Volver a pantalla de espera
await SecondaryDisplay.instance.showWallpaper();
// o
await SecondaryDisplay.instance.showIdleScreen();
```

### 5. Manejo de Lectura de Tarjeta

Antes de iniciar la lectura de tarjeta, es necesario pausar el SDK para evitar conflictos con el hardware:

```dart
// Antes de leer la tarjeta
await SecondaryDisplay.instance.pauseForCardRead();

// ... ejecutar lectura de tarjeta con el SDK financiero ...

// Después de leer (éxito o fallo)
await SecondaryDisplay.instance.resumeAfterCardRead();
```

> ⚠️ **Siempre** llama a `resumeAfterCardRead()` después de la lectura, incluso si falla. De lo contrario, la pantalla secundaria dejará de responder.

---

## 📐 API Reference

### `SecondaryDisplay` (Singleton)

| Método | Descripción | Retorno |
|---|---|---|
| `initialize({config})` | Inicializa el plugin con configuración opcional | `Future<void>` |
| `power(bool on)` | Enciende/apaga la pantalla | `Future<bool>` |
| `setBrightness(int value)` | Ajusta brillo (0-100) | `Future<bool>` |
| `getScreenResolution()` | Obtiene resolución de pantalla | `Future<Map?>` |
| `showWelcome()` | Pantalla de bienvenida | `Future<bool>` |
| `showWallpaper()` | Wallpaper por defecto | `Future<bool>` |
| `showIdleScreen()` | Pantalla de reposo | `Future<bool>` |
| `updateInput(value, {title, currency})` | Muestra valor de entrada (monto, CI, etc.) | `Future<bool>` |
| `showStatus(title, {subtitle})` | Mensaje de estado personalizado | `Future<bool>` |
| `showReadCard()` | Animación de lectura de tarjeta | `Future<bool>` |
| `showApproved()` | Animación de aprobación | `Future<bool>` |
| `showRejected({message})` | Animación de rechazo | `Future<bool>` |
| `pauseForCardRead()` | Pausa SDK antes de leer tarjeta | `Future<void>` |
| `resumeAfterCardRead()` | Reanuda SDK después de leer tarjeta | `Future<void>` |

### `ScreenTheme`

| Propiedad | Tipo | Default | Descripción |
|---|---|---|---|
| `approvedColorTopHex` | `String?` | Verde | Color superior del gradiente de aprobación |
| `approvedColorBottomHex` | `String?` | Verde oscuro | Color inferior del gradiente de aprobación |
| `rejectedColorTopHex` | `String?` | Rojo | Color superior del gradiente de rechazo |
| `rejectedColorBottomHex` | `String?` | Rojo oscuro | Color inferior del gradiente de rechazo |
| `approvedLabel` | `String?` | "APROBADO" | Texto de la pantalla de aprobación |
| `rejectedLabel` | `String?` | "DENEGADO" | Texto de la pantalla de rechazo |
| `wallpaperLogoPath` | `String?` | Logo Intelipunto | Ruta al logo personalizado |
| `readCardGifPath` | `String?` | GIF incluido | Ruta al GIF de lectura de tarjeta |

---

## 🐛 Troubleshooting

### Error -10004 (Version Mismatch)

**Causa**: La versión de `ComponentLib` (AAR en tu app) no coincide con la versión de `ComponentSDK` (APK instalado en el POS).

**Solución**:
1. Verifica la versión del AAR en `android/libs/` → debe ser `ComponentLib_1.3.0_release.aar`
2. Instala el APK correcto en el POS: `adb install -r ComponentSDK_1.3.0_release.apk`
3. Limpia el proyecto: `flutter clean && flutter pub get`

### `ComponentEngine.secondaryScreenManager == null`

**Causa**: El servicio ComponentSDK no está instalado o no se ha inicializado.

**Solución**:
1. Verifica que el APK del servicio está instalado en el POS
2. Reinicia el POS si es necesario
3. Asegúrate de llamar `SecondaryDisplay.instance.initialize()` al inicio de la app

### Pantalla no responde después de leer tarjeta

**Causa**: No se llamó a `resumeAfterCardRead()`.

**Solución**: Siempre usa un bloque try/finally:
```dart
await SecondaryDisplay.instance.pauseForCardRead();
try {
  // leer tarjeta...
} finally {
  await SecondaryDisplay.instance.resumeAfterCardRead();
}
```

---

## 🛠️ Requisitos Técnicos

| Requisito | Versión |
|---|---|
| **Flutter** | >= 3.3.0 |
| **Android Min SDK** | 24 (Android 7.0) |
| **Compile SDK** | 36 |
| **Kozen Component SDK** | 1.3.0 |
| **Terminal compatible** | Kozen K1141 / P2351 |

---

## 🤝 Contribución

Si encuentras un error o tienes una sugerencia de mejora, por favor abre un *Issue* o envía un *Pull Request*.

---

Desarrollado con ❤️ para el ecosistema POS de Kozen.
