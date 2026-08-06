package com.phantomcode.app.data

import android.content.Context

/**
 * Estado de sessão do app (D18 — auto-save + restaurar sessão):
 * último arquivo aberto, projeto ativo e projetos recentes.
 */
class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        prefs.getStringSet(KEY_RECENTS, emptySet())?.toList()?.sorted() ?: emptyList()

    fun addRecent(project: String) {
        val recents = recentProjects().toMutableSet().apply { add(project) }
        prefs.edit().putStringSet(KEY_RECENTS, recents.toList().takeLast(MAX_RECENTS).toSet()).apply()
    }

    companion object {
        private const val PREFS_NAME = "phantom_session"
        private const val KEY_LAST_OPEN = "last_open_path"
        private const val KEY_ACTIVE_PROJECT = "active_project"
        private const val KEY_RECENTS = "recent_projects"
        private const val MAX_RECENTS = 12
    }
}
