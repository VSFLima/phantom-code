# Phantom-Code — regras R8/ProGuard

# Kotlin
-keep class kotlin.Metadata { *; }

# Mantém os nomes das classes de tema (usadas por reflexão em presets)
-keep class com.phantomcode.app.ui.theme.** { *; }
