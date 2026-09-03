# RAG and Network Node Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Extensions-owned RAG and network node artifacts while preserving the existing Core node APIs and behavior through the 2.x compatibility period.

**Architecture:** Core retains provider-neutral contracts and deprecated compatibility classes. Extensions adds one RAG artifact and one network artifact; HTTP and document extraction share one package-private network security policy. Cross-repository CI pins reviewed commits without creating a Maven dependency from Core to Extensions.

**Tech Stack:** Java 17, Maven, Spring AI 2.0.1, Spring Boot 4.1.1, Reactor, Apache HttpClient 5, OkHttp MockWebServer, JUnit 5, Mockito.

**Spec:** `docs/graph-node-integration-migration.md`

## Global Constraints

- Core must never depend on an Extensions artifact or the Extensions BOM.
- Existing Core public and protected APIs remain source and binary compatible through 2.x.
- New implementations use new packages; the same fully qualified class name must not exist in both repositories.
- New nodes preserve constructors, builder methods, nested public types, defaults, state keys, output shapes, exception behavior, and null handling.
- HTTP and document nodes share one package-private `NetworkAccessPolicy`; do not create two security implementations.
- Preserve private-network blocking, local-root containment, redirect validation, timeouts, byte limits, and cancellation behavior.
- Do not add Spring Boot auto-configuration, component scanning, `ServiceLoader` node discovery, or automatic node beans.
- Parser discovery inside `DocumentExtractorNode` continues to use the existing `DocumentParserProvider` SPI.
- New artifacts are part of Extensions 2.1; old Core classes use `@Deprecated(since = "2.1.0", forRemoval = true)` and remain until 3.0.
- Extensions owns new artifact versions through `agentic-spring-ai-extensions-bom`; Core BOM remains Core-only.
- Tests follow RED-GREEN: new-package tests must fail before implementation is added.
- Reflection parity tests compare public/protected constructors, methods, fields, and nested types after normalizing the old/new package prefixes.
- Release verification must resolve both Extensions version properties to `2.1.0` and prove every reactor flattened POM contains no `2.1.0-dev` reference.

## Commit and Pin DAG

```text
E1  Extensions RAG artifact
 |
E2  Extensions network artifact and reviewed implementation head
 |
C1  Core compatibility deprecations
 |
C2  Core CI/docs pin E2
 |
E3  Extensions setup-core pin C2
 |
final isolated Core HEAD -> Extensions HEAD verification
```

Core `setup-extensions` intentionally pins E2 rather than E3. E3 changes only
the Extensions Core pin; repinning it into Core would create an impossible SHA
cycle.

---

### Task 1: Add the RAG Node Extension

**Files:**

- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/pom.xml`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/agentic-spring-ai-extensions-bom/pom.xml`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-rag/pom.xml`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-rag/src/main/java/io/github/agentic/spring/ai/graph/node/rag/KnowledgeRetrievalNode.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-rag/src/test/java/io/github/agentic/spring/ai/graph/node/rag/KnowledgeRetrievalNodeTest.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-rag/src/test/java/io/github/agentic/spring/ai/graph/node/rag/KnowledgeRetrievalNodeCompatibilityTests.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-rag/src/test/java/io/github/agentic/spring/ai/graph/node/rag/PublicApiParity.java`

**Interfaces:**

- Consumes: Core `NodeAction`, `OverAllState`, `RerankModel`, `RerankOptions`, `RerankRequest`, and `RerankResponse`.
- Produces: `io.github.agentic.spring.ai.graph.node.rag.KnowledgeRetrievalNode` in artifact `agentic-spring-ai-graph-node-rag`.

- [ ] **Step 1: Add the module POM and failing new-package test**

Add the module to the Extensions root reactor and BOM. The module has direct
compile dependencies on `agentic-spring-ai-graph-core`,
`agentic-spring-ai-model`, `spring-ai-commons`, `spring-ai-rag`,
`spring-ai-vector-store`, `spring-core`, `slf4j-api`, and JSpecify; the old
builtin-nodes artifact is test scoped only.

