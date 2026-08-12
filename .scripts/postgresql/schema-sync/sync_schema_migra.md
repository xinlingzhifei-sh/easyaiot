# PostgreSQL 表结构同步操作手册（基于 migra）

> 配套脚本：`./sync_schema_migra.sh`（与本手册同目录）
> 适用对象：在服务器上对 Docker 中的 PostgreSQL 数据库执行**表结构增量同步**的运维/开发人员。

---

## 1. 这个脚本是做什么的

把 `.scripts/postgresql/*.sql`（各业务库的目标建表脚本）当作**目标结构**，对比 Docker 里**正在运行的现有数据库**，自动算出"把现有库结构对齐到目标结构"所需的差异 SQL（`ALTER / ADD COLUMN / CREATE` 等），并可选择应用。

**核心特性：**

- ✅ **保留数据**：只改结构，不重导全量数据。
- ✅ **默认 dry-run**：默认只生成并打印差异，不碰数据库；必须显式 `--apply` 才会修改。
- ✅ **应用前自动备份**：`--apply` 时先 `pg_dump` 整库备份。
- ✅ **破坏性语句拦截**：差异中若含 `DROP` / 改列类型等危险语句，默认**拒绝应用**。
- ✅ **自动发现库**：按文件命名规约自动识别全部库，新增 `.sql` 无需改脚本。

### 适用 / 不适用场景

