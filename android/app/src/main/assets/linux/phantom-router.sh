#!/usr/bin/env bash
# =============================================================================
# phantom-router.sh — daemon do PHANTOM AI SUITE no Linux guest (Fase A)
# =============================================================================
# Par do AiSuiteManager (app Android). Implementa o protocolo ROUTER:<json>
# sobre o canal CTRL do virtio-serial (/dev/vport1p1 — ver phantom-agent.sh).
#
# Fase A (spec docs/roteador-ias.md §10):
#   - hello / heartbeat (auto-registro e keep-alive de IAs)
#   - lock_req / lock_ack / lock_den / release (espelho local de locks;
#     a AUTORIDADE final é o app — o locks.json do app manda no boot)
#   - msg / done (threads de tarefas em messages.jsonl)
#   - Agent Runner: embrulha QUALQUER CLI de IA (ollama run, claude -p, codex…)
#     lendo inbox/<agent>.jsonl, montando o prompt com contexto e interceptando
#     [LOCK] [RELEASE] [WRITE] [MSG] [DONE] na saída.
#
# Uso:  phantom-router.sh            (lê ROUTER:<json> do stdin / canal)
#       phantom-router.sh run-agent  <agent-id>  (processa uma mensagem da inbox)
# =============================================================================
set -u

WORKSPACE="${WORKSPACE:-/root/phantom-code}"
SUITE="$WORKSPACE/.phantom/ai-suite"
LOCK_TTL=900            # segundos (R7 — 15 min)
MAX_LOG=200

mkdir -p "$SUITE/tasks" "$SUITE/inbox" "$SUITE/diffs" "$SUITE/logs"

log() { echo "[router] $*" >&2; }
now() { date +%s; }

# ---- append jsonl (append-only — R5) --------------------------------------
append_jsonl() { # file json
    echo "$2" >> "$1"
    tail -n "$MAX_LOG" "$1" > "$1.tmp" && mv "$1.tmp" "$1"
}

# ---- locks (espelho local; autoridade final no app) ------------------------
lock_conflict() { # path mode owner
    local path="$1" mode="$2" owner="$3"
    [ -f "$SUITE/locks.json" ] || return 1
    jq -e --arg p "$path" --arg m "$mode" --arg o "$owner" '
        .locks[] | select(.owner != $o and .until > (now * 1000)) |
        select(.path == $p or .mode == "G" or .mode == "D")' "$SUITE/locks.json" >/dev/null 2>&1
}

lock_req() { # owner path mode task_id
    local owner="$1" path="$2" mode="$3" task_id="${4:-t-manual}"
    if lock_conflict "$path" "$mode" "$owner"; then
        echo "{\"type\":\"lock_den\",\"path\":\"$path\",\"reason\":\"DENIED por outro agente\"}"
        return
    fi
    local until=$(( $(now) + LOCK_TTL ))
    if [ -f "$SUITE/locks.json" ]; then
        jq --arg p "$path" '.locks |= map(select(.path != $p))' "$SUITE/locks.json" > "$SUITE/locks.json.tmp" && mv "$SUITE/locks.json.tmp" "$SUITE/locks.json"
    else
        echo '{"locks":[]}' > "$SUITE/locks.json"
    fi
    jq --arg p "$path" --arg m "$mode" --arg o "$owner" --arg t "$task_id" --argjson u "$until" \
        '.locks += [{"path":$p,"mode":$m,"owner":$o,"task_id":$t,"until":$u}]' \
        "$SUITE/locks.json" > "$SUITE/locks.json.tmp" && mv "$SUITE/locks.json.tmp" "$SUITE/locks.json"
    echo "{\"type\":\"lock_ack\",\"path\":\"$path\",\"mode\":\"$mode\",\"until\":$until}"
}

release_locks() { # owner [path]
    local owner="$1" path="${2:-}"
    [ -f "$SUITE/locks.json" ] || return
    if [ -n "$path" ]; then
        jq --arg o "$owner" --arg p "$path" '.locks |= map(select(.owner != $o or .path != $p))' "$SUITE/locks.json" > "$SUITE/locks.json.tmp" && mv "$SUITE/locks.json.tmp" "$SUITE/locks.json"
    else
        jq --arg o "$owner" '.locks |= map(select(.owner != $o))' "$SUITE/locks.json" > "$SUITE/locks.json.tmp" && mv "$SUITE/locks.json.tmp" "$SUITE/locks.json"
    fi
}

