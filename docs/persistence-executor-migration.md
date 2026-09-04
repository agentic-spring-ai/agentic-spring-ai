# Persistence and Executor Migration

Status: Introduced in 2.1. Database implementations coexist through 2.x; the old
Core Docker executor was removed before the 2.1 release.

## Objective

Keep Agentic Spring AI Core focused on graph, agent, persistence, and execution
contracts while moving vendor-specific database and Docker implementations to
Agentic Spring AI Extensions. The migration must preserve 2.1 source, binary,
storage, and rollback compatibility.

Core must never depend on Extensions. The same fully qualified class name must
never be present in both a Core jar and an Extensions jar.

## Target Ownership

Core continues to own:

- `BaseCheckpointSaver`, `SaverConfig`, checkpoint and serializer contracts.
- `Store` and its request, result, and item contracts.
- `MemorySaver`, `FileSystemSaver`, `MemoryStore`, and `FileSystemStore`.
- `CodeExecutor`, code execution configuration/result types, and
  `CodeExecutorNodeAction`.
- `LocalCommandlineCodeExecutor` until a separate security and compatibility
  review decides otherwise.

Extensions owns these new artifacts in reviewed commit
`5e7d4912800548adf3a36744779841cea131c519`:

| Artifact | New package | Implementations |
| --- | --- | --- |
| `agentic-spring-ai-graph-persistence-jdbc` | `io.github.agentic.spring.ai.graph.persistence.jdbc` | `AbstractJdbcCheckpointSaver`, `H2Saver`, `PostgresSaver`, `MysqlSaver`, `OracleSaver`, `DatabaseStore` |
| `agentic-spring-ai-graph-persistence-redis` | `io.github.agentic.spring.ai.graph.persistence.redis` | `RedisSaver` |
| `agentic-spring-ai-graph-persistence-mongodb` | `io.github.agentic.spring.ai.graph.persistence.mongodb` | `MongoSaver` |
| `agentic-spring-ai-code-executor-docker` | `io.github.agentic.spring.ai.graph.node.code.docker` | `DockerCodeExecutor` |

`RedisStore` and `MongoStore` remain in Core for this migration. They currently
provide in-memory behavior rather than real Redis or MongoDB persistence, so
moving them into vendor artifacts would misrepresent their behavior. Their
naming and ownership require a separate deprecation decision.

## Implementation Status

The 2.1 migration work is implemented across these reviewed commits:

- Core deprecation and binary compatibility:
  `a843ec5877f89480087a30e05b2756625a3ae432`.
- Core CI, examples, and migration documentation:
  `57657b9afd28688ea8a47fa290131a4dec098a2e`.
- Extensions persistence and Docker implementations:
  `5e7d4912800548adf3a36744779841cea131c519`.
- Extensions Core pin and cross-repository verification:
  `d6be576b358c2f56c6f52cf4f75fc9fe6032184f`.

- Core keeps the old database compatibility classes and marks the migrated
  JDBC, Redis, and MongoDB implementations
  `@Deprecated(since = "2.1.0", forRemoval = true)`.
- Core removes the old `DockerCodeExecutor`; the Extensions artifact is the only
  supported Docker implementation.
- Core active Redis documentation examples now use
  `io.github.agentic.spring.ai.graph.persistence.redis.RedisSaver` from
  `agentic-spring-ai-graph-persistence-redis`.
- Core production POMs and BOMs do not declare any Extensions dependency.
  Extensions artifacts are installed only for CI/example compilation through
  `tools/github-actions/setup-extensions`.
- `setup-extensions` is pinned to reviewed Extensions commit
  `5e7d4912800548adf3a36744779841cea131c519` and installs these four migrated
  artifacts for Core integration jobs:
  `agentic-spring-ai-graph-persistence-jdbc`,
  `agentic-spring-ai-graph-persistence-mongodb`,
  `agentic-spring-ai-graph-persistence-redis`, and
  `agentic-spring-ai-code-executor-docker`.

## Compatibility Phases

