# ✅ TASKS EM ABERTO — Phantom-Code (consolidado)

> **Atualizado:** 08/08/2026 · **HEAD:** `1574da0` (build `31258209185` → SUCCESS, APK `apk-20260808-1257`) — ⚠️ **código novo não commitado** (sessão 08/08: P3.1/P3.2/P3.3/SFTP/P1.5/EXEC/Explorer/Fase B/C; commit pendente de autorização)
> **Fonte:** `TAREFAS-PHANTOM.md` + `docs/REQUESTS-USUARIO.md` + `docs/PENDENCIAS-CONSOLIDADAS.md` + `docs/TAREFA-EDITOR-IDE.md` + `docs/roteador-ias.md`
>
> Este é o **documento único e definitivo** das tarefas em aberto. Use-o como checklist. Cada task tem: o que falta, prioridade e critério de aceite.

---

## 🟥 BLOQUEADAS — exigem DEVICE ou decisão do usuário

| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| **R12** | **Polish profundo de UI** (tema no terminal + demais áreas por completo) | ⚠️ **Proibido fazer sem consultar o usuário** (lição aprendida: um polish automático foi revertido). Proposta de escopo + aprovação ANTES | Usuário aprova o escopo; visual consistente em todas as telas |
| **—** | **Apagar release órfã** `apk-20260807-2209` | Confirmar com o usuário se pode excluir a release do build de polish revertido | Release removida do GitHub Releases |
| **T22** | **Backup cloud (WebDAV)** | Código pronto (`CloudBackupManager` + UI no Toolbox). Falta: **validar num WebDAV real** (Nextcloud/ownCloud) e marcar T22 no TAREFAS | Enviar/restaurar backup via WebDAV com as chaves do catálogo |
| **T16** | **virtio-9p + virtio-serial — validação real** | Código pronto (montagem, workspace 9p, sockets). Validação real depende de rodar a VM no device e conferir: workspace montado no guest, console via socket, canal de controle | Workspace aparece em `/home/user/workspace` no guest; SCAN/RUN respondem |

## 🟨 CÓDIGO EXECUTÁVEL (podem ser feitas por IA, sem device)

### GIT / CLOUD
| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| ~~**P3.3**~~ | ~~**GitHub colaborativo**~~ ✅ **feito** | Issues/PRs via API (`githubIssues`/`githubPrs`), release download por botão (`downloadReleaseAsset`) | GitScreen mostra status de equipe (issues/PRs) e downloads de releases por botão |
| ~~**P2.2+**~~ | ~~**SFTP (JSch)**~~ ✅ **feito** | `SftpClient.kt` com chaves do catálogo (`sftp_host`/`sftp_user`/`sftp_pass`, aceita `user@host:porta`), menu "Upload SFTP" no editor, keep rules do JSch | Upload/download SFTP funcionando com credenciais do catálogo |

### EDITOR / IDE
| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| ~~**P3.2**~~ | ~~**Temas unificados — completar**~~ ✅ **feito** | Cursor (blink-block/underline/bar/block) e cor de seleção configuráveis via `EditorPrefs` + menu Ações; persistem | Settings/Editor permitem trocar cursor e cor de seleção; persistem |
| ~~**P3.1**~~ | ~~**Preview Hub — completar**~~ ✅ **feito** | **PDF/CSV/SQL** no PreviewPane; **PHP instalado na Phantom** (`php-cli php-curl` no `build-distros.yml`); PDF via LocalServer + "Abrir externamente" | Preview abre PDF/CSV/SQL; servidor PHP da VM responde no navegador interno |
| ~~**P1.5**~~ | ~~**Terminal integrado — modo split**~~ ✅ **feito** | `SplitEditorPane` com WebView/bridge próprios, auto-save 800 ms, split tem precedência sobre o preview; menu "Split view" e "Abrir no split" | Botão no editor abre terminal split sem sair da edição |
| ~~**—**~~ | ~~**Executar código na VM**~~ ✅ **feito** | Protocolo `EXEC:` no `phantom-agent.sh` (EXEC-BEGIN/END, 8 KB); `PackageScanner.exec()` com timeout 20 s; menu "Executar no guest" + diálogo de saída (copiar/fechar) | Rodar `python3 x.py` do editor → saída aparece no app |
| ~~**—**~~ | ~~**Novo arquivo/pasta + mover/copiar no explorer lateral do editor**~~ ✅ **feito** | Botões `Add`/`CreateNewFolder` no header, context menu "Mover para…"/"Copiar para…" com `FolderPickerDialog` | Menu de contexto do explorer lateral igual ao do Explorer standalone |

### VM / DISTROS
| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| **T29** | **Publicar Ubuntu/Debian/Alpine/Kali no catálogo** | Hoje só a Phantom (`distro-phantom`) é real; as demais usam `example.com`. Workflow `build-distros.yml` precisa gerar os tarballs + publicar releases + atualizar `DistroCatalog` (urls/SHAs/tamanhos) | Catálogo mostra 4+ distros reais baixáveis; instalação automática de pelo menos 1 adicional validada |
| ~~**—**~~ | ~~**Instalar PHP na Phantom**~~ ✅ **feito** | `php-cli php-curl` adicionados ao `apt-get install` do `build-distros.yml` (pré-requisito do servidor VM) | `php -v` responde no terminal do guest; Preview Hub VM serve `.php` |

### AI SUITE (docs/roteador-ias.md)
| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| ~~**Fase B**~~ | ~~**UI de conversa entre IAs**~~ ✅ **feito** | `AiSuiteManager`: tarefas (`tasks/<id>/` com context.json/messages.jsonl/proposal.json), propostas de delegação com **Aprovar/Ajustar/Recusar** (R4), threads append-only (R5) com intervenção do dono; guest: `delegate`/`approved`/`rejected`/`owner_msg` no `phantom-router.sh` | Usuário vê as conversas entre IAs e aprova/nega cada delegação |
| ~~**Fase C**~~ | ~~**Integração com runners reais no guest**~~ ✅ **feito (executável)** | Botões "Rodar no guest" por agente e por tarefa aprovada (via `scanner.exec`); `create_task`/`scan` (PHANTOM-IA-HELLO) no `phantom-router.sh`; aviso de lock no Editor (Fase C do roteador) | IA do guest executa tarefa real com lock de arquivo + aprovação |

---

## 📊 RESUMO POR STATUS

| Status | Qtd | Tasks |
|---|---|---|
| 🟥 Bloqueada (device/decisão) | 4 | R12, release órfã, T22, T16 |
| 🟨 Código executável | 1 | T29 (publicar distros) |
| ✅ **Concluídas na sessão** | **10** | P3.1, P3.2, P3.3, P2.2+ (SFTP), P1.5, Executar VM, EditorExplorer, PHP na Phantom, Fase B, Fase C |
| **Total em aberto** | **5** | (4 bloqueadas + T29) |

## 🎯 PRÓXIMOS PASSOS RECOMENDADOS (ordem)

1. **Código:** T29 (publicar distros — precisa autorização para rodar o workflow no GitHub)
2. **Device:** validar tudo no Galaxy Note 10 Plus quando disponível (T22 WebDAV, T16 virtio, previews, SFTP)
3. **UI:** qualquer polish só após aprovação do usuário (R12)

---
*Este documento substitui a leitura de 4 arquivos para saber o que falta. Manter atualizado a cada commit.*
