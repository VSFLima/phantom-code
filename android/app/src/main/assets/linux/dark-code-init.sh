#!/bin/sh
# ─────────────────────────────────────────────────────────────
# dark-code-init.sh — inicialização do guest Phantom-Code (T18)
# Roda no boot da VM (Phantom Base e demais distros):
#   · rede SLIRP (DHCP)
#   · usuário + skel padrão
#   · mount do workspace via virtio-9p em /home/user/workspace
#   · prompt no projeto ativo
# ─────────────────────────────────────────────────────────────
set -e

WORKSPACE_TAG="darkcode-ws"
WORKSPACE_MOUNT="/home/user/workspace"
USER_NAME="user"

log() { echo "[phantom] $*"; }

log "Inicializando Phantom-Code (guest)…"

# ── 1. Rede (SLIRP) ─────────────────────────────────────────
if command -v ip >/dev/null 2>&1; then
  ip link set eth0 up 2>/dev/null || true
  udhcpc -i eth0 -q 2>/dev/null || dhclient eth0 2>/dev/null || true
fi

# ── 2. Usuário padrão ───────────────────────────────────────
if ! id "$USER_NAME" >/dev/null 2>&1; then
  if command -v useradd >/dev/null 2>&1; then
    useradd -m -s /bin/sh "$USER_NAME" 2>/dev/null || true
  elif command -v adduser >/dev/null 2>&1; then
    adduser -D -s /bin/sh "$USER_NAME" 2>/dev/null || true
  fi
fi

# ── 3. Workspace compartilhado (virtio-9p, D3) ───────────────
if [ -d "/mnt/9p" ]; then
  WORKSPACE_MOUNT="/mnt/9p"
elif command -v mount >/dev/null 2>&1; then
  mkdir -p "$WORKSPACE_MOUNT"
  mount -t 9p -o trans=virtio,version=9p2000.L,msize=1048576 "$WORKSPACE_TAG" "$WORKSPACE_MOUNT" \
    && log "workspace montado em $WORKSPACE_MOUNT" \
    || log "falha ao montar 9p (sem suporte no kernel?)"
fi

# ── 4. Shell de login ────────────────────────────────────────
if [ -x /bin/bash ]; then SHELL=/bin/bash; else SHELL=/bin/sh; fi

export SHELL
export HOME=/root
cd /root

# Prompt com o projeto ativo (se workspace visível)
PS1='\[\033[01;35m\]\u@phantom\[\033[00m\]:\[\033[01;36m\]\w\[\033[00m\]\$ '
export PS1

log "Pronto. Workspace: $WORKSPACE_MOUNT"

if [ -n "$1" ]; then
  # comando único (ex.: usado pelo app p/ executar algo)
  exec $SHELL -c "$*"
else
  exec $SHELL -i
fi
