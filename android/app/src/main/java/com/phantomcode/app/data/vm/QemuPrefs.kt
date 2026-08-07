package com.phantomcode.app.data.vm

import android.app.ActivityManager
import android.content.Context

/**
 * Detecção de hardware do aparelho para guiar os limites dos presets custom
 * (o usuário define conforme o poder de processamento do próprio celular).
 */
object DeviceCapabilities {

    /** Núcleos lógicos visíveis ao processo (mín. 1). */
    fun cores(context: Context): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    /** RAM total do aparelho em MB (fallback: 8 GB). */
    fun totalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 8192L
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return if (mi.totalMem > 0) mi.totalMem / (1024 * 1024) else 8192L
    }

    /** RAM máxima segura para a VM (total - reserva de 2 GB), mín. 1 GB. */
    fun maxRamMb(context: Context): Int =
        (totalRamMb(context) - 2048L).coerceAtLeast(1024L).toInt()
}

/**
 * Preferências da VM (D13): preset, valores custom (cores/RAM) e tamanho do HD
 * da distro. Persistidos para sobreviver a reinícios — o usuário pode mudar a
 * qualquer momento.
 */
class QemuPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("qemu_prefs", Context.MODE_PRIVATE)

    var presetId: String
        get() = prefs.getString(KEY_PRESET, QemuPresets.BALANCED.id)!!
        set(value) = prefs.edit().putString(KEY_PRESET, value).apply()

    /** Núcleos custom do preset CUSTOM. */
    var customCores: Int
        get() = prefs.getInt(KEY_CORES, QemuPresets.BALANCED.cpu)
        set(value) = prefs.edit().putInt(KEY_CORES, value).apply()

    /** RAM custom (MB) do preset CUSTOM. */
    var customRamMb: Int
        get() = prefs.getInt(KEY_RAM, QemuPresets.BALANCED.ramMb)
        set(value) = prefs.edit().putInt(KEY_RAM, value).apply()

    /** Tamanho do HD da distro em MB (padrão: 3 GB). */
    var diskSizeMb: Int
        get() = prefs.getInt(KEY_DISK, DEFAULT_DISK_MB)
        set(value) = prefs.edit().putInt(KEY_DISK, value).apply()

    /** Escolha efetiva de preset, considerando os valores custom. */
    fun effectivePreset(context: Context): QemuPreset {
        val base = QemuPresets.byId(presetId)
        if (base.custom) {
            val maxCores = DeviceCapabilities.cores(context)
            val maxRam = DeviceCapabilities.maxRamMb(context)
            return base.copy(
                cpu = customCores.coerceIn(1, maxCores),
                ramMb = customRamMb.coerceIn(512, maxRam),
            )
        }
        return base
    }

    /** Presets de disco disponíveis (tamanhos em MB). */
    fun diskOptions(): List<Int> = listOf(3, 4, 8, 16, 32, 64).map { it * 1024 }

    companion object {
        const val DEFAULT_DISK_MB = 3 * 1024
        private const val KEY_PRESET = "preset_id"
        private const val KEY_CORES = "custom_cores"
        private const val KEY_RAM = "custom_ram_mb"
        private const val KEY_DISK = "disk_size_mb"
    }
}
