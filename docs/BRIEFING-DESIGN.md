# Briefing de Produto — Phantom-Code 👻
### Documento de contexto para criação do novo Design (para uso com IA de design)

> **Como usar este documento:** ele descreve o produto, o que o app faz, as telas e fluxos que já existem, os estados que o design precisa cobrir e a identidade visual atual. Use como base para construir o **documento de design novo** do app. O design atual está **fora do padrão planejado pelo dono** — este redesign é esperado e aprovado.
> Código-fonte: `github.com/VSFLima/phantom-code` (público por enquanto).
>
> ⚠️ **Arquitetura — leia antes:** o motor do app é **PRoot + distro Wolfi embutida**. Versões antigas do projeto usaram **QEMU** — foi **descontinuado** e está sendo removido. Documentos antigos do repositório (README, Documento Mestre) ainda mencionam QEMU: **ignore essas menções**. Nada no design deve representar "VM/QEMU" — o conceito atual é apenas "ambiente Linux".

---

## 1. Resumo executivo

**Phantom-Code** é um **IDE completo para Android** — editor de código, explorador de arquivos, Git e um **terminal Linux real** — tudo em um único app. Dentro dele roda uma **distribuição Linux completa embutida no próprio APK** (Wolfi, glibc real, zero download no primeiro boot), onde qualquer ferramenta de linha de comando funciona de verdade: `node`, `npm`, `git`, `python`, `curl`, e principalmente **CLIs de IA** (opencode, Claude Code, Gemini CLI, Ollama…).

**Frase-âncora do produto:** *"O que o editor cria, o terminal e a IA veem na hora."*

- **Editor e terminal operam na MESMA pasta** — sem sincronização, sem cópia. O que o editor salva, a IA no terminal lê no mesmo instante.
- Não é um app de terminal: **é um IDE que possui o terminal**.
- Não precisa de root, não precisa de PC, funciona no celular.

**Nome:** Phantom-Code · **Codinome de dev:** Dark-Code · **Autor:** VSFLima / Asgard · **Versão:** 0.1.0

---

## 2. Para quem é

- Programadores que querem **programar de verdade no celular** (não só visualizar).
- Quem usa **IAs de código** (agentes/CLIs) e quer que elas enxerguem o projeto sendo editado.
- Quem trabalha com Git/GitHub fora do computador.
- Público técnico/avançado — a estética e a densidade de informação são bem-vindas.

---

## 3. Como o app funciona (motor — o que o design precisa saber)

| Peça | O que é |
|---|---|
| **Motor Linux** | **PRoot** (espaço de usuário, sem root) rodando a distro **Wolfi** — glibc real, binários originais ARM64 |
| **Distro embutida** | A distro **já vem dentro do APK** (~113 MB): node v26, npm, git, curl, nano. No **primeiro boot** ela é extraída localmente (com progresso, ~1 min). Depois disso, boot instantâneo. **Funciona offline.** |
| **Terminal** | Emulador VT100 real + **PTY de verdade** (prompt, cores, `^C`, redimensionamento). Múltiplas abas independentes. |
| **Workspace** | Uma pasta de projetos do app. Editor, Git, terminal e IA veem **os mesmos arquivos** — simultaneamente. |
| **Background** | O ambiente pode continuar rodando em segundo plano (notificação persistente "ambiente Linux ativo"). |

> Nota: o app **não usa mais QEMU** (arquitetura antiga, em remoção). O status exibido é apenas "ambiente Linux" — nada de "VM".

> Um status do ambiente Linux é exibido no app (ex.: pill verde `RUNNING`). Estados possíveis: parado, subindo, rodando, erro.

**O que o usuário consegue fazer de verdade:**
1. Criar/abrir um projeto no workspace.
2. Editar o código no editor com syntax highlight e abas.
3. Abrir o terminal — ele já abre no projeto ativo, como no VS Code.
4. Rodar `node main.js`, `npm install`, `git commit`, e **CLIs de IA** (`opencode`, `claude`, `gemini`, `ollama run …`) com acesso direto aos arquivos.
5. Clonar/commitar/pushar direto pela UI do app.
6. Ajustar tudo: temas, cores, fonte, terminal.

---

## 4. Funcionalidades completas

### 4.1 Editor
- Editor profissional embutido (motor Ace) com **abas múltiplas**, syntax highlight, números de linha.
- **Auto-save** + restauração da sessão (fecha o app e volta com os arquivos abertos).
- Preview de arquivos (HTML/Markdown/imagens).
- Paleta de comandos (Command Palette) para achar qualquer ação sem poluir a navegação.

### 4.2 Explorador de arquivos + Busca
- Árvore de arquivos do workspace com ícones por tipo.
- Long-press: novo arquivo/pasta, renomear, excluir, copiar caminho, **abrir no terminal**.
- Busca global no projeto (filtros por extensão, case, regex), e busca dentro do arquivo aberto.

