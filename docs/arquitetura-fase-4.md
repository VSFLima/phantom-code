# Arquitetura — Fase 4 (Git + Toolbox + Backup)

> Status: T19 ✅ · T20 🔶 (API Keys ✅ · scanner pendente) · T21 ✅ (Backup) · T22 pendente · T23 ✅ (FGS) · T24 ✅ (Onboarding/Palette)

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

## T20 — Toolbox: Integrações & API Keys (D8) — 🔶 API Keys feita, scanner pendente

| Arquivo | Papel |
|---------|-------|
| `data/secrets/SecretsManager.kt` | Android Keystore + AES-256/GCM (`AES/GCM/NoPadding`), master key `phantom_master`; metadados em `phantom_secrets` prefs; máscara `sk-…xxxx`; `setInvalidatedByBiometricEnrollment(false)` |
| `ui/components/SecretKeyWidgets.kt` | `SecretKeyCard` (alias, categoria, valor mascarado, switch "Expor ao Linux", copiar `$VAR`, revogar) + `AddSecretKeyDialog` (nome, variável, valor oculto, categoria, expor) |
| `ui/screens/ToolboxScreen.kt` | Seção real: lista de chaves (`remember(keysTick)`), adicionar, copiar p/ clipboard, revogar, toggle |
| `data/git/GitManager.kt` | Token migrado p/ Keystore (alias `github_token`, cat. GIT) com migração automática do prefs antigo |

**Decisões (D8):**
- Valores NUNCA em texto plano — só o valor mascarado aparece na UI; nunca logar o valor real
- Backup do app não inclui secrets por padrão (a chave fica no Keystore do device)
- "Expor ao Linux" = toggle por chave; na Fase 3 a VM recebe via env vars
- Keystore: IV + ciphertext em Base64 NO_WRAP; GCM tag 128 bits

## T21 — Backup local + fix de permissões (D2) ✅

| Arquivo | Papel |
|---------|-------|
| `data/backup/BackupManager.kt` | Workspace → ZIP (`java.util.zip`) via SAF (`ACTION_CREATE_DOCUMENT`) com manifest JSON; restauração (`ACTION_OPEN_DOCUMENT`) com merge que nunca apaga; entradas inválidas (path traversal) puladas |
| `data/StorageHelper.kt` | Permissões: `MANAGE_EXTERNAL_STORAGE` (API 30+) / `WRITE_EXTERNAL_STORAGE` (<30); pasta pública `/storage/emulated/0/Phantom-Code/` + fallback `filesDir/Phantom-Code`; intents de permissão |
| `data/WorkspaceManager.kt` | Raiz **dinâmica** (reavalia ao conceder permissão) + migração automática do workspace antigo |
| `HomeScreen.kt` / `SettingsScreen.kt` | Card de permissão (Home) + seção Armazenamento (Settings) |
| `AndroidManifest.xml` | `READ/WRITE_EXTERNAL_STORAGE` + `MANAGE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage` |

**Decisões (D2):**
- Pasta pública com o nome do app: `/storage/emulated/0/Phantom-Code/workspace`
- Sem permissão → fallback privado (`filesDir/Phantom-Code/workspace`) — o app nunca quebra
- Migração automática: projetos do `filesDir/workspace` antigo movidos para a nova raiz ao abrir
- Restauração = merge (sobrescreve/recria) — **nunca apaga silenciosamente**

## Próximos (T22)

- **T22** — Backup cloud: Drive/OneDrive/S3 (maior esforço — pode ficar por último)
- **T20 restante** — Scanner de pacotes do guest (IAs/Linguagens/Ferramentas/Sistema)
