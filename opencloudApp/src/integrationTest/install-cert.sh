#!/bin/bash
TMP_FOLDER=$(mktemp -d)
if [[ -z "$OPENCLOUD_PATH" ]]; then
   OPENCLOUD_PATH="$HOME/.opencloud/"
fi
openssl x509 -in "$OPENCLOUD_PATH"/proxy/server.crt -out "$TMP_FOLDER"/opencloud-cert.pem -outform PEM
adb-install-cert --cert "$TMP_FOLDER"/opencloud-cert.pem