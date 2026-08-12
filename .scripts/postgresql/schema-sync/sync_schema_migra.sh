#!/bin/bash
# ============================================
# PostgreSQL 表结构同步脚本（基于 migra）
# ============================================
# 作用：
#   以 .scripts/postgresql/*.sql 作为"目标结构"，对比 Docker 中现有数据库，
#   生成（并可选应用）只改结构、保留数据的差异 SQL。
#
# 原理：
#   1. 用最新 .sql 在容器里建一个临时"参考库" <db>_ref
#   2. 用 migra 对比：现有库(from) -> 参考库(to)，得到对齐结构所需的 SQL
#   3. 默认 dry-run：只生成并打印差异，不改动任何数据
#   4. --apply：review 后应用；应用前自动 pg_dump 备份；含破坏性语句需 --allow-destructive
#
# 使用方法：
#   ./sync_schema_migra.sh sync                     # ⭐ 一键：全部库 新增或更新（=--apply --create-missing）
#   ./sync_schema_migra.sh                          # 对全部库做 dry-run（只看差异）
#   ./sync_schema_migra.sh --db iot-device20        # 只处理单个库
#   ./sync_schema_migra.sh --apply                  # review 后应用（仅安全变更）
#   ./sync_schema_migra.sh --db iot-ai20 --apply    # 单库应用
#   ./sync_schema_migra.sh --apply --allow-destructive   # 允许应用 DROP 等破坏性语句
#   ./sync_schema_migra.sh --apply --yes            # 应用时跳过交互确认
#   ./sync_schema_migra.sh --db iot-ai20 --keep-ref # 保留临时参考库（调试用）
#   ./sync_schema_migra.sh --db iot-xxx20 --apply --create-missing  # 新模块：建库并全量导入
#
# 可用环境变量覆盖默认值：
#   PG_CONTAINER（默认 postgres-server） PG_USER（postgres）
#   PG_PASSWORD（必填，由环境变量提供）        PG_PORT（5432）
#   SQL_DIR（目标 *10.sql 所在目录，默认脚本上一级 .scripts/postgresql/）
#   PG_NETWORK（默认共享 postgres 容器网络命名空间 container:<容器>；设为某 bridge 网络名则改走该网络）
#   PG_DB_HOST（默认 127.0.0.1；显式指定 PG_NETWORK 时默认用容器名）
#   MIGRA_BUILD_LOCAL（默认 true，用官方 python:3.11-slim 现场 pip 自建 migra）
#   MIGRA_IMAGE（自建时默认 easyaiot-migra:local；设 MIGRA_BUILD_LOCAL=false 可改用现成镜像）
#   PIP_INDEX（仅 MIGRA_BUILD_LOCAL=true 时生效，pip 镜像源，如清华源）
# ============================================

# 注意：不使用 set -e —— migra 在"发现差异"时返回非 0，需手动判定退出码
set -uo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 目标 .sql 在上一级目录（.scripts/postgresql/），脚本与手册放在 schema-sync/ 子目录里
SQL_DIR="${SQL_DIR:-$(cd "${SCRIPT_DIR}/.." && pwd)}"

# ---- 配置（可用环境变量覆盖）----
CONTAINER="${PG_CONTAINER:-postgres-server}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-}"
PG_PORT="${PG_PORT:-5432}"
# 默认：用 Docker 官方镜像 python:3.11-slim 现场 pip 自建 migra（migra 无官方预构建镜像）
# 想改用某个现成镜像：MIGRA_BUILD_LOCAL=false MIGRA_IMAGE=<你的migra镜像> ./sync_schema_migra.sh
MIGRA_BUILD_LOCAL="${MIGRA_BUILD_LOCAL:-true}"
if [ "$MIGRA_BUILD_LOCAL" = true ]; then
    MIGRA_IMAGE="${MIGRA_IMAGE:-easyaiot-migra:local}"
else
    MIGRA_IMAGE="${MIGRA_IMAGE:-djrobstep/migra:latest}"
