#!/bin/bash
# =====================================================
# TurnoFácil - Backup diario de MySQL
# Uso: ./backup-mysql.sh
# Cron: 0 3 * * * /ruta/scripts/backup-mysql.sh >> /var/log/turnofacil-backup.log 2>&1
# =====================================================

set -euo pipefail

# Configuración (sobreescribir con variables de entorno)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-turnofacil_db}"
DB_USER="${DB_USER:-turnofacil}"
DB_PASS="${DB_PASS:-}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/turnofacil}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

# S3 (opcional)
S3_BUCKET="${S3_BUCKET:-}"

DATE=$(date +%Y-%m-%d_%H%M%S)
FILENAME="turnofacil_${DATE}.sql.gz"

echo "[$(date)] Iniciando backup de ${DB_NAME}..."

# Crear directorio si no existe
mkdir -p "${BACKUP_DIR}"

# Dump comprimido
mysqldump \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USER}" \
    --password="${DB_PASS}" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    "${DB_NAME}" | gzip > "${BACKUP_DIR}/${FILENAME}"

FILESIZE=$(du -h "${BACKUP_DIR}/${FILENAME}" | cut -f1)
echo "[$(date)] Backup local creado: ${FILENAME} (${FILESIZE})"

# Subir a S3 si está configurado
if [ -n "${S3_BUCKET}" ]; then
    aws s3 cp "${BACKUP_DIR}/${FILENAME}" "s3://${S3_BUCKET}/backups/${FILENAME}"
    echo "[$(date)] Backup subido a S3: s3://${S3_BUCKET}/backups/${FILENAME}"
fi

# Limpiar backups antiguos
find "${BACKUP_DIR}" -name "turnofacil_*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete
echo "[$(date)] Backups con más de ${RETENTION_DAYS} días eliminados"

echo "[$(date)] Backup completado exitosamente"
