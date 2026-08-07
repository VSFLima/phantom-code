# 🤖 PHANTOM AI SUITE — Roteador de Comunicação entre IAs

> **Especificação técnica** · Phantom-Code v1 · 07/08/2026
> **Codinome:** "Router" / "Phantom Hub"
> **Status:** 📐 Especificação — aguardando implementação

---

## 1. Visão geral

O **Phantom AI Suite** é um **roteador de comunicação entre IAs** que roda dentro do
ecossistema Phantom-Code (app Android + Linux guest via QEMU). Ele permite que **todas as
IAs instaladas no Linux** (Ollama local, llama.cpp, CLIs de cloud como OpenAI/Claude/Groq/
Gemini, ou qualquer CLI/daemon de IA) trabalhem **juntas no mesmo projeto** com:

- **Contexto compartilhado** — todas enxergam o mesmo estado do trabalho (arquivos abertos,
  diffs Git, decisões, comandos recentes).
- **Delegação inteligente** — cada IA delega subtarefas à IA mais qualificada para aquilo.
- **Zero conflito de arquivos** — regras rígidas de reserva (lock) impedem duas IAs de
  mexerem no mesmo arquivo/área ao mesmo tempo.
- **Aprovação humana obrigatória** — **nenhuma delegação acontece sem o dono do projeto
  (o usuário) confirmar**.
- **Conversação entre IAs** — threads de discussão por tarefa, visíveis e auditáveis pelo
  usuário, que pode intervir a qualquer momento.

> O usuário é **sempre o dono do projeto**. As IAs são operárias que propõem, o dono decide.

---

## 2. Componentes da arquitetura