| 场景 | 是否适用 | 说明 |
|------|----------|------|
| 已有库的表结构有增量变更（加表/加列/加索引等） | ✅ 适用 | 本脚本的主用途 |
| 已有库的列类型变更、删列等破坏性变更 | ⚠️ 谨慎适用 | 需 `--allow-destructive`，且常需手工处理数据迁移 |
| 全新模块、库在 Docker 里还**不存在** | ❌ 不适用 | 走 `install` / `init-databases.sh` 全量导入（见 [场景 3](#场景-3新增一个数据库如何接入)） |
| 同步**数据**（INSERT 种子数据等） | ❌ 不适用 | migra 只比对结构，不同步任何数据行 |

---

## 2. 前置条件

执行前必须满足：

1. **在服务器上执行**（数据库容器在服务器，本地 Windows 不运行）。按"本地改、服务器跑"的工作流，改动的 `.sql` 要先上传到服务器对应目录。
2. **Docker 正常运行**，且 PostgreSQL 容器（默认名 `postgres-server`）处于运行状态：
   ```bash
   docker ps --filter name=postgres-server
   ```
3. **首次运行需能构建 migra 镜像**：默认用 Docker 官方镜像 `python:3.11-slim` 现场 `pip install "migra[pg]"` 自建（migra 无官方预构建镜像），**仅首次构建、之后复用**。pip 慢/失败见 [场景 4](#场景-4环境变量覆盖与离线处理)。
4. **脚本可执行**（或直接 `bash sync_schema_migra.sh ...` 运行，免 chmod）：
   ```bash
   cd /path/to/easyaiot/.scripts/postgresql/schema-sync
   chmod +x sync_schema_migra.sh
   ```

> 📁 **目录结构**：脚本与本手册在 `.scripts/postgresql/schema-sync/`；各业务库的目标 `*10.sql` 仍在上一级 `.scripts/postgresql/`。脚本会自动去上一级读取 `.sql`，差异/备份产物按【库名/日期】分层归档在 `schema-sync/` 内：差异 `schema_diffs/<库>/<日期>/`、备份 `backups/<库>/<日期>/`，每次运行新建当日子目录(归当前用户所有)，不会再散落 `*.rootbak.<时间戳>` 这类零碎目录。

---

## 3. 核心概念与机制

### 3.1 命名规约（务必遵守）

```
<名字>10.sql   <—>   数据库 <名字>20
```

| SQL 文件 | 对应数据库 |
|----------|-----------|
| `iot-device10.sql` | `iot-device20` |
| `iot-ai10.sql` | `iot-ai20` |
| `ruoyi-vue-pro10.sql` | `ruoyi-vue-pro20` |
| `iot-gb2818110.sql` | `iot-gb2818120` |

规则：文件名去掉 `.sql`，末尾的 `10` 换成 `20` 即库名。脚本据此**双向推导**。

### 3.2 自动发现

脚本启动时扫描**上一级目录**（`.scripts/postgresql/`）下所有 `*10.sql`，按规约推导出库清单（`ALL_DATABASES`）。**新增一个符合规约的 `.sql` 文件，无需修改脚本**即可被识别。查看当前识别到的库：

```bash
./sync_schema_migra.sh --help
```

### 3.3 工作原理（单库流程）

1. 在容器内建一个临时**参考库** `<db>_ref`；
2. 把目标 `.sql` 导入参考库（`ON_ERROR_STOP=1`，保证结构完整）；
3. 用 migra 对比：**现有库（from） → 参考库（to）**，生成对齐结构的差异 SQL；
4. 打印差异、检测破坏性语句；
5. dry-run 到此为止；`--apply` 则备份后应用；
6. 用完**删除参考库**（`--keep-ref` 可保留以便调试）。

> migra 始终以 `--unsafe` 生成**完整**差异（含 DROP 等），目的是让你**看全**所有变更；是否**应用**破坏性语句由 `--allow-destructive` 单独控制。

#### 导入参考库的双重安全防护（第 2 步）

各 `*10.sql` 多为 `pg_dump --create --clean` 整库 dump，开头带 `DROP DATABASE "<db>"` / `CREATE DATABASE` / `\connect` 等**库级语句**——若原样导入，`DROP DATABASE` 打到的是**真实生产库**。脚本对此做了双重防护：

1. **剥除库级语句**：导入前用 sed（不区分大小写）删掉所有 `DROP/CREATE/ALTER/COMMENT ON DATABASE` 与 `\connect`，只让纯表结构进入参考库；
2. **单事务兜底**：导入加 `--single-transaction`——`DROP/CREATE DATABASE` 无法在事务块内执行，即使有漏网语句也会直接报错中止，绝不会落到生产库。

因此**无需手工改造 `.sql` 文件**，pg_dump 整库 dump 可直接作为目标结构使用。

### 3.4 破坏性语句拦截

差异中命中以下模式即视为**破坏性**（可能丢数据），会被红色高亮列出：

- `DROP TABLE`、`DROP COLUMN`、`DROP CONSTRAINT`、`DROP NOT NULL`
- `SET DATA TYPE`（改列类型）

`--apply` 时若存在破坏性语句且**未加** `--allow-destructive`，脚本会**拒绝应用并退出**，不做任何修改。

### 3.5 文件产物位置

| 产物 | 路径 | 说明 |
|------|------|------|
| 差异 SQL | `schema_diffs/<db>_<时间戳>.sql` | 每次发现差异都会保存；无差异时不留文件 |
| 整库备份 | `backups/<db>_<时间戳>.sql` | 仅 `--apply` 实际应用前生成（`pg_dump` 明文） |
| 应用日志 | `schema_diffs/<db>_<时间戳>.sql.apply.log` | 仅应用阶段产生，失败时排查用 |

> 产物按 `<库>/<日期>/` 两级子目录归档（`<日期>` 格式 `YYYY-MM-DD`）；`<时间戳>` 格式为 `YYYYMMDD_HHMMSS`，**同一次运行的所有产物（差异/备份/日志）共享同一时间戳**，便于互相对应。

---

## 4. 参数速查

| 参数 | 作用 | 默认 |
|------|------|------|
| `sync` | **一键模式** = `--apply --create-missing`：已有库增量更新、缺失库创建并全量导入（逐库 `yes` 确认） | — |
| `--db <名称>` | 只处理指定库（可重复多次传入） | 不传则处理**全部**自动发现的库 |
| `--apply` | 应用差异（否则仅 dry-run 打印） | 关闭（dry-run） |
| `--allow-destructive` | 允许应用 `DROP`/改类型等破坏性语句 | 关闭（拒绝破坏性应用） |
| `--create-missing` | 目标库**不存在**时创建并全量导入对应 `*10.sql`（新模块建库，见[场景 3](#场景-3新增一个数据库如何接入)） | 关闭（不存在则跳过） |
| `--yes` / `-y` | 应用时跳过交互确认 | 关闭（需手动输入 `yes`） |
| `--keep-ref` | 保留临时参考库 `<db>_ref`（调试用） | 关闭（用完即删） |
| `-h` / `--help` | 显示帮助与已发现的库清单 | — |

---

## 5. 环境变量速查

所有变量均可在命令前临时覆盖，如 `PG_PASSWORD=xxx ./sync_schema_migra.sh ...`。

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PG_CONTAINER` | `postgres-server` | PostgreSQL 容器名 |
| `PG_USER` | `postgres` | 数据库用户 |
| `PG_PASSWORD` | `<POSTGRES_PASSWORD>` | 数据库密码（来自 compose 默认；线上改过须覆盖） |
| `PG_PORT` | `5432` | 容器内端口（用于 migra 连接串） |
| `PG_NETWORK` | 共享 postgres 容器网络栈 | 默认 `--network container:<容器>`（host/bridge/none/未发布端口都通）；设为某 bridge 网络名则改走该网络 |
| `PG_DB_HOST` | `127.0.0.1` | migra 连库主机名；默认走容器内回环，一般无需手动设 |
| `MIGRA_BUILD_LOCAL` | `true` | 默认用官方 `python:3.11-slim` 现场 `pip` 自建 migra；设 `false` 改用拉取现成镜像 |
| `MIGRA_IMAGE` | 自建时 `easyaiot-migra:local` | migra 镜像（`MIGRA_BUILD_LOCAL=false` 时默认 `djrobstep/migra:latest`） |
| `PIP_INDEX` | 空 | 仅 `MIGRA_BUILD_LOCAL=true` 生效，pip 镜像源（如清华源 `https://pypi.tuna.tsinghua.edu.cn/simple`） |
| `SQL_DIR` | 脚本上一级 `.scripts/postgresql/` | 目标 `*10.sql` 所在目录，一般无需改 |

---

## 6. 标准操作流程（分场景）

### 场景 1：同步某个已有数据库的表结构

> 最常见场景。以修改了 `iot-ai10.sql`（库 `iot-ai20`）为例。

**前置条件**：改动后的 `iot-ai10.sql` 已上传到服务器的 `.scripts/postgresql/`（本手册的上一级目录）；`iot-ai20` 库已存在。

#### 推荐：一步式（审阅 + 确认 + 应用一条命令完成）

```bash
cd /path/to/easyaiot/.scripts/postgresql/schema-sync
./sync_schema_migra.sh --db iot-ai20 --apply
```

`--apply` 本身就是交互式流程：**打印差异 → 你审阅 → 输入 `yes` 才会动库**（先自动 `pg_dump` 备份到 `backups/` 再应用）。审阅时发现不对，输入任意其他键即取消——效果等同 dry-run，数据库分毫未动。含破坏性语句时会**自动拒绝**应用（见下方「含破坏性变更的处理」）。

- 首次运行会用官方 `python:3.11-slim` 自动构建 migra 镜像（仅一次，之后复用）。
- 差异 SQL 同时保存到 `schema_diffs/iot-ai20_<时间戳>.sql` 留档。
- 免交互（脚本化）加 `--yes`；**首次操作生产库强烈建议保留人工确认**。

#### 可选：纯审阅 dry-run（只看不动，适合留档/送评审）

```bash
./sync_schema_migra.sh --db iot-ai20        # 不带 --apply 即 dry-run
./sync_schema_migra.sh                      # 全量 dry-run：结尾会汇总有差异的库并给出对应 --apply 命令
```

> ℹ️ 不必"先 dry-run 再 --apply"跑两遍——两者各自都会完整做一次比对（建参考库 + migra），常规操作直接 `--apply` 一步到位即可；dry-run 留给只想审阅或需要他人评审的场景。

#### 第 3 步 — 验证

```bash
# 查看表结构是否已更新
docker exec -e PGPASSWORD=<POSTGRES_PASSWORD> postgres-server \
    psql -U postgres -d iot-ai20 -c "\d 你的表名"
```

若结构变更涉及对应业务服务的 ORM/实体映射，重启该服务（示例）：

```bash
cd /path/to/easyaiot && ./AI/install_linux.sh restart
```

#### 含破坏性变更的处理（三选一）

- **A. 确认就是要删/改类型，且无需迁移数据**：
  ```bash
  ./sync_schema_migra.sh --db iot-ai20 --apply --allow-destructive
  ```
- **B. 改类型/加 NOT NULL 等需要先迁移数据**（推荐）：先 dry-run 生成差异文件，**手动编辑**补上数据回填/转换逻辑，再自行应用：
  ```bash
  # 编辑 schema_diffs/iot-ai20_<时间戳>.sql 后：
  docker exec -i -e PGPASSWORD=<POSTGRES_PASSWORD> postgres-server \
      psql -U postgres -d iot-ai20 -v ON_ERROR_STOP=1 < schema_diffs/iot-ai20_<时间戳>.sql
  ```
  > 注意：`--apply` 应用的是**脚本重新生成**的差异，不会读取你手工编辑过的文件；手工编辑后须按上面命令**自行 psql 应用**。
- **C. 不确定影响**：保持 dry-run，连同差异文件交给 DBA/负责人评审后再定。

> 🔴 **高风险提示**：`DROP COLUMN` / `DROP TABLE` 会**永久删除数据**；`ALTER COLUMN ... TYPE` 可能失败或截断；`SET NOT NULL` 在存在 NULL 行时会失败。务必先评估、先备份。

---

### 场景 2：全量同步所有已发现的库

> 不传 `--db` 即处理全部自动发现的库。

#### 一键：新增或更新所有库

```bash
./sync_schema_migra.sh sync          # = --apply --create-missing：逐库确认后执行
./sync_schema_migra.sh sync --yes    # 完全免交互（确认无风险后再用）
```

已存在的库走增量同步（差异审阅 → 确认 → 备份 → 应用），不存在的库自动创建并全量导入；破坏性语句依旧默认拦截。

#### 第 1 步 — 全量 dry-run

```bash
cd /path/to/easyaiot/.scripts/postgresql/schema-sync
./sync_schema_migra.sh
```

逐库打印差异；结尾汇总成功/失败数量，并**列出所有有差异的库及各自可直接复制执行的 `--apply` 命令**。差异文件分别存于 `schema_diffs/`。

> ℹ️ 不存在的库会被**跳过**并计入"失败/跳过"，属正常（见 [场景 3](#场景-3新增一个数据库如何接入)）。

#### 第 2 步 — 全量应用（仅安全增量）

```bash
./sync_schema_migra.sh --apply
```

每个有差异的库都会**单独**要求 `yes` 确认并各自备份。含破坏性语句的库会被**拒绝**（不影响其他库继续）。

> ⚠️ **前置条件 / 风险**：全量应用会同时改动多个库，建议**先逐库**在 [场景 1](#场景-1同步某个已有数据库的表结构) 跑通、确认无误后，再考虑全量。生产环境优先逐库操作。

---

### 场景 3：新增一个数据库如何接入

> migra 的"增量同步"只对**已存在**的库有效；全新库需先创建并全量导入。本脚本提供 `--create-missing` 一键完成。

#### 步骤

1. **按命名规约创建 SQL 文件**：在**上一级目录** `.scripts/postgresql/` 新增 `iot-<新模块>10.sql`（库将对应 `iot-<新模块>20`；放在 `schema-sync/` 子目录内不会被发现）。
   - ⚠️ 文件名**必须**以 `10.sql` 结尾，否则不会被自动发现。
2. **一键建库并全量导入**（只动这一个库，其余库不受影响）：
   ```bash
   ./sync_schema_migra.sh --db iot-<新模块>20 --apply --create-missing
   ```
   - 交互确认后：`CREATE DATABASE` → 剥除库级语句后**全量导入**（含表结构与 `COPY` 种子数据，单事务，失败自动回收半成品库）。
   - 不带 `--apply` 为 dry-run，仅提示将要创建。
3. **建库后**：该库进入"已存在"状态，**后续的结构变更**用本脚本常规增量同步（[场景 1](#场景-1同步某个已有数据库的表结构)）即可。
4. ✅ **install 流程已同样自动发现**：`init-databases.sh` 与 `install_middleware_linux.sh` 的 `init_databases()` 均按 `*10.sql` 规约自动扫描——新增库**无需在任何清单登记**，全新装机也会自动创建（前提：服务器部署的是新版脚本）。

#### 验证库是否已创建

```bash
docker exec -e PGPASSWORD=<POSTGRES_PASSWORD> postgres-server \
    psql -U postgres -d postgres -c "\l" | grep iot-<新模块>20
```

> ℹ️ 一句话：**放一个 `*10.sql` 就是接入；当前服务器用 `--create-missing` 立即建，全新装机由 install 自动建。**

---

### 场景 4：环境变量覆盖与离线处理

#### 线上密码与默认不同

```bash
PG_PASSWORD='线上真实密码' ./sync_schema_migra.sh --db iot-ai20
```

#### 容器名/端口/网络不同

```bash
PG_CONTAINER=my-postgres PG_PORT=5433 PG_NETWORK=yfeieye-network \
    ./sync_schema_migra.sh --db iot-ai20
```

> ℹ️ `PG_NETWORK` 默认无需设置（migra 容器直接共享 postgres 容器的网络栈，host/bridge/none/未发布端口都通）；仅当你想让 migra 走某个指定 bridge 网络时才设置。

#### migra 镜像构建慢 / 完全离线

```bash
# pip 源慢：指定国内源加速默认的本地自建（默认即自建，无需额外开关）
PIP_INDEX=https://pypi.tuna.tsinghua.edu.cn/simple ./sync_schema_migra.sh --db iot-ai20

# 完全离线：改用你已 push 到内网仓库（Nexus/Harbor）的现成镜像
MIGRA_BUILD_LOCAL=false MIGRA_IMAGE=your-registry/migra:latest ./sync_schema_migra.sh --db iot-ai20
```

> ⚠️ **前置条件**：自建需宿主机能联网装包；现成镜像需其内已正确安装 migra 且 `migra` 命令在 PATH 中。

---

### 场景 5：应用失败后的回滚恢复

`--apply` 在应用前会自动备份到 `backups/<db>_<时间戳>.sql`（`pg_dump` 明文）。应用失败时脚本会提示对应的备份路径与 `*.apply.log` 日志。

#### 第 1 步 — 定位问题

```bash
cat schema_diffs/<db>_<时间戳>.sql.apply.log
```

> 因应用使用 `ON_ERROR_STOP=1`，遇错即停，通常只会部分执行。

#### 第 2 步 — 用备份恢复

> 🔴 **风险/前置**：恢复前**先停掉所有使用该库的业务服务**，避免连接占用与写入冲突。`pg_dump` 明文备份需导入到一个**干净的库**，因此采用"删库重建后导入"。

```bash
DB=iot-ai20
BK=backups/${DB}_<时间戳>.sql
PGPASS=<POSTGRES_PASSWORD>

# 1) 断开连接并删库重建（确保目标库干净）
docker exec -e PGPASSWORD=$PGPASS postgres-server psql -U postgres -d postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${DB}';"
docker exec -e PGPASSWORD=$PGPASS postgres-server psql -U postgres -d postgres -c \
  "DROP DATABASE IF EXISTS \"${DB}\";"
docker exec -e PGPASSWORD=$PGPASS postgres-server psql -U postgres -d postgres -c \
  "CREATE DATABASE \"${DB}\";"

# 2) 从备份导入
docker exec -i -e PGPASSWORD=$PGPASS postgres-server \
  psql -U postgres -d "${DB}" -v ON_ERROR_STOP=1 < "$BK"
```

#### 第 3 步 — 验证并重启服务

```bash
docker exec -e PGPASSWORD=$PGPASS postgres-server psql -U postgres -d $DB -c "\dt"
# 确认无误后重启相关业务服务
```

> ℹ️ 若失败仅是个别语句报错且未造成数据破坏，也可手动修正差异文件后重新 `psql` 应用，不一定要整库回滚。回滚是**最坏情况下的兜底**。

---

## 7. 风险与注意事项（汇总）

- 🔴 **生产先备份**：脚本虽会自动备份，重要库建议在 `--apply` 前**再手动整库备份**一次。
- 🔴 **破坏性变更**：`DROP` / 改类型 / 加 `NOT NULL` 可能丢数据或失败；默认被拦截，加 `--allow-destructive` 即放行，务必先评审。
- 🟡 **只同步结构，不同步数据**：`.sql` 里新增的 `INSERT` 种子数据 migra **不会**应用，需另行手动导入。
- 🟡 **先测试后生产**：生产应用前，建议在测试环境跑通同一流程。
- 🟡 **密码一致性**：默认密码取自 compose；线上若改过，必须用 `PG_PASSWORD` 覆盖，否则连接失败。
- 🟡 **本地改→服务器跑**：改动的 `.sql` 必须先上传到服务器的 `.scripts/postgresql/`，脚本才会以最新结构为目标。
- 🟢 **dry-run 安全**：不带 `--apply` 的任何运行都不会改动**业务库**（仅临时创建/删除 `<db>_ref` 参考库），可反复执行审阅。

---

## 8. 故障排查 FAQ

| 现象 | 可能原因 / 处理 |
|------|----------------|
| `容器 postgres-server 未运行` | 先启动 PostgreSQL；或用 `PG_CONTAINER` 指定实际容器名 |
| `migra 执行失败` 且日志含 `Connection refused` | 默认共享 postgres 容器网络栈一般都通；核对 `PG_PORT` 是否为容器内端口，或用 `PG_NETWORK`/`PG_DB_HOST` 指定其它连接方式 |
| `migra 镜像构建失败` | pip 源不通：加 `PIP_INDEX=<国内源>`；或完全离线时用 `MIGRA_BUILD_LOCAL=false MIGRA_IMAGE=<内网镜像>` |
| `目标结构导入参考库失败` | 该 `.sql` 在空库上无法干净导入；按日志（tail 20 行）修正 SQL |
| `migra 执行失败 (exit ≠ 0/2)` | 多为连接串/密码/网络问题；核对 `PG_PASSWORD`、`PG_NETWORK` |
| `现有库不存在，跳过` | 该库尚未创建，属正常；新库走 [场景 3](#场景-3新增一个数据库如何接入) install 流程 |
| `差异含破坏性语句，已拒绝自动应用` | 评审后加 `--allow-destructive`，或手工编辑差异文件再自行 psql 应用 |
| `应用失败` | 查看 `schema_diffs/<db>_<时间戳>.sql.apply.log`；必要时按 [场景 5](#场景-5应用失败后的回滚恢复) 回滚 |

---

## 9. 速查命令卡

```bash
cd /path/to/easyaiot/.scripts/postgresql/schema-sync

./sync_schema_migra.sh sync                       # ⭐ 一键：全部库 新增或更新（逐库确认；免交互加 --yes）
./sync_schema_migra.sh --db iot-ai20 --apply      # ⭐ 推荐：单库一步式（审阅+确认+应用）
./sync_schema_migra.sh                            # 全量 dry-run（结尾汇总有差异的库及 apply 命令）
./sync_schema_migra.sh --db iot-ai20              # 单库 dry-run（只看）
./sync_schema_migra.sh --apply                    # 全量应用（逐库确认）
./sync_schema_migra.sh --db iot-ai20 --apply --allow-destructive  # 含破坏性
./sync_schema_migra.sh --db iot-xxx20 --apply --create-missing    # 新模块：建库并全量导入
./sync_schema_migra.sh --help                     # 看帮助 + 已发现的库
PG_PASSWORD=*** ./sync_schema_migra.sh --db iot-ai20             # 覆盖密码
PIP_INDEX=https://pypi.tuna.tsinghua.edu.cn/simple ./sync_schema_migra.sh  # pip 国内源加速首次构建
```