fi
# 产物按【模块(库名)/日期】分层归档：差异 schema_diffs/<db>/<YYYY-MM-DD>/，备份 backups/<db>/<YYYY-MM-DD>/。
# 每次运行新建当日子目录(归当前用户所有)，不再在 schema-sync/ 下散落 *.rootbak.<时间戳> 这类目录。
DIFF_DIR="${DIFF_DIR:-${SCRIPT_DIR}/schema_diffs}"
BACKUP_DIR="${BACKUP_DIR:-${SCRIPT_DIR}/backups}"

print_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
print_section() {
    echo ""
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}  $1${NC}"
    echo -e "${YELLOW}========================================${NC}"
    echo ""
}

if [ -z "$PG_PASSWORD" ]; then
    print_error "必须通过 PG_PASSWORD 环境变量提供 PostgreSQL 密码"
    exit 1
fi

# 命名规约：<名字>10.sql  <->  数据库 <名字>20
# （iot-device10.sql <-> iot-device20，iot-gb2818110.sql <-> iot-gb2818120）

# 由数据库名推导 SQL 文件名（纯推导；文件是否存在由调用方校验，便于给出准确报错）
get_sql_file() {
    local db="$1"
    case "$db" in
        *20) echo "${db%20}10.sql" ;;   # 库名须以 20 结尾，符合规约
        *) echo "" ;;
    esac
}

