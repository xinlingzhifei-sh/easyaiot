# yFeiEye Full Project Server Deployment Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Commit every deployable project change on the current branch and deploy the complete full-profile yFeiEye delta to the production server through PuTTY with rollback and live proof.

**Architecture:** Treat the currently deployed commit as the immutable release base, create a new source release from a binary Git delta, and keep the previous source and WEB releases intact. Build all affected modules before the public WEB switch, create only missing database structures, then recreate services whose bind mounts must resolve to the new source release.

**Tech Stack:** Git, PowerShell, PuTTY `plink`/`pscp`, Bash, Docker Compose, Maven/Java 21, pnpm/Vite/Vue, Python/pytest, PostgreSQL, Nginx

---

### Task 1: Freeze the release scope and safety baseline

**Files:**
- Inspect: repository-wide tracked changes
- Exclude: `deploy-packages/`, `.codex-smoke/`, `.playwright-cli/`, `.superpowers/`, `tmp/`, `output/`, screenshots, dependency folders
- Preserve uncommitted: root `AGENTS.md` because it is local agent guidance rather than application code

- [ ] **Step 1: Record the local and remote Git baseline**

Run:

```powershell
git status --short --untracked-files=all
git branch --show-current
git rev-parse HEAD
git ls-remote --heads origin codex/login-page-restoration
```

Expected: the current branch is `codex/login-page-restoration`; deployment artifacts remain untracked and excluded.

- [ ] **Step 2: Record the current production release and health baseline**

Run through PuTTY without storing credentials:

```bash
readlink -f /opt/yfeieye-source/current
readlink -f /opt/yfeieye-web/current
cat /opt/yfeieye-source/current/DEPLOY_COMMIT
cat /opt/yfeieye-web/current/DEPLOY_COMMIT
df -hP /
docker ps --filter health=unhealthy --format '{{.Names}}|{{.Status}}'
nginx -t
```

Expected: source and WEB markers agree on the release base; no unhealthy container blocks deployment.

- [ ] **Step 3: Prove stateful mounts are release-independent**

Run:

```bash
docker inspect --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}' minio-server
docker exec minio-server cat /proc/self/mountinfo | grep ' /data '
docker inspect $(docker ps -q) --format '{{range .Mounts}}{{.Source}}{{println}}{{end}}' | grep '//deleted'
```

Expected: MinIO uses `/opt/yfeieye-source/shared/docker/minio_data`, its data is non-empty, and no live mount contains `//deleted`.

### Task 2: Run release gates against every affected module

**Files:**
- Validate: changed shell scripts under `.scripts/`, `AI/`, `VIDEO/`, `DEVICE/`, `WEB/`, `APP/`, and `VISUALIZE/`
- Validate: `WEB/`, `APP/`, `VISUALIZE/`, `DEVICE/`, `AI/`, and `VIDEO/`

- [ ] **Step 1: Run repository integrity checks**

Run:

```powershell
git diff --check
git diff --name-only --diff-filter=ACMRTUXB 9d5f2bf68da30a420ef242a8c75185b022d4d213 HEAD -- '*.sh'
```

For every returned shell script, assign its repository-relative name to `$path` and run `bash -n "$path"` from a Linux-compatible Bash environment. Expected: exit code 0 for every file.

- [ ] **Step 2: Validate WEB**

Run from `WEB/`:

```powershell
pnpm type:check
pnpm build
$tests = Get-ChildItem tests -Filter *.test.ts
$tests | ForEach-Object { pnpm exec tsx $_.FullName }
```

Expected: type check and build exit 0; all Node assertion tests pass.

- [ ] **Step 3: Validate APP and VISUALIZE**

Run:

```powershell
pnpm --dir APP install --frozen-lockfile
pnpm --dir APP type-check
pnpm --dir APP build
pnpm --dir VISUALIZE install --frozen-lockfile
$env:NODE_OPTIONS='--max-old-space-size=8192'
$env:HUSKY='0'
pnpm --dir VISUALIZE build
```

Expected: both production builds exit 0. If APP type-check reproduces only the pre-existing missing generated declaration references while the affected APP diff and production build pass, record that baseline blocker explicitly rather than changing unrelated files.

- [ ] **Step 4: Validate Java and Python changes**

Run:

```powershell
mvn -f DEVICE/pom.xml test -Drevision=1.0.0
python -m pytest AI/test_minio_proxy.py AI/tests
python -m pytest VIDEO/tests
```

Expected: all focused suites exit 0. If an unrelated historical test fails, record it separately and prove the affected acceptance surface before deciding whether deployment is safe.

### Task 3: Commit and push only deployable project changes

**Files:**
- Delete: the eight already-removed tracked legacy images under `.image/`
- Add: `docs/superpowers/plans/2026-07-22-full-project-server-deployment.md`

- [ ] **Step 1: Stage the intended changes**

Run:

```powershell
git add -u -- .image
git add docs/superpowers/plans/2026-07-22-full-project-server-deployment.md
git diff --cached --check
git diff --cached --stat
```

