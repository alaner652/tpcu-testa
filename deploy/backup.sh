#!/bin/sh
# 每天備份一次資料庫到 /backups，保留 BACKUP_KEEP_DAYS 天（預設 30）。
#
# 還原（會把資料庫整個換成備份當時的內容；先停後端，還原完再開）：
#   docker compose -f docker-compose.prod.yml stop server
#   gunzip -c backups/testa-YYYYmmdd-HHMM.sql.gz | docker compose -f docker-compose.prod.yml exec -T db psql -U testa -d testa -v ON_ERROR_STOP=1 --single-transaction
#   docker compose -f docker-compose.prod.yml start server
#
# 立刻多備份一次：docker compose -f docker-compose.prod.yml restart backup
set -eu
# 沒有 pipefail 的話，pg_dump 失敗但 gzip 成功會被當成備份成功
set -o pipefail

# 第一次啟動時後端還在建表，等資料表出現再備份，才不會留下一份空的備份
until psql -h db -U testa -d testa -Atc "select 1 from flyway_schema_history limit 1" >/dev/null 2>&1; do
  echo "等後端把資料表建好…"
  sleep 5
done

while true; do
  file="/backups/testa-$(date +%Y%m%d-%H%M%S).sql.gz"
  if pg_dump -h db -U testa -d testa --clean --if-exists | gzip > "$file.tmp"; then
    mv "$file.tmp" "$file"
    echo "備份完成：$file"
  else
    rm -f "$file.tmp"
    echo "備份失敗" >&2
  fi
  find /backups -name 'testa-*.sql.gz' -mtime +"${BACKUP_KEEP_DAYS:-30}" -delete
  sleep 86400
done
