# Compatible Enterprise Runtime Phase 0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current 2.1 legacy API, configuration, storage, and runtime behavior reproducibly green and enforce those compatibility contracts before enterprise runtime changes begin.

**Architecture:** Phase 0 adds no enterprise runtime feature and changes no default behavior. It replaces scheduler-sensitive assertions with deterministic coordination, expands source and binary compatibility checks across every public Core artifact, records configuration and storage fixtures, adds explicit legacy behavior contracts, and makes all gates run before merge.

**Tech Stack:** Java 17, Maven 3.9.1+, JUnit 5, AssertJ, Mockito, Reactor, Jackson, japicmp, Bash, GNU Make, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-22-langgraph-inspired-compatible-enterprise-runtime-design.md`

## Global Constraints

- Preserve every existing public and protected Java signature and Maven coordinate.
- Preserve every existing `spring.ai.alibaba.*` key and default value.
- Preserve legacy graph execution, checkpoint timing, state merge, interrupt, and stream ordering.
- Preserve existing checkpoint and Store physical formats and namespace rules.
- Do not add a production dependency.
- Do not enable a new runtime feature or execution mode.
- Keep JDK 17 as the compilation target and Maven 3.9.1 as the minimum Maven version.
- Keep Core independent of Extensions; verify Extensions only through an isolated Maven repository.
- Leave user-owned `.codex/` content and generated `target/` output uncommitted.

## File Structure

### Deterministic Tests

- `agentic-spring-ai-agent-framework/src/main/java/io/github/agentic/spring/ai/graph/agent/interceptor/modelretry/RetryDelayCalculator.java`
  owns the package-private, pure backoff calculation shared by synchronous and streaming retries.
- `agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/interceptor/modelretry/RetryDelayCalculatorTest.java`
  locks the exact delay sequence without sleeping.
- `agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/interceptors/ModelRetryInterceptorTest.java`
  retains end-to-end retry behavior tests without wall-clock upper bounds.
- `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/StateGraphParallelTest.java`
  uses latches and controlled futures to prove ALL_OF and ANY_OF semantics.
- `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/FluxErrorHandlingTest.java`
  asserts terminal error behavior rather than elapsed time.
- `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/EdgeCaseSerializationTest.java`
  uses a broad JUnit timeout only as a deadlock guard.

### Compatibility Gates

- `tools/scripts/verify-core-binary-compatibility.sh`
  compares all five public runtime artifacts with the fixed baseline.
- `tools/scripts/verify-core-source-compatibility.sh`
  installs the candidate into an isolated repository and compiles a legacy consumer fixture.
- `tools/compatibility/legacy-api-consumer/pom.xml`
  is a standalone source-compatibility fixture.
- `tools/compatibility/legacy-api-consumer/src/main/java/io/github/agentic/spring/ai/compatibility/GraphApiFixture.java`
  compiles the existing Graph API.
- `tools/compatibility/legacy-api-consumer/src/main/java/io/github/agentic/spring/ai/compatibility/AgentApiFixture.java`
  compiles the existing Agent and flow API.
- `tools/compatibility/legacy-api-consumer/src/main/java/io/github/agentic/spring/ai/compatibility/StudioApiFixture.java`
  compiles the existing Studio loader and DTO API.
- `tools/make/java.mk`
  exposes source, binary, and aggregate compatibility targets.

### Compatibility Fixtures

- `spring-boot-starters/agentic-spring-ai-starter-graph-observation/src/test/resources/compatibility/2.1.0/graph-observation-metadata.json`
  freezes observation property names, types, and defaults.
- `agentic-spring-ai-studio/src/test/resources/compatibility/2.1.0/studio-metadata.json`
  freezes the Studio authentication property contract.
- `agentic-spring-ai-graph-core/src/test/resources/compatibility/2.1.0/checkpoint.json`
  is a previous-version checkpoint fixture.
- `agentic-spring-ai-graph-core/src/test/resources/compatibility/2.1.0/store-item.json`
  is a previous-version Store item fixture.
- `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/compatibility/LegacySerializationCompatibilityTest.java`
  proves old fixtures remain readable and semantically stable.
- `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/compatibility/LegacyGraphBehaviorCompatibilityTest.java`
  locks sequential, parallel, interrupt, resume, and time-travel behavior.
- `agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/compatibility/LegacyAgentBehaviorCompatibilityTest.java`
  locks deterministic `call`, `invoke`, and message streaming behavior without credentials.

### CI And Documentation

- `.github/workflows/build-and-test.yml`
  runs build, test, JDK, API compatibility, and Extensions compatibility gates for pull requests.
- `.github/workflows/linter.yml`
  runs lint before merge.
- `.github/workflows/license-check.yml`
  runs license checks before merge.
- `.github/workflows/secret-check.yml`
  runs secret checks before merge.
- `docs/compatibility-policy.md`
  documents the supported compatibility surfaces and local verification commands.

---

### Task 1: Make Model Retry Tests Deterministic

**Files:**

- Create: `agentic-spring-ai-agent-framework/src/main/java/io/github/agentic/spring/ai/graph/agent/interceptor/modelretry/RetryDelayCalculator.java`
- Create: `agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/interceptor/modelretry/RetryDelayCalculatorTest.java`
- Modify: `agentic-spring-ai-agent-framework/src/main/java/io/github/agentic/spring/ai/graph/agent/interceptor/modelretry/ModelRetryInterceptor.java:101`
- Modify: `agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/interceptors/ModelRetryInterceptorTest.java:307`

**Interfaces:**

- Consumes: current `initialDelay`, `maxDelay`, and `backoffMultiplier` values.
- Produces: package-private `RetryDelayCalculator.nextDelay(long, double, long)` used by both retry paths; no public API.

- [ ] **Step 1: Add a failing pure backoff test**

```java
package io.github.agentic.spring.ai.graph.agent.interceptor.modelretry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryDelayCalculatorTest {

    @Test
    void exponentialDelayIsCappedWithoutReadingTheClock() {
        assertEquals(150, RetryDelayCalculator.nextDelay(100, 3.0, 150));
        assertEquals(150, RetryDelayCalculator.nextDelay(150, 3.0, 150));
    }

    @Test
    void zeroDelayRemainsZero() {
        assertEquals(0, RetryDelayCalculator.nextDelay(0, 2.0, 30_000));
    }
}
```

- [ ] **Step 2: Run the test and verify the calculator is missing**

Run:

```bash
./mvnw -B -pl :agentic-spring-ai-agent-framework -am \
  -Dtest=RetryDelayCalculatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because `RetryDelayCalculator` does not exist.