### 2.1

- Add the four Extensions artifacts and their new packages.
- Copy behavior into the new implementations; do not move the old fully
  qualified class names.
- Mark the old Core database saver and JDBC store classes
  `@Deprecated(since = "2.1.0", forRemoval = true)` and remove the old Core
  Docker executor after parity verification.
- Preserve every old constructor, public or protected method, static factory,
  builder method, nested type, visibility, return type, enum constant,
  exception contract, and default value. In particular, do not normalize the
  existing `datasource` versus `dataSource` builder method spelling.
- Move documentation and active examples to the new Extensions artifacts while
  retaining one deprecated-source compilation fixture for the old API.
- Manage new artifacts only from `agentic-spring-ai-extensions-bom`.
- Do not add automatic saver, store, or executor beans. Any future Spring Boot
  auto-configuration must be explicitly enabled, use `@ConditionalOnClass` and
  `@ConditionalOnMissingBean`, and leave user beans authoritative.
- Do not add `ServiceLoader` or `META-INF/services` discovery for savers,
  stores, or executors in 2.1. Users must construct and inject these
  implementations explicitly.

### 2.2

- Continue the database compatibility period; do not remove the deprecated Core
  persistence APIs.
- Keep the 2.1 physical storage formats, Docker defaults, and replacement
  package names stable.
- Continue compiling legacy database import fixtures and running bidirectional
  Core/Extensions persistence compatibility tests.
- Migrate remaining applications and documentation to the Extensions artifacts.

### 3.0

- Remove the deprecated database saver implementations, `DatabaseStore`, and
  their vendor dependencies from Core. Docker dependencies are already absent.
- Remove old compatibility tests only after the new implementations pass all
  cross-version storage tests.
- Keep Core SPI and provider-neutral implementations unchanged.

## Storage Compatibility

New implementations must preserve existing physical formats exactly.

Redis checkpoint compatibility includes:

- `graph:checkpoint:content:`
- `graph:thread:meta:`
- `graph:thread:reverse:`
- `graph:checkpoint:lock:`
- Existing checkpoint serialization and Base64 encoding.

MongoDB checkpoint compatibility includes:

- Database `check_point_db`.
- Collections `thread_meta` and `checkpoint_collection`.
- Id prefixes `mongo:thread:meta:` and `mongo:checkpoint:content:`.
- Content field `checkpoint_content` and thread fields `thread_id`,
  `thread_name`, and `is_released`.
- Existing checkpoint ordering and retention semantics.

JDBC compatibility includes the current table, column, index, sequence, JSON,
and content-type definitions for H2, PostgreSQL, MySQL, and Oracle:

- PostgreSQL tables `GraphThread` and `GraphCheckpoint`, including
  `parent_checkpoint_id`, JSONB `state_data`, `state_content_type`, `saved_at`,
  the thread foreign key, and all three `idx_lg4j*` indexes.
- H2/MySQL/Oracle tables `GRAPH_THREAD` and `GRAPH_CHECKPOINT`, including
  `checkpoint_id`, `thread_id`, `node_id`, `next_node_id`, `state_data`,
  `state_content_type` where currently present, `saved_at`, and the thread
  foreign key.
- H2 and MySQL `checkpoint_seq` ordering and
  `IDX_GRAPH_CHECKPOINT_THREAD_SEQUENCE`.
- H2/MySQL `active_thread_name` and `IDX_GRAPH_THREAD_ACTIVE_NAME`; Oracle's
  `IDX_GRAPH_THREAD_NAME_RELEASED` behavior.
- `DatabaseStore` default table `spring_ai_store` and its current columns,
  dialect-specific types, primary key, and upsert behavior.

The 2.1 implementation includes checked-in schema snapshots for each dialect;
tests compare generated DDL with those snapshots instead of duplicating SQL in
assertions.

Redis store compatibility remains governed by the current
`agentic:spring:ai:store:` prefix and legacy reads from
`spring:ai:alibaba:store:`. This contract must not be changed as part of the
checkpoint migration.