# 自动发现：扫描目录下所有 *10.sql，按规约推导库名 <名字>20
discover_databases() {
    local f base
    for f in "${SQL_DIR}"/*10.sql; do
        [ -e "$f" ] || continue          # 无匹配文件时跳过字面量
        base="$(basename "$f" .sql)"     # 去掉 .sql 后缀
        case "$base" in
            *10) echo "${base%10}20" ;;  # 末尾 10 -> 20
        esac
    done
}

# 自动构建全部库清单（按文件名规约动态发现，新增 .sql 无需改脚本）
ALL_DATABASES=()
while IFS= read -r _db; do
    [ -n "$_db" ] && ALL_DATABASES+=("$_db")
done < <(discover_databases)

show_help() {
    echo "PostgreSQL 表结构同步脚本（基于 migra）"
    echo ""
    echo "使用方法:"
    echo "  ./sync_schema_migra.sh [sync] [选项]"
    echo ""
    echo "一键模式:"
    echo "  sync                   = --apply --create-missing：已有库增量更新、缺失库自动创建"
    echo "                           并全量导入（逐库 yes 确认；免交互再加 --yes）"
    echo ""
    echo "选项:"
    echo "  --db <名称>            只处理指定库（可重复传入多次）"
    echo "  --apply                应用差异（默认仅 dry-run，只打印不改库）"
    echo "  --allow-destructive    允许应用 DROP/改类型 等破坏性语句（默认拒绝）"
    echo "  --create-missing       目标库不存在时创建并全量导入对应 *10.sql（新模块建库）"
    echo "  --yes, -y              应用时跳过交互确认"
    echo "  --keep-ref             保留临时参考库（调试用，默认用完即删）"
    echo "  -h, --help             显示帮助"
    echo ""
    echo "自动发现的库（扫描 *10.sql 推导，新增文件无需改脚本）:"
    if [ ${#ALL_DATABASES[@]} -eq 0 ]; then
        echo "  （未发现任何 *10.sql 文件）"
    else
        for db in "${ALL_DATABASES[@]}"; do
            echo "  - $db  ($(get_sql_file "$db"))"
        done
    fi
    echo ""
}

# ---- 参数解析 ----
APPLY=false
ALLOW_DESTRUCTIVE=false
ASSUME_YES=false
KEEP_REF=false
CREATE_MISSING=false
TARGET_DBS=()

while [ $# -gt 0 ]; do
    case "$1" in
        # 一键模式：等价于 --apply --create-missing —— 已有库增量更新、缺失库创建并全量导入。
        # 仍保留逐库 yes 确认与破坏性语句拦截；免交互可再加 --yes
        sync)                APPLY=true; CREATE_MISSING=true; shift ;;
        --db)                TARGET_DBS+=("$2"); shift 2 ;;
        --apply)             APPLY=true; shift ;;
        --allow-destructive) ALLOW_DESTRUCTIVE=true; shift ;;
        --create-missing)    CREATE_MISSING=true; shift ;;
        --yes|-y)            ASSUME_YES=true; shift ;;
        --keep-ref)          KEEP_REF=true; shift ;;
        -h|--help)           show_help; exit 0 ;;
        *) print_error "未知参数: $1"; echo ""; show_help; exit 1 ;;
    esac
done

if [ ${#TARGET_DBS[@]} -eq 0 ]; then
    TARGET_DBS=("${ALL_DATABASES[@]}")
fi

# ---- 容器内执行 psql 的封装（统一注入密码）----
pg_psql() {
    docker exec -i -e PGPASSWORD="$PG_PASSWORD" "$CONTAINER" psql -U "$PG_USER" "$@"
}

# ---- 前置检查 ----
check_prerequisites() {
    if ! docker info &> /dev/null; then
        print_error "Docker daemon 未运行或无法访问"
        exit 1
    fi

    if ! docker ps --filter "name=^${CONTAINER}$" --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
        print_error "容器 $CONTAINER 未运行，请先启动 PostgreSQL"
        exit 1
    fi

    # migra 容器如何连到 postgres：
    # 默认共享 postgres 容器的网络命名空间（--network container:<容器> + 127.0.0.1），
    # 对 host / bridge / none / 未发布端口 全部适用，无需探测 Docker 网络。
    # 若显式指定 PG_NETWORK（某个 bridge 网络名），则改用该网络 + 容器名解析。
    if [ -n "${PG_NETWORK:-}" ]; then
        MIGRA_NET="$PG_NETWORK"
        PG_DB_HOST="${PG_DB_HOST:-$CONTAINER}"
    else
        MIGRA_NET="container:${CONTAINER}"
        PG_DB_HOST="${PG_DB_HOST:-127.0.0.1}"
    fi
    print_info "migra 网络: $MIGRA_NET（DB 主机: $PG_DB_HOST）"
}

# ---- 准备 migra 镜像（仅首次，之后复用）----
# 默认：用官方 python:3.11-slim 现场 pip 自建（migra 无官方预构建镜像）
# 可选：MIGRA_BUILD_LOCAL=false MIGRA_IMAGE=<现成镜像> 改用拉取现成镜像
ensure_migra_image() {
    if docker image inspect "$MIGRA_IMAGE" &> /dev/null; then
        print_info "migra 镜像已存在: $MIGRA_IMAGE"
        return 0
    fi

    if [ "$MIGRA_BUILD_LOCAL" = true ]; then
        print_info "首次运行，本地构建 migra 镜像 $MIGRA_IMAGE ..."
        local pip_index_args="${PIP_INDEX:+-i $PIP_INDEX}"
        if printf 'FROM python:3.11-slim\nRUN pip install --no-cache-dir %s "migra[pg]"\n' "$pip_index_args" \
            | docker build -t "$MIGRA_IMAGE" - ; then
            print_success "migra 镜像构建完成"
        else
            print_error "migra 镜像构建失败（可设置 PIP_INDEX，如 https://pypi.tuna.tsinghua.edu.cn/simple）"
            exit 1
        fi
        return 0
    fi

    print_info "首次运行，拉取现成 migra 镜像: $MIGRA_IMAGE ..."
    if docker pull "$MIGRA_IMAGE"; then
        print_success "migra 镜像拉取完成"
    else
        print_error "拉取 $MIGRA_IMAGE 失败"
        print_info "  - 若网络无法拉取，可改用本地自建：MIGRA_BUILD_LOCAL=true ./sync_schema_migra.sh ..."
        print_info "  - 或指定可用镜像：MIGRA_IMAGE=<你的镜像> ./sync_schema_migra.sh ..."
        exit 1
    fi
}

# 判断库是否存在
db_exists() {
    pg_psql -d postgres -tA -c "SELECT 1 FROM pg_database WHERE datname = '$1';" 2>/dev/null | grep -q 1
}

# 剥除库级语句后输出 SQL（不区分大小写）：
# pg_dump --create --clean 整库 dump 含 DROP/CREATE/ALTER DATABASE、\connect，
# 原样执行会打到真实库；统一经此过滤后再导入（参考库导入与新库导入共用）
strip_db_level_sql() {
    sed -E '/^[[:space:]]*(DROP|CREATE|ALTER|COMMENT[[:space:]]+ON)[[:space:]]+DATABASE/Id; /^[[:space:]]*\\connect/d; /^[[:space:]]*\\c /d' "$1"
}

# 新库创建：目标库不存在且 --create-missing 时，建库并全量导入目标 SQL（含表结构与种子数据）
# 仅作用于当前处理的这一个库，不影响其它库；失败时回收刚建的半成品库保持干净
create_missing_db() {
    local db="$1" sql_path="$2"

    if [ "$CREATE_MISSING" = false ]; then
        print_warning "$db: 现有库不存在，跳过（新库可加 --create-missing 一键创建，或走 install 全量导入）"
        return 1
    fi
    if [ "$APPLY" = false ]; then
        print_info "$db: 库不存在。[dry-run] 执行 --apply --create-missing 将创建该库并全量导入 $(basename "$sql_path")"
        return 0
    fi
    if [ "$ASSUME_YES" = false ]; then
        local confirm
        echo -ne "${YELLOW}[确认]${NC} 新库 ${RED}$db${NC} 不存在，将创建并全量导入 $(basename "$sql_path")。输入 yes 继续: "
        read -r confirm
        if [ "$confirm" != "yes" ]; then
            print_info "$db: 已取消创建"
            return 0
        fi
    fi

    print_section "创建新库: $db"
    local err_file
    err_file="$(mktemp)"
    if ! pg_psql -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$db\";" &> /dev/null; then
        print_error "$db: 创建数据库失败"
        rm -f "$err_file"
        return 1
    fi
    print_info "导入 $(basename "$sql_path")（含表结构与种子数据，已剥除库级语句）..."
    if strip_db_level_sql "$sql_path" | pg_psql -d "$db" -v ON_ERROR_STOP=1 --single-transaction -q > "$err_file" 2>&1; then
        rm -f "$err_file"
        print_success "$db: 新库创建并全量导入完成"
        print_warning "$db: 提醒——install 流程的库清单为硬编码，记得在 init-databases.sh 与 install_middleware_linux.sh 的 init_databases() 登记，否则全新装机不会自动创建该库"
        return 0
    else
        print_error "$db: 导入失败，最后输出："
        tail -n 20 "$err_file"
        rm -f "$err_file"
        # 刚创建的空库导入失败 → 回收，避免留下半成品（该库本就不存在，删除无数据风险）
        pg_psql -d postgres -c "DROP DATABASE IF EXISTS \"$db\";" &> /dev/null
        print_warning "$db: 已回收刚创建的库，修正 SQL 后可重试"
        return 1
    fi
}

# 检测差异 SQL 中的破坏性语句，命中则打印并返回 0（有破坏性）
detect_destructive() {
    local diff_file="$1"
    grep -inE 'drop[[:space:]]+table|drop[[:space:]]+column|set[[:space:]]+data[[:space:]]+type|drop[[:space:]]+constraint|drop[[:space:]]+not[[:space:]]+null' "$diff_file"
}

# 把差异 SQL 按「涉及破坏性语句的表」拆成两份（以表为单位隔离，不再整库一刀切拒绝）：
#   $2=安全部分：不涉及任何破坏性表的语句 —— 可安全应用
#   $3=破坏部分：涉及破坏性表的全部语句 —— 需 --allow-destructive 或人工处理
# 规则：某张表只要有一条破坏性语句，该表的所有变更都归入破坏部分，避免「只应用一半」破坏表内依赖。
# 无法识别目标表的破坏性语句（极少见）一并归入破坏部分，从安全侧排除，宁可保守。
split_destructive_by_table() {
    local diff_file="$1" safe_file="$2" destr_file="$3"
    : > "$safe_file"; : > "$destr_file"
    awk -v safefile="$safe_file" -v destfile="$destr_file" '
    # 从一条语句提取目标表名，兼容 "public"."t" / public.t 两种 migra 输出写法
    function table_of(s,   t){
        t=""
        if (match(s, /"public"\."[A-Za-z_][A-Za-z_0-9]*"/)) {
            t=substr(s,RSTART,RLENGTH); gsub(/"public"\."/,"",t); gsub(/"/,"",t)
        } else if (match(s, /[Pp][Uu][Bb][Ll][Ii][Cc]\.[A-Za-z_][A-Za-z_0-9]*/)) {
            t=substr(s,RSTART,RLENGTH); sub(/[Pp][Uu][Bb][Ll][Ii][Cc]\./,"",t)
        }
        return t
    }
    BEGIN{ RS=";" }
    {
        raw=$0
        s=raw; gsub(/^[[:space:]]+/,"",s); gsub(/[[:space:]]+$/,"",s)
        if (s=="") next
        n++; stmt[n]=s; tb[n]=table_of(s)
        sl=tolower(s)
        if (sl ~ /drop[[:space:]]+table/ || sl ~ /drop[[:space:]]+column/ || sl ~ /set[[:space:]]+data[[:space:]]+type/ || sl ~ /drop[[:space:]]+constraint/ || sl ~ /drop[[:space:]]+not[[:space:]]+null/) {
            if (tb[n] != "") dtab[tb[n]]=1; else dtab["__notable__"]=1
        }
    }
    END{
        for (i=1; i<=n; i++) {
            bad = (tb[i]!="" && (tb[i] in dtab)) || (tb[i]=="" && ("__notable__" in dtab))
            if (bad) print stmt[i] ";\n" > destfile
            else     print stmt[i] ";\n" > safefile
        }
    }
    ' "$diff_file"
}