### 4.3 Git / GitHub
- Clone, commit, push/pull, branches, **diff viewer** — tudo na UI (JGit).
- Autenticação com token do GitHub.
- Estado de repositório vazio / sem repo / alterações pendentes / staged.

### 4.4 Terminal
- **Abas ilimitadas na prática** — cada aba é uma sessão separada (pode rodar um servidor numa, a IA noutra, testes noutra).
- Abas com nome e **cor customizáveis** (ex.: "IA", "API", "Build").
- Bitmap de teclado do celular é limitado → o app tem **barra de teclas especiais** (Ctrl, Tab, Esc, setas, etc.).
- Personalização completa: fundo, texto, cursor, prompt, cores ANSI, fonte, densidade, histórico por aba.

### 4.5 Toolbox — central de ferramentas
- **Status do ambiente Linux** no topo (rodando/parado, uso de RAM/CPU).
- **Pacotes instalados** escaneados automaticamente do Linux, agrupados: IAs · Linguagens/Runtimes · Ferramentas · Sistema (protegidos).
- **Integrações & API Keys**: cofre de segredos (Android Keystore) para OpenAI/Anthropic/Groq/Gemini/GitHub etc., cada uma com mascara e toggle **"expor ao terminal como variável de ambiente"** (`$OPENAI_API_KEY`).
- Backups locais (ZIP) e em nuvem (Google Drive/OneDrive).

### 4.6 IA — o coração do fluxo
- **Hoje:** qualquer CLI de IA roda no terminal do guest, com o código ao vivo na mesma pasta — é o fluxo principal de trabalho.
- **Especificado (próxima fase): Phantom AI Suite** — um roteador de IAs dentro do app: várias IAs (locais e cloud) trabalhando **juntas no mesmo projeto**, com contexto compartilhado (arquivo aberto, diff Git, comandos recentes), delegação de tarefas entre IAs, e **regra de ouro: nada acontece sem aprovação do dono do projeto** (o usuário). Aparece como painel, nunca como nova aba raiz.

### 4.7 Mais
- Navegador interno (para OAuth de CLIs e logins).
- Onboarding de primeiro uso.
- Temas: 7 presets + **custom total** (color pickers) + temas de terminal independentes da UI.
- Modos de operação: **App-only (só editor)** · **App + Terminal** · **Terminal em background**. Com escolha "iniciar o Linux na abertura?" (on/off/perguntar).

---

## 5. Mapa de telas atual (o que cada uma faz)

| Tela | Conteúdo e ações |
|---|---|
| **Home / Welcome** | Status do Linux, logo, projetos recentes, botões "Clonar repositório", "Abrir projeto", "Novo arquivo/projeto"; terminal dock minimizado |
| **Workspace (Editor)** | Top bar (nome do arquivo, Run, busca no arquivo, status Linux), editor com abas, **terminal dock no rodapé** |
| **Explorador** | Árvore do workspace + ações (novo, renomear, excluir, abrir no terminal) |
| **Busca** | Busca/substituição global no projeto com filtros |
| **Git** | Changes/Staged, commit, push/pull, diff, branch atual |
| **Toolbox** | Status do ambiente, Integrações & API Keys, pacotes por categoria (cards com glow) |
| **Terminal** | Abas, sessão PTY real, barra de teclas especiais, engrenagem de aparência |
| **Settings** | Ambiente Linux (start/stop, CPU/RAM), Backup, Git/conta/SSH, Editor, Aparência & Temas, Teclado & atalhos, Sobre |
| **Onboarding** | Primeira execução, enxuto |
| **Browser interno** | OAuth/logins de CLI |

**Navegação (regra fixa do produto):**
- **Bottom Nav com 5 itens, nunca um 6º:** Explorador · Busca · Git · Toolbox · Settings.
- Funções extras vivem em **painéis/overlays** (ex.: AI Suite) ou na **Command Palette** — nunca como novo ícone na navegação.
- Activity Bar lateral fina (~50 dp, só ícones) é opcional; em tablets/landscape ganha mais presença.

---

## 6. Fluxos principais (para desenhar passo a passo)

1. **Primeiro boot** — abrir o app → extração da distro embutida **com progresso visível** → "ambiente pronto" → terminal funcional.
2. **Dia a dia** — abrir app → projeto recente → editar → terminal no cwd do projeto → rodar IA/comando.
3. **IA no fluxo** — abrir CLI de IA → ela lê o projeto → edita arquivos → editor reflete na hora → commit na UI.
4. **Clonar repo** — Home → "Clonar" → autenticação (token) → URL → pasta no workspace → abre o projeto.
5. **Instalar ferramenta nova** — terminal: `npm i -g …` ou gerenciador de pacotes → Toolbox atualiza o scan sozinha.
6. **Backup** — Settings/Toolbox → ZIP local ou nuvem → progresso → restaurar depois.
7. **Criar nova aba de terminal** — dock → "+" → nome/cor → sessão separada.
8. **API key** — Toolbox → "+ Adicionar" → nome, valor (oculto), toggle "disponível no terminal" → salva no cofre → terminal vê `$VAR`.

