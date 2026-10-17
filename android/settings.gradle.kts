pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Termux terminal-emulator/view são publicados via JitPack (não Maven Central)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PhantomCode"
include(":app")
// Motor PRoot (fork Termux, GPL-2.0) — compila libproot.so + libloader.so
// nativos no CI (NDK/CMake). Sem root: guest Linux em espaço de usuário.
include(":proot")
