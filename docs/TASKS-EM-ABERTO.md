# ✅ TASKS EM ABERTO — Phantom-Code (consolidado)

> **Atualizado:** 08/08/2026 · **HEAD:** `1574da0` (build `31258209185` → SUCCESS, APK `apk-20260808-1257`)
> **Fonte:** `TAREFAS-PHANTOM.md` + `docs/REQUESTS-USUARIO.md` + `docs/PENDENCIAS-CONSOLIDADAS.md` + `docs/TAREFA-EDITOR-IDE.md` + `docs/roteador-ias.md`
>
> Este é o **documento único e definitivo** das tarefas em aberto. Use-o como checklist. Cada task tem: o que falta, prioridade e critério de aceite.

---

## 🟥 BLOQUEADAS — exigem DEVICE ou decisão do usuário

| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| **T25** | **Testes em device (Galaxy Note 10 Plus)** | Instalar o APK novo (`apk-20260808-1257`) e validar: instalação da Phantom, terminal (teclado abre), QEMU auto-início, Git (baixar projeto traz todos os arquivos), editor (autocomplete/folding/explorer/teclado prog.), Preview Hub, export ZIP | Todos os fluxos acima funcionam no device; bugs encontrados viram novas tasks |
| **R12** | **Polish profundo de UI** (tema no terminal + demais áreas por completo) | ⚠️ **Proibido fazer sem consultar o usuário** (lição aprendida: um polish automático foi revertido). Proposta de escopo + aprovação ANTES | Usuário aprova o escopo; visual consistente em todas as telas |
| **—** | **Apagar release órfã** `apk-20260807-2209` | Confirmar com o usuário se pode excluir a release do build de polish revertido | Release removida do GitHub Releases |
| **T22** | **Backup cloud (WebDAV)** | Código pronto (`CloudBackupManager` + UI no Toolbox). Falta: **validar num WebDAV real** (Nextcloud/ownCloud) e marcar T22 no TAREFAS | Enviar/restaurar backup via WebDAV com as chaves do catálogo |
| **T16** | **virtio-9p + virtio-serial — validação real** | Código pronto (montagem, workspace 9p, sockets). Validação real depende de rodar a VM no device e conferir: workspace montado no guest, console via socket, canal de controle | Workspace aparece em `/home/user/workspace` no guest; SCAN/RUN respondem |

## 🟨 CÓDIGO EXECUTÁVEL (podem ser feitas por IA, sem device)

### GIT / CLOUD
| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| **P3.3** | **GitHub colaborativo** (roadmap) | Clones/releases/publicação visuais, status de equipe via API | GitScreen mostra status de equipe (issues/PRs) e downloads de releases por botão |
| **P2.2+** | **SFTP (JSch)** | Hoje só FTP (commons-net). Adicionar `FtpClient` SFTP com as chaves do catálogo | Upload/download SFTP funcionando com credenciais do catálogo |

### EDITOR / IDE
| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| **P3.2** | **Temas unificados — completar** | Fonte ✅ feito (picker no editor). Falta: **cursor** (blink/estilo) e **seleção** configuráveis | Settings/Editor permitem trocar cursor e cor de seleção; persistem |
| **P3.1** | **Preview Hub — completar** | Falta: **PDF/CSV/SQL** no PreviewPane; **PHP instalado na Phantom** (para o servidor da VM); validar servidor local (AJAX) e servidor VM (hostfwd 8384) | Preview abre PDF/CSV/SQL; servidor PHP da VM responde no navegador interno |
| **P1.5** | **Terminal integrado — modo split** (opcional) | Terminal junto ao editor em tela dividida (editor+terminal lado a lado) | Botão no editor abre terminal split sem sair da edição |
| **—** | **Executar código na VM** | Extensão do protocolo `SERVER:/RUN:`: JS/PY/SH executado no guest com **saída de volta no app** (console/terminal) | Rodar `python3 x.py` do editor → saída aparece no app |
| **—** | **Novo arquivo/pasta + mover/copiar no explorer lateral do editor** | O Explorer standalone já tem (✅ feito em `a0103b1`); o `EditorExplorer` (lateral) só tem abrir/renomear/duplicar/excluir — falta criar/mover/copiar/baixar | Menu de contexto do explorer lateral igual ao do Explorer standalone |

### VM / DISTROS
| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| **T29** | **Publicar Ubuntu/Debian/Alpine/Kali no catálogo** | Hoje só a Phantom (`distro-phantom`) é real; as demais usam `example.com`. Workflow `build-distros.yml` precisa gerar os tarballs + publicar releases + atualizar `DistroCatalog` (urls/SHAs/tamanhos) | Catálogo mostra 4+ distros reais baixáveis; instalação automática de pelo menos 1 adicional validada |
| **—** | **Instalar PHP na Phantom** | `apt install php` no guest + documentar no Toolbox (pré-requisito do servidor VM) | `php -v` responde no terminal do guest; Preview Hub VM serve `.php` |

### AI SUITE (docs/roteador-ias.md)
| # | Tarefa | O que falta | Critério de aceite |
|---|--------|-------------|--------------------|
| **Fase B** | **UI de conversa entre IAs** (Shared Context Bus visual) | Tela de conversa onde as IAs trocam mensagens com contexto compartilhado; **aprovação humana obrigatória** em delegações (Human Approval Gate) | Usuário vê as conversas entre IAs e aprova/nega cada delegação |
| **Fase C** | **Integração com runners reais no guest** | Conectar o roteador aos agentes reais (ex.: `ollama`/`codellama`) instalados no Linux via `phantom-router.sh` | IA do guest executa tarefa real com lock de arquivo + aprovação |

---

## 📊 RESUMO POR STATUS

| Status | Qtd | Tasks |
|---|---|---|
| 🟥 Bloqueada (device/decisão) | 5 | T25, R12, release órfã, T22, T16 |
| 🟨 Código executável | 9 | P3.3, P2.2+, P3.2, P3.1, P1.5, Executar VM, EditorExplorer, T29, PHP, Fase B, Fase C |
| **Total em aberto** | **14** | (T29/Fase B/Fase C contam 1 cada) |

## 🎯 PRÓXIMOS PASSOS RECOMENDADOS (ordem)

1. **Device:** instalar `apk-20260808-1257` e rodar o T25 (validação geral) — desbloqueia T16/T22
2. **Código:** T29 (publicar distros) e Fase B (UI de conversa entre IAs) — as de maior valor de produto
3. **Código:** P3.1 completar (PDF/CSV/SQL + PHP no guest) e "Executar código na VM"
4. **UI:** qualquer polish só após aprovação do usuário (R12)

---
*Este documento substitui a leitura de 4 arquivos para saber o que falta. Manter atualizado a cada commit.*
