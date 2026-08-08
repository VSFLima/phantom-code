#!/bin/sh
# ─────────────────────────────────────────────────────────────
# phantom-agent.sh — canal app↔guest (T20, scanner de pacotes)
# Escuta a 2ª porta do virtio-serial (/dev/vport1p1) e responde:
#   PING            → PONG
#   SCAN            → lista categorizada por linha:
#                     S:<cat>|<nome>|<versão>|<rodando:0/1>|<tamanho KB>
#   RUN:<cmd>       → executa <cmd> em background no guest
#   SERVER:<dir>|<lang> → sobe servidor web na porta 80 (php/python/node)
#                     servindo a pasta <dir> do workspace (Preview Hub VM)
#   STOPSERVER      → derruba o servidor web
#   SERVERSTATUS    → "1" se há algo escutando na :80, senão "0"
# ─────────────────────────────────────────────────────────────
CTRL="${PHANTOM_CTRL:-/dev/vport1p1}"

[ -c "$CTRL" ] || exit 0

v() {
  out=$("$1" --version 2>&1 | head -1)
  [ -n "$out" ] || out=$("$1" -V 2>&1 | head -1)
  [ -n "$out" ] || out=$("$1" version 2>&1 | head -1)
  echo "$out"
}

scan_cat() {
  cat="$1"
  for t in $2; do
    p=$(command -v "$t" 2>/dev/null) || continue
    ver=$(v "$t")
    run=0
    pgrep -x "$t" >/dev/null 2>&1 && run=1
    size=$(du -k "$p" 2>/dev/null | awk '{print $1}')
    printf 'S:%s|%s|%s|%s|%s\n' "$cat" "$t" "$ver" "$run" "$size"
  done
}

scan() {
  echo "PHANTOM-SCAN-BEGIN"
  scan_cat "IA"   "ollama llama-server gpt4all whisper-cli whisper-cpp"
  scan_cat "LANG" "python3 python node npm npx rustc cargo go java javac ruby php perl gcc g++ lua deno bun"
  scan_cat "TOOL" "git curl wget nano vim htop make cmake gdb strace file tar gzip unzip rsync openssl ssh jq sqlite3 tmux rg tree"
  scan_cat "SYS"  "bash apt dpkg"
  if command -v uname >/dev/null 2>&1; then
    printf 'S:SYS|kernel|%s|1|0\n' "$(uname -r)"
  fi
  if command -v ldd >/dev/null 2>&1; then
    printf 'S:SYS|glibc|%s|1|0\n' "$(ldd --version 2>/dev/null | head -1)"
  fi
  if command -v systemctl >/dev/null 2>&1; then
    printf 'S:SYS|systemd|%s|1|0\n' "$(systemctl --version 2>/dev/null | head -1)"
  fi
  echo "PHANTOM-SCAN-END"
}

while :; do
  exec 3<>"$CTRL" 2>/dev/null || { sleep 2; continue; }
  while IFS= read -r line <&3; do
    case "$line" in
      PING) echo "PONG" >&3 ;;
      SCAN) scan >&3 ;;
      RUN:*) { eval "${line#RUN:}" >/dev/null 2>&1; } & echo "OK" >&3 ;;
      SERVER:*) 
        req="${line#SERVER:}"
        dir="${req%%|*}"
        lang="${req#*|}"
        case "$lang" in
          php)
            sh -c "cd '$dir' 2>/dev/null && php -S 0.0.0.0:80 -t ." >/dev/null 2>&1 &
            echo "OK" >&3 ;;
          python)
            sh -c "cd '$dir' 2>/dev/null && python3 -m http.server 80 --bind 0.0.0.0" >/dev/null 2>&1 &
            echo "OK" >&3 ;;
          node)
            sh -c "cd '$dir' 2>/dev/null && (command -v npx >/dev/null && npx --yes serve -l 80 . || true)" >/dev/null 2>&1 &
            echo "OK" >&3 ;;
          *) echo "ERR:linguagem nao suportada ($lang)" >&3 ;;
        esac ;;
      STOPSERVER)
        pkill -f "php -S 0.0.0.0:80" 2>/dev/null
        pkill -f "http.server 80" 2>/dev/null
        pkill -f "serve -l 80" 2>/dev/null
        echo "OK" >&3 ;;
      SERVERSTATUS)
        if ss -ltn 2>/dev/null | grep -q ':80 '; then echo "1" >&3
        elif netstat -ltn 2>/dev/null | grep -q ':80 '; then echo "1" >&3
        else echo "0" >&3; fi ;;
      *) echo "ERR" >&3 ;;
    esac
  done
  sleep 1
done
