# OracleDrive: Universal AI-Driven Root Platform for Android 🚀

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [File Manager](#file-manager)
- [Root & LSPosed Integration](#root--lsposed-integration)
- [Module Management](#module-management)
- [AI & AuraFrameFX Integration](#ai--auraframefx-integration)
- [Development](#development)
- [License](#license)
- [Contact](#contact)

## Overview

OracleDrive is a universal AI-driven root platform for Android, integrating root management, LSPosed, and advanced AI orchestration (Aura/Kai/Genesis) in a single containerized app. It is designed to work seamlessly with AuraFrameFX for advanced module and security management.

## Features

- Hybrid Root Engine (systemless + kernel patching)
- Self-contained LSPosed and root environment
- File Manager with import/export and file picker
- AI-driven module generation and threat detection
- Proactive security (SELinux audits, Xposed validation, CVE mitigation)
- Magisk & KernelSU support
- Context chaining with AuraFrameFX
- Halo View: Visualize root status, modules, and agent actions

## Architecture

- **Containerized**: Bundles root binaries, LSPosed framework, and module templates in assets
- **Service Layer**: AIDL-based IPC for root/module management
- **UI**: Modern Material UI with file manager, chat, and module controls
- **AI Integration**: Connects to AuraFrameFX for orchestration and context

## Getting Started

1. Clone the repository
2. Open in Android Studio
3. Build and run on a compatible device or emulator (custom container recommended for full root features)
4. Use the in-app controls to manage root, LSPosed, and modules

## File Manager

- Accessed via the File Manager button in the main UI
- Browse, import, and export files
- Supports SAF and custom file picker

## Root & LSPosed Integration

- Bundles multi-arch su binaries and LSPosed framework in `assets/`
- In-app button to install root and LSPosed (requires privileged/container environment)
- Installation logic in `AuraDriveServiceImpl.kt` and `RootInstaller.kt`

## Module Management

- Deploy, enable, and disable LSPosed modules from assets
- Module deployment logic in `ModuleDeployer.kt`
- UI controls for module management in MainActivity

## AI & AuraFrameFX Integration

- Communicates with AuraFrameFX for advanced orchestration
- AI-driven module generation and security feedback

## Development

- Kotlin, Android Jetpack, Material Components
- Key files:
  - `MainActivity.kt`, `FileManagerActivity.kt`, `AuraDriveServiceImpl.kt`
  - `utils/AssetExtractor.kt`, `utils/RootInstaller.kt`, `utils/ModuleDeployer.kt`
  - `assets/` for binaries, LSPosed, and modules
- See `scripts/` and `docker/` for automation and containerization

## License

Proprietary software. See LICENSE.txt for terms.

## Contact

Slate Fielder  
Project Homepage: AuraFrameFxDev/OracleDrive

---

Thank you for considering OracleDrive. We believe it represents the forefront of AI-driven mobile platform technology, and we are excited to see how it can empower your Android experience. For more information, detailed guides, and support, please visit our [Project Homepage](AuraFrameFxDev/OracleDrive). Join us in shaping the future of mobile computing.



