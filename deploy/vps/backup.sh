#!/usr/bin/env sh
# Backup diario do PostgreSQL do Operador Zero. Mantem os ultimos 14 arquivos.
# Com OFFSITE_REMOTE (remote rclone criptografado, ex.: gdrive-crypt:), envia uma copia para fora
# do servidor e apaga ali as copias com mais de OFFSITE_KEEP_DAYS dias.
# Cron sugerido: 30 3 * * * OFFSITE_REMOTE=gdrive-crypt: /opt/operadorzero/deploy/vps/backup.sh >> /var/log/operadorzero-backup.log 2>&1
set -eu

BACKUP_DIR="${BACKUP_DIR:-/var/backups/operadorzero}"
KEEP="${KEEP:-14}"
OFFSITE_REMOTE="${OFFSITE_REMOTE:-}"
OFFSITE_KEEP_DAYS="${OFFSITE_KEEP_DAYS:-60}"
REPO_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
target="$BACKUP_DIR/operadorzero-$(date +%Y%m%d-%H%M%S).dump"

docker compose -f "$REPO_DIR/deploy/vps/compose.yml" exec -T postgres \
  pg_dump -U operadorzero -d operadorzero --format=custom > "$target.partial"
mv "$target.partial" "$target"
chmod 600 "$target"

ls -1t "$BACKUP_DIR"/operadorzero-*.dump | tail -n +"$((KEEP + 1))" | xargs -r rm --
echo "$(date -Iseconds) backup criado: $target"

if [ -n "$OFFSITE_REMOTE" ]; then
  rclone copy "$target" "$OFFSITE_REMOTE" --immutable
  rclone delete "$OFFSITE_REMOTE" --min-age "${OFFSITE_KEEP_DAYS}d" --include "operadorzero-*.dump"
  echo "$(date -Iseconds) copia enviada para $OFFSITE_REMOTE"
fi
