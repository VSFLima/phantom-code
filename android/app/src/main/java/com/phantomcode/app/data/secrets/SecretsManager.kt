package com.phantomcode.app.data.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Categoria da chave (D8 — Toolbox → Integrações & API Keys). */
enum class SecretCategory(val id: String, val label: String) {
    GIT("git", "Git"),
    IA("ia", "IA"),
    CLOUD("cloud", "Cloud"),
    SERVER("server", "Servidor"),
    OUTROS("outros", "Outros"),
}

/** Chave salva (valor mascarado na UI — nunca logar o valor real). */
data class SecretEntry(
    val alias: String,
    val category: String,
    val envVar: String,
    val exposeToLinux: Boolean,
    val masked: String,
)

/**
 * Secrets no Android Keystore (D8): valores criptografados com AES-256/GCM
 * (chave gerada no Keystore, nunca sai do device). Metadados (categoria,
 * variável de ambiente, expor ao Linux) em SharedPreferences.
 *
 * Regras: nunca logar o valor; expor ao Linux só com toggle; backup não
 * inclui secrets por padrão.
 */
class SecretsManager(context: Context) {

    private val prefs = context.getSharedPreferences("phantom_secrets", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setInvalidatedByBiometricEnrollment(false)
                .build(),
        )
        return gen.generateKey()
    }

    fun save(
        alias: String,
        value: String,
        category: SecretCategory,
        envVar: String,
        exposeToLinux: Boolean,
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("v_$alias", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv_$alias", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("cat_$alias", category.id)
            .putString("env_$alias", envVar.ifBlank { alias.uppercase() })
            .putBoolean("exp_$alias", exposeToLinux)
            .apply()
    }

    /** Retorna o valor REAL (uso interno — nunca exibir na UI). */
    fun get(alias: String): String? {
        val encB64 = prefs.getString("v_$alias", null) ?: return null
        val ivB64 = prefs.getString("iv_$alias", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(encB64, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun delete(alias: String) {
        prefs.edit()
            .remove("v_$alias")
            .remove("iv_$alias")
            .remove("cat_$alias")
            .remove("env_$alias")
            .remove("exp_$alias")
            .apply()
    }

    fun setExposeToLinux(alias: String, expose: Boolean) {
        prefs.edit().putBoolean("exp_$alias", expose).apply()
    }

    fun list(): List<SecretEntry> =
        prefs.all.keys
            .filter { it.startsWith("v_") }
            .map { it.removePrefix("v_") }
            .mapNotNull { alias ->
                val value = get(alias) ?: return@mapNotNull null
                SecretEntry(
                    alias = alias,
                    category = prefs.getString("cat_$alias", SecretCategory.OUTROS.id)
                        ?: SecretCategory.OUTROS.id,
                    envVar = prefs.getString("env_$alias", alias.uppercase())
                        ?: alias.uppercase(),
                    exposeToLinux = prefs.getBoolean("exp_$alias", false),
                    masked = mask(value),
                )
            }
            .sortedBy { it.alias }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "phantom_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** Máscara de exibição: `sk-…xxxx` (D8). */
        fun mask(value: String): String = when {
            value.length <= 6 -> "••••"
            else -> value.take(3) + "…" + value.takeLast(4)
        }
    }
}
