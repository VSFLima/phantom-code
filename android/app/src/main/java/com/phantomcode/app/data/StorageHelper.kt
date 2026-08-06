package com.phantomcode.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Armazenamento público do app (fix de permissões):
 * pasta padrão `/storage/emulated/0/Phantom-Code/` — visível no celular,
 * com o nome do app. Sem permissão, cai no `filesDir` (privado) como fallback.
 */
object StorageHelper {

    /** Nome da pasta pública (igual ao app_name). */
    const val APP_DIR_NAME = "Phantom-Code"

    /** Pasta pública do app: `/storage/emulated/0/Phantom-Code/`. */
    fun publicAppDir(): File {
        val base = Environment.getExternalStorageDirectory()
        return File(base, APP_DIR_NAME)
    }

    /** Pasta de projetos dentro da pasta pública. */
    fun publicWorkspaceDir(): File = File(publicAppDir(), "workspace")

    /** Tem permissão para gravar na pasta pública? */
    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Raiz do workspace: pública quando tem permissão, senão privada (fallback). */
    fun workspaceRoot(context: Context): File {
        val appDir = if (hasStorageAccess(context)) {
            publicAppDir().apply { mkdirs() }
        } else {
            File(context.filesDir, APP_DIR_NAME).apply { mkdirs() }
        }
        return File(appDir, "workspace").apply { mkdirs() }
    }

    /** Intent para o usuário conceder acesso (Settings do sistema). */
    fun permissionIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }
}
