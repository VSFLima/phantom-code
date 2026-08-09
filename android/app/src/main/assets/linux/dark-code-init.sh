#!/bin/sh
# ─────────────────────────────────────────────────────────────
# dark-code-init.sh — inicialização do guest Phantom-Code (T18)
# Roda no boot da VM (Phantom e demais distros):
#   · lê dark-code.conf (hostname/user definidos na instalação)
#   · rede SLIRP (DHCP)
#   · usuário + skel padrão
#   · mount do workspace via virtio-9p em /home/user/workspace
#   · redimensiona o rootfs (resize2fs) no 1º boot
#   · prompt no projeto ativo
# ─────────────────────────────────────────────────────────────
set -e

WORKSPACE_TAG="darkcode-ws"
WORKSPACE_MOUNT="/home/user/workspace"
USER_NAME="user"
HOSTNAME="phantom"

# ── Config do usuário (gravada pelo app na instalação) ───────
CONF="$(dirname "$0")/dark-code.conf"
if [ -r "$CONF" ]; then
  . "$CONF"
  [ -n "$USER" ] && USER_NAME="$USER"
fi

# Evita nomes inválidos/injeção no shell e monta o workspace na home escolhida.
case "$USER_NAME" in
  ''|*[!a-zA-Z0-9_-]*) USER_NAME="user" ;;
esac
case "$HOSTNAME" in
  ''|*[!a-zA-Z0-9_-]*) HOSTNAME="phantom" ;;
esac
WORKSPACE_MOUNT="/home/$USER_NAME/workspace"

log() { echo "[phantom] $*"; }

log "Inicializando Phantom-Code (guest)…"
hostname "$HOSTNAME" 2>/dev/null || true

# ── Redimensiona o rootfs no 1º boot (tamanho do HD escolhido) ──
if command -v resize2fs >/dev/null 2>&1 && ! [ -f /var/run/phantom-resized ]; then
  log "Redimensionando o sistema de arquivos (HD configurado)…"
  e2fsck -fp /dev/vda 2>/dev/null || true
  resize2fs /dev/vda 2>/dev/null || true
  touch /var/run/phantom-resized
fi

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
export HOME="/home/$USER_NAME"
cd "$WORKSPACE_MOUNT" 2>/dev/null || cd "$HOME"

# Prompt com o projeto ativo (se workspace visível) — hostname real (phantom por padrão)
PS1='\[\033[01;35m\]\u@'"$HOSTNAME"'\[\033[00m\]:\[\033[01;36m\]\w\[\033[00m\]\$ '
export PS1

# ── 5. Agente Phantom (canal app↔guest — T20) ───────────────
# Sobe o agente do scanner de pacotes na 2ª porta do virtio-serial.
AGENT="$(dirname "$0")/phantom-agent.sh"
if [ -c /dev/vport1p1 ] && [ -x "$AGENT" ]; then
  nohup "$AGENT" >/dev/null 2>&1 &
fi

log "Pronto. Workspace: $WORKSPACE_MOUNT"

if [ -n "$1" ]; then
  # comando único (ex.: usado pelo app p/ executar algo)
  if [ "$(id -u)" = "0" ] && id "$USER_NAME" >/dev/null 2>&1 && command -v su >/dev/null 2>&1; then
    exec su - "$USER_NAME" -s "$SHELL" -c "$*"
  fi
  exec "$SHELL" -c "$*"
else
  if [ "$(id -u)" = "0" ] && id "$USER_NAME" >/dev/null 2>&1 && command -v su >/dev/null 2>&1; then
    exec su - "$USER_NAME" -s "$SHELL" -c "cd '$WORKSPACE_MOUNT' 2>/dev/null || true; exec $SHELL -i"
  fi
  exec "$SHELL" -i
fi