Create a test that imports the new FQCN and exercises the ranker contract:

```java
var ranker = new KnowledgeRetrievalNode.KnowledgeRetrievalDocumentRanker(rerankModel, options);
assertEquals(List.of("doc-2", "doc-1"),
        ranker.process(new Query("query"), documents).stream().map(Document::getId).toList());
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
mvn -B -pl :agentic-spring-ai-graph-node-rag -am -Dtest=KnowledgeRetrievalNodeTest test
```

Expected: test compilation fails because `io.github.agentic.spring.ai.graph.node.rag.KnowledgeRetrievalNode` does not exist.

- [ ] **Step 3: Add the implementation without semantic refactoring**

Copy the Core `KnowledgeRetrievalNode` implementation and change only its package to:

```java
package io.github.agentic.spring.ai.graph.node.rag;
```

Keep all public constructors, builder methods, nested types, state-key precedence, retrieval, rerank, prompt augmentation, and output behavior unchanged.

- [ ] **Step 4: Add old/new compatibility coverage**

Use one deterministic `VectorStore` test double and one deterministic
`RerankModel` to run old and new nodes with identical configuration. Assert
literal equality for retrieval query/filter/top-k/similarity inputs, returned
document ids, augmented prompt, configured/default output keys, state-key versus
preset-value precedence, disabled/enabled rerank behavior, and rerank request
fields. Cover null/invalid required configuration with identical exception type
and stable message fragments.

Use `PublicApiParity` to recursively compare the old and new top-level classes
and public nested types. Normalize
`io.github.agentic.spring.ai.graph.node.KnowledgeRetrievalNode` to
`io.github.agentic.spring.ai.graph.node.rag.KnowledgeRetrievalNode` before
comparing signatures; fail on a missing or extra public/protected constructor,
method, field, or nested type.

- [ ] **Step 5: Verify and commit**

Run:

```bash
mvn -B -pl :agentic-spring-ai-graph-node-rag -am test
mvn -B -pl :agentic-spring-ai-graph-node-rag dependency:analyze
git diff --check
```

Commit only the RAG artifact, root Reactor, and BOM changes:

```bash
git commit -m "feat: add graph RAG node extension"
```

### Task 2: Add the Shared Network Node Extension

**Files:**

- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/pom.xml`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/agentic-spring-ai-extensions-bom/pom.xml`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/pom.xml`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/main/java/io/github/agentic/spring/ai/graph/node/network/HttpNode.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/main/java/io/github/agentic/spring/ai/graph/node/network/DocumentExtractorNode.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/main/java/io/github/agentic/spring/ai/graph/node/network/NetworkAccessPolicy.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/test/java/io/github/agentic/spring/ai/graph/node/network/HttpNodeTest.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/test/java/io/github/agentic/spring/ai/graph/node/network/DocumentExtractorNodeTest.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/test/java/io/github/agentic/spring/ai/graph/node/network/NetworkAccessPolicyTest.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/test/java/io/github/agentic/spring/ai/graph/node/network/NetworkNodeCompatibilityTests.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/test/java/io/github/agentic/spring/ai/graph/node/network/PublicApiParity.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/test/java/io/github/agentic/spring/ai/graph/node/network/TestDocumentParserProvider.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/graph-nodes/agentic-spring-ai-graph-node-network/src/test/resources/META-INF/services/io.github.agentic.spring.ai.document.DocumentParserProvider`
- Copy: `test.png` into the new module test resources.

**Interfaces:**

- Consumes: Core `NodeAction`, `OverAllState`, graph exceptions/file storage, and model `DocumentParser` contracts.
- Produces: new-package `HttpNode` and `DocumentExtractorNode`; both consume the same package-private `NetworkAccessPolicy`.

- [ ] **Step 1: Add the module POM and failing tests**

