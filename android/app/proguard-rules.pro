# Phantom-Code — regras R8/ProGuard

# Kotlin
-keep class kotlin.Metadata { *; }

# Mantém os nomes das classes de tema (usadas por reflexão em presets)
-keep class com.phantomcode.app.ui.theme.** { *; }

# Ponte JS↔Kotlin do editor (CodeMirror/WebView) — métodos chamados pelo JS
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# JGit (usa reflexão em transport/config)
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

# JSch (SFTP/SSH — usa reflexão em ciphers/kex)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