- [ ] **Step 3: Add the package-private calculator**

```java
final class RetryDelayCalculator {

    private RetryDelayCalculator() {
    }

    static long nextDelay(long currentDelay, double multiplier, long maxDelay) {
        return Math.min((long) (currentDelay * multiplier), maxDelay);
    }
}
```

Replace both synchronous `Math.min(...)` expressions and the streaming
`nextDelay(...)` body with this method. Do not change retry count, sleep timing,
exception classification, log messages, or reactive delay behavior.

- [ ] **Step 4: Remove wall-clock assertions from end-to-end retry tests**

Change `testExponentialBackoff` to use `initialDelay(0)` and assert only the
three attempts and successful response. Delete `testMaxDelayLimit` because the
pure calculator test now covers the cap. Remove the `< 100ms` assertion from
`testZeroDelay`; the three immediate attempts prove the zero-delay branch.

```java
assertEquals(3, attemptCount.get());
assertEquals("Success", ((AssistantMessage) response.getMessage()).getText());
```

- [ ] **Step 5: Run the targeted tests**

Run:

```bash
./mvnw -B -pl :agentic-spring-ai-agent-framework -am \
  -Dtest=RetryDelayCalculatorTest,ModelRetryInterceptorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS with no elapsed-time assertion.

- [ ] **Step 6: Commit the deterministic retry seam**

```bash
git add agentic-spring-ai-agent-framework/src/main/java/io/github/agentic/spring/ai/graph/agent/interceptor/modelretry/RetryDelayCalculator.java \
  agentic-spring-ai-agent-framework/src/main/java/io/github/agentic/spring/ai/graph/agent/interceptor/modelretry/ModelRetryInterceptor.java \
  agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/interceptor/modelretry/RetryDelayCalculatorTest.java \
  agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/interceptors/ModelRetryInterceptorTest.java