```
┌────────────────────────────── ANDROID APP ──────────────────────────────┐
│                                                                          │
│  Toolbox → seção IAs        AI Suite (painel/overlay)   Command Palette  │
│        │                          │                          │           │
│        ▼                          ▼                          ▼           │
│  ┌───────────────┐   ┌──────────────────────────────┐                   │
│  │ AgentRegistry │◄──│     AiSuiteManager (Kotlin)   │                   │
│  │ (perfil IAs)  │   │  router · tarefas · aprovações│                   │
│  └───────────────┘   │  ConflictGuard (locks)        │                   │
│                      │  Threads de conversação       │                   │
│                      └──────────────┬───────────────┘                   │
└─────────────────────────────────────┼───────────────────────────────────┘
                                      │ virtio-serial (vport1p1 — canal CTRL)
┌─────────────────────────────────────┼────────────── LINUX GUEST ─────────┐
│                                     ▼                                    │
│  ┌──────────────────────┐   ┌────────────────────────────────────────┐   │
│  │  phantom-agent.sh    │   │  phantom-router.sh  (daemon do guest)  │   │
│  │  (PING/SCAN/RUN)     │◄──│  - recebe ROUTER:<json>                │   │
│  └──────────────────────┘   │  - roteia para as IAs locais (CLIs)    │   │
│                             │  - fala com o Shared Context Bus       │   │
│                             └───────────────┬────────────────────────┘   │
│                                             │                            │
│        ┌────────────────────────────────────┼────────────────────┐       │
│        │  workspace/.phantom/ai-suite/      │                    │       │
│        │  ├─ state.json        (estado)     │   ┌────────────┐   │       │
│        │  ├─ agents.json       (cadastro)   │   │ IA LOCAL   │   │       │
│        │  ├─ locks.json        (reservas)   │   │ (ollama…)  │   │       │
│        │  ├─ tasks/<id>/       (por tarefa) │   └────────────┘   │       │
│        │  │   ├─ context.json                │   ┌────────────┐   │       │
│        │  │   ├─ messages.jsonl (thread)    │   │ IA CLOUD   │   │       │
│        │  │   └─ diffs/        (versões)    │   │ (CLI/HTTP) │   │       │
│        │  └─ inbox/<agent>.jsonl (fila)     │   └────────────┘   │       │
│        └────────────────────────────────────┴────────────────────┘       │
└────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Os 6 componentes

| Componente | Papel | Onde vive |
|---|---|---|
| **Agent Registry** | Cadastro de cada IA: nome, provedor, modelo, capacidades, status (online/offline), chave usada, "especialidade" | App (Kotlin) + `agents.json` |
| **AI Router** | Cérebro: recebe pedidos, escolhe a IA certa (por tipo de tarefa), roteia delegações, orquestra multi-agent | App (Kotlin) + daemon do guest |
| **Agent Runner** | Adaptador que transforma **qualquer CLI** em participante do bus: lê a `inbox/`, chama a CLI com o contexto, publica a resposta na thread | Guest (`phantom-ia-agent`) |
| **Shared Context Bus** | Memória compartilhada do projeto: arquivos abertos, seleções, diff Git, comandos recentes, decisões, estado das tarefas | `workspace/.phantom/ai-suite/` |
| **Conflict Guard** | Gestor de reservas (locks): por arquivo, pasta e área correlata; fila, TTL, prioridade, deadlock | App (Kotlin) + `locks.json` |
| **Human Approval Gate** | Toda delegação recebida exige **aprovação do usuário**; card/notificação no app | App (Kotlin) |
| **Message Bus** | Conversação entre IAs: threads por tarefa, mensagens append-only, menções, histórico | `messages.jsonl` + canal CTRL |

### 2.4 Agent Runner — como uma CLI vira participante do bus

A maioria das IAs no guest são **CLIs de uma invocação** (`ollama run`, `claude -p`,
`codex`, `aider`) — não mantêm daemon falando o protocolo. O **Agent Runner**
(`phantom-ia-agent`, script no guest) embrulha qualquer CLI e a transforma em agente:

1. **Lê a inbox** (`inbox/<agent>.jsonl`) aguardando mensagens dirigidas a ele.
2. **Monta o prompt** = mensagem + contexto resumido da tarefa (`context.json` truncado)
   + regras de trabalho (escopo, locks que possui).
3. **Invoca a CLI** (`invoke` do `agents.json`) com o prompt via stdin.
4. **Intercepta a saída** e interpreta ações estruturadas que a CLI emitir:
   - `[LOCK] path` / `[RELEASE] path` → negocia locks com o Router;
   - `[WRITE] path` → escreve **só se tiver lock** (prevenção R1);
   - `[MSG] texto` → publica na thread;
   - `[DONE] resumo` → fecha a tarefa com diff.
5. **Publica a resposta** na thread (`messages.jsonl`) e volta a escutar a inbox.

> Sem o Agent Runner, o protocolo não funciona: **é a peça central da Fase A**.

### 2.2 Como uma IA é "registrada"

O registro pode ser automático ou manual:

1. **Automático:** o `phantom-agent.sh` já escaneia IAs instaladas (`ollama llama-server
   gpt4all whisper-cli whisper-cpp`). O Router amplia o scan e registra cada uma com perfil.
2. **Manual (UI):** Toolbox → IAs → "Adicionar IA": nome, tipo (local/cloud), comando de
   invocação (ex.: `ollama run codellama` / `claude -p`), capacidades (checkbox), chave
   vinculada (do SecretsManager, nunca em texto plano no JSON — só referência `$OPENAI_API_KEY`).
3. **Auto-registro:** qualquer CLI que implementar o protocolo `PHANTOM-IA` (ver §6.4) se
   anuncia sozinha no bus.

**Exemplo de `agents.json`:**
```json
{
  "agents": [
    {
      "id": "ollama-codellama",
      "name": "CodeLlama (local)",
      "type": "local",
      "invoke": "ollama run codellama",
      "status": "online",
      "skills": { "code": 5, "review": 4, "docs": 3, "shell": 2, "test": 3 },
      "key_ref": null
    },
    {
      "id": "claude",
      "name": "Claude (cloud)",
      "type": "cloud",
      "invoke": "claude -p",
      "status": "online",
      "skills": { "code": 5, "review": 5, "docs": 5, "shell": 3, "test": 4, "architecture": 5 },
      "key_ref": "$ANTHROPIC_API_KEY"
    }
  ]
}
```

---

## 3. Regras de trabalho (as "regras de ouro")

> Estas regras são **imutáveis** e aplicadas pelo Conflict Guard **antes de qualquer ação
> de escrita**. Nenhuma IA pode contorná-las.

### R1 — Regra da reserva (write lock)
**Nenhuma IA escreve, renomeia ou apaga arquivo sem antes reservá-lo.**
- Reserva = pedido `LOCK:<path>` ao Router → resposta `LOCKED` (ou `DENIED`).
- Sem lock de escrita, o máximo permitido é **leitura**.
- Escrita sem lock é **violação**. Há duas camadas de enforcement:
  1. **Prevenção (para IAs via runner):** o Agent Runner (§2.4) só executa escrita sob lock —
     tentativa fora do lock é recusada antes de tocar o arquivo.
  2. **Detecção (qualquer outro processo):** o daemon observa o workspace (inotify) e
     loga a violação em `audit.jsonl` + notifica o usuário (impossível bloquear processo
     arbitrário — detecta e audita).

### R2 — Regra da área correlata (soft lock)
Ao reservar um arquivo, o Router calcula a **área correlata**:
- mesmo diretório;
- arquivos **importados/referenciados** pelo arquivo reservado (imports, requires);
- arquivos que o **importam** (dependências reversas — via grep no projeto);
- arquivos de config do módulo (`package.json`, `build.gradle.kts`, `Cargo.toml`, etc.).

Esses arquivos ficam em **soft-lock**: leitura permitida, escrita proibida para **outras** IAs
enquanto a tarefa da reserva estiver ativa. A IA dona da reserva pode escrever.

### R3 — Regra da tarefa (task scoping)
Cada IA trabalha **dentro do escopo da sua tarefa**:
- a tarefa declara quais arquivos pode tocar (`scope.files`);
- tocar arquivo fora do escopo = recusa automática do daemon + aviso;
- exceção: se a IA pedir **expansão de escopo** ao Router → vira nova proposta de delegação
  (passa pela aprovação do usuário, §5).

### R4 — Regra do dono (human approval)
**Nenhuma delegação entre IAs é executada sem confirmação do usuário.** (§5)
- O usuário pode **Aprovar**, **Ajustar** (editar o escopo/pedido) ou **Recusar**.
- Timeout padrão de aprovação: **30 min** → expirado = **recusado automaticamente**.
- O usuário pode configurar "auto-aprovar" por IA/origem (opt-in explícito).

### R5 — Regra da conversação
- Toda comunicação entre IAs é **append-only** (nunca sobrescreve) e vive na thread da tarefa.
- Mensagens têm autor, timestamp e id únicos — **nada é editável/apagável** (auditoria).
- O usuário vê tudo (modo observador) e pode escrever: **mensagens do usuário têm prioridade
  máxima** e pausam a tarefa em execução.

### R6 — Regra da reversibilidade
- Toda escrita gera um **diff** antes/depois (cópia da versão anterior em `diffs/`).
- Conflito (dois diffs no mesmo arquivo) → **merge assistido** ou **rollback** — nunca silencioso.
- Nada é perdido: o estado anterior fica em `workspace/.phantom/ai-suite/tasks/<id>/diffs/`.

### R7 — Regra do TTL e da IA morta
- Todo lock tem **TTL** (padrão 15 min).
- **Renovação automática:** enquanto a IA mantiver heartbeat recente (< 2 min), o lock é
  renovado sozinho — a IA não precisa "lembrar" de renovar durante um comando longo.
- TTL expirado (IA travou/crashou/sem heartbeat) → lock liberado automaticamente.
- IA sem resposta por 5 min é marcada `offline` e perde as reservas.

### R8 — Regra do contexto
- Antes de agir, a IA **deve** ler o contexto da tarefa no bus (`context.json`) —
  nunca trabalha "no escuro".
- Se o contexto mudou desde a última leitura (outra IA fez commit/merge), o Router avisa
  (`STALE_CONTEXT`) e a IA precisa reler antes de escrever.

---

## 4. Tipos de reserva (locks)

| Tipo | Símbolo | O que permite | O que bloqueia para outros |
|---|---|---|---|
| **Escrita (write)** | `W` | editar/criar/apagar/renomear o arquivo | leitura + escrita (outros veem versão "em edição") |
| **Leitura (read)** | `R` | ler o arquivo | escrita |
| **Soft (correlato)** | `S` | ler | escrita (via R2) |
| **Área (diretório)** | `D` | operar na pasta | criar/editar/apagar arquivos da pasta (exceto sub-escopos delegados) |
| **Git (merge/commit)** | `G` | commit, merge, push | qualquer escrita + outros commits (fila única) |

**Política:**
- Uma IA pode pedir vários locks (sempre na ordem que declarar; deadlock evitado por
  **timeout de aquisição** e **ordem canônica** de paths ordenados).
- Lock de `G` é exclusivo global (só um commit por vez).
- O usuário pode **preempir** qualquer lock (ele é o dono).

**Sincronização (quem é a fonte da verdade):**
- **Autoridade única: o app (Kotlin)** — o `ConflictGuard` no app é dono do `locks.json`.
- O daemon do guest **nunca escreve** `locks.json` direto — sempre pede via `ROUTER:<json>`
  e recebe `lock_ack`/`lock_den`. Sem isso, dois processos corromperiam o arquivo e
  quebrariam a exclusividade.
- No boot, o app **limpa locks órfãos** (sessões anteriores / restore de backup) —
  estado de reservas sempre parte do zero.

---

## 5. Fluxo de delegação com aprovação do dono

```
IA-A quer delegar para IA-B (ex.: "gera os testes desta função que escrevi")

 1. IA-A envia DELEGATE ao Router
    { to: "IA-B", subtask: "criar testes para src/api.ts",
      scope: { files: ["src/api.test.ts"], read: ["src/api.ts"] },
      reason: "especialista em testes", due: "2h", context_ref: "t-42" }

 2. Router valida
    - IA-B existe e está online?   → senão: RECUSADO (motivo: offline)
    - locks em conflito?           → senão: enfileira até liberar
    - escopo dentro do projeto?    → validação de path (sem /etc, /proc, etc.)
    - gera proposta (id único) + contexto resumido da tarefa

 3. Human Approval Gate — NOTIFICA O DONO (UI)
    ┌───────────────────────────────────────────────┐
    │ 🤖 Proposta de delegação                      │
    │ CodeLlama (local) → Claude (cloud)            │
    │ "criar testes para src/api.ts"  ·  prazo 2h   │
    │ Escopo: src/api.test.ts (escreve)             │
    │         src/api.ts (leitura)                  │
    │ Contexto: 3 commits, T-42 em andamento        │
    │        [ ✅ Aprovar ] [ ✏️ Ajustar ] [ ❌ Recusar ] │
    └───────────────────────────────────────────────┘

 4. DONO aprova (ou ajusta o escopo e aprova)

 5. Router:
    - cria a tarefa filha (t-43) com locks concedidos a IA-B
    - notifica IA-B (mensagem na inbox dela + via daemon)
    - loga a delegação na thread de t-42

 6. IA-B executa, reporta DONE com resumo + diff
    - Router integra o diff (R6), atualiza o bus

 7. IA-A recebe o resultado e continua a tarefa
    - mensagem de retorno + fechamento da delegação

 8. Se IA-B recusar/errar → IA-A escolhe: outra IA, ou pede ajuda ao dono

