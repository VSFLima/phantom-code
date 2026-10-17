// Módulo nativo PRoot — motor do guest Linux sem root (fork Termux, GPL-2.0).
// Compila dois executáveis ELF renomeados como bibliotecas para que o Android
// os extraia em nativeLibraryDir com permissão de execução (targetSdk 35
// bloqueia execve de binários soltos no data dir):
//   libproot.so   — o PRoot em si (ptrace + emulação de syscalls)
//   libloader.so  — loader ELF usado pelo PRoot dentro do guest
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.phantomcode.proot"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 26

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }

        // Guest arm64 — mesmo alvo do QEMU embutido (libphantom_qemu.so).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
