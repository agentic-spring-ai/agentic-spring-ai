# Persistence and Executor Migration

Status: Proposed for 2.2, removal phase targeted for 3.0.

## Objective

Keep Agentic Spring AI Core focused on graph, agent, persistence, and execution
contracts while moving vendor-specific database and Docker implementations to
Agentic Spring AI Extensions. The migration must preserve 2.2 source, binary,
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

Extensions will own these new artifacts:

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

## Compatibility Phases

### 2.1

- Keep all existing public persistence and executor classes in their current
  artifacts.
- Remove only dependencies proven unused by source and runtime tests.
- Add this migration contract without changing public API.

### 2.2

- Add the four Extensions artifacts and their new packages.
- Copy behavior into the new implementations; do not move the old fully
  qualified class names.
- Mark the old Core database saver, JDBC store, and Docker executor classes
  `@Deprecated(since = "2.2.0", forRemoval = true)`.
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
  stores, or executors in 2.2. Users must construct and inject these
  implementations explicitly.

### 3.0

- Remove the deprecated database saver implementations, `DatabaseStore`,
  `DockerCodeExecutor`, and their vendor dependencies from Core.
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

The 2.2 implementation must add checked-in schema snapshots for each dialect;
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

## 2.2 Verification Gates

The 2.2 implementation is not complete until all of these pass:

1. Binary API comparison from Core 2.1 to 2.2 for every old public class.
2. A consumer fixture using old imports and builders compiles against Core 2.2.
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

## 3.0 Verification Gates

1. Core compile and runtime dependency trees contain no PostgreSQL, MySQL,
   Oracle, Redisson, MongoDB, H2, or Docker Java artifacts.
2. Core no longer contains the deprecated vendor implementation FQCNs.
3. Extensions retain all 2.2 storage compatibility and Docker security tests.
4. The migration guide names every removed class and its replacement.

## Release Order

For 2.2 and later, publish Core first, then publish Extensions against that
exact Core release. Core documentation may reference Extensions coordinates,
but Core build and published POMs must remain independent of Extensions.