# ---- threads / contexto ----------------------------------------------------
thread_msg() { # task_id from type text [refs]
    local task_id="$1" from="$2" type="$3" text="$4" refs="${5:-[]}"
    local file="$SUITE/tasks/$task_id/messages.jsonl"
    mkdir -p "$SUITE/tasks/$task_id"
    local entry
    entry=$(jq -nc --arg ts "$(now)" --arg from "$from" --arg type "$type" --arg text "$text" --argjson refs "$refs" \
        '{ts:$ts,from:$from,type:$type,text:$text,refs:$refs}')
    append_jsonl "$file" "$entry"
}

context_summary() { # task_id -> prompt resumido (R8)
    local task_id="$1"
    local ctx="$SUITE/tasks/$task_id/context.json"
    if [ -f "$ctx" ]; then
        head -c 4000 "$ctx"
    else
        echo "Tarefa $task_id: sem contexto adicional (R8 — leia antes de agir)."
    fi
}

# ---- Fase B: propostas de delegação + aprovação do dono --------------------
save_proposal() { # id from to subtask scope_write scope_read reason due status
    local id="$1" from="$2" to="$3" subtask="$4" sw="$5" sr="$6" reason="$7" due="$8" status="$9"
    local dir="$SUITE/tasks/$id"
    mkdir -p "$dir"
    jq -n --arg id "$id" --arg from "$from" --arg to "$to" --arg subtask "$subtask" \
        --argjson sw "$sw" --argjson sr "$sr" --arg reason "$reason" --arg due "$due" --arg status "$status" \
        '{id:$id,from:$from,to:$to,subtask:$subtask,scope_write:$sw,scope_read:$sr,reason:$reason,due:$due,status:$status}' \
        > "$dir/proposal.json"
    jq -n --arg id "$id" --arg title "$subtask" --arg status "$status" --arg owner "$to" \
        --argjson sw "$sw" --argjson sr "$sr" --argjson due "$due" \
        '{id:$id,title:$title,status:$status,owner:$owner,scope_files:$sw,scope_read:$sr,created_at:'"$(now)"',due:$due}' \
        > "$dir/context.json"
}

# ---- registrador de agentes ------------------------------------------------
register_hello() { # id name invoke
    local id="$1" name="$2" invoke="$3"
    local f="$SUITE/agents.json"
    [ -f "$f" ] || echo '{"agents":[]}' > "$f"
    jq --arg id "$id" --arg name "$name" --arg invoke "$invoke" \
        '.agents |= ([{id:$id,name:$name,type:"local",invoke:$invoke,status:"online",skills:{code:3,review:3,docs:3,shell:2,test:3}}] + map(select(.id != $id)))' \
        "$f" > "$f.tmp" && mv "$f.tmp" "$f"
}

# ---- whitelist de comandos (segurança §11) ---------------------------------
WHITELIST="git ls cat head tail grep rg find sed awk jq python3 node npm ollama claude codex aider echo printf mkdir cp mv rm touch tar"

shell_allowed() { # cmd
    local bin="${1%% *}"
    case " $WHITELIST " in
        *" $bin "*) return 0 ;;
        *) return 1 ;;
    esac
}

# ---- Agent Runner: embrulha uma CLI como participante do bus ---------------
run_agent() { # agent_id
    local id="$1"
    local f="$SUITE/inbox/$id.jsonl"
    [ -f "$f" ] || { log "sem inbox para $id"; exit 0; }
    local line; line=$(head -1 "$f")
    [ -n "$line" ] || { log "inbox vazia"; exit 0; }
    tail -n +2 "$f" > "$f.tmp" && mv "$f.tmp" "$f"

    local task_id from msg invoke
    task_id=$(echo "$line" | jq -r '.task_id // "t-manual"')
    from=$(echo "$line" | jq -r '.from // "router"')
    msg=$(echo "$line" | jq -r '.payload.text // .text // ""')
    invoke=$(jq -r --arg id "$id" '.agents[] | select(.id == $id) | .invoke' "$SUITE/agents.json" 2>/dev/null)
    [ -n "$invoke" ] || { log "agente $id sem invoke"; exit 0; }

    local prompt
    prompt="Você é o agente $id (Phantom AI Suite). Tarefa: $task_id
Mensagem recebida: $msg

CONTEXTO (R8):
$(context_summary "$task_id")

REGRAS:
- Para escrever/criar/editar/apagar arquivo, emita [LOCK] <path> antes e [RELEASE] <path> depois.
- Para escrever um arquivo (com lock), emita [WRITE] <path> e o conteúdo em seguida.
- Para falar com outra IA ou o dono, emita [MSG] <texto>.
- Ao terminar, emita [DONE] <resumo>.
- Sempre leia o contexto antes de agir (R8)."
    echo "$prompt" | $invoke 2>&1 | while IFS= read -r out; do
        case "$out" in
            "[LOCK] "*)
                p="${out#"[LOCK] "}"
                lock_req "$id" "$p" "W" "$task_id" >/dev/null
                ;;
            "[RELEASE] "*)
                release_locks "$id" "${out#"[RELEASE] "}"
                ;;
            "[WRITE] "*)
                p="${out#"[WRITE] "}"
                if [ -f "$SUITE/locks.json" ] && jq -e --arg p "$p" --arg o "$id" '.locks[] | select(.path == $p and .owner == $o)' "$SUITE/locks.json" >/dev/null 2>&1; then
                    mkdir -p "$(dirname "$WORKSPACE/$p")"
                    tee "$WORKSPACE/$p" >/dev/null   # R1: só com lock
                else
                    echo "[router] violação R1: escrita sem lock em $p" >&2
                fi
                ;;
            "[MSG] "*)
                thread_msg "$task_id" "$id" "msg" "${out#"[MSG] "}"
                ;;
            "[DONE] "*)
                thread_msg "$task_id" "$id" "done" "${out#"[DONE] "}"
                release_locks "$id"
                ;;
            *)
                echo "$out" >&2
                ;;
        esac
    done
}

