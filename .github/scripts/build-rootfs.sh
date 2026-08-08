#!/bin/bash
# ============================================================
# build-rootfs.sh — constrói rootfs arm64 para QEMU (T29)
#
# Uso: build-rootfs.sh <id> <image> <pm:apt|apk> <extra> <pkgs> <divert>
#
# Pipeline:
#   1. docker (binfmt/qemu-user) instala os pacotes da distro no arm64
#   2. export → /tmp/rootfs  · kernel + initrd vindos do pacote (linux-image*/linux-virt)
#   3. rootfs.img via mkfs.ext2 -d (mesmo método da Phantom)
#   4. tar czf <id>.tar.gz = rootfs.img + kernel + initrd.img + qemu-system-aarch64
#
# O qemu-system-aarch64 DEVE já estar em /tmp (o workflow baixa da release
# distro-phantom) — assim o pacote fica autocontido e o app não depende do
# fallback de download (a release qemu-aarch64 foi removida).
# ============================================================
set -euo pipefail

ID="$1"
IMAGE="$2"
PM="$3"
EXTRA="$4"
PKGS="$5"
DIVERT="$6"

echo "=== [$ID] construindo rootfs a partir de $IMAGE (pm=$PM) ==="

docker run --platform linux/arm64 \
  -e PM="$PM" -e DIVERT="$DIVERT" -e EXTRA="$EXTRA" -e PKGS="$PKGS" \
  --name "$ID" "$IMAGE" bash -s <<'EOF'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

if [ "$PM" = "apt" ]; then
  # O python3.x segfaulta sob o qemu-user do runner (postinst py3compile) —
  # desviamos py3compile/pycompile para /bin/true (mesma receita da Phantom).
  if [ "$DIVERT" = "true" ]; then
    dpkg-divert --local --add --rename /usr/bin/py3compile
    ln -sf /bin/true /usr/bin/py3compile
    dpkg-divert --local --add --rename /usr/bin/pycompile
    ln -sf /bin/true /usr/bin/pycompile
  fi
  apt-get update
  # shellcheck disable=SC2086
  apt-get install -y --no-install-recommends $EXTRA $PKGS
  rm -rf /var/lib/apt/lists/*
  # Garante o initrd (o postinst já gera; força para segurança).
  V=$(ls /lib/modules 2>/dev/null | head -1 || true)
  if [ -n "$V" ] && command -v update-initramfs >/dev/null 2>&1; then
    update-initramfs -c -k "$V" 2>/dev/null || true
  fi
else
  apk add --no-cache $EXTRA $PKGS
  rm -rf /var/cache/apk/*
fi
EOF

mkdir -p /tmp/rootfs
docker export "$ID" | tar -xf - -C /tmp/rootfs
docker rm -f "$ID" >/dev/null 2>&1 || true

echo "=== [$ID] capturando kernel + initrd ==="
K=$(ls /tmp/rootfs/boot/vmlinuz-* 2>/dev/null | head -1)
I=$(ls /tmp/rootfs/boot/initrd.img-* /tmp/rootfs/boot/initramfs-* 2>/dev/null | head -1)
if [ -z "$K" ]; then echo "❌ [$ID] kernel não encontrado em /boot"; exit 1; fi
if [ -z "$I" ]; then echo "❌ [$ID] initrd não encontrado em /boot"; exit 1; fi
cp -L "$K" /tmp/kernel
cp -L "$I" /tmp/initrd.img
ls -la /tmp/kernel /tmp/initrd.img

echo "=== [$ID] criando rootfs.img (ext2) ==="
SIZE_MB=$(( $(du -sk -l /tmp/rootfs | awk '{print $1}') * 140 / 100 / 1024 + 200 ))
echo "rootfs real: $(du -sk /tmp/rootfs | awk '{print $1}') KiB → ext2: ${SIZE_MB} MiB"
truncate -s "${SIZE_MB}M" /tmp/rootfs.img
sudo mkfs.ext2 -q -F /tmp/rootfs.img -d /tmp/rootfs

echo "=== [$ID] empacotando tarball (com QEMU embutido) ==="
cd /tmp
tar czf "$ID.tar.gz" rootfs.img kernel initrd.img qemu-system-aarch64
ls -la "$ID.tar.gz"
sha256sum "$ID.tar.gz" | tee "/tmp/$ID.sha256"
echo "=== [$ID] OK ==="
