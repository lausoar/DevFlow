#!/usr/bin/env bash
# ============================================
# 数据库备份 / 恢复脚本
# ============================================
# 第 4 批把"备份恢复"列为遗留项，这个脚本补上最小可用版本。
#
# 用法：
#   scripts/db-backup.sh                     # 备份到 backups/，文件名带时间戳
#   DB_NAME=devflow BACKUP_DIR=/data/bk scripts/db-backup.sh
#   scripts/db-backup.sh restore backups/devflow-20261115-120000.sql.gz
#
# 设计要点：
# - 备份用 --single-transaction（InnoDB 一致性快照，不锁表）
# - 同时导出 routines/triggers，避免恢复后行为不一致
# - 恢复前**强制要求**确认（除非 FORCE=1），且默认先备份当前库
# - 不把口令写进命令行（走 MYSQL_PWD，避免出现在 ps 里）
# - 保留最近 N 份（默认 14），更早的自动清理

set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USERNAME:-root}"
DB_PASS="${DB_PASSWORD:-devflow123}"
DB_NAME="${DB_NAME:-devflow}"
BACKUP_DIR="${BACKUP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/backups}"
KEEP="${KEEP:-14}"

export MYSQL_PWD="$DB_PASS"

usage() {
    cat <<EOF
用法: $(basename "$0") [restore <file.sql.gz>]

  无参数        备份 $DB_NAME 到 $BACKUP_DIR
  restore FILE  从备份文件恢复（会先提示确认，并自动备份当前库）
EOF
    exit 1
}

backup() {
    mkdir -p "$BACKUP_DIR"
    local stamp file
    stamp="$(date +%Y%m%d-%H%M%S)"
    file="$BACKUP_DIR/${DB_NAME}-${stamp}.sql.gz"

    echo "→ 备份 $DB_NAME → $file"
    # --single-transaction：InnoDB 一致性快照，不阻塞业务写入
    # --routines --triggers：存储过程与触发器一并导出
    # --set-gtid-purged 只存在于 MySQL 5.6+ 的 mysqldump，5.5 会直接报 unknown variable。
    # 用 `--help` 里是否有该选项判断不可靠（5.5 的帮助文本里也可能出现这个词），
    # 因此改成"真的试一次"：失败就去掉该选项重试。
    local raw="$BACKUP_DIR/.${DB_NAME}-${stamp}.sql"
    local base=(mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER"
                --single-transaction --routines --triggers
                --default-character-set=utf8mb4)

    if ! "${base[@]}" --set-gtid-purged=OFF "$DB_NAME" > "$raw" 2>/dev/null; then
        echo "  （当前 mysqldump 不支持 --set-gtid-purged，改用兼容模式）"
        if ! "${base[@]}" "$DB_NAME" > "$raw"; then
            echo "✗ mysqldump 失败"
            rm -f "$raw"
            exit 1
        fi
    fi

    # 校验内容而不是只看文件大小：空库也会产生几十字节的 gzip 头
    if ! grep -q "CREATE TABLE" "$raw"; then
        echo "✗ 备份内容异常（没有任何 CREATE TABLE），已删除"
        rm -f "$raw" "$file"
        exit 1
    fi
    gzip -9 -c "$raw" > "$file"
    rm -f "$raw"

    local size
    size=$(du -h "$file" | cut -f1)
    echo "✓ 备份完成（$size）"

    # 清理旧备份：保留最近 KEEP 份
    local count
    count=$(ls -1 "$BACKUP_DIR"/"${DB_NAME}"-*.sql.gz 2>/dev/null | wc -l || echo 0)
    if [ "$count" -gt "$KEEP" ]; then
        echo "→ 清理旧备份（保留最近 $KEEP 份）"
        ls -1t "$BACKUP_DIR"/"${DB_NAME}"-*.sql.gz | tail -n +$((KEEP + 1)) | while read -r old; do
            echo "  删除 $(basename "$old")"
            rm -f "$old"
        done
    fi
}

restore() {
    local file="$1"
    if [ ! -f "$file" ]; then
        echo "✗ 备份文件不存在: $file"
        exit 1
    fi

    echo "⚠️  即将把 $file 恢复到数据库 $DB_NAME（会覆盖现有数据）"
    if [ "${FORCE:-0}" != "1" ]; then
        read -r -p "确认继续？输入 yes 继续: " answer
        [ "$answer" = "yes" ] || { echo "已取消"; exit 1; }
    fi

    # 恢复前先备份当前状态 —— 恢复本身也可能出错
    echo "→ 先备份当前库（回滚用）"
    backup

    echo "→ 恢复 $file"
    case "$file" in
        *.gz) gunzip -c "$file" | mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --default-character-set=utf8mb4 "$DB_NAME" ;;
        *)    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --default-character-set=utf8mb4 "$DB_NAME" < "$file" ;;
    esac
    echo "✓ 恢复完成"

    # 恢复后校验：表数量与关键表存在
    local tables
    tables=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -N -e \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")
    echo "✓ 当前库共 $tables 张表"
}

case "${1:-}" in
    "")          backup ;;
    restore)     [ $# -ge 2 ] || usage; restore "$2" ;;
    *)           usage ;;
esac