**Regras do portão humano:**
- Timeout de aprovação: **30 min** → expirado = **recusado** (e notificado).
- Se o dono clicar **Ajustar**, o timeout **recomeça** com o novo escopo.
- **Auto-aprovar:** toggle **nunca ligado por padrão**; o dono pode habilitar por IA/origem
  após confiar nela (histórico de N aprovações).
- **App em background:** a proposta dispara **notificação Android real** (canal próprio,
  via ForegroundService T23) — o dono decide mesmo com o app minimizado; se o app ficar
  fora por muito tempo, o timeout padrão vale.
```

---

## 6. Protocolo de comunicação

### 6.1 Canal principal (app ↔ guest)
Reutiliza o **virtio-serial** já existente (`/dev/vport1p1`, o canal CTRL do
`phantom-agent.sh`), estendendo o protocolo com o comando:

```
ROUTER:<json-linha>
```

O daemon `phantom-router.sh` (no guest) e o `AiSuiteManager` (no app) trocam mensagens
JSON de **uma linha** (sem quebra de linha no payload).

### 6.2 Mensagem padrão (JSON Schema)
```json
{
  "v": 1,
  "ts": 1754553600,
  "id": "m-000123",
  "from": "ollama-codellama",
  "to": "router",
  "type": "delegate",
  "task_id": "t-42",
  "reply_to": "m-000100",
  "payload": { },
  "refs": ["src/api.ts"]
}
```

| Campo | Obrigatório | Descrição |
|---|---|---|
| `v` | sim | versão do protocolo (1) |
| `ts` | sim | unix timestamp |
| `id` | sim | uuid único (gerado por quem envia) |
| `from` / `to` | sim | `router` ou id de IA |
| `type` | sim | ver §6.3 |
| `task_id` | sim | tarefa à qual a mensagem pertence |
| `reply_to` | não | id da mensagem respondida |
| `payload` | sim | conteúdo específico do tipo |
| `refs` | não | arquivos/caminhos citados |

### 6.3 Tipos de mensagem

| type | Quem envia | Payload | Uso |
|---|---|---|---|
| `hello` | IA | `{skills, capabilities}` | auto-registro |
| `heartbeat` | IA | `{}` | keep-alive (5 min) |
| `ask` | IA | `{question, context}` | pergunta ao Router/contexto |
| `delegate` | IA | `{to, subtask, scope, reason, due}` | proposta de delegação (§5) |
| `approval_req` | Router | `{proposal_id, from, to, summary}` | notifica o app → card do dono |
| `approved` / `rejected` | Router | `{proposal_id}` | resposta do dono |
| `lock_req` | IA | `{path, mode: W/R/D/G, ttl}` | pedido de reserva |
| `lock_ack` / `lock_den` | Router | `{path, mode, until}` | resultado do lock |
| `release` | IA | `{paths}` | libera locks |
| `stale` | Router | `{path, changed_at}` | contexto desatualizado (R8) |
| `msg` | IA | `{text, to?}` | conversa na thread |
| `done` | IA | `{summary, diff_ref}` | tarefa concluída |
| `error` | qualquer | `{code, message}` | erro |
| `cancel` | dono/Router | `{task_id, reason}` | cancelamento (prioridade máxima) |

### 6.4 Auto-registro de IAs (protocolo opcional `PHANTOM-IA`)
Qualquer CLI pode se anunciar respondendo ao comando:
```
PHANTOM-IA-HELLO
→ {"id":"...","name":"...","skills":{...},"invoke":"..."}
```
O daemon do guest envia `hello` em uma lista conhecida (`ollama`, `claude`, `codex`,
`aider`, `gpt4all`, `llama-server`) e registra quem responder.

> **Unificado com o protocolo:** o `hello` do §6.3 **é** o auto-registro — a resposta ao
> `PHANTOM-IA-HELLO` usa exatamente o formato `hello` do protocolo (mesmo payload).

---

## 7. Shared Context Bus — o que entra no contexto

| Fonte | Como entra | Frequência |
|---|---|---|
| Arquivo ativo + seleção | JSON com path + trecho | ao abrir/editar |
| Árvore do projeto | resumo (dirs/arquivos, sem binários) | ao iniciar projeto |
| Diff Git atual | `git diff` resumido (stat + hunks) | ao salvar/commit |
| Últimos comandos do terminal | últimos N (path do comando + saída truncada) | contínuo |
| Decisões das IAs | mensagens das threads | contínuo |
| Estado das tarefas | tasks/*/context.json | contínuo |
| Secrets | **nunca** — só referência `$OPENAI_API_KEY` | — |

**Rotação:** o `context.json` de cada tarefa mantém no máximo os últimos 100 eventos ou 200 KB;
eventos antigos vão para `archive/`.

---

## 8. Ciclo de vida da tarefa

```
proposed → pending_approval → approved → in_progress ⇄ awaiting_review
                                 ↓              ↑              ↓
                             cancelled        (novo pedido)  review
                                                              ↓  ↖ (changes_requested)
                                                    done | merged | rejected