# ---- main: processa ROUTER:<json> do canal ---------------------------------
process_line() { # linha ROUTER:json
    local payload="${1#ROUTER:}"
    echo "$payload" | jq -e . >/dev/null 2>&1 || { log "payload inválido"; return; }
    local type from task_id
    type=$(echo "$payload" | jq -r '.type')
    from=$(echo "$payload" | jq -r '.from // "ia"')
    task_id=$(echo "$payload" | jq -r '.task_id // "t-manual"')

    case "$type" in
        hello)
            register_hello "$(echo "$payload" | jq -r '.id')" \
                "$(echo "$payload" | jq -r '.name // .id')" \
                "$(echo "$payload" | jq -r '.invoke // ""')"
            log "hello de $from"
            ;;
        heartbeat)
            log "heartbeat de $from"
            ;;
        lock_req)
            lock_req "$from" "$(echo "$payload" | jq -r '.path')" \
                "$(echo "$payload" | jq -r '.mode // "W"')" "$task_id"
            ;;
        release)
            release_locks "$from" "$(echo "$payload" | jq -r '.path // ""')"
            ;;
        msg)
            thread_msg "$task_id" "$from" "msg" "$(echo "$payload" | jq -r '.payload.text // .text')" \
                "$(echo "$payload" | jq -c '.refs // []')"
            ;;
        done)
            thread_msg "$task_id" "$from" "done" "$(echo "$payload" | jq -r '.payload.summary // .text')"
            release_locks "$from"
            ;;
        delegate)
            # Fase B — proposta de delegação: cria a tarefa, avisa o APP
            # (Human Approval Gate). O dono decide: approved/rejected/ajustar.
            local to subtask sw sr reason due
            to=$(echo "$payload" | jq -r '.payload.to // ""')
            subtask=$(echo "$payload" | jq -r '.payload.subtask // ""')
            sw=$(echo "$payload" | jq -c '.payload.scope.write // .payload.scope_write // []')
            sr=$(echo "$payload" | jq -c '.payload.scope.read // .payload.scope_read // []')
            reason=$(echo "$payload" | jq -r '.payload.reason // ""')
            due=$(echo "$payload" | jq -r '.payload.due // ""')
            [ -n "$to" ] || { log "delegate sem destino"; break; }
            save_proposal "$task_id" "$from" "$to" "$subtask" "$sw" "$sr" "$reason" "$due" "pending_approval"
            thread_msg "$task_id" "$from" "msg" "$subtask"
            echo "{\"type\":\"approval_req\",\"task_id\":\"$task_id\",\"payload\":{\"proposal_id\":\"$task_id\",\"from\":\"$from\",\"to\":\"$to\",\"summary\":\"$subtask\"}}"
            log "proposta $task_id aguardando o dono"
            ;;
        approved)
            local to sw
            to=$(echo "$payload" | jq -r '.payload.to // ""')
            sw=$(echo "$payload" | jq -c '.payload.scope_write // []')
            [ -f "$SUITE/tasks/$task_id/proposal.json" ] && \
                jq --arg s "approved" '.status = $s' "$SUITE/tasks/$task_id/proposal.json" > "$SUITE/tasks/$task_id/proposal.json.tmp" && mv "$SUITE/tasks/$task_id/proposal.json.tmp" "$SUITE/tasks/$task_id/proposal.json"
            [ -f "$SUITE/tasks/$task_id/context.json" ] && \
                jq --arg s "approved" '.status = $s' "$SUITE/tasks/$task_id/context.json" > "$SUITE/tasks/$task_id/context.json.tmp" && mv "$SUITE/tasks/$task_id/context.json.tmp" "$SUITE/tasks/$task_id/context.json"
            echo "$sw" | jq -e . >/dev/null 2>&1 && echo "$sw" | jq -r '.[]' | while IFS= read -r p; do
                lock_req "$to" "$p" "W" "$task_id" >/dev/null
            done
            thread_msg "$task_id" "dono" "approval" "Delegação aprovada — $to pode executar"
            log "proposta $task_id aprovada pelo dono"
            ;;
        rejected)
            [ -f "$SUITE/tasks/$task_id/proposal.json" ] && \
                jq --arg s "rejected" '.status = $s' "$SUITE/tasks/$task_id/proposal.json" > "$SUITE/tasks/$task_id/proposal.json.tmp" && mv "$SUITE/tasks/$task_id/proposal.json.tmp" "$SUITE/tasks/$task_id/proposal.json"
            thread_msg "$task_id" "dono" "approval" "Delegação recusada"
            log "proposta $task_id recusada pelo dono"
            ;;
        owner_msg)
            # R5 — o dono intervém na thread (prioridade máxima)
            thread_msg "$task_id" "dono" "msg" "$(echo "$payload" | jq -r '.payload.text // .text')"
            ;;
        create_task)
            # Fase C — o dono cria uma tarefa para uma IA executar no guest
            local to title scope sw sr
            to=$(echo "$payload" | jq -r '.payload.to // .to // ""')
            title=$(echo "$payload" | jq -r '.payload.title // .payload.subtask // ""')
            sw=$(echo "$payload" | jq -c '.payload.scope_write // []')
            sr=$(echo "$payload" | jq -c '.payload.scope_read // []')
            [ -n "$title" ] || { log "create_task sem título"; break; }
            mkdir -p "$SUITE/tasks/$task_id"
            jq -n --arg id "$task_id" --arg title "$title" --arg status "approved" --arg owner "$to" \
                --argjson sw "$sw" --argjson sr "$sr" \
                '{id:$id,title:$title,status:$status,owner:$owner,scope_files:$sw,scope_read:$sr,created_at:'"$(now)"',due:""}' \
                > "$SUITE/tasks/$task_id/context.json"
            thread_msg "$task_id" "dono" "msg" "Tarefa criada para $to: $title"
            echo "$sw" | jq -e . >/dev/null 2>&1 && echo "$sw" | jq -r '.[]' | while IFS= read -r p; do
                lock_req "$to" "$p" "W" "$task_id" >/dev/null
            done
            log "tarefa $task_id criada para $to"
            ;;
        scan)
            # Fase C — PHANTOM-IA-HELLO nas CLIs conhecidas; registra quem responder
            for cli in ollama claude codex aider gpt4all llama-server; do
                if command -v "$cli" >/dev/null 2>&1; then
                    local res
                    res=$(printf 'PHANTOM-IA-HELLO\n' | "$cli" 2>/dev/null | head -1)
                    if echo "$res" | jq -e . >/dev/null 2>&1; then
                        register_hello "$(echo "$res" | jq -r '.id // "'$cli'"')" \
                            "$(echo "$res" | jq -r '.name // "'$cli'"')" \
                            "$(echo "$res" | jq -r '.invoke // "'$cli'"')"
                        log "auto-registrado: $cli"
                    else
                        log "CLI $cli presente, sem protocolo PHANTOM-IA"
                    fi
                fi
            done
            ;;
        error)
            log "erro de $from: $(echo "$payload" | jq -r '.payload.message')"
            ;;
        *)
            log "tipo desconhecido: $type"
            ;;
    esac
}

case "${1:-daemon}" in
    run-agent)
        run_agent "${2:?agente obrigatório}"
        ;;
    *)
        # daemon: lê linhas ROUTER:<json> do canal (stdin / /dev/vport1p1)
        while IFS= read -r line; do
            case "$line" in
                ROUTER:*) process_line "$line" ;;
                *) [ -n "$line" ] && log "linha ignorada: ${line:0:60}" ;;
            esac
        done
        ;;
esac
