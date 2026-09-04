# Extensions Model Artifact Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unpublished Core `agentic-spring-ai-model` artifact with the Extensions-owned `agentic-spring-ai-extensions-model` artifact while preserving all Java packages and public behavior.

**Architecture:** Extensions owns and publishes the provider-neutral document parser and rerank contracts. Core removes the old module and BOM entry completely. Fixed cross-repository pins are committed in E1 -> C1 -> E2 order so every intermediate revision is reproducible and acyclic.

**Tech Stack:** Java 17, Maven, Spring AI 2.0.1, Jackson 3, JUnit 5, AssertJ, japicmp, GitHub Actions composite actions.

**Spec:** `docs/superpowers/specs/2026-09-03-extensions-model-artifact-migration-design.md`

## Global Constraints

- Publish only `io.github.agentic-spring-ai:agentic-spring-ai-extensions-model`.
- Do not publish or retain a BOM alias for `agentic-spring-ai-model`.
- Preserve `io.github.agentic.spring.ai.document` and `io.github.agentic.spring.ai.model` packages and all public/protected signatures.
- Core production POMs and BOM must not depend on or manage Extensions artifacts.
- Extensions owns the new artifact version through `${project.version}`.
- Keep the existing node deletion work and the user-owned `.codex/ai-context.yaml` files intact.
- Create local commits only; do not push or publish.

Execution note: Maven analysis found 12 additional production modules using the
contract through accidental transitive dependencies. The completed migration
adds direct `agentic-spring-ai-extensions-model` dependencies to those modules,
then creates Extensions E3 and Core C2 so the final Core pin covers the complete
dependency graph without requiring another reciprocal Extensions pin.

---

### Task 1: Capture the Contract Baseline

**Files:**
- Read: `agentic-spring-ai-model/src/main/java/**/*.java`
- Read: `agentic-spring-ai-model/src/test/java/**/*.java`
- Read: `agentic-spring-ai-model/pom.xml`
- Produce: `agentic-spring-ai-model/target/agentic-spring-ai-model-2.1.0-dev.jar`

**Interfaces:**
- Consumes: the last Core-owned development contract module.
- Produces: the baseline JAR and test evidence used to verify the renamed Extensions artifact.

- [ ] **Step 1: Build and test the old module from a clean target**

Run:

```bash
./mvnw -B -pl :agentic-spring-ai-model clean test package
```

Expected: four test classes pass and the baseline JAR is created.

- [ ] **Step 2: Record the baseline public class list**

Run:

```bash
jar tf agentic-spring-ai-model/target/agentic-spring-ai-model-2.1.0-dev.jar \
  | sort > target/model-artifact-baseline-classes.txt
```

Expected: the list contains the five document types and six rerank types from the design.

### Task 2: Create the Extensions Contract Artifact

