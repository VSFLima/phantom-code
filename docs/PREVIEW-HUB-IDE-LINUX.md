# 🖥️ PREVIEW HUB + INTEGRAÇÃO IDE ↔ LINUX (D24)

> **Atualizado:** 08/08/2026 · **Objetivo:** o usuário cria o código e acompanha a
> pré-visualização na hora (quando a tecnologia for compatível com o aparelho),
> usando o app, e quando o Android não suporta nativamente, o app usa o **Linux da VM**
> de forma transparente — **tudo conversando IDE + Linux**.

---

## 1. VISÃO GERAL — como o app pré-visualiza

O app tem **3 motores de pré-visualização**, escolhidos automaticamente pelo tipo de arquivo:

| Motor | O que é | Quando entra |
|-------|---------|--------------|
| **WebView direto** | HTML/CSS/JS renderizado pelo Android | site estático sem AJAX |
| **Servidor HTTP local** (`127.0.0.1:8383`) | app serve a pasta do projeto; AJAX/fetch e caminhos relativos funcionam | HTML com JS/fetch, imagens, CSS, Markdown, JSON |
| **Servidor na VM** (`127.0.0.1:8384`) | o Linux roda `php -S` / `python3 -m http.server` / `node serve`; a porta 80 do guest é exposta via `hostfwd` do QEMU | **PHP**, Python (Flask), Node/React, e qualquer coisa que o Android não roda |

### Diagrama da conversa IDE ↔ Linux

```
┌─────────────── ANDROID (app) ───────────────┐        ┌───────── LINUX (guest/QEMU) ─────────┐
│ Editor (CodeMirror 6)                        │        │                                      │
│   │ Ctrl+S → salva no workspace              │ 9p     │  /home/user/workspace ← montado     │
│   ▼                                          │ ◄────► │                                      │
│ Preview Hub (painel split no editor)         │        │  php -S 0.0.0.0:80  (PHP)          │
│   ▼                                          │        │  python3 -m http.server 80 (Python) │
│ WebView → http://127.0.0.1:8384/…            │   net  │  npx serve -l 80  (Node)           │
│                    ▲                         │  user  │                                      │
│                    │  hostfwd tcp:8384-:80   │  net0  │                                      │
│                    └─────────────────────────┼────────┼──► porta 80 do guest                 │
│                                              │        │                                      │
│ Canal de controle (virtio-serial, T20):      │  ctrl  │  phantom-agent.sh /dev/vport1p1      │
│   SERVER:<dir>|<lang>  ·  STOPSERVER        │ ◄────► │   → inicia/para o servidor web       │
│   SERVERSTATUS                                │        │                                      │
└──────────────────────────────────────────────┘        └──────────────────────────────────────┘
```

---

## 2. TIPOS DE PRÉ-VISUALIZAÇÃO (Preview Hub)

### 2.1 Já implementado (painel lateral do editor, `PreviewPane.kt`)

| Tipo | Arquivo | Como renderiza | Requisito |
|------|---------|----------------|-----------|
| **HTML/CSS/JS** | `.html` `.htm` | WebView via servidor local (URL `http://127.0.0.1:8383`) | servidor local (auto-inicia ao abrir o Preview Hub) |
| **Markdown** | `.md` `.markdown` | mini-engine JS própria (headings, listas, código, tabelas, links, imagens, blockquote) | — |
| **JSON** | `.json` | formatado (`JSON.stringify(…, 2)`) com cores de chave/string/bool | — |
| **Imagem** | `.png .jpg .jpeg .gif .svg .webp .ico` | `<img>` do servidor local | servidor local |
| **PHP** | `.php` | servidor da VM (PHP real) quando ativo; senão orientação | **VM rodando + servidor da VM** |
| **Código executável** | `.py .js .ts .sh .java .kt …` | prévia mono + orientação p/ Executar (VM) | VM p/ executar |
| **Texto** | outros | prévia fonte mono | — |

**Auto-recarga:** o preview recarrega sozinho quando o arquivo é salvo (Ctrl+S / botão salvar) — `previewTick++` no handler de save. Botão manual de refresh também.

### 2.2 No roadmap (fáceis de adicionar — mesmo mecanismo)

| Tipo | Arquivo | Como |
|------|---------|------|
| **PDF** | `.pdf` | WebView do Android renderiza PDF por URL |
| **CSV → tabela** | `.csv` | JS transforma linhas em `<table>` |
| **SVG** | já cai em imagem | — |
| **SQL** | `.sql` | realce + botão "rodar no guest" (sqlite3) |
| **Cores/CSS playground** | — | página com paleta e preview de estilo |

---

## 3. SERVIDOR HTTP LOCAL (`LocalServer.kt`)