```

- `proposed` — rascunho criado pela IA
- `pending_approval` — aguarda o dono (timeout 30 min → `rejected`)
- `approved` — dono aprovou; locks concedidos
- `in_progress` — dono da tarefa tem locks de escrita do escopo
- `awaiting_review` — IA pede revisão (de outra IA ou do dono)
- `review` — revisor lê (lock R) e responde `approved` ou `changes_requested`
- `changes_requested` → volta para `in_progress` (a IA ajusta e reenvia)
- `done` / `merged` — integrado e logado
- `rejected` / `cancelled` — recusado pelo dono ou cancelado

---

## 9. Conversação entre IAs (exemplo real)

**Tarefa t-42:** "Implementar endpoint `/search`"

```
[router] t-42 criada · escopo: src/api.ts, src/api.test.ts · dono: CodeLlama
[CodeLlama] Vou implementar o endpoint. src/api.ts reservado (W). 🔒
[CodeLlama] msg→Claude: vou codar a rota; depois você revisa o schema, @claude
[claude] msg: ok, enquanto isso reviso o schema em src/schema.ts (soft-lock R2) ✓
[CodeLlama] done: rota implementada, diff em t-42/diffs/001.patch
[router] diff integrado · aviso: src/api.ts foi lido por claude (R) — sem conflito
[claude] msg: vi o diff; sugiro tratar erro de query vazia — quer que eu faça?
[router] ⏸ PROPOSTA de delegação (claude → CodeLlama) — aguardando o DONO...
[dono] ✅ Aprovado
[CodeLlama] ok, aplico a sugestão e rodo os testes
[CodeLlama] done: t-42 concluída · 3 arquivos · testes verdes ✅
```

---

## 10. Implementação (faseamento)

### Fase A — Fundação (Rota mínima viável)
1. **`AiSuiteManager.kt`** (app): estado da suite, lista de agentes, registro local.
2. **`ConflictGuard.kt`** (app): locks em memória + `locks.json`; R1/R2/R7/R8.
3. **`phantom-router.sh`** (guest): daemon que estende `phantom-agent.sh`
   (`ROUTER:<json>` + filas `inbox/<agent>.jsonl`).
4. **Protocolo base**: `hello`, `heartbeat`, `lock_req/ack/den`, `release`, `msg`, `done`, `error`.
5. **UI**: Toolbox → seção IAs → "Abrir AI Suite" (painel com agents, locks, threads).

### Fase B — Delegação com aprovação humana
6. **`delegate` / `approval_req` / `approved` / `rejected`** no protocolo.
7. **Cards de aprovação** no app (push/notificação in-app) com Aprovar/Ajustar/Recusar.
8. Tasks (`tasks/<id>/`) com `context.json` + `messages.jsonl` + `diffs/`.
9. UI de thread (chat entre IAs, modo observador, intervenção do dono).

### Fase C — Roteamento inteligente + multi-agent
10. **Router automático** por tipo de tarefa (skills × tarefa: code/review/docs/shell/test).
11. **Modo multi-agent**: `initiate` → `plan` → `implement` → `review` → `test` → `merge`
    (cada etapa na IA mais forte, sempre passando por aprovação na delegação).
12. **Merge assistido** (R6) e auditoria (`audit.jsonl`).

---

## 11. Segurança e limites

- **Sandbox de paths:** escopo só dentro de `workspace/`; bloqueio de `/etc`, `/proc`,
  `/dev`, `/root`, `.ssh`, secrets.
- **Comandos shell** vindos de IAs passam por **whitelist concreta** — o `RUN:` atual do
  `phantom-agent.sh` usa `eval`; o Router **proíbe eval de payload**. Lista inicial:
  `git, ls, cat, head, tail, grep, rg, find, sed (somente -e), awk, jq, python3, node,
  npm, ollama, claude, codex, aider` — qualquer outro comando exige aprovação do dono.
- **Secrets nunca no bus** — só `$NOME_DA_VARIAVEL` (SecretsManager continua a fonte).
- **Data residency:** toggle **por agente** de "pode ver o workspace / tem acesso à rede" —
  o dono decide se uma IA cloud lê o código do projeto (proteção contra exfiltração).
- **Custo:** orçamento de tokens/custo por tarefa (limite default configurável) para IAs
  cloud — a IA para e pede autorização ao dono ao atingir o teto.
- **Quotas:** máximo de 3 IAs ativas em paralelo (configurável); fila de espera para o resto.
- **Auditoria:** `audit.jsonl` imutável com toda ação de escrita (quem, o quê, quando, hash).
- **Kill switch:** botão "Pausar todas as IAs" no AI Suite libera todos os locks e congela
  a suite até o dono reativar.

---

## 12. Decisões em aberto (para validar com o dono)

1. **Onde fica o Router "de verdade"?** — proposta: a **autoridade de locks e aprovações fica
   no app (Kotlin)** — é quem tem a UI do dono; o daemon do guest só roteia localmente e faz
   as operações de escrita sob lock. Alternativa: tudo no guest com ponte de UI.
2. **Auto-aprovar por IA?** — sugestão: nunca por padrão; toggle por IA depois de X
   aprovações do dono.
3. **Conversa entre IAs assíncrona ou síncrona?** — proposta: **assíncrona via threads**
   (mensagens), com a opção de "chamada" síncrona (`reply_to` com timeout) para subtarefas
   curtas.
4. **Integração com o Git nativo (JGit)** — commits feitos por IAs devem entrar na mesma
   fila `G` do usuário (sim) e sempre via **branches**? (proposta: branch `ai/<task>` +
   PR local revisado pelo dono antes do merge em `main`).

---

## 13. Glossário

| Termo | Definição |
|---|---|
| Agent | Uma IA registrada (local ou cloud) com perfil de skills |
| Router | Orquestrador de quem faz o quê |
| Bus | Contexto compartilhado do projeto |
| Lock | Reserva de arquivo/área (W/R/S/D/G) |
| Soft-lock | Área correlata: lê, não escreve (R2) |
| Delegação | Proposta de uma IA pedir que outra faça algo |
| Approval Gate | Portão humano: nenhuma delegação sem o dono |
| Thread | Conversa de uma tarefa (messages.jsonl) |
| Task | Unidade de trabalho com escopo, dono e ciclo de vida |
| Agent Runner | Adaptador que embrulha uma CLI para participar do bus (inbox → CLI → resposta) |
| Data residency | Política por agente: o que ele pode ver/acessar (rede, workspace) |