git commit -m "test: make model retry backoff deterministic"
```

### Task 2: Replace Scheduler-Sensitive Graph Assertions

**Files:**

- Modify: `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/StateGraphParallelTest.java:65`
- Modify: `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/FluxErrorHandlingTest.java:45`
- Modify: `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/EdgeCaseSerializationTest.java:75`

**Interfaces:**

- Consumes: current ALL_OF, ANY_OF, error propagation, and serialization behavior.
- Produces: deterministic tests only; no production API or behavior change.

- [ ] **Step 1: Introduce a controlled parallel action helper in the test**

```java
private AsyncNodeAction controlledNode(String id, CountDownLatch entered,
        CountDownLatch release, AtomicBoolean interrupted) {
    return node_async(state -> {
        entered.countDown();
        try {
            release.await();
        }
        catch (InterruptedException ex) {
            interrupted.set(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
        return Map.of("messages", id, "nodeId", id);
    });
}
```

Use a dedicated `ExecutorService` in each test and close it in `finally` with
`shutdownNow()`.

- [ ] **Step 2: Rewrite ALL_OF tests around completion state**

Start the stream in `CompletableFuture.runAsync`, wait until every branch has
entered, assert the graph future is not complete, release all branches, then
assert the final state contains every branch and the merge node.

```java
assertTrue(entered.await(2, TimeUnit.SECONDS));
assertFalse(run.isDone(), "ALL_OF must wait while one branch is blocked");
release.countDown();
run.get(2, TimeUnit.SECONDS);
assertThat(messages).contains("fastNode", "slowNode1", "slowNode2", "merge");
```

- [ ] **Step 3: Rewrite ANY_OF tests around winner and cancellation**

Make one branch return immediately and block the other branches. Assert that
the graph completes before releasing the blocked branches, that the winner's
state is selected, and that remaining work is cancelled or ignored. Release the
latch in `finally` even when the assertion fails.

```java
assertEquals("fastNode", finalState.value("nodeId").orElseThrow());
assertFalse(run.isCompletedExceptionally());
```

- [ ] **Step 4: Remove elapsed-time assertions from error and serialization tests**

In `FluxErrorHandlingTest`, use bounded blocking only as a deadlock guard and
assert the emitted exception:

```java
RuntimeException error = assertThrows(RuntimeException.class,
        () -> graph.stream(Map.of(), config).blockLast(Duration.ofSeconds(2)));
assertThat(error).hasMessageContaining("intentional failure");
```

In `EdgeCaseSerializationTest`, wrap the operation in
`assertTimeoutPreemptively(Duration.ofSeconds(10), ...)` and assert the restored
value. Do not assert a measured duration.

- [ ] **Step 5: Run the graph tests repeatedly**

Run:

```bash
for run in 1 2 3 4 5; do
  ./mvnw -B -pl :agentic-spring-ai-graph-core \
    -Dtest=StateGraphParallelTest,FluxErrorHandlingTest,EdgeCaseSerializationTest test || exit 1
done
```

Expected: all five runs pass without wall-clock threshold failures.

- [ ] **Step 6: Commit deterministic graph tests**

```bash
git add agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/StateGraphParallelTest.java \
  agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/FluxErrorHandlingTest.java \
  agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/EdgeCaseSerializationTest.java
git commit -m "test: remove scheduler-sensitive graph assertions"
```

### Task 3: Expand Java API Compatibility Gates

**Files:**

- Modify: `tools/scripts/verify-core-binary-compatibility.sh:75`
- Create: `tools/scripts/verify-core-source-compatibility.sh`
- Create: `tools/compatibility/legacy-api-consumer/pom.xml`
- Create: `tools/compatibility/legacy-api-consumer/src/main/java/io/github/agentic/spring/ai/compatibility/GraphApiFixture.java`
- Create: `tools/compatibility/legacy-api-consumer/src/main/java/io/github/agentic/spring/ai/compatibility/AgentApiFixture.java`
- Create: `tools/compatibility/legacy-api-consumer/src/main/java/io/github/agentic/spring/ai/compatibility/StudioApiFixture.java`
- Modify: `tools/make/java.mk:48`

**Interfaces:**

- Consumes: the current five public artifacts and fixed baseline commit `c128f02584fc976ee641572074db2e556466f6a2`.
- Produces: `make binary-compatibility-check`, `make source-compatibility-check`, and aggregate `make compatibility-check`.

- [ ] **Step 1: Extend the binary compatibility script module list**

Build and compare these artifacts in baseline and candidate worktrees:

```text
agentic-spring-ai-graph-core
agentic-spring-ai-agent-framework
agentic-spring-ai-studio
agentic-spring-ai-starter-graph-observation
agentic-spring-ai-starter-builtin-nodes
```

Use one Maven reactor build:

```bash
-pl :agentic-spring-ai-graph-core,:agentic-spring-ai-agent-framework,\
:agentic-spring-ai-studio,:agentic-spring-ai-starter-graph-observation,\
:agentic-spring-ai-starter-builtin-nodes -am package
```

Retain the existing built-in-node exclusions and add no exclusion for the other
four artifacts.

- [ ] **Step 2: Add a standalone legacy source fixture POM**

The POM declares Java 17 and direct dependencies on all five candidate
artifacts at `${agentic-spring-ai.version}`. It has no parent and no repository
declaration so it can resolve only from Maven Central and the isolated local
repository supplied by the script.

```xml
<properties>
    <maven.compiler.release>17</maven.compiler.release>
    <agentic-spring-ai.version>2.1.0-dev</agentic-spring-ai.version>
</properties>
```

- [ ] **Step 3: Add compile-only legacy API consumers**

`GraphApiFixture` must construct `StateGraph`, add a node and edges, build
`CompileConfig`, `RunnableConfig`, and `MemorySaver`, then reference `invoke`,
`stream`, `getStateHistory`, and `updateState`.

```java
StateGraph graph = new StateGraph(() -> Map.of("messages", KeyStrategy.APPEND))
        .addNode("node", node_async(state -> Map.of("messages", "done")))
        .addEdge(StateGraph.START, "node")
        .addEdge("node", StateGraph.END);
CompiledGraph compiled = graph.compile(CompileConfig.builder().build());
compiled.stream(Map.of(), RunnableConfig.builder().threadId("fixture").build());
```

`AgentApiFixture` must reference `ReactAgent.builder`, `SequentialAgent`,
`ParallelAgent`, `LoopAgent`, `LlmRoutingAgent`, hooks, interceptors, `call`,
`invoke`, `stream`, and `asNode`. Methods may accept a `ChatModel` parameter;
the fixture is compiled, not executed.

`StudioApiFixture` must reference `AgentLoader`, `GraphLoader`,
`AgentRunRequest`, `AgentResumeRequest`, `GraphRunRequest`, and `Thread`.

- [ ] **Step 4: Add the isolated source-compatibility script**

```bash
readonly REPO_ROOT="$(git rev-parse --show-toplevel)"
readonly COMPAT_REPO="$(mktemp -d "${TMPDIR:-/tmp}/agentic-source-compat.XXXXXX")"
trap 'rm -rf "$COMPAT_REPO"' EXIT

"${REPO_ROOT}/mvnw" -B -Dmaven.repo.local="$COMPAT_REPO" \
  -DskipTests -pl :agentic-spring-ai-agent-framework,:agentic-spring-ai-studio,\
:agentic-spring-ai-starter-graph-observation,:agentic-spring-ai-starter-builtin-nodes \
  -am install

"${REPO_ROOT}/mvnw" -B -Dmaven.repo.local="$COMPAT_REPO" \
  -f tools/compatibility/legacy-api-consumer/pom.xml clean compile
```

- [ ] **Step 5: Add Make targets**

<!-- markdownlint-disable MD010 -->
```make
.PHONY: source-compatibility-check
source-compatibility-check:
	@$(LOG_TARGET)
	tools/scripts/verify-core-source-compatibility.sh

.PHONY: compatibility-check
compatibility-check: binary-compatibility-check source-compatibility-check
```
<!-- markdownlint-enable MD010 -->

- [ ] **Step 6: Run both compatibility gates**

Run:

```bash
make binary-compatibility-check
make source-compatibility-check
```

Expected: five japicmp reports under `target/binary-compatibility/` and a
successful standalone fixture compilation.

- [ ] **Step 7: Commit compatibility gates**

```bash
git add tools/scripts/verify-core-binary-compatibility.sh \
  tools/scripts/verify-core-source-compatibility.sh \
  tools/compatibility/legacy-api-consumer tools/make/java.mk
git commit -m "ci: cover public runtime APIs with compatibility gates"
```

### Task 4: Freeze Spring Configuration Contracts

**Files:**

- Create: `spring-boot-starters/agentic-spring-ai-starter-graph-observation/src/test/resources/compatibility/2.1.0/graph-observation-metadata.json`
- Create: `spring-boot-starters/agentic-spring-ai-starter-graph-observation/src/test/java/io/github/agentic/spring/ai/autoconfigure/graph/GraphObservationConfigurationMetadataCompatibilityTest.java`
- Create: `agentic-spring-ai-studio/src/test/resources/compatibility/2.1.0/studio-metadata.json`
- Create: `agentic-spring-ai-studio/src/test/java/io/github/agentic/spring/ai/agent/studio/config/StudioConfigurationMetadataCompatibilityTest.java`

**Interfaces:**

- Consumes: current configuration metadata JSON files.
- Produces: normalized snapshots of property name, Java type, and default value; descriptions remain editable.

- [ ] **Step 1: Add the observation metadata fixture**

```json
{
  "properties": [
    {
      "name": "spring.ai.alibaba.graph.observation.capture-content",
      "type": "java.lang.Boolean",
      "defaultValue": false
    },
    {
      "name": "spring.ai.alibaba.graph.observation.enabled",
      "type": "java.lang.Boolean",
      "defaultValue": true
    },
    {
      "name": "spring.ai.alibaba.graph.observation.max-content-length",
      "type": "java.lang.Integer",
      "defaultValue": 1000
    }
  ]
}
```

- [ ] **Step 2: Add the Studio metadata fixture**

```json
{
  "properties": [
    {
      "name": "spring.ai.alibaba.agent.studio.execution.auth-token",
      "type": "java.lang.String"
    }
  ]
}
```

- [ ] **Step 3: Add normalized metadata comparison tests**

Each test loads the production and fixture JSON with `ObjectMapper`, projects
only `name`, `type`, and `defaultValue`, sorts by name, and compares the arrays.

```java
private JsonNode contract(JsonNode root) {
    ArrayNode result = mapper.createArrayNode();
    StreamSupport.stream(root.path("properties").spliterator(), false)
            .sorted(Comparator.comparing(node -> node.path("name").asText()))
            .map(node -> {
                ObjectNode property = mapper.createObjectNode();
                property.set("name", node.path("name"));
                property.set("type", node.path("type"));
                property.set("defaultValue", node.has("defaultValue")
                        ? node.get("defaultValue") : NullNode.instance);
                return property;
            })
            .forEach(result::add);
    return result;
}
```

For missing defaults, normalize `defaultValue` to a JSON null so absence is an
explicit part of the contract.

- [ ] **Step 4: Verify metadata and runtime defaults together**

Retain `GraphObservationAutoConfigurationTest.shouldAutoConfigureWithDefaultProperties`.
Add an assertion in the Studio test that an empty token remains fail-closed with
HTTP 403; do not add a default token.

- [ ] **Step 5: Run the two modules**

Run:

```bash
./mvnw -B -pl :agentic-spring-ai-starter-graph-observation,:agentic-spring-ai-studio \
  -am -Dtest=GraphObservationConfigurationMetadataCompatibilityTest,\
StudioConfigurationMetadataCompatibilityTest,GraphObservationAutoConfigurationTest,\
StudioExecutionControllerSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS and no generated metadata change.

- [ ] **Step 6: Commit configuration fixtures**

```bash
git add spring-boot-starters/agentic-spring-ai-starter-graph-observation/src/test \
  agentic-spring-ai-studio/src/test
git commit -m "test: freeze legacy configuration contracts"
```

### Task 5: Add Checkpoint And Store Compatibility Fixtures

**Files:**

- Create: `agentic-spring-ai-graph-core/src/test/resources/compatibility/2.1.0/checkpoint.json`
- Create: `agentic-spring-ai-graph-core/src/test/resources/compatibility/2.1.0/store-item.json`
- Create: `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/compatibility/LegacySerializationCompatibilityTest.java`

**Interfaces:**

- Consumes: Jackson checkpoint and Store item shapes plus `BaseCheckpointSaver.checkpointThreadId`.
- Produces: semantic old-fixture read/write compatibility and a locked app/user/thread namespace key.

- [ ] **Step 1: Add the checkpoint fixture**

```json
{
  "id": "checkpoint-2.1.0",
  "state": {
    "counter": 7,
    "owner": "legacy-user"
  },
  "nodeId": "legacy-node",
  "nextNodeId": "legacy-next"
}
```

- [ ] **Step 2: Add the Store item fixture**

```json
{
  "namespace": ["users", "legacy-user", "preferences"],
  "key": "ui-settings",
  "value": {
    "language": "en-US",
    "theme": "dark"
  },
  "createdAt": 1700000000000,
  "updatedAt": 1700000001000
}
```

- [ ] **Step 3: Add fixture read and semantic round-trip tests**

```java
Checkpoint checkpoint = mapper.readValue(resource("checkpoint.json"), Checkpoint.class);
assertThat(checkpoint.getId()).isEqualTo("checkpoint-2.1.0");
assertThat(checkpoint.getState()).containsEntry("counter", 7);
assertThat(mapper.readTree(mapper.writeValueAsBytes(checkpoint)))
        .isEqualTo(mapper.readTree(resource("checkpoint.json")));

StoreItem item = mapper.readValue(resource("store-item.json"), StoreItem.class);
assertThat(item.getNamespace()).containsExactly("users", "legacy-user", "preferences");
assertThat(item.getCreatedAt()).isEqualTo(1700000000000L);
```

- [ ] **Step 4: Lock the current checkpoint namespace encoding**

```java
RunnableConfig config = RunnableConfig.builder()
        .threadId("thread")
        .addMetadata(RunnableConfig.APP_NAME_METADATA_KEY, "app")
        .addMetadata(RunnableConfig.USER_ID_METADATA_KEY, "user")
        .build();
assertThat(MemorySaver.builder().build().checkpointThreadId(config))
        .isEqualTo("ns-YXBw.dXNlcg.dGhyZWFk");
```

Also assert that a config without app/user metadata still maps to the literal
thread ID, preserving old unscoped storage.

- [ ] **Step 5: Run compatibility and existing serializer tests**

Run:

```bash
./mvnw -B -pl :agentic-spring-ai-graph-core \
  -Dtest=LegacySerializationCompatibilityTest,SpringAIJacksonStateSerializerTest,\
GraphResponseSerializationRoundTripTest,MemorySaverNamespaceTest test
```

Expected: PASS with both fixtures unchanged.

- [ ] **Step 6: Commit storage fixtures**

```bash
git add agentic-spring-ai-graph-core/src/test/resources/compatibility \
  agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/compatibility/LegacySerializationCompatibilityTest.java
git commit -m "test: add legacy storage compatibility fixtures"
```

### Task 6: Add Explicit Legacy Runtime Behavior Contracts

**Files:**

- Create: `agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/compatibility/LegacyGraphBehaviorCompatibilityTest.java`
- Create: `agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/compatibility/LegacyAgentBehaviorCompatibilityTest.java`

**Interfaces:**

- Consumes: public legacy graph and agent APIs.
- Produces: deterministic compatibility contracts used unchanged by later phases.

- [ ] **Step 1: Lock sequential graph stream order and state replacement**

```java
List<NodeOutput> outputs = graph.stream(Map.of("value", "input"), config)
        .collectList().block(Duration.ofSeconds(2));
assertThat(outputs).extracting(NodeOutput::node)
        .containsExactly(StateGraph.START, "first", "second", StateGraph.END);
assertThat(outputs.get(outputs.size() - 1).state().data())
        .containsEntry("value", "second");
```

- [ ] **Step 2: Lock parallel and interrupt behavior**

Add deterministic tests proving:

- ALL_OF exposes every branch result before the merge node.
- An interrupt-after-edge emits the current node twice, matching the existing
  `START, A, B, B` sequence.
- Resume continues with the selected successor and does not emit START again.
- A historical checkpoint resumes from its successor and retains old state.

Use `MemorySaver`, controlled actions, and exact node lists. Do not call a real
model or use wall-clock ordering.

- [ ] **Step 3: Lock ReactAgent call and invoke behavior with a deterministic model**

Create a deterministic `ChatModel` that supports both synchronous and streaming
calls:

```java
private static final class DeterministicChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage("legacy-response"))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}

ReactAgent agent = ReactAgent.builder()
        .name("legacy-agent")
        .model(new DeterministicChatModel())
        .build();

assertThat(agent.call("hello").getText()).isEqualTo("legacy-response");
assertThat(agent.invoke("hello")).isPresent();
assertThat(agent.streamMessages("hello").map(Message::getText).collectList().block())
        .containsExactly("legacy-response");
```

The `streamMessages` assertion locks that only the user-facing assistant message
is exposed and graph bookkeeping outputs remain filtered.

- [ ] **Step 4: Run the new contracts with their existing counterparts**

Run:

```bash
./mvnw -B -pl :agentic-spring-ai-agent-framework -am \
  -Dtest=LegacyGraphBehaviorCompatibilityTest,LegacyAgentBehaviorCompatibilityTest,\
InterruptionTest,TimeTravelTest,CompiledGraphStreamOrderTest,ReactAgentStreamingReturnDirectTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS without credentials or Docker.

- [ ] **Step 5: Commit legacy runtime contracts**

```bash
git add agentic-spring-ai-graph-core/src/test/java/io/github/agentic/spring/ai/graph/compatibility/LegacyGraphBehaviorCompatibilityTest.java \
  agentic-spring-ai-agent-framework/src/test/java/io/github/agentic/spring/ai/graph/agent/compatibility/LegacyAgentBehaviorCompatibilityTest.java
git commit -m "test: lock legacy graph and agent behavior"
```

### Task 7: Enforce All Gates Before Merge

**Files:**

- Modify: `.github/workflows/build-and-test.yml:17`
- Modify: `.github/workflows/linter.yml:17`
- Modify: `.github/workflows/license-check.yml:17`
- Modify: `.github/workflows/secret-check.yml:17`
- Create: `docs/compatibility-policy.md`

**Interfaces:**

- Consumes: Make compatibility targets and all Phase 0 tests.
- Produces: pull-request gates for build, test, lint, license, secrets, Java matrix, API compatibility, and Extensions compatibility.

- [ ] **Step 1: Add pull-request triggers to all quality workflows**

```yaml
on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main
```

Keep existing push behavior, concurrency, repository checks, and read-only
permissions.

- [ ] **Step 2: Rename the existing compile matrix job**

Rename `compatibility` to `jdk-compatibility` so it cannot be confused with the
new API/storage gates. Update `build.needs` accordingly.

- [ ] **Step 3: Add API compatibility jobs**

```yaml
  api-compatibility:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683
        with:
          fetch-depth: 0
      - uses: ./tools/github-actions/setup-deps
      - run: make compatibility-check
```

`fetch-depth: 0` is required because the binary script creates a worktree at
the fixed baseline commit.

- [ ] **Step 4: Add isolated Extensions compatibility**

```yaml
  extensions-compatibility:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683
      - uses: ./tools/github-actions/setup-deps
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683
        with:
          repository: agentic-spring-ai/agentic-spring-ai-extensions
          ref: 77718562c9b23b417407617a814c1fb3719aa87d
          path: target/agentic-spring-ai-extensions
      - run: tools/scripts/verify-extensions-compatibility.sh target/agentic-spring-ai-extensions
```

Add both compatibility jobs to `build.needs`. Do not use credentials beyond the
read-only GitHub token required for the public checkout.

- [ ] **Step 5: Document the compatibility policy**

`docs/compatibility-policy.md` must state:

- The protected Java, Maven, configuration, storage, and runtime surfaces.
- The two-minor deprecation window and major-only removal rule.
- The distinction between legacy and future opt-in execution semantics.
- The commands `make compatibility-check`, `./mvnw test`, `make lint`, and
  `make licenses-check`.
- The Core-first isolated Extensions verification process.
- The rule that sidecar data must remain ignorable by old binaries.

- [ ] **Step 6: Validate workflow syntax and local gates**

Run:

```bash
make yaml-lint
make markdown-lint
make compatibility-check
./mvnw -B test
make lint
make licenses-check
git diff --check
```

Expected: every command passes. Credential-gated model tests and Docker-gated
database tests may report skips but no failure. The previously failing
`ModelRetryInterceptorTest.testMaxDelayLimit` no longer exists.

- [ ] **Step 7: Confirm only intended files are tracked**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: source, tests, fixtures, workflows, scripts, Make targets, and the
compatibility policy only. `.codex/`, `target/`, Maven repositories, and
generated reports remain untracked or ignored.

- [ ] **Step 8: Commit CI and policy changes**

```bash
git add .github/workflows/build-and-test.yml .github/workflows/linter.yml \
  .github/workflows/license-check.yml .github/workflows/secret-check.yml \
  docs/compatibility-policy.md
git commit -m "ci: enforce compatibility gates before merge"
```

## Phase 0 Completion Check

- [ ] The complete reactor test suite passes from a clean target directory.
- [ ] Retry, parallel, error, and serialization tests contain no scheduler-sensitive elapsed-time thresholds.
- [ ] japicmp covers graph core, agent framework, Studio, graph observation, and built-in nodes.
- [ ] The standalone legacy source fixture compiles against candidate artifacts.
- [ ] Configuration snapshots lock names, types, and defaults without freezing descriptions.
- [ ] Checkpoint and Store fixtures load and round-trip semantically.
- [ ] Legacy graph and agent contracts run without external credentials.
- [ ] Core/Extensions compatibility passes in an isolated Maven repository.
- [ ] Pull requests run test, build, lint, license, secret, Java matrix, and compatibility gates.
- [ ] No runtime feature or default behavior changed.
