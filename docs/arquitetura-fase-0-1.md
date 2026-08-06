# Arquitetura — Fase 0 + 1 (Fundação e Shell da UI)

> Status: ✅ implementado · Build: GitHub Actions (sem SDK Android local)

## Decisões técnicas

| Item | Decisão |
|------|---------|
| **Linguagem** | Kotlin 2.1.0 + Jetpack Compose (BOM 2024.12.01) |
| **SDK** | compileSdk/targetSdk 35 · minSdk 26 |
| **Build** | AGP 8.7.3 · Gradle 8.11.1 (wrapper commitado) |
| **Navegação** | Navigation Compose 2.8.5 — abas preservando estado (`popUpTo startDestination { saveState }`) |
| **Tema** | `ThemeController` (SharedPreferences) + CompositionLocal → preview ao vivo (D10) |
| **Ícones** | material-icons-extended (R8 remove os não usados no release) |
| **App ID** | `com.phantomcode.app` (provisório) |

## Estrutura do código (`android/app/src/main/java/com/phantomcode/app/`)

```
MainActivity.kt           → enableEdgeToEdge + PhantomRoot
PhantomApp.kt             → ThemeController + PhantomTheme + NavHost (6 rotas)
ui/
├── theme/
│   ├── Palette.kt        → PhantomPalette + 7 presets (Phantom…Light)
│   ├── Theme.kt          → PhantomTheme (dark/light ColorScheme)
│   └── ThemeController.kt→ preset + cores custom + persistência
├── navigation/
│   └── Destinations.kt   → Routes + BottomNavItems (5 itens — D14)
├── components/
│   ├── PhantomScaffold.kt→ TopBar, ActivityBar (52dp), BottomNav, TerminalDock
│   └── PhantomWidgets.kt → PhantomLogo (Canvas), PhantomCard, botões, SwatchRow…
└── screens/
    ├── HomeScreen.kt     → logo, status QEMU, Recent Projects, ações
    ├── ExplorerScreen.kt → estado vazio do workspace + FAB
    ├── SearchScreen.kt   → campo de busca estilizado
    ├── GitScreen.kt      → sem repo + badge branch
    ├── ToolboxScreen.kt  → status VM + Integrações + chips de pacotes
    └── SettingsScreen.kt → presets + color pickers custom + seções
```

## Design (D15 — Cyber-Phantom / Neon Dark IDE)

- Fundo OLED `#000000`, cards `#121212`, bordas angulares 4–6dp
- Accent roxo `#9F4DFF` + cyan `#00FFFF` (strings/outlines/status)
- Bottom Nav fixa com 5 itens; indicador angular no item ativo
- Activity Bar fina (~52dp) só com ícones + trilho separador
- Terminal dock no rodapé com abas (Terminal 1/2/Run) e prompt `root@phantom:~/workspace`
- Pill de status `QEMU LINUX: RUNNING/STOPPED` (verde/neutro)
- Logo: escudo gradiente roxo→cyan com raio (Canvas) — usado também como botão "Home"

## CI (`.github/workflows/build.yml`)

- Push/PR em `main`/`master` + `workflow_dispatch`
- JDK 17 (temurin) + Android SDK (android-actions) → `./gradlew :app:assembleRelease`
- Assinatura opcional via secrets: `PHANTOM_KEYSTORE_BASE64`, `PHANTOM_STORE_PASSWORD`,
  `PHANTOM_KEY_ALIAS`, `PHANTOM_KEY_PASSWORD` (ver `scripts/generate-keystore.sh`)
- Artifact: `phantom-code-apk` (release + debug)

## Próxima fase (T11–T13 — Explorer real + Editor CodeMirror 6)

- Persistência do workspace (`filesDir`) + SAF (`OPEN_DOCUMENT_TREE`)
- WebView com CodeMirror 6 (asset local) + ponte JS↔Kotlin
- Auto-save (D18) e restauração de sessão
