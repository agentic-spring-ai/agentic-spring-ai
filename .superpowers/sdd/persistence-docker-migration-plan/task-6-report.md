# Task 6 Report: Core compatibility deprecations and binary API gate

## Scope

- Preserved migrated Core persistence and Docker executor classes for source/binary compatibility.
- Marked migrated compatibility APIs as deprecated for removal in `2.2.0`.
- Added reflection-based regression tests for deprecation metadata.
- Added a local binary compatibility gate for graph-core and builtin-nodes artifacts.

## RED / GREEN

- RED: `MigratedPersistenceDeprecationTests` initially failed because
  `AbstractJdbcCheckpointSaver`, `DatabaseStore`, and `RedisSaver` did not carry
  `@Deprecated`.
- RED: `DockerCodeExecutorDeprecationTest` initially failed because
  `DockerCodeExecutor` did not carry `@Deprecated`.
- GREEN: after adding `@Deprecated(since = "2.2.0", forRemoval = true)` and
  replacement Javadoc, both targeted deprecation test suites passed.

## Deprecated compatibility APIs

- `io.github.agentic.spring.ai.graph.checkpoint.savers.jdbc.AbstractJdbcCheckpointSaver`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.h2.H2Saver`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.h2.CreateOption`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.mysql.MysqlSaver`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.mysql.CreateOption`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.oracle.OracleSaver`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.oracle.CreateOption`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.postgresql.PostgresSaver`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.postgresql.CreateOption`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.redis.RedisSaver`
- `io.github.agentic.spring.ai.graph.checkpoint.savers.mongo.MongoSaver`
- `io.github.agentic.spring.ai.graph.store.stores.DatabaseStore`
- `io.github.agentic.spring.ai.graph.node.code.DockerCodeExecutor`

## Replacement artifacts

- JDBC saver/store APIs now point to:
  `io.github.agentic-spring-ai:agentic-spring-ai-graph-persistence-jdbc`
- MongoDB saver API now points to:
  `io.github.agentic-spring-ai:agentic-spring-ai-graph-persistence-mongodb`
- Redis saver API now points to:
  `io.github.agentic-spring-ai:agentic-spring-ai-graph-persistence-redis`
- Docker executor API now points to:
  `io.github.agentic-spring-ai:agentic-spring-ai-code-executor-docker`

## Verification

- `mvn -B -pl :agentic-spring-ai-graph-core -Dtest=MigratedPersistenceDeprecationTests test`
  - Result: `BUILD SUCCESS`; 3 tests, 0 failures, 0 errors.
- `mvn -B -pl :agentic-spring-ai-starter-builtin-nodes -am -Dtest=DockerCodeExecutorDeprecationTest test`
  - Result: `BUILD SUCCESS`; 1 targeted test, 0 failures, 0 errors.
- `mvn -B test`
  - Result: `BUILD SUCCESS`; 8 reactor modules, 122 builtin-nodes tests with 1 Docker runtime skip.
- `tools/scripts/verify-core-binary-compatibility.sh`
  - Result: reports regenerated under `target/binary-compatibility/`.
  - `agentic-spring-ai-graph-core-japicmp.md`: no binary incompatibility failure.
  - `agentic-spring-ai-starter-builtin-nodes-japicmp.md`: no binary incompatibility failure.
- Binary gate failure history:
  - First failure: transient Maven Central transfer failure while resolving
    dependencies (`Premature end of Content-Length delimited message body`).
  - Second failure: japicmp CLI rejected unsupported `--access-modifier`; 0.23.1
    help shows the supported form is `-a <accessModifier>`.
  - Third run: reused ignored isolated cache `target/binary-compatibility/m2`,
    regenerated both reports, and completed with compatible reports.
- `git diff --check`
  - Result: no whitespace errors.

## Files

- `.superpowers/sdd/persistence-docker-migration-plan/task-6-report.md`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/h2/CreateOption.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/h2/H2Saver.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/jdbc/AbstractJdbcCheckpointSaver.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/mongo/MongoSaver.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/mysql/CreateOption.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/mysql/MysqlSaver.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/oracle/CreateOption.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/oracle/OracleSaver.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/postgresql/CreateOption.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/postgresql/PostgresSaver.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/checkpoint/savers/redis/RedisSaver.java`
- `agentic-spring-ai-graph-core/src/main/java/io/github/agentic/spring/ai/graph/store/stores/DatabaseStore.java`
- `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/compatibility/MigratedPersistenceDeprecationTests.java`
- `spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/src/main/java/io/github/agentic/spring/ai/graph/node/code/DockerCodeExecutor.java`
- `spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/src/test/java/io/github/agentic/spring/ai/graph/node/code/DockerCodeExecutorDeprecationTest.java`
- `tools/make/java.mk`
- `tools/scripts/verify-core-binary-compatibility.sh`

## Self-review

- No migrated Core API signatures or dependencies were removed.
- Active examples and version pins were not modified.
- Extensions migration worktree was inspected read-only to verify replacement
  artifact/FQCN names and remains clean.
- Binary gate reports are build output under `target/` and intentionally not
  committed; the script is committed so the gate can be reproduced.

## Notes

- The binary compatibility gate compares locally built JARs from baseline commit
  `c128f02584fc976ee641572074db2e556466f6a2` and the current candidate.
- The gate uses isolated reusable Maven cache `target/binary-compatibility/m2`, which is under ignored build output and does not rely on published `2.1.0-dev` artifacts.
- Active examples and version pins were not modified.