# 列出差异中受破坏性影响的表名（逗号分隔），用于提示
destructive_tables() {
    local destr_file="$1"
    awk '
    function table_of(s,   t){
        t=""
        if (match(s, /"public"\."[A-Za-z_][A-Za-z_0-9]*"/)) { t=substr(s,RSTART,RLENGTH); gsub(/"public"\."/,"",t); gsub(/"/,"",t) }
        else if (match(s, /[Pp][Uu][Bb][Ll][Ii][Cc]\.[A-Za-z_][A-Za-z_0-9]*/)) { t=substr(s,RSTART,RLENGTH); sub(/[Pp][Uu][Bb][Ll][Ii][Cc]\./,"",t) }
        return t
    }
    BEGIN{ RS=";" }
    { s=$0; gsub(/^[[:space:]]+|[[:space:]]+$/,"",s); if(s=="")next; t=table_of(s); if(t!="" && !(t in seen)){seen[t]=1; out=out (out==""?"":", ") t} }
    END{ print out }
    ' "$destr_file"
}

# ---- 处理单个库 ----
# 返回: 0=成功(无论有无差异)  1=失败
process_db() {
    local db="$1"
    local sql_file_name sql_path ref_db from_url to_url diff_file err_file
    # 时间戳取运行级 RUN_TS（main 中生成）：同一次运行的所有产物共享时间戳，便于对应排查

    sql_file_name="$(get_sql_file "$db")"
    if [ -z "$sql_file_name" ]; then
        print_warning "$db: 没有对应的 SQL 文件映射，跳过"
        return 1
    fi
    sql_path="${SQL_DIR}/${sql_file_name}"
    if [ ! -f "$sql_path" ]; then
        print_error "$db: SQL 文件不存在: $sql_path"
        return 1
    fi
    if ! db_exists "$db"; then
        # 新库路径：--create-missing 时建库并全量导入；否则保持原跳过行为
        create_missing_db "$db" "$sql_path"
        return $?
    fi

    ref_db="${db}_ref"
    # 差异文件按 模块(库名)/日期 分层归档：schema_diffs/<db>/<YYYY-MM-DD>/<db>_<时间戳>.sql
    local diff_subdir="${DIFF_DIR}/${db}/${RUN_DATE}"
    mkdir -p "$diff_subdir"
    diff_file="${diff_subdir}/${db}_${RUN_TS}.sql"
    err_file="$(mktemp)"

    print_section "处理库: $db"
    print_info "目标结构文件: $sql_file_name"

    # 1) 重建临时参考库（两条 -c 各自独立执行，ON_ERROR_STOP 保证 CREATE 失败时退出非 0）
    print_info "创建临时参考库: $ref_db"
    if ! pg_psql -d postgres -v ON_ERROR_STOP=1 \
        -c "DROP DATABASE IF EXISTS \"$ref_db\";" \
        -c "CREATE DATABASE \"$ref_db\";" &> /dev/null; then
        print_error "$db: 创建参考库失败"
        rm -f "$err_file"
        return 1
    fi

    # 2) 导入目标结构到参考库，双重防护：
    #    ⚠️ 安全关键：*.sql 多为 pg_dump --create --clean 整库 dump，含 DROP/CREATE/ALTER DATABASE、
    #    \connect 等【库级语句】。若原样导入，里面的 `DROP DATABASE "<db>"` 会打到【真实生产库】！
    #    防护一：sed 剥除库级语句（不区分大小写），只让纯表结构进入参考库；
    #    防护二：--single-transaction —— DROP/CREATE DATABASE 不能在事务块内执行，
    #            即使有漏网语句也会直接报错中止，绝不会真正落到生产库。
    print_info "导入目标结构到参考库（已剥除 DROP/CREATE/ALTER DATABASE、\\connect 等库级语句）..."
    if ! strip_db_level_sql "$sql_path" \
        | pg_psql -d "$ref_db" -v ON_ERROR_STOP=1 --single-transaction -q > "$err_file" 2>&1; then
        print_error "$db: 目标结构导入参考库失败，最后输出："
        tail -n 20 "$err_file"
        pg_psql -d postgres -c "DROP DATABASE IF EXISTS \"$ref_db\";" &> /dev/null
        rm -f "$err_file"
        return 1
    fi

    # 3) migra 对比：现有库(from) -> 参考库(to)
    #    始终用 --unsafe 生成"完整"差异，便于人工审阅；是否应用破坏性语句由 --allow-destructive 控制
    print_info "运行 migra 生成差异 ..."
    from_url="postgresql://${PG_USER}:${PG_PASSWORD}@${PG_DB_HOST}:${PG_PORT}/${db}"
    to_url="postgresql://${PG_USER}:${PG_PASSWORD}@${PG_DB_HOST}:${PG_PORT}/${ref_db}"

    docker run --rm --network "$MIGRA_NET" "$MIGRA_IMAGE" \
        migra --unsafe "$from_url" "$to_url" > "$diff_file" 2>"$err_file"
    local rc=$?

    # 清理参考库
    if [ "$KEEP_REF" = false ]; then
        pg_psql -d postgres -c "DROP DATABASE IF EXISTS \"$ref_db\";" &> /dev/null
    else
        print_info "保留参考库: $ref_db（--keep-ref）"
    fi

    # migra 退出码：0=无差异  2=有差异  其它=出错
    if [ $rc -eq 0 ]; then
        print_success "$db: 结构已与目标一致，无需变更"
        rm -f "$diff_file" "$err_file"
        return 0
    elif [ $rc -ne 2 ]; then
        print_error "$db: migra 执行失败 (exit $rc)"
        tail -n 20 "$err_file"
        rm -f "$diff_file" "$err_file"
        return 1
    fi
    rm -f "$err_file"

    # 有差异：打印（dry-run 时记入清单，供结尾汇总与生成 apply 命令）
    if [ "$APPLY" = false ]; then
        DIFF_DBS+=("$db")
    fi
    print_warning "$db: 检测到结构差异，已保存到: $diff_file"
    echo ""
    echo -e "${BLUE}---------- 差异 SQL ($db) ----------${NC}"
    cat "$diff_file"
    echo -e "${BLUE}-----------------------------------${NC}"
    echo ""

    # 检测破坏性语句
    local destructive_lines
    destructive_lines="$(detect_destructive "$diff_file")"
    if [ -n "$destructive_lines" ]; then
        print_warning "$db: 差异中包含【破坏性语句】（可能丢数据），请重点审阅："
        echo "$destructive_lines" | while IFS= read -r l; do echo -e "    ${RED}$l${NC}"; done
        echo ""
    fi

    # dry-run：到此为止
    if [ "$APPLY" = false ]; then
        print_info "$db: 当前为 dry-run，未做任何修改。审阅无误后加 --apply 应用。"
        print_info "  也可手动编辑 $diff_file 后执行："
        print_info "  docker exec -i -e PGPASSWORD=*** $CONTAINER psql -U $PG_USER -d $db < $diff_file"
        return 0
    fi

    # ---- 应用流程 ----
    # 决定本次实际应用哪些 SQL：
    #   - 无破坏性，或显式 --allow-destructive：应用整份差异（原行为）
    #   - 有破坏性且未授权：按表隔离 —— 仅应用「不涉及破坏性表」的安全变更，
    #     破坏性表的全部变更留到 *.destructive.sql，待 --allow-destructive 重跑或人工处理，
    #     不再因个别表的破坏性操作而整库拒绝。
    local apply_file="$diff_file"
    local destr_only_file=""
    if [ -n "$destructive_lines" ] && [ "$ALLOW_DESTRUCTIVE" = false ]; then
        local safe_file="${diff_file%.sql}.safe.sql"
        destr_only_file="${diff_file%.sql}.destructive.sql"
        split_destructive_by_table "$diff_file" "$safe_file" "$destr_only_file"
        local dtabs
        dtabs="$(destructive_tables "$destr_only_file")"
        print_warning "$db: 差异含破坏性语句，已【按表隔离】（破坏性仅影响下列表，不再整库拒绝）："
        print_warning "      受影响表: ${dtabs:-未识别}"
        print_warning "      这些表的变更已留存(未应用): $destr_only_file"
        print_info    "      如确认执行：加 --allow-destructive 重跑，或人工 psql 应用上面文件"
        if [ ! -s "$safe_file" ]; then
            print_warning "$db: 本次差异全部涉及破坏性表，无可安全应用的变更，已跳过应用。"
            rm -f "$safe_file"
            return 0
        fi
        print_success "$db: 其余安全表变更将正常应用: $safe_file"
        apply_file="$safe_file"
    fi

    if [ "$ASSUME_YES" = false ]; then
        local confirm
        echo -ne "${YELLOW}[确认]${NC} 即将对库 ${RED}$db${NC} 应用差异（会先自动备份）。输入 yes 继续: "
        read -r confirm
        if [ "$confirm" != "yes" ]; then
            print_info "$db: 已取消应用"
            return 0
        fi
    fi

    # 应用前备份（同样按 模块(库名)/日期 分层归档：backups/<db>/<YYYY-MM-DD>/）
    local backup_subdir="${BACKUP_DIR}/${db}/${RUN_DATE}"
    mkdir -p "$backup_subdir"
    local backup_file="${backup_subdir}/${db}_${RUN_TS}.sql"
    print_info "$db: 备份到 $backup_file ..."
    # 退出码 + 非空双重校验：pg_dump 失败或产出空文件（连接半途断开等）都中止应用
    if docker exec -e PGPASSWORD="$PG_PASSWORD" "$CONTAINER" pg_dump -U "$PG_USER" -d "$db" > "$backup_file" 2>/dev/null \
        && [ -s "$backup_file" ]; then
        print_success "$db: 备份完成（$(du -h "$backup_file" | cut -f1)）"
    else
        print_error "$db: 备份失败或备份为空，已中止应用（未做任何修改）"
        rm -f "$backup_file"
        return 1
    fi

    # 应用差异（apply_file：无破坏性时=整份差异；按表隔离时=仅安全表部分）
    print_info "$db: 应用差异 SQL ..."
    if pg_psql -d "$db" -v ON_ERROR_STOP=1 -q < "$apply_file" &> "${diff_file}.apply.log"; then
        print_success "$db: 结构同步完成"
        if [ -n "$destr_only_file" ] && [ -s "$destr_only_file" ]; then
            print_warning "$db: 注意——破坏性表的变更未应用，仍待处理: $destr_only_file"
        fi
        return 0
    else
        print_error "$db: 应用失败，请查看日志: ${diff_file}.apply.log"
        print_warning "$db: 如有部分语句已执行，可用备份恢复: $backup_file"
        return 1
    fi
}

