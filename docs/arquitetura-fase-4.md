# Arquitetura — Fase 4 (Git + Toolbox + Backup)

> Status: T19 ✅ (Git JGit) · T20–T22 pendentes

## T19 — Git nativo (JGit)

| Arquivo | Papel |
|---------|-------|
| `data/git/GitManager.kt` | JGit 6.10: status (A/M/D/U/C), git init, clone, commit (stage all), push/pull, log |
| `ui/screens/GitScreen.kt` | Seleção de projeto, status do repo, changes, commit, push/pull, log, token |

**Decisões:**
- JGit `org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r` (Maven Central)
- Auth: `UsernamePasswordCredentialsProvider("oauth2", <PAT>)` — token em SharedPreferences (T20 migrará para Android Keystore)
- Author/committer fixo "Phantom-Code <phantom@localhost>" (evita depender de config git)
- ProGuard: `-keep class org.eclipse.jgit.**` (reflexão em transport/config)
- Todas as ops em `Dispatchers.IO` (suspend)

## Próximos (T20–T22)

- **T20** — Toolbox: seção Integrações & API Keys com **Android Keystore** (guardar tokens de forma criptografada) + scanner de pacotes do guest
- **T21** — Backup local: ZIP do workspace (`java.util.zip`) + SAF (`ACTION_CREATE_DOCUMENT`) + restauração com merge
- **T22** — Backup cloud: Drive/OneDrive/S3 (maior esforço — pode ficar por último)