Declare direct compile dependencies for `agentic-spring-ai-graph-core`,
`agentic-spring-ai-model`, `reactor-core`, `spring-core`, `spring-web`,
`spring-webflux`, `jackson-databind`, `httpclient5`, `httpcore5`,
`httpcore5-reactive`, and `slf4j-api`. Declare builtin-nodes, Spring Boot test,
OkHttp, and MockWebServer as test dependencies.

Copy the existing singular-named `HttpNodeTest`, `DocumentExtractorNodeTest`,
and `NetworkAccessPolicyTest` into the new package so they import the new
classes. Add `TestDocumentParserProvider` and its test service descriptor to
prove parser discovery from `ServiceLoader`.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
mvn -B -pl :agentic-spring-ai-graph-node-network -am \
  -Dtest=HttpNodeTest,DocumentExtractorNodeTest,NetworkAccessPolicyTest test
```

Expected: test compilation fails because the new-package classes do not exist.

- [ ] **Step 3: Add the shared implementation**

Copy Core `HttpNode`, `DocumentExtractorNode`, and `NetworkAccessPolicy` into the new package:

```java
package io.github.agentic.spring.ai.graph.node.network;
```

Keep `NetworkAccessPolicy` package-private and use it from both public nodes. Do not copy it into separate HTTP and document packages.

- [ ] **Step 4: Add old/new behavior compatibility tests**

Create parameterized factory-based tests covering:

```text
HTTP: GET/non-2xx response, URL/header/query substitution, JSON/raw/form bodies,
      basic auth, retries, binary file response, private-address rejection,
      custom-WebClient preflight, request timeout, null/invalid input
Document: local TXT/JSON, explicit parser, ServiceLoader parser, regular-file
          and local-root checks, unsupported extension, remote-disabled mode,
          redirect validation, private-address rejection, max bytes, total
          deadline, null/invalid input
```

Assert the same literal state maps or exception category/message fragments for old and new factories. Verify blocked requests never reach `MockWebServer`.

Use `PublicApiParity` to recursively compare public/protected constructors,
methods, fields, and public nested types for old/new `HttpNode` and
`DocumentExtractorNode`, normalizing the old/new package prefixes. Assert that
the new `NetworkAccessPolicy` remains package-private.

- [ ] **Step 5: Verify and commit**

Run:

```bash
mvn -B -pl :agentic-spring-ai-graph-node-network -am test
mvn -B -pl :agentic-spring-ai-graph-node-network dependency:analyze
git diff --check
```

Commit only the network artifact, root Reactor, and BOM changes:

```bash
git commit -m "feat: add graph network node extension"
```

### Task 3: Deprecate Core Compatibility Nodes

**Files:**

- Modify: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/src/main/java/io/github/agentic/spring/ai/graph/node/KnowledgeRetrievalNode.java`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/src/main/java/io/github/agentic/spring/ai/graph/node/HttpNode.java`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/src/main/java/io/github/agentic/spring/ai/graph/node/DocumentExtractorNode.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/src/test/java/io/github/agentic/spring/ai/graph/node/MigratedNodeDeprecationTests.java`

**Interfaces:**

- Consumes: reviewed Extensions artifact and FQCN names from Tasks 1-2.
- Produces: unchanged Core classes annotated for 3.0 removal.

- [ ] **Step 1: Write and run failing deprecation tests**

For each Core class, assert:

```java
Deprecated deprecated = type.getAnnotation(Deprecated.class);
assertNotNull(deprecated);
assertEquals("2.1.0", deprecated.since());
assertTrue(deprecated.forRemoval());
```

Run:

```bash
./mvnw -B -pl :agentic-spring-ai-starter-builtin-nodes \
  -Dtest=MigratedNodeDeprecationTests test
```

Expected: FAIL because the three classes are not deprecated.

- [ ] **Step 2: Add deprecation metadata and replacement Javadoc**

Add `@Deprecated(since = "2.1.0", forRemoval = true)` without changing any constructor, method, nested type, field visibility, or runtime logic. Javadoc names the replacement artifact and FQCN exactly.