---

## 7. Estados que o design PRECISA cobrir

- **Ambiente Linux:** parado · subindo · rodando · erro · **extraindo a distro pela 1ª vez (com progresso)**
- **Terminal:** vazio (novo) · teclado aberto cobrindo a tela · seleção/cópia · output de erro
- **Editor:** arquivo não salvo · abas demais (scroll/overflow) · arquivo binário/imagem
- **Git:** sem repositório · sem token · diffs longos · conflito
- **Toolbox:** escaneando · categorias vazias · chave adicionada/removida
- **Busca:** resultados vazios · muitos resultados
- **Rede:** offline (tudo local funciona; só clone/cloud/fetch falham)
- **Permissões de armazenamento:** pasta padrão do app vs pasta livre escolhida (SAF)
- **Background:** app minimizado com o ambiente vivo (notificação)

---

## 8. Identidade visual atual (ponto de partida obrigatório)

**Estilo atual:** "Cyber-Phantom / Neon Dark IDE (OLED)" — preto profundo + neon roxo/cyan, bordas angulares, glow sutil. Fantasma 👻 como marca (launcher) + shield geométrico.

| Token | Cor | Uso |
|---|---|---|
| `bg-primary` | `#000000` | fundo (OLED) |
| `bg-surface` | `#121212` | painéis, cards, top bar |
| `accent-primary` | `#9F4DFF` | ações, cursor, ícones ativos |
| `accent-bright` | `#D34DFF` | hover/glow |
| `accent-secondary` | `#00FFFF` | cyan: acentos, outlines |
| `success` | `#00FF9F` | status RUNNING/commit ok |
| `error` | `#FF3366` | erros |
| `text-primary` | `#E5E7EB` | texto |
| `border` | `#4B5563` | bordas |

- **Fonte de código:** JetBrains Mono.
- **Presets de tema existentes:** Phantom · Deep Slate · Matrix · Dracula · Nord · Solarized Dark · Light Soft · Custom.
- Regras do estilo atual: fundo OLED sempre; botão primário = filled roxo angular; secundário = outlined cyan; status do Linux sempre visível (pill verde); terminal no rodapé com abas e cursor neon; nada "redondo demais" (Material arredondado é indesejado); traços de circuito apenas como atmosfera, nunca sobre o código.
- **O redesign pode evoluir isto** — o dono já declarou que o visual atual está abaixo do padrão desejado. Estes tokens são o **estado atual**, não uma camisa de força.

---

## 9. Restrições técnicas que o design deve respeitar

- **Android** (celular primeiro; minSdk 26). Kotlin + Jetpack Compose — o design será implementado nativamente.
- **Mobile-first com teclado virtual** — o teclado do sistema cobre ~40% da tela quando aberto; o design do editor e terminal deve sobreviver a isso.
- Landscape e tablets são **bem-vindos** (multi-painel), mas retrato é o caso principal.
- **O terminal é cidadão de primeira classe** — não é apêndice: dock/abas/teclas especiais fazem parte da experiência central.
- Ações destrutivas/irreversíveis (apagar projeto, remover distro, reset) pedem confirmação clara.
- O app já carrega a distro embutida (APK grande) — o primeiro boot tem **estado de extração com progresso** que precisa de design decente.
- Sem root, sem PC, sem servidor — tudo é local no aparelho.

---

## 10. Pedido direto ao design (escopo do documento a ser criado)

1. **Sistema visual completo** — cores, tipografia, espaçamento, cantos, elevação, glow, ícones, motion. Dark-first (OLED), com identidade própria "Phantom".
2. **Navegação e arquitetura de informação** — Bottom Nav fixa (5), Activity Bar/painéis, Command Palette, hierarquia entre telas.
3. **Telas redesenhadas** — Home, Workspace (editor + terminal dock), Explorador, Busca, Git, Toolbox, Terminal, Settings, Onboarding, e os **estados de boot/extração**.
4. **Componentes-chave** — cards, abas do terminal/editor, pills de status, dialogs, campos de chave secreta, dock de terminal, barra de teclas especiais, toasts/snackbars.
5. **Micro-interações** — feedback de toque, transições, loading/boot, conexão com a vibe "neon/phantom" sem sacrificar legibilidade do código.
6. **O que NÃO fazer** — poluir a navegação com novos itens raiz; visual "Material genérico"; decoração sobre o conteúdo (código/terminal são sagrados).

> O dono do produto vai complementar este briefing com **o que ele quer** — use os dois juntos para fechar o documento de design final.