- **Porta:** `8383` · URL `http://127.0.0.1:8383` · **sem dependências** (ServerSocket + coroutine).
- Serve a **raiz do workspace**: HTML/CSS/JS com caminhos relativos OK, `fetch()`/XHR (AJAX), imagens, fonte.
- `Access-Control-Allow-Origin: *` → CORS liberado para testes.
- **MIME** para html/css/js/json/svg/png/jpg/gif/webp/pdf/wasm/fontes/áudio/vídeo.
- Segurança: bloqueia `..` (navegação fora da raiz → 403).
- `.php` → **501** com orientação p/ usar o servidor da VM (o app não executa PHP).
- `start(workspaceRoot)` / `stop()` / `isRunning()` — singleton.

## 4. SERVIDOR NA VM (IDE ↔ LINUX) — PHP/Python/Node

### 4.1 QEMU (`QemuManager.kt`)
- Rede SLIRP com **hostfwd**: `-netdev user,id=net0,hostfwd=tcp::8384-:80`
- A porta **80 do guest** (o servidor web) aparece no app como **`http://127.0.0.1:8384`**.
- Constantes no companion: `VM_SERVER_PORT=8384`, `VM_SERVER_BASE_URL`, `GUEST_WORKSPACE=/home/user/workspace`.

### 4.2 Protocolo no guest (`phantom-agent.sh`)
Comandos novos no canal `/dev/vport1p1` (virtio-serial):

```
SERVER:<dir>|<lang>   → sobe servidor na :80 servindo <dir>
   lang=php    → php -S 0.0.0.0:80 -t .
   lang=python → python3 -m http.server 80 --bind 0.0.0.0
   lang=node   → npx --yes serve -l 80 .
   resposta: OK | ERR:<motivo>
STOPSERVER             → derruba php/http.server/serve  (pkill -f)
SERVERSTATUS           → "1" se há listener na :80, senão "0"
```

### 4.3 Cliente do canal (`PackageScanner.kt`)
- Fila serial `sendOp()`: cada comando espera UMA resposta (`OK`/`ERR:…`/`0`/`1`) antes do próximo.
- `startServer(projectDir, lang, cb)` · `stopServer(cb)` · `serverStatus(cb)`.
- `projectDir` é o caminho **no guest**: `/home/user/workspace/<projeto>`.

### 4.4 No editor (`EditorScreen.kt` → menu **Ações**)
- **Preview Hub** — abre/fecha o painel de pré-visualização (split); auto-inicia o servidor local.
- **Servidor local (VM)** — liga/desliga o servidor do guest para o projeto aberto.
  - Linguagem escolhida automaticamente: `.php`→php · `.py`→python · `.js/.ts`→node ·
    fallback: `index.php`→php, `package.json`→node, senão python.
  - Se a VM não estiver rodando → aviso "inicie no Toolbox".
- **Ver no servidor (VM)** — abre `http://127.0.0.1:8384/<arquivo>` no navegador interno.

---

## 5. STATUS

| # | Item | Status |
|---|------|--------|
| — | Servidor HTTP local (`LocalServer.kt`) | ✅ código pronto |
| — | Preview Hub: HTML/MD/JSON/imagem/texto | ✅ código pronto |
| — | Auto-recarga no salvar | ✅ código pronto |
| — | Split view (código + preview) | ✅ código pronto |
| — | `hostfwd` QEMU (8384→80) | ✅ código pronto |
| — | Protocolo SERVER/STOPSERVER/SERVERSTATUS no guest | ✅ código pronto |
| — | Cliente `PackageScanner` (fila serial) | ✅ código pronto |
| — | Menu Ações: Preview Hub · Servidor VM · Ver no servidor | ✅ código pronto |
| — | **Validar em device (Galaxy Note 10 Plus)** | ❌ depende de build |
| — | PHP instalado na Phantom? (`apt install php`) | ❌ instalar na VM / documentar |
| — | Executar código (JS/PY/SH) com saída no app | 🔶 ver PENDENCIAS — R. Executar |
| — | PDF/CSV/SQL previews | ❌ roadmap |

---

## 6. FLUXO DE USO (exemplos)

**Site PHP (ex.: `site/index.php` + AJAX):**
1. Abra o arquivo no editor → menu **Ações → Servidor local (VM)**.
2. A VM precisa estar ligada (Toolbox) e ter `php` instalado.
3. Preview Hub mostra o site rodando em `http://127.0.0.1:8384/index.php`.
4. **Ver no servidor (VM)** abre no navegador interno com botões de navegação.

**Site estático com AJAX (ex.: `app/index.html` + `fetch('dados.json')`):**
1. Abra o arquivo → **Ações → Preview Hub** (servidor local inicia sozinho).
2. O fetch resolve por `http://127.0.0.1:8383/dados.json`. Tudo relativo funciona.

**Markdown (README):** abra o `.md` → Preview Hub → renderiza com estilo do tema.