- [ ] **Step 3: Verify Core compatibility and commit**

Run:

```bash
./mvnw -B -pl :agentic-spring-ai-starter-builtin-nodes test
tools/scripts/verify-core-binary-compatibility.sh
git diff --check
```

Commit only Core deprecation/test changes:

```bash
git commit -m "refactor: deprecate migrated integration nodes"
```

### Task 4: Integrate Artifacts into CI and Documentation

**Files:**

- Modify: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/tools/github-actions/setup-extensions/action.yml`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/README.md`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/docs/graph-node-integration-migration.md`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/docs/2.1.0-migration.md`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/docs/releases/2.1.0.md`
- Add: `/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/docs/superpowers/plans/2026-09-03-rag-document-node-migration.md`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/README.md`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/README-zh.md`

**Interfaces:**

- Consumes: reviewed Extensions implementation commit and reviewed Core deprecation commit.
- Produces: reproducible cross-repository pins and user migration instructions.

- [ ] **Step 1: Update Core integration pin and artifact install list**

Pin `setup-extensions` to the reviewed Extensions commit containing both new artifacts and add:

```yaml
:agentic-spring-ai-graph-node-rag
:agentic-spring-ai-graph-node-network
```

- [ ] **Step 2: Update migration and release documentation**

Document old/new package mappings, 2.1 introduction, 2.x coexistence, 3.0 removal, no automatic registration, and Core-first release order. Update builtin-nodes README examples to declare the appropriate Extensions artifact and import the new package.

Add `Graph nodes` pointing to `graph-nodes` in both Extensions README module
tables.

- [ ] **Step 3: Verify and commit Core and Extensions documentation separately**

Run Core lint, Extensions lint, and `git diff --check` in both repositories. Commit Core CI/docs separately from the Extensions README change.

### Task 5: Pin Core and Run Cross-Repository Verification

**Files:**

- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/.worktrees/migrate-rag-document-nodes/tools/github-actions/setup-core/action.yml`

**Interfaces:**

- Consumes: reviewed Core deprecation/integration commit.
- Produces: Extensions CI pinned to the Core compatibility revision.

- [ ] **Step 1: Update the Extensions Core pin**

Set the default ref in `setup-core` to the reviewed Core commit containing the deprecated compatibility nodes and CI/documentation integration.

- [ ] **Step 2: Verify the complete migration**

Using an Extensions-local isolated Maven repository:

```bash
CORE_M2="$PWD/target/node-migration-verification/m2"
/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/mvnw \
  -B -Dmaven.repo.local="$CORE_M2" -DskipTests install
mvn -B -Dmaven.repo.local="$CORE_M2" \
  -Poracle-integration -Djdbc.persistence.oracle.enabled=true test
```

Also run both repositories' package, format, checkstyle, license, lint, and diff checks. Confirm Core production dependency trees contain no Extensions coordinates and all RAG/network compatibility tests execute with zero skips.

Run non-publishing release-profile verification in order:

```bash
/Users/aias/Work/github/agentic-for-spring-ai/.worktrees/migrate-rag-document-nodes/mvnw \
  -B -Dmaven.repo.local="$CORE_M2" -Prelease -Drevision=2.1.0 \
  -DskipTests -Dgpg.skip=true install
mvn -B -Dmaven.repo.local="$CORE_M2" -Prelease -Drevision=2.1.0 \
  -Dagentic-spring-ai.version=2.1.0 -DskipTests -Dgpg.skip=true package
```

Parse every Extensions reactor `.flattened-pom.xml` and fail if any project or
Core dependency version contains `2.1.0-dev`. Verify the two new artifacts are
version `2.1.0` and depend only on Core `2.1.0` coordinates.

- [ ] **Step 3: Commit and perform final review**

Commit the Extensions pin, generate whole-branch review packages for both repositories, run one independent final review, address findings in one fix wave, and use `superpowers:finishing-a-development-branch` after final verification.