# ---- 主流程 ----
main() {
    print_section "PostgreSQL 表结构同步（migra）"
    if [ "$APPLY" = true ]; then
        print_warning "模式: 应用 (--apply)$([ "$CREATE_MISSING" = true ] && echo '  + 缺失库自动创建')$([ "$ALLOW_DESTRUCTIVE" = true ] && echo '  + 允许破坏性变更')"
    else
        print_info "模式: dry-run（只生成差异，不修改数据库）"
    fi
    print_info "目标库: ${TARGET_DBS[*]}"

    check_prerequisites
    ensure_migra_image

    # 运行级时间戳：本次运行所有产物（差异/备份/日志）共用，便于互相对应
    RUN_TS="$(date +%Y%m%d_%H%M%S)"
    RUN_DATE="$(date +%Y-%m-%d)"   # 日期子目录：差异/备份按 模块(库名)/日期 分层归档
    mkdir -p "$DIFF_DIR"

    DIFF_DBS=()   # dry-run 发现差异的库（结尾汇总用）
    local ok=0 fail=0
    local failed_dbs=()
    for db in "${TARGET_DBS[@]}"; do
        if process_db "$db"; then
            ok=$((ok + 1))
        else
            fail=$((fail + 1))
            failed_dbs+=("$db")
        fi
    done

    print_section "执行结果"
    echo -e "成功: ${GREEN}$ok${NC}  失败/跳过: ${RED}$fail${NC}  / 共 ${#TARGET_DBS[@]}"
    if [ ${#failed_dbs[@]} -gt 0 ]; then
        print_warning "以下库未成功处理: ${failed_dbs[*]}"
    fi
    # dry-run 汇总：直接给出每个有差异库的应用命令，免去翻屏定位
    if [ "$APPLY" = false ] && [ ${#DIFF_DBS[@]} -gt 0 ]; then
        print_warning "以下 ${#DIFF_DBS[@]} 个库存在结构差异（审阅上方差异后可执行）："
        local d
        for d in "${DIFF_DBS[@]}"; do
            echo -e "  ${YELLOW}./sync_schema_migra.sh --db $d --apply${NC}"
        done
    fi
    [ -d "$DIFF_DIR" ] && print_info "差异文件目录: ${DIFF_DIR}/<库>/${RUN_DATE}/"
    [ "$APPLY" = true ] && [ -d "$BACKUP_DIR" ] && print_info "备份目录: ${BACKUP_DIR}/<库>/${RUN_DATE}/"

    [ $fail -gt 0 ] && exit 1 || exit 0
}

main