Expected: only the tracked image removals and this release plan are staged; no credential, build artifact, screenshot, package, or local agent file is staged.

- [ ] **Step 2: Commit and push the current branch**

Run:

```powershell
git commit -m "chore(release): prepare full project deployment"
git push origin codex/login-page-restoration
git ls-remote --heads origin codex/login-page-restoration
```

Expected: the remote branch SHA equals local `HEAD`.

### Task 4: Package committed source and WEB artifacts

**Files:**
- Create: `deploy-packages/yfeieye-source-${baseShort}-${headShort}.patch`
- Create: `deploy-packages/yfeieye-web-${headShort}.tar.gz`
- Create: `deploy-packages/yfeieye-app-${headShort}.tar.gz`
- Create: `deploy-packages/yfeieye-visualize-${headShort}.tar.gz`
- Create: SHA-256 manifest beside all artifacts

- [ ] **Step 1: Build the binary source delta from committed objects**

Run:

```powershell
$base = '9d5f2bf68da30a420ef242a8c75185b022d4d213'
$head = git rev-parse HEAD
$patch = "deploy-packages/yfeieye-source-$($base.Substring(0,9))-$($head.Substring(0,9)).patch"
git diff --binary --full-index $base $head --output=$patch -- .
git apply --stat --binary $patch
```

Expected: the patch is generated from committed Git objects. Its clean application is checked against the hard-linked copy of the recorded base on the server before mutation.

- [ ] **Step 2: Package the already-verified WEB, APP, and VISUALIZE builds**

Run:

```powershell
$head = git rev-parse HEAD
$web = "deploy-packages/yfeieye-web-$($head.Substring(0,9)).tar.gz"
$app = "deploy-packages/yfeieye-app-$($head.Substring(0,9)).tar.gz"
$visualize = "deploy-packages/yfeieye-visualize-$($head.Substring(0,9)).tar.gz"
tar -C WEB/dist -czf $web .
tar -C APP/dist/build/h5 -czf $app .
tar -C VISUALIZE/dist -czf $visualize .
Get-FileHash -Algorithm SHA256 $patch,$web,$app,$visualize
```

Expected: all artifacts have non-zero sizes and recorded SHA-256 values.

### Task 5: Create the immutable source release and build affected modules

**Files:**
- Create: the source release held in `$new_source`, computed from timestamp and committed SHA
- Preserve: the previous `/opt/yfeieye-source/current` target
- Upload: source patch, WEB archive, APP archive, and VISUALIZE archive under the SHA-specific `$upload_dir`

- [ ] **Step 1: Upload with PuTTY SCP and verify hashes**

Run with `D:\PuTTY\pscp.exe -scp`, using the supplied credential only as an execution input. On the server, run `sha256sum -c` against the local manifest.

Expected: both remote hashes equal the local hashes.

- [ ] **Step 2: Clone the current source release and apply the delta**

Run:

```bash
head="${DEPLOY_HEAD:?DEPLOY_HEAD is required}"
head_short="$(printf '%s' "$head" | cut -c1-9)"
release_id="$(date +%Y%m%d-%H%M)-${head_short}-full"
upload_dir="/opt/yfeieye-upload/${head}"
old_source="$(readlink -f /opt/yfeieye-source/current)"
new_source="/opt/yfeieye-source/releases/${release_id}"
cp -al "$old_source" "$new_source"
cd "$new_source"
git apply --check --binary "${upload_dir}/source.patch"
git apply --binary "${upload_dir}/source.patch"
printf '%s\n' "$head" > DEPLOY_COMMIT
install -d APP/dist-prebuilt
tar -xzf "${upload_dir}/app.tar.gz" -C APP/dist-prebuilt
install -d VISUALIZE/dist-prebuilt
tar -xzf "${upload_dir}/visualize.tar.gz" -C VISUALIZE/dist-prebuilt
```

Expected: `DEPLOY_COMMIT` equals the committed local SHA; the previous immutable release remains untouched and available for rollback.

- [ ] **Step 3: Build changed runtime images serially from the new release**

Run from `new_source` with `EASYAIOT_DEPLOY_PROFILE=full`, serial execution, and local builds:

```bash
bash DEVICE/install_linux.sh build
(cd AI && set -- help && source ./install_linux.sh >/dev/null && detect_architecture && configure_architecture && build_with_cache "")
(cd VIDEO && set -- help && source ./install_linux.sh >/dev/null && build_with_cache "")
(cd APP && set -- help && source ./install_linux.sh >/dev/null && SKIP_VITE_BUILD=1 docker_build_image -t app-service:latest .)
(cd VISUALIZE && set -- help && source ./install_linux.sh >/dev/null && SKIP_VITE_BUILD=1 docker_build_image -t visualize-service:latest .)
```

Expected: every command exits 0. WEB static output plus the APP and VISUALIZE images are supplied by verified local artifacts; middleware FUXA uses its pinned upstream image. The function-level build calls preserve Docker layer cache while avoiding the scripts' `update` path because release trees intentionally contain no `.git` directory.

