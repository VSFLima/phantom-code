package com.phantomcode.app.data

import android.content.Context

/**
 * Estado de sessão do app (D18 — auto-save + restaurar sessão):
 * último arquivo aberto, projeto ativo e projetos recentes.
 */
class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Onboarding de 1º uso (T24 · D14). */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var lastOpenPath: String?
        get() = prefs.getString(KEY_LAST_OPEN, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_OPEN, value).apply()
        }

    var activeProject: String?
        get() = prefs.getString(KEY_ACTIVE_PROJECT, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_ACTIVE_PROJECT).apply()
            else prefs.edit().putString(KEY_ACTIVE_PROJECT, value).apply()
        }

    fun recentProjects(): List<String> =
        prefs.getString(KEY_RECENTS_ORDERED, null)
            ?.split(RECENT_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: prefs.getStringSet(KEY_RECENTS, emptySet())?.toList()?.sorted().orEmpty()

    fun addRecent(project: String) {
        val recents = (listOf(project) + recentProjects().filterNot { it == project }).take(MAX_RECENTS)
        prefs.edit()
            .putString(KEY_RECENTS_ORDERED, recents.joinToString(RECENT_SEPARATOR))
            .putStringSet(KEY_RECENTS, recents.toSet())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "phantom_session"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_LAST_OPEN = "last_open_path"
        private const val KEY_ACTIVE_PROJECT = "active_project"
        private const val KEY_RECENTS = "recent_projects"
        private const val KEY_RECENTS_ORDERED = "recent_projects_ordered"
        private const val RECENT_SEPARATOR = "\u001f"
        private const val MAX_RECENTS = 12
    }
}
