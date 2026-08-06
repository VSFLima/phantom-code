# Arquitetura — Fase 4 (Git + Toolbox + Backup)

> Status: T19 ✅ (Git JGit) · T20 🔶 (API Keys ✅ · scanner pendente) · T21–T22 pendentes

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

## Próximos (T21–T22)

- **T21** — Backup local: ZIP do workspace (`java.util.zip`) + SAF (`ACTION_CREATE_DOCUMENT`) + restauração com merge
- **T22** — Backup cloud: Drive/OneDrive/S3 (maior esforço — pode ficar por último)
- **T20 restante** — Scanner de pacotes do guest (IAs/Linguagens/Ferramentas/Sistema)