### Task 6: Create additive database structures and switch runtime services

**Files:**
- Read: `.scripts/postgresql/iot-visualize10.sql`
- Read: `DEVICE/iot-device/iot-device-biz/src/main/resources/sql/device_threshold_alarm.sql`
- Apply: `.scripts/go-view/patches/visualize_menu.sql`
- Apply: `.scripts/go-view/patches/visualize_demo_seed.sql`

- [ ] **Step 1: Create only the missing VISUALIZE database**

Check first:

```bash
docker exec postgres-server psql -U postgres -Atc "SELECT count(*) FROM pg_database WHERE datname='iot-visualize20'"
```

If the result is `0`, create the database, then stream only the dump content after its `\connect` line into that new database. Do not execute the dump's `DROP DATABASE` statement.

Expected: `iot-visualize20` exists with the required `visualize_*` tables.

- [ ] **Step 2: Add the new device tables without dropping existing data**

Stream `device_threshold_alarm.sql` into `iot-device20` after filtering its four leading `DROP TABLE` statements.

Expected: `device_property_threshold`, `device_alarm_strategy`, `device_threshold_alarm`, and `device_associated_link` exist; no existing table is dropped.

- [ ] **Step 3: Apply idempotent menu and demo seeds**

Run:

```bash
docker exec -i postgres-server psql -U postgres -d ruoyi-vue-pro20 < .scripts/go-view/patches/visualize_menu.sql
bash .scripts/go-view/seed_visualize_demo.sh
```

Expected: transaction exit 0 and the super-admin role owns the VISUALIZE permissions.

- [ ] **Step 4: Atomically switch source and start changed services**

Run:

```bash
ln -sfn "$new_source" /opt/yfeieye-source/current
docker compose -f /opt/yfeieye-source/current/.scripts/docker/docker-compose.yml up -d FUXA
EASYAIOT_DEPLOY_PROFILE=full bash /opt/yfeieye-source/current/DEVICE/install_linux.sh restart
bash /opt/yfeieye-source/current/AI/install_linux.sh restart
bash /opt/yfeieye-source/current/VIDEO/install_linux.sh restart
(cd /opt/yfeieye-source/current/APP && docker compose -f docker-compose.yaml up -d --force-recreate --remove-orphans)
(cd /opt/yfeieye-source/current/VISUALIZE && docker compose -f docker-compose.yaml up -d --force-recreate --remove-orphans)
```

Expected: changed containers are recreated from the new release and become healthy. If any command fails, repoint `current` to the preserved previous source release and recreate the affected service from that release before continuing.

- [ ] **Step 5: Seed FUXA after it becomes healthy**

Run:

```bash
bash /opt/yfeieye-source/current/.scripts/fuxa/seed_fuxa_demo.sh
```

Expected: the FUXA import returns HTTP 200 or 204.

### Task 7: Publish WEB and prove the live release

**Files:**
- Create: `/opt/yfeieye-web/releases/${release_id}` using the variables established in Task 5
- Preserve: previous `/opt/yfeieye-web/current` target

- [ ] **Step 1: Extract WEB to a new release and switch atomically**

Run:

```bash
new_web="/opt/yfeieye-web/releases/${release_id}"
install -d "$new_web"
tar -xzf "${upload_dir}/web.tar.gz" -C "$new_web"
printf '%s\n' "$head" > "${new_web}/DEPLOY_COMMIT"
ln -sfn "$new_web" /opt/yfeieye-web/current
nginx -t
systemctl reload nginx
```

Expected: Nginx reloads successfully and public HTML references the new asset hash.

- [ ] **Step 2: Verify commit mapping, containers, databases, and public routes**

Run:

```bash
cat /opt/yfeieye-source/current/DEPLOY_COMMIT
cat /opt/yfeieye-web/current/DEPLOY_COMMIT
docker ps --filter health=unhealthy --format '{{.Names}}|{{.Status}}'
docker ps --filter status=restarting --format '{{.Names}}|{{.Status}}'
docker exec postgres-server psql -U postgres -Atc "SELECT datname FROM pg_database WHERE datname='iot-visualize20'"
curl -fsS http://127.0.0.1:48080/actuator/health
curl -fsS http://127.0.0.1:48095/actuator/health
curl -fsS http://127.0.0.1:8002/health
curl -fsS http://127.0.0.1:1881/
```

Also verify public `/yfeieye/`, its static asset hash, a representative authenticated-fail-closed API, the algorithm-task API, face/plate model status, and a known GB28181/media path.

Expected: local HEAD, remote branch, both server markers, service health, database presence, public static assets, API behavior, and media behavior all agree.

- [ ] **Step 3: Re-run stateful mount checks after recreation**

Run:

```bash
docker exec minio-server cat /proc/self/mountinfo | grep ' /data '
docker inspect $(docker ps -q) --format '{{range .Mounts}}{{.Source}}{{println}}{{end}}' | grep '//deleted'
```

Expected: MinIO remains on the shared path, data remains non-empty, and no recreated container references a deleted release.
