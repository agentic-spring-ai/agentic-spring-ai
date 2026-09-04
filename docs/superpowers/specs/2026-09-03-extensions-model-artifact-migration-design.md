# Extensions Model Artifact Migration Design

Status: Approved for implementation on 2026-09-03.

## Objective

Move the provider-neutral document parsing and rerank contracts out of the Core
repository and into the Extensions repository before the first public 2.1.0
release. Publish them only as
`io.github.agentic-spring-ai:agentic-spring-ai-extensions-model`.

The old development-only coordinate
`io.github.agentic-spring-ai:agentic-spring-ai-model` has never been published.
It is removed without a compatibility artifact or BOM alias.

## Ownership Boundary

Core owns graph execution, agent orchestration, Studio, Core starters, and the
Core BOM. Core does not build, publish, or manage document parser and rerank
extension contracts after this migration.

Extensions owns the new contract artifact and every current production
consumer. The artifact contains the existing Java packages:

- `io.github.agentic.spring.ai.document`
- `io.github.agentic.spring.ai.model`

The migration changes the Maven artifact name and repository ownership only.
Public Java class names, packages, signatures, parser behavior, rerank request
and response shapes, and serialization behavior remain unchanged.

## Extensions Module

Create the root-level Extensions module
`agentic-spring-ai-extensions-model`. Move all 11 production classes and four
test classes from the Core `agentic-spring-ai-model` module into it.

The new module keeps the existing dependencies on Spring AI Commons, Spring AI
Model, Jackson Databind, and JSpecify. It keeps JUnit Jupiter and AssertJ as test
dependencies. Its Maven description identifies it as the provider-neutral
contract and parser SPI artifact owned by Extensions.

Register the new module before document parsers and graph nodes in the
Extensions root reactor. Maven reactor dependency sorting remains authoritative;
the physical module order documents the intended ownership and build direction.

## Consumer Migration

Replace `agentic-spring-ai-model` with
`agentic-spring-ai-extensions-model` in the seven existing direct production
consumers:

1. `agentic-spring-ai-graph-node-network`
2. `agentic-spring-ai-graph-node-rag`
3. `agentic-spring-ai-dashscope`
4. `agentic-spring-ai-starter-document-parser-markdown`
5. `agentic-spring-ai-starter-document-parser-yaml`
6. `agentic-spring-ai-starter-document-parser-bshtml`
7. `agentic-spring-ai-starter-document-parser-tika`

No Java import changes are required because package names remain stable. Maven
analysis proved that 12 additional production modules relied on an accidental
transitive edge, so they also declare the contract artifact directly:

- Apache PDFBox, BibTeX, Directory, Multi-modality, and PDF Tables parsers
- Archive, Arxiv, CSDN, GitHub, Tencent COS, and Yuque readers
- `agentic-spring-ai-rag`

All 19 production consumers therefore express the contract dependency directly.

## BOM Contract

The Extensions root dependency management and
`agentic-spring-ai-extensions-bom` manage
`agentic-spring-ai-extensions-model` at `${project.version}`.

The Core BOM removes `agentic-spring-ai-model` completely. It does not manage
the new Extensions artifact. Applications using document parser or rerank
contracts import the Extensions BOM.

No compatibility alias is published because the old artifact never had a
public release. Repository-local `2.1.0-dev` consumers migrate atomically with
the source move.

## Cross-Repository CI Pins

The repositories keep fixed reviewed SHA pins without creating a cycle. The
migration uses this commit order:

```text
E1  Extensions adds agentic-spring-ai-extensions-model and migrates consumers
 |
C1  Core removes agentic-spring-ai-model and pins setup-extensions to E1
 |
E2  Extensions setup-core pins C1 and stops requesting the old Core module
 |
E3  Extensions declares the contract directly in 12 transitive consumers
 |
C2  Core setup-extensions pins the complete E3 migration
```

E1 may build while the old Core commit still contains the development-only
artifact because Extensions consumers depend only on the new artifactId. C1
removes the old Core reactor module, Core BOM entry, setup action entry, and
Core documentation. E2 makes the reciprocal Core pin reproducible. E3 contains
only Extensions dependency declarations. C2 can pin E3 without another
reciprocal update because E3 already pins the complete Core implementation C1.

The local commits are created in this order so each SHA used by the next
repository exists. They are not pushed until separately requested.

## Release Order

Publish Core 2.1.0 first. Core has no dependency on either model contract
artifact after C1.

Publish Extensions 2.1.0 second with both `revision` and
`agentic-spring-ai.version` resolved to `2.1.0`. The Extensions reactor builds
and publishes `agentic-spring-ai-extensions-model` before its parser, graph
node, DashScope, starter, and BOM consumers.

Published Core POMs and the Core BOM contain no reference to either model
artifact. Published Extensions POMs contain no reference to the unpublished
`agentic-spring-ai-model` coordinate or a `2.1.0-dev` Core version.

## Compatibility Strategy

- Maven: the development-only artifactId changes; all repository consumers are
  updated in the same migration.
- Java source: packages and public type names stay unchanged.
- Binary API: japicmp compares the last Core-built development JAR with the new
  Extensions JAR and must report no incompatible API changes.
- Runtime: parser discovery, JSON/text parsing, rerank model calls, metadata,
  equality, and serialization tests move unchanged with the module.
- Rollback before release: revert E2, C1, and E1 in reverse order.
- Post-release: `agentic-spring-ai-model` is not published or supported; the
  Extensions coordinate is the only supported contract artifact.

## Documentation

Core README and migration documents remove the Core Model module and point
contract consumers to the Extensions artifact and BOM. Extensions README files
list the new contract module. The 2.1 release notes identify the artifact as an
Extensions-owned coordinate and retain the Core-first release order.

Historical plans may mention the temporary Core module only when clearly marked
as archived and superseded.

## Verification Gates

1. The new Extensions module passes its four migrated test classes from a clean
   target directory.
2. japicmp reports no public or protected API incompatibility between the old
   Core JAR and the new Extensions JAR.
3. All 19 direct production consumers resolve only
   `agentic-spring-ai-extensions-model`.
4. No active POM or CI action references `agentic-spring-ai-model`.
5. Core builds, tests, packages, and passes quality checks without installing a
   model contract artifact.
6. Extensions builds, tests, packages, and passes quality checks after installing
   the pinned Core candidate first in an isolated Maven repository.
7. Core and Extensions release-profile dry runs contain no unresolved
   `2.1.0-dev` coordinates in flattened POMs.
8. Both repositories pass `git diff --check`; generated artifacts and the
   user-owned `.codex/ai-context.yaml` files remain uncommitted.

## Out Of Scope

- Renaming Java packages or public contract types.
- Redesigning parser discovery or rerank APIs.
- Splitting document and rerank contracts into separate artifacts.
- Cleaning unrelated dependency-analysis warnings.
- Publishing or pushing either repository.