## Docker Compatibility

The Extensions Docker executor must implement Core's existing `CodeExecutor`
and reuse the same configuration and result classes. It must preserve timeout,
output limits, disabled networking, read-only root filesystem, writable tmpfs,
memory, CPU, PID, capability, and no-new-privileges defaults.

No executor should be discovered or activated automatically merely because its
jar is present.

## 2.1 Release Gates

The 2.1 Core and Extensions releases are not publishable until all of these pass:

1. Binary API comparison from the pre-migration Core baseline to Core 2.1 for
   every old public class.
2. A consumer fixture using old imports and builders compiles against Core 2.1.
3. For every migrated persistence implementation and every supported dialect
   or backend, the old implementation writes and the new implementation reads
   the same checkpoint or store data.
4. For every migrated persistence implementation and every supported dialect
   or backend, the new implementation writes and the old implementation reads
   the same data, so rollback requires only removing the new dependency.
5. JDBC schema snapshots match for every supported dialect.
6. Redis keys and MongoDB documents match byte-for-byte or field-for-field.
7. Docker security-default and output-limit tests pass against both executors.
8. Core does not depend on Extensions, new Extensions artifacts are managed
   only by the Extensions BOM, and Core gains no new non-optional vendor
   dependency.
9. Extensions tests pass with Core artifacts installed first from an isolated
   Maven repository.

## Completed 2.1 Release Evidence

- Core binary API gate:
  `tools/scripts/verify-core-binary-compatibility.sh` compares locally built
  graph-core and builtin-nodes jars from baseline commit
  `c128f02584fc976ee641572074db2e556466f6a2` against the current candidate.
  Reports generated on 2026-09-03 show compatible changes for both modules.
- Core deprecation regression tests:
  `MigratedPersistenceDeprecationTests` passed for the retained database
  compatibility APIs. Docker parity was verified before direct removal.
- JDBC extension compatibility:
  `agentic-spring-ai-graph-persistence-jdbc` passed DDL snapshot checks, H2
  old/new bidirectional checkpoint and `DatabaseStore` compatibility, and real
  PostgreSQL, MySQL, and Oracle bidirectional compatibility. The full JDBC
  module test with Oracle enabled reported 18 tests, 0 failures, 0 errors,
  0 skipped.
- Redis extension compatibility:
  `RedisPersistenceCompatibilityTests` passed against real Valkey/Redis,
  covering old-write/new-read, new-write/old-read, key prefixes, Base64
  checkpoint content, and serialized checkpoint content.
- MongoDB extension compatibility:
  `MongoPersistenceCompatibilityTests` passed against real MongoDB, covering
  old-write/new-read, new-write/old-read, database/collection names, document
  id prefixes, persisted fields, Base64 checkpoint content, and serialized
  checkpoint content.
- Docker executor regression coverage:
  `DockerCodeExecutorTests` passes 12 replacement tests covering defaults,
  timeout, cleanup, output truncation, language mapping, Java classpath command
  shape, restart no-op, and execution directory cleanup. Migration-time parity
  was verified before the old Core implementation was removed.

Release the Core artifacts first. Then release Extensions with both
`revision=2.1.0` and `agentic-spring-ai.version=2.1.0`, and verify the published
coordinates from a clean consumer repository.

## 3.0 Verification Gates

1. Core compile and runtime dependency trees contain no PostgreSQL, MySQL,
   Oracle, Redisson, MongoDB, H2, or Docker Java artifacts.
2. Core no longer contains the deprecated vendor implementation FQCNs.
3. Extensions retain all 2.1 storage compatibility and Docker security tests.
4. The migration guide names every removed class and its replacement.

## Release Order

For 2.1 and later, publish Core first, then publish Extensions against that
exact Core release. Core documentation may reference Extensions coordinates,
but Core build and published POMs must remain independent of Extensions.
The migrated JDBC, Redis, MongoDB, and Docker executor artifacts are part of the
Extensions 2.1 release.
