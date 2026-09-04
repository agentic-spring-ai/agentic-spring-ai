# Graph Node Integration Migration

Status: Implemented in Extensions 2.1. The old Core classes were removed before
the Core 2.1 release.

## Objective

Keep the Core built-in node artifact focused on graph and Spring AI primitives
while moving integration-heavy RAG, HTTP, and document-loading nodes to
Extensions. The replacement implementations preserve behavior and security,
but applications must update their dependencies and imports.

Core must not acquire an Extensions Maven dependency. New implementations use
new packages so the same fully qualified class name is never present in both
repositories.

## Target Ownership

Core continues to own:

- Graph execution and `NodeAction` contracts.
- Basic state/control nodes and Spring AI-native LLM, tool, and agent nodes.
- Provider-neutral graph nodes that do not own external integrations.

Extensions owns the provider-neutral rerank and document parser contracts in
`agentic-spring-ai-extensions-model` and adds the graph node artifacts in
reviewed implementation commit
`d50a367ee500411ec819b884720a794aa8bb9db2`:

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
- Historical Core compatibility deprecations before direct removal:
  `c9efc03dfea8bdb01e672a9715f2c63429bc9cbb`.

Core `tools/github-actions/setup-extensions` pins the reviewed Extensions
implementation commit above and installs the contract and graph node artifacts
for Core integration jobs. It intentionally does not pin a later Extensions
commit that updates the reciprocal Core pin, because that would create a
cross-repository SHA cycle.

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

The Extensions implementations copied the Core behavior before the old classes
were removed. Public constructors, builder methods, nested public types,
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
- Remove Core `KnowledgeRetrievalNode`, `HttpNode`, and
  `DocumentExtractorNode` after the replacement behavior and API shape are
  verified.
- Remove the Core-only `NetworkAccessPolicy` and obsolete integration
  dependencies from builtin-nodes.
- Update active documentation to use the Extensions artifacts.
- Keep replacement behavior and security tests in Extensions.
- Publish Core first, then publish Extensions against the exact Core 2.1
  release.

### 2.x

- Keep the Extensions package names, network defaults, RAG output formats, and
  public replacement signatures stable.
- Do not reintroduce the removed Core FQCNs.

### 3.0

- No additional removal is required for these graph nodes; the old Core types
  are already absent.

The unpublished Core `agentic-spring-ai-model` artifact has been removed.
Extensions parsers, graph nodes, and rerank providers consume
`agentic-spring-ai-extensions-model` instead.

## Verification Gates

1. The binary compatibility gate allows only the explicitly removed migrated
   Core types and rejects unrelated incompatible changes.
2. Extensions RAG behavior tests cover retrieval, rerank, prompt, and output
   state.
3. Extensions HTTP tests cover request and response mapping.
4. Extensions document tests cover local and remote parsing.
5. Every private-network, local-path, redirect, byte-limit, and timeout
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
