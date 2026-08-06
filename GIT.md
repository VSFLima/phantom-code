# 📖 GIT.md — Guia de Git & CI do Phantom-Code

> Guia completo de versionamento, workflow, CI e troubleshooting deste projeto.
> **Tema:** Cyber-Phantom (`#000000` · `#9F4DFF` · `#00FFFF`)

---

## 1. 📦 Primeira configuração (uma vez)

```bash
# 1. Iniciar o repositório
git init -b main

# 2. Identidade (usar o noreply mantém o email privado)
git config user.name  "VSFLima"
git config user.email "VSFLima@users.noreply.github.com"

# 3. Criar o repositório privado no GitHub e enviar tudo
gh repo create VSFLima/phantom-code --private --source . --remote origin --push

# 4. Conferir
git remote -v
gh repo view VSFLima/phantom-code
```

> 💡 `gh` já está autenticado neste ambiente (`gh auth status`).

---

## 2. 🔁 Fluxo diário

```bash
git add -A
git commit -m "tipo(escopo): descrição"
git push origin main
```

### Convenção de commits

| Tipo | Uso | Exemplo |
|------|-----|---------|
| `feat` | Nova funcionalidade | `feat(editor): abas do CodeMirror 6` |
| `fix` | Correção de bug | `fix(theme): cor do cursor no preset Matrix` |
| `docs` | Documentação | `docs(readme): seção de build` |
| `refactor` | Mudança sem mudar comportamento | `refactor(nav): extrai navigateToTab` |
| `chore` | Tarefas de manutenção | `chore(ci): atualiza JDK do build.yml` |

**Escopos sugeridos:** `theme` · `nav` · `editor` · `explorer` · `vm` · `git` · `toolbox` · `ci` · `docs`

---

## 3. 🌿 Branches

| Branch | Uso |
|--------|-----|
| `main` | Produção — sempre compilando (CI roda em todo push) |
| `feat/*` | Funcionalidades em andamento → PR para `main` |

```bash
git checkout -b feat/editor-codemirror
# ... trabalhar ...
git push -u origin feat/editor-codemirror
# abrir PR: gh pr create --title "..." --body "..."
```

---

## 4. 🤖 CI — GitHub Actions (`build.yml`)

**Dispara em:** push/PR para `main` · `master` · `workflow_dispatch` (manual).

**Pipeline:**
1. `actions/checkout@v4`
2. JDK 17 (Temurin) + Android SDK
3. Restaura `release.keystore` (se o secret existir)
4. `./gradlew :app:assembleRelease`
5. Upload do APK (artifact `phantom-code-apk`)

### Sem SDK local?

Correto — **nunca** rode `gradlew` localmente esperando APK. O ambiente Termux/PRoot não tem SDK Android; o build oficial é via CI. Localmente você pode editar/testar lógica pura (se um dia houver testes JVM).

### Como baixar o APK do CI

1. GitHub → repo → **Actions** → workflow mais recente
2. No fim da página, seção **Artifacts**
3. Baixar `phantom-code-apk` → descompactar → instalar no celular

---

## 5. 🔑 Assinatura do app (configurada)

O Phantom-Code tem **assinatura própria** (identidade VSFLima/Asgard). Tudo já está configurado:

| Item | Valor |
|------|-------|
| Keystore | `android/release.keystore` (RSA 4096 · validade 27 anos) |
| Alias | `phantom` |
| DN | `CN=Phantom-Code, OU=Asgard, O=VSFLima, C=BR` |
| Secrets no GitHub | ✅ 4/4 configurados (`gh secret list`) |

**Os 4 secrets do repo** (Settings → Secrets and variables → Actions):

| Secret | Conteúdo |
|--------|----------|
| `PHANTOM_KEYSTORE_BASE64` | base64 do keystore |
| `PHANTOM_STORE_PASSWORD` | senha do keystore |
| `PHANTOM_KEY_ALIAS` | `phantom` |
| `PHANTOM_KEY_PASSWORD` | senha da chave |

**⚠️ BACKUP OBRIGATÓRIO — guarde em lugar MUITO seguro (fora do repositório):**
`/root/PHANTOM-SIGNING-BACKUP.txt` (contém senhas + base64 completos).
Se perder o keystore, os APKs futuros **não poderão atualizar** os antigos (identity muda).

**Importante:**
- `android/release.keystore` está no `.gitignore` — **nunca** commitá-lo
- Para regenerar do zero (não recomendado): `bash scripts/generate-keystore.sh`
- O CI já usa os secrets automaticamente quando `PHANTOM_SIGNING=true` (setado no workflow)

---

## 6. 🚫 Arquivos ignorados

| Caminho | Por quê |
|---------|---------|
| `/linux/` · `/workspace/` · `/config/` · `/backups/` | Pastas de **runtime** (rootfs, projetos, temas do device) — não são código |
| `android/app/build/` · `.gradle/` · `local.properties` | Artefatos de build |
| `*.keystore` · `*.jks` | Chaves de assinatura (secret) |
| `*.apk` · `*.aab` | Binários |

---

## 7. 🛠️ Troubleshooting

| Problema | Solução |
|----------|---------|
| `git push` pede senha | Usar o token do `gh` como senha, ou `gh auth setup-git` |
| CI falha sem mensagem útil | Rodar `gh run view --log-failed` para ver o log completo |
| Artifact vazio | Conferir `if-no-files-found: error` no workflow (caminho do APK) |
| Quero testar sem CI | `cd android && ./gradlew :app:assembleDebug` **somente com SDK instalado** |
| Repo virou público por engano | Settings → General → Danger Zone → Change visibility → Private |

---

<div align="center">

**👻 Phantom-Code** · *Projeto proprietário · uso autorizado apenas*

</div>
