#!/usr/bin/env bash
# Gera o keystore de release do Phantom-Code e imprime o base64
# para colar no GitHub Secret: PHANTOM_KEYSTORE_BASE64
#
# Uso: bash scripts/generate-keystore.sh
set -euo pipefail

cd "$(dirname "$0")/../android"

KEYSTORE="release.keystore"
ALIAS="phantom"
STORE_PASS="${PHANTOM_STORE_PASSWORD:-}"
KEY_PASS="${PHANTOM_KEY_PASSWORD:-}"

if [ -z "$STORE_PASS" ]; then
  read -rsp "Senha do keystore (store + key): " STORE_PASS
  echo
  KEY_PASS="$STORE_PASS"
fi

if [ -f "$KEYSTORE" ]; then
  echo "ℹ️  $KEYSTORE já existe — apague-o se quiser gerar outro."
else
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=Phantom-Code, OU=Asgard, O=VSFLima, C=BR"
fi

echo
echo "── Base64 para o secret PHANTOM_KEYSTORE_BASE64 ──────────────"
base64 -w 0 "$KEYSTORE"
echo
echo "──────────────────────────────────────────────────────────────"
echo "Secrets para o GitHub:"
echo "  PHANTOM_KEYSTORE_BASE64  = (acima)"
echo "  PHANTOM_STORE_PASSWORD   = $STORE_PASS"
echo "  PHANTOM_KEY_ALIAS        = $ALIAS"
echo "  PHANTOM_KEY_PASSWORD     = $KEY_PASS"