**Files:**
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/agentic-spring-ai-extensions-model/pom.xml`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/agentic-spring-ai-extensions-model/src/main/java/io/github/agentic/spring/ai/document/*.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/agentic-spring-ai-extensions-model/src/main/java/io/github/agentic/spring/ai/model/*.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/agentic-spring-ai-extensions-model/src/test/java/io/github/agentic/spring/ai/document/*.java`
- Create: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/agentic-spring-ai-extensions-model/src/test/java/io/github/agentic/spring/ai/model/*.java`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/pom.xml`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/agentic-spring-ai-extensions-bom/pom.xml`
- Modify: the seven direct consumer POMs named in the design.
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/README.md`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/README-zh.md`

**Interfaces:**
- Consumes: the exact Core source and tests captured in Task 1.
- Produces: `io.github.agentic-spring-ai:agentic-spring-ai-extensions-model:${project.version}` for all Extensions consumers.

- [ ] **Step 1: Copy the module and rename only Maven metadata**

Copy all tracked source and test files. Set the artifactId to
`agentic-spring-ai-extensions-model`, use the Extensions parent, and retain the
old dependency set and Java packages.

- [ ] **Step 2: Register and manage the new artifact**

Add the module before document parser modules in the Extensions reactor. Replace
the old dependency-management and Extensions BOM entries with the new artifact
at `${project.version}`.

- [ ] **Step 3: Update all seven direct consumers**

Replace only the dependency artifactId; keep scopes and Java imports unchanged.

- [ ] **Step 4: Update Extensions module documentation**

List the new provider-neutral contract artifact in both README module tables and
state that it owns document parser SPI and rerank contracts.

- [ ] **Step 5: Run clean contract and consumer tests**

Run:

```bash
mvn -B -pl :agentic-spring-ai-extensions-model,\
:agentic-spring-ai-graph-node-network,\
:agentic-spring-ai-graph-node-rag,\
:agentic-spring-ai-dashscope,\
:agentic-spring-ai-starter-document-parser-markdown,\
:agentic-spring-ai-starter-document-parser-yaml,\
:agentic-spring-ai-starter-document-parser-bshtml,\
:agentic-spring-ai-starter-document-parser-tika clean test
```

Expected: all eight modules compile against the new coordinate and tests pass.

- [ ] **Step 6: Commit E1**

Include the already-approved Extensions node regression cleanup and the complete
new artifact migration. Exclude `.codex/ai-context.yaml`.

```bash
git commit -m "refactor: move model contracts to extensions"
```

Record the E1 SHA for Core `setup-extensions`.

### Task 3: Remove the Core Contract Artifact

**Files:**
- Delete: `agentic-spring-ai-model/**`
- Modify: `pom.xml`
- Modify: `agentic-spring-ai-bom/pom.xml`
- Modify: `tools/github-actions/setup-extensions/action.yml`
- Modify: `README.md`
- Modify: `README-zh.md`
- Modify: `docs/2.1.0-migration.md`
- Modify: `docs/graph-node-integration-migration.md`
- Modify: `docs/releases/2.1.0.md`
- Modify: `docs/superpowers/plans/2026-09-03-rag-document-node-migration.md`
- Add: `docs/superpowers/plans/2026-09-03-extensions-model-artifact-migration.md`

**Interfaces:**
- Consumes: the committed E1 Extensions artifact and SHA.
- Produces: a Core reactor and BOM with no model contract artifact ownership.

- [ ] **Step 1: Remove the module and BOM entry**

Delete the module directory, remove it from the root reactor, and remove its Core
BOM dependency-management entry.

- [ ] **Step 2: Update cross-repository setup**

Remove `:agentic-spring-ai-model` from the Core install list and pin the
Extensions checkout to E1. Add
`:agentic-spring-ai-extensions-model` to the Extensions module install list when
the action installs only selected Extensions artifacts.

- [ ] **Step 3: Update Core documentation**

Remove the Core Model module row. Name the new Extensions coordinate and BOM,
state that the old coordinate was development-only, and retain Core-first then
Extensions release order.

- [ ] **Step 4: Verify Core independently**

Run:

```bash
./mvnw -B clean test
./mvnw -B -DskipTests package
make format-check checkstyle-check lint licenses-check
tools/scripts/verify-core-binary-compatibility.sh
```

Expected: Core succeeds without resolving or producing either model contract artifact.

- [ ] **Step 5: Commit C1**

Include the already-approved Core node deletions, dependency cleanup, migration
documentation, model removal, implementation plan, and E1 pin. Exclude
`.codex/ai-context.yaml`.

```bash
git commit -m "refactor: remove migrated extension implementations"
```

Record the C1 SHA for Extensions `setup-core`.

### Task 4: Complete the Reciprocal Extensions Pin

**Files:**
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/tools/github-actions/setup-core/action.yml`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/README.md`
- Modify: `/Users/aias/Work/github/agentic-for-spring-ai-extensions/README-zh.md`

**Interfaces:**
- Consumes: committed Core C1.
- Produces: reproducible Extensions CI that installs only remaining Core artifacts.

- [ ] **Step 1: Remove the old Core module from setup-core**

Delete `:agentic-spring-ai-model` from `-pl` and pin the action to C1.

- [ ] **Step 2: Run Extensions full verification**

Run:

```bash
mvn -B clean test
mvn -B -DskipTests package
make format-check checkstyle-check lint licenses-check
```

Expected: the complete Extensions reactor succeeds with the new contract artifact.

- [ ] **Step 3: Commit E2**

```bash
git commit -m "ci: pin core after model contract migration"
```

Exclude `.codex/ai-context.yaml`.

### Task 5: Verify Cross-Repository Compatibility

**Files:**
- Verify: both repository worktrees and generated JARs.

**Interfaces:**
- Consumes: E1, C1, and E2.
- Produces: final evidence that the migration is complete and releaseable.

- [ ] **Step 1: Compare public APIs**

Run japicmp with the Task 1 Core JAR as old and the new Extensions JAR as new.
Use protected access and fail on binary incompatibility.

Expected: no incompatible API changes.

- [ ] **Step 2: Scan active build metadata**

Run:

```bash
rg -n 'agentic-spring-ai-model' --glob 'pom.xml' --glob '*.yml' --glob '*.yaml' .
```

Expected: no active Core or Extensions POM/CI reference to the old artifactId.

- [ ] **Step 3: Verify published POM shape**

Run release-profile package dry runs with `revision=2.1.0` and
`agentic-spring-ai.version=2.1.0`, then scan flattened POMs for `2.1.0-dev` and
the old artifactId.

Expected: neither value appears in published dependency metadata.

- [ ] **Step 4: Review and report**

Run `git diff --check`, inspect both commit ranges, verify both repositories are
ahead only by the intended local commits, and independently review BOM,
release-order, pin-DAG, and consumer dependency changes.
