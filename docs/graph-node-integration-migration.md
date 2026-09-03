# Graph Node Integration Migration

Status: Approved for the 2.1 compatibility line. Core compatibility classes
remain until 3.0.

## Objective

Keep the Core built-in node artifact focused on graph and Spring AI primitives
while moving integration-heavy RAG, HTTP, and document-loading nodes to
Extensions. The migration must preserve source, binary, behavior, security, and
rollback compatibility.

Core must not acquire an Extensions Maven dependency. New implementations use
new packages so the same fully qualified class name is never present in both
repositories.

## Target Ownership

Core continues to own:

- `agentic-spring-ai-model`, including provider-neutral rerank and document
  parser contracts.
- Graph execution and `NodeAction` contracts.
- Basic state/control nodes and Spring AI-native LLM, tool, and agent nodes.
- Existing RAG, HTTP, and document extractor compatibility classes through the
  2.x line.

Extensions adds the new graph node artifacts in reviewed implementation commit
`895d99b644f38969829168402ffeac52d91c4f9b`:

| Artifact | Package | Classes |
| --- | --- | --- |
| `agentic-spring-ai-graph-node-rag` | `io.github.agentic.spring.ai.graph.node.rag` | `KnowledgeRetrievalNode` |
| `agentic-spring-ai-graph-node-network` | `io.github.agentic.spring.ai.graph.node.network` | `HttpNode`, `DocumentExtractorNode`, package-private `NetworkAccessPolicy` |

The new artifacts are managed only by `agentic-spring-ai-extensions-bom` and
are included in the Extensions 2.1 release. They are plain libraries: no Spring
Boot auto-configuration, component scanning, or automatic node registration is
added.

## Implementation Status

The 2.1 compatibility work is implemented across these reviewed commits:

- Extensions RAG and network node implementations:
  `895d99b644f38969829168402ffeac52d91c4f9b`.
- Core compatibility deprecations:
  `c9efc03dfea8bdb01e672a9715f2c63429bc9cbb`.

Core `tools/github-actions/setup-extensions` pins the reviewed Extensions
implementation commit above and installs both graph node artifacts for Core
integration jobs. It intentionally does not pin a later Extensions commit that
updates the reciprocal Core pin, because that would create a cross-repository
SHA cycle.

Applications should migrate imports as follows:

| Old Core package | New Extensions package | Artifact |
| --- | --- | --- |
| `io.github.agentic.spring.ai.graph.node.KnowledgeRetrievalNode` | `io.github.agentic.spring.ai.graph.node.rag.KnowledgeRetrievalNode` | `agentic-spring-ai-graph-node-rag` |
| `io.github.agentic.spring.ai.graph.node.HttpNode` | `io.github.agentic.spring.ai.graph.node.network.HttpNode` | `agentic-spring-ai-graph-node-network` |
| `io.github.agentic.spring.ai.graph.node.DocumentExtractorNode` | `io.github.agentic.spring.ai.graph.node.network.DocumentExtractorNode` | `agentic-spring-ai-graph-node-network` |

Users must continue to construct and register graph nodes explicitly. Adding an
Extensions graph node jar to the classpath does not create node beans or alter
graph routing.

## Behavior Contract

The Extensions implementations copy the Core behavior without cleanup or
semantic refactoring. Public constructors, builder methods, nested public types,
defaults, state keys, output shapes, exception behavior, and null handling stay
equivalent except for the new package names.

`KnowledgeRetrievalNode` preserves:

- `VectorStoreDocumentRetriever` query, filter, top-k, and similarity behavior.
- State-key versus preset-value precedence.
- Optional reranking through the provider-neutral `RerankModel` contracts.
- Prompt augmentation and output-key behavior.

The network artifact preserves one shared security implementation for both
nodes. It must not duplicate security policy separately inside `HttpNode` and
`DocumentExtractorNode`.

`HttpNode` preserves:

- URI, header, query, body, and authentication variable replacement.
- Response body/file mapping and output keys.
- Retry and total request timeout behavior.
- Private, loopback, link-local, and metadata endpoint blocking by default.

`DocumentExtractorNode` preserves:

- Local-root containment and regular-file checks.
- Remote access opt-in, redirect validation, and private-network blocking.
- Connection, read, and total deadline behavior.
- Maximum byte limits and stream cancellation.
- TXT/JSON defaults, explicit parser precedence, and
  `DocumentParserProvider` discovery.

## Compatibility Phases

### 2.1

- Add both Extensions artifacts and new packages.
- Keep all existing Core classes and dependencies.
- Mark Core `KnowledgeRetrievalNode`, `HttpNode`, and `DocumentExtractorNode`
  `@Deprecated(since = "2.1.0", forRemoval = true)` with replacement
  coordinates and fully qualified names.
- Update active documentation to use the Extensions artifacts.
- Keep compatibility tests running against both implementations.
- Publish Core first, then publish Extensions against the exact Core 2.1
  release.

### 2.x

- Continue the coexistence period and migrate remaining consumers.
- Do not change network defaults, RAG output formats, or public Core signatures.
- Keep Core and Extensions compatibility tests as release gates.

### 3.0

- Remove the three deprecated Core classes.
- Remove the Core package-private `NetworkAccessPolicy` only when no remaining
  Core node uses it. If `HttpNode` and `DocumentExtractorNode` move together,
  this policy moves as one unit.
- Remove `spring-ai-rag` and `agentic-spring-ai-model` from builtin-nodes if no
  remaining production source imports them.
- Remove HTTP dependencies only after checking all remaining builtin nodes.

`agentic-spring-ai-model` remains in Core even if builtin-nodes no longer uses
it: it is the provider-neutral contract consumed by Extensions parsers and
rerank providers.

## Verification Gates

1. Core binary compatibility passes against the current 2.1 baseline.
2. New and old RAG nodes produce equivalent retrieval, rerank, prompt, and
   output state for the same inputs.
3. New and old HTTP nodes produce equivalent requests and response state.
4. New and old document nodes produce equivalent local/remote parsing results.
5. Every existing private-network, local-path, redirect, byte-limit, and timeout
   test passes against the new network implementation.
6. Core production POMs and BOM contain no Extensions dependency.
7. Extensions BOM manages both new artifacts.
8. Cross-repository tests install Core first in an isolated Maven repository.
9. Core and Extensions full tests, package, format, checkstyle, license, lint,
   and diff checks pass.

## Release Order

Publish Core 2.1 first, then Extensions 2.1 against that exact Core release.
Extensions release builds must resolve both `revision` and
`agentic-spring-ai.version` to `2.1.0`.
