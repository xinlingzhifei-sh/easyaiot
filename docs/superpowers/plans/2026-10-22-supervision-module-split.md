# 实施计划：supervision 从 iot-system 拆出

设计：`docs/superpowers/specs/2026-10-22-supervision-module-split.md`
分支：`claude/supervision-split-936f81`（不推送、不合并到 main）

每个阶段单独提交，每个提交都要在测试服务器容器里通过对应模块的单元测试。

## 阶段 0：基线

1. 在测试服务器临时容器（`maven:3.9-eclipse-temurin-21`，`-m` 限内存）里跑 `mvn -pl iot-system/iot-system-biz -am test`，记录基线（通过数 / 失败数）。
2. 只打包 `DEVICE/` 需要的目录（`git archive HEAD DEVICE -o x.tar`），放到 `/opt/yfeieye-unittest-supervision-split/`，测完删除。

## 阶段 1：设计文档与计划（提交 1）

- 本计划与设计文档。

## 阶段 2：特征测试（提交 2）

在 `SupervisionAlertReviewServiceTest` 中为没有服务级覆盖的方法补测试，复用文件内的内存替身：

- `findReviewCaseByItem`：有案件 / 无案件。
- `listRules`：保存后列出。
- `reconcileEventProjections`：已转事件条目的投影对账结果。
- `scheduleReviewReportDelivery`：报表投递计划的返回结构。
- `recordEvidenceDownload`：对已就绪导出任务记录下载审计。

只断言当前行为。跑通后提交。

## 阶段 3：模块拆出（提交 3）

1. 新建 `DEVICE/iot-supervision/pom.xml`（聚合）、`iot-supervision-api/pom.xml`、`iot-supervision-biz/pom.xml`；`DEVICE/pom.xml` 加模块。
2. `git mv`：
   - `iot-system-api/.../enums/supervision/*` → `iot-supervision-api`
   - `iot-system-biz/.../{controller/admin,dal/dataobject,dal/pgsql,job,service}/supervision/**` → `iot-supervision-biz`（`NotifyReviewRuntimeOutboxPublisher` 除外）
   - `resources/sql/migrations/*`、`resources/schemas/*` → `iot-supervision-biz/src/main/resources`
   - `src/test/.../supervision/*` → `iot-supervision-biz/src/test`（`MediaPermissionCheckControllerTest`、`NotifyReviewRuntimeOutboxPublisherTest` 除外）
3. 端口：新增 `ReviewOperatorPermissionChecker`；`ConfiguredReviewCameraPermissionResolver` 改用端口；iot-system-biz 新增 `PermissionServiceReviewOperatorPermissionChecker`。
4. `NotifyReviewRuntimeOutboxPublisher` 移到 iot-system-biz 的 `framework.supervision` 包，测试跟着改 import。
5. `iot-system-biz/pom.xml` 依赖 `iot-supervision-biz`。
6. 测试里按模块目录取文件的路径（`application.yaml`、`docker-compose.yml`）改成新相对路径。
7. 更新 `.scripts` 中引用迁移路径和源码路径的脚本与测试。
8. 本地 `mvn -o test-compile` 编译通过 → 服务器跑 `mvn -pl iot-system/iot-system-biz,iot-supervision/iot-supervision-biz -am test` 与 `node --test` 相关 `.scripts` 测试 → 提交。

## 阶段 4：拆分大类（提交 4）

1. 用脚本（放 scratchpad，不入库）把 `SupervisionAlertReviewServiceImpl` 解析成成员块，按设计 §5.2 的归属表生成组件类：
   - 依赖字段改为 `deps.xxx`；
   - 跨组件调用加上组件字段前缀；
   - 静态方法、常量、私有 record 放到归属类，组件用 `import static` / 单类型 import 引用；
   - 检查组件依赖图无环、同名方法（重载）在同一个类中。
2. 门面保留构造器、setter、注解，方法体改为一行委托。
3. 编译 → 服务器跑测试（含阶段 2 的特征测试）→ 提交。
4. 统计拆分前后行数。

## 阶段 5：收尾

- `node .scripts/verify-repository-secret-hygiene.mjs`。
- 服务器清理临时目录与容器。
- 在共享笔记目录留交接说明；最终报告给出模块图、行数变化、兼容性说明、待决策事项、与 Boot 3 会话的冲突面。
