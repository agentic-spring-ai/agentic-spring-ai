# 示例仓库迁移记录

最后验证日期：2026-09-07。

## 迁移结果

框架仓库的 `examples/` 已迁入 [agentic-spring-ai/examples](https://github.com/agentic-spring-ai/examples) 仓库的 `examples/`，包含 12 个 Maven 模块、177 个原文件。原文件中 16 份 Markdown 调整了启动命令或链接，其余 161 个文件逐字节一致。Java 包名、应用配置、依赖版本和模块 POM 均未修改。

本地目标路径为 `../spring-ai-alibaba-examples`。该仓库的 `origin` 从 `https://github.com/springaialibaba/spring-ai-alibaba-examples.git` 改为 `https://github.com/agentic-spring-ai/examples.git`；`main` 从 `98288c29` 快进 7 个提交至 `1198f16c`，跟踪 `origin/main`。修改前工作区干净，原提交保存在 `backup/examples-before-migration-20260907` 分支。

框架仓库远端保持原值。目标仓库接收示例的提交为 `3940e478`，已通过普通推送更新远端 `main`。主仓库将示例删减、入口更新和本记录作为独立迁出提交。两端迁移提交均包含 `[skip ci]`，跳过远端 CI；未修改远端 workflow。

## 文件与行为变化

| 仓库 | 文件 | 变化 |
| --- | --- | --- |
| 框架 | `examples/**` | 177 个文件迁出；目标逐文件校验成功后移除源文件 |
| 框架 | `README.md`、`README-zh.md` | 示例入口改为独立仓库；启动命令切换到目标仓库执行 |
| 框架 | `AGENTS.md`、`CLAUDE.md` | 更新示例目录位置 |
| 框架 | `docs/graph-engineering.md`、`docs/graph-engineering-repoops-lightweight.md` | 图工程源码及拓扑链接指向目标仓库 |
| 框架 | `tools/ci-config/labeler.yml` | 移除已迁出示例的路径标签规则 |
| 框架 | `docs/examples-repository-migration.md` | 记录结果、证据、限制与回滚方式 |
| 目标 | `examples/**` | 接收原示例，修正 Markdown 中的 Maven 命令及失效路径 |
| 目标 | `examples/pom.xml` | 新增仅聚合 12 个示例的独立构建入口，不继承目标根 POM |
| 目标 | `examples/README.md` | 新增中文示例索引、环境要求和依赖准备说明 |
| 目标 | `README.md`、`README-en.md` | 新增迁入示例入口 |
| 目标 | `pom.xml` | 项目 URL 更新为新仓库，旧示例依赖管理保持原值 |
| 目标 | `.gitignore` | 为迁入示例的 Java `node/` 目录增加例外，防止原有 `**/node/**` 规则遗漏 7 个节点类 |

目标仓库原有示例保持独立。迁入示例通过 `mvn -f examples/pom.xml verify` 聚合构建，单模块通过 `mvn -f examples/chatbot/pom.xml spring-boot:run` 等命令运行。依赖版本仍为 Spring Boot `4.1.1`、Spring AI `2.0.1`、核心及 Extensions `2.1.0-dev`。

## 验证与证据

本地备份与日志位于框架仓库 `target/examples-migration-20260907/`，该目录由现有 Git 忽略规则排除。完整原文件清单及 SHA-256 见 `manifest.json`；迁移准备副本见 `stage/`。

| 检查 | 命令或证据 | 结果 |
| --- | --- | --- |
| 迁移完整性及静态回归 | `python3 target/examples-migration-20260907/verify.py` | 通过：177 个文件齐全、161 个文件字节一致、仅 16 份 Markdown 调整、源文件均已迁出 |
| 模块与链接 | 同一验证脚本 | 通过：12 个模块完整映射、父 POM 路径独立、迁入 Markdown 本地链接有效 |
| 补丁空白 | 两个仓库分别执行 `git diff --check`；提交前另执行暂存区检查及归档基线比对 | 工作区检查通过；暂存区暴露原有空白告警，核验无新增空白问题，保留原文件空白及 Markdown 换行，见 `staged-whitespace-check.log` |
| 提交完整性 | 将 `git ls-files examples` 与清单比对，并逐文件比较 `git show HEAD:<path>` 与工作区内容 | 通过：179 个示例文件全部进入提交，包括 177 个原文件及 2 个新增文件 |
| 新上游 | `git ls-remote`、`git fetch`、`git rev-list --left-right --count HEAD...FETCH_HEAD` | 只读核验远端 `main`；同步前结果为 `0 7`，仅快进 |
| Maven 构建与测试 | JDK 17、Maven 3.9.10；对 `stage/examples/pom.xml` 执行 `verify`，公开依赖下载至隔离缓存 | 未通过：12 个模块在模型解析阶段受核心及 Extensions `2.1.0-dev` BOM 缺失阻断，见 `build.log` |
| Lint | `make lint` | 未运行完成：本机缺少 `yamllint`，见 `lint.log` |
| 许可证工具 | `make licenses-check` | 未运行完成：本机缺少 `license-eye`，见 `licenses.log` |

Maven 验证使用与最终目标逐文件一致的准备副本，命令从框架仓库执行：

```shell
mise exec java@temurin-17.0.19+10 -- mvn -B \
  -f target/examples-migration-20260907/stage/examples/pom.xml \
  -Dmaven.repo.local=target/examples-migration-20260907/m2 \
  -Dmaven.wagon.http.retryHandler.count=1 \
  -Dmaven.wagon.rto=10000 verify
```

外部核验日期为 2026-09-07，仅访问用户指定 GitHub 仓库和 Maven 官方仓库；GitHub API 根目录用于检查目标是否有冲突目录，Maven Central 父 POM 请求返回 HTTP 200。网页工具读取 GitHub 失败、沙箱中的 Git 无法连接本机代理后，使用获准的只读 Git 与 HTTP 请求完成核验，未改代理配置。

## 未验证项与后续验收

本次没有修改 Java 行为，但完整编译、单元测试、集成测试、契约测试及端到端运行尚未通过依赖解析阶段，不能宣称测试通过或覆盖率达到 90%。依赖漏洞扫描未完成，不能确认无高危 CVE。性能、压力、容量与混沌测试未执行，迁移本身没有新增运行时路径。

后续先将对应版本的核心和所需 Extensions 模块安装至本地 Maven 仓库，再从目标仓库执行 `mvn -f examples/pom.xml verify`，并按各示例要求准备模型凭据及外部服务。补齐 `yamllint` 和 `license-eye` 后重新执行项目静态检查。目录迁移的完整性校验不能替代上述构建验收。

## 回滚方式

迁移前的源文件、README、指南和标签配置保存在 `target/examples-migration-20260907/source-backup.tar`；目标根文件备份为同目录的 `README.md.before`、`README-en.md.before`、`pom.xml.before`、`target-gitignore.before`，原 Git 配置为 `target-git-config.before`。

共享分支的回滚应新增撤销提交，避免重写共享历史。先撤销主仓库对应的迁出提交，恢复示例与入口，再根据需要撤销目标仓库的 `3940e478`；回滚推送须获得授权。未提交的本地回滚可在确认没有后续改动后恢复备份中的对应文件。若还需恢复目标远端配置，可使用备份的原 Git 配置。不要通过整体重置覆盖其他工作。

上游快进与目录迁移可分别回滚；若还需要回到同步前历史，可在妥善保存工作区后使用备份分支。日常继续开发应保留已同步的上游提交，仅撤销迁移改动。
