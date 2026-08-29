# Changelog

All notable changes to Agentic for Spring AI are documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> For full release notes and downloadable artifacts, see the
> [GitHub Releases](https://github.com/agentic-spring-ai/agentic-spring-ai/releases) page.

## [Unreleased]

### Changed
- Started the `2.1.0-dev` development line.
- Removed the retired admin module and aligned repository configuration and documentation
  with the Agentic for Spring AI project identity.
- Hardened execution, authentication, persistence, and build contracts.
- Studio now requires `spring.ai.alibaba.agent.studio.execution.auth-token` for every API request;
  static UI assets remain public.
- Docker code execution now defaults to disabled networking, a read-only root filesystem,
  bounded CPU/memory/PIDs, dropped capabilities, a 64 MiB writable `/tmp` tmpfs, and a 1 MiB
  captured-output limit.
- Local code execution uses a temporary per-invocation directory; multiple blocks in one invocation
  share that directory, which is deleted when the invocation finishes.
- Document extraction now restricts local files to a configured root, blocks private-network URLs by
  default, limits response size, and enforces a total remote-fetch timeout.
- Graph observation content capture now defaults to disabled. Set
  `spring.ai.alibaba.graph.observation.capture-content=true` only when prompt and completion content
  may be recorded; captured values are redacted and truncated.
- `FileSystemStore` namespace elements and keys must each be a single safe path segment. Represent
  hierarchy with multiple namespace elements instead of embedding `/`, absolute paths, `.` or `..`.
- The A2A server now uses a bounded executor. Size it with the
  `spring.ai.alibaba.a2a.server.executor-*` properties; when saturated, its caller-runs policy applies
  backpressure on the submitting thread instead of growing an unbounded queue.
- Fixed checkpoint and graph correctness edge cases: FileSystemSaver thread IDs are treated literally,
  MongoSaver honors checkpoint retention, resumed nodes receive checkpoint state, parallel subgraph
  results are merged as deltas, and one-shot `jump_to` routing is consumed instead of leaking into
  later loops.
- Added the [2.1.0 migration and compatibility guide](docs/2.1.0-migration.md), an isolated
  Extensions source-compatibility check, and CI compilation coverage for JDK 17 and JDK 21.

## [2.0.0.0] - 2026-08-27

### Changed
- Migrated the project coordinates and repository identity to Agentic for Spring AI.
- Established the Spring Boot 4.1 and Spring AI 2.0 generation of the project.

## [1.1.2.2] - 2026-03-10

### Added
- **AgentScope integration** — `AgentScopeAgent` wraps [AgentScope ReActAgent](https://github.com/agentscope-ai/agentscope-java)
  as a `BaseAgent` for use in graph workflows (`agentic-spring-ai-starter-agentscope`).
- **Multiagent patterns** under `examples/multiagent-patterns/`:
  Subagent, Supervisor, Skills, Routing (simple and graph variants),
  Handoffs (single- and multi-agent), and Workflow.
- **Voice Agent** example — sandwich architecture (STT → ReactAgent → TTS)
  with WebSocket streaming, DashScope ASR and CosyVoice TTS.
- **Multimodal agent** examples (image understanding, audio input).

### Changed
- Default agent configuration refinements for graph-based workflows.
- Updated documentation portal at <https://java2ai.com/docs/overview>.

## [1.1.2.1] - 2026-03-09

### Fixed
- Patch release addressing regressions introduced in 1.1.2.0.

## [1.1.2.0] - 2026-02-02

### Added
- Enhancements to the Agent Graph core, including additional node types
  and improved reactive stream handling.
- New starters and example modules.

## [1.1.0.0] - 2025-12-30

### Added
- First stable release of the 1.1.x line, built on
  [Spring AI 1.1.0](https://github.com/spring-projects/spring-ai/releases/tag/v1.1.0)
  and [Spring AI Alibaba Extensions 1.1.0.0](https://github.com/agentic-spring-ai/agentic-spring-ai-extensions/releases/tag/v1.1.0.0).
- Production-ready Agent Graph runtime with comprehensive documentation
  available at <https://java2ai.com/docs/overview>.

### Changed
- Aligned public APIs with Spring AI 1.1.0.

## [1.0.0.4] - 2025-09-25

### Added
- **Rebuilt Agent Graph Engine** — refactored core Agent API; shifted to
  a Flux-based reactive stream architecture.
- **Agent-to-Agent (A2A) communication** — A2A client/server with
  Nacos integration for remote agent discovery.
- Bumped to Spring AI 1.0.1 with widespread stability fixes.

## [1.0.0.3] - 2025-08-14

### Fixed
- Stability and compatibility fixes on top of 1.0.0.x.

## [1.0.0.2] - 2025-05-29

### Fixed
- Early production fixes following the 1.0.0 GA release.

## Earlier releases

See [GitHub Releases](https://github.com/agentic-spring-ai/agentic-spring-ai/releases)
for the full history, including 1.1.0.0-RC1, 1.1.0.0-M5, 1.1.0.0-M4, 1.0.0.1, and 1.0.0.0.

[Unreleased]: https://github.com/agentic-spring-ai/agentic-spring-ai/compare/v2.0.0.0...HEAD
[2.0.0.0]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v2.0.0.0
[1.1.2.2]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.2.2
[1.1.2.1]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.2.1
[1.1.2.0]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.2.0
[1.1.0.0]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.0.0
[1.0.0.4]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.0.0.4
[1.0.0.3]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.0.0.3
[1.0.0.2]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.0.0.2
