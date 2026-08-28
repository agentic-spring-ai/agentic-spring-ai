# Changelog

All notable changes to Spring AI Alibaba are documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> For full release notes and downloadable artifacts, see the
> [GitHub Releases](https://github.com/agentic-spring-ai/agentic-spring-ai/releases) page.

## [Unreleased]

### Added
- Placeholder section. New changes will be listed here before the next release.

## [1.1.2.3] - 2026-08-29

### Changed
- 发布元数据与项目文档中的源码、问题跟踪及持续集成链接迁移至 `agentic-spring-ai` 组织。
- 发布工具链升级为 Maven Central Publishing Plugin 0.11.0 与 Maven GPG Plugin 3.2.8。
- Spring AI Alibaba Extensions 依赖升级至 1.1.2.4。

### Fixed
- 修复元数据 record 中强类型容器实际包含通用 Map 时的 Jackson 序列化失败。

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

[Unreleased]: https://github.com/agentic-spring-ai/agentic-spring-ai/compare/v1.1.2.3...HEAD
[1.1.2.3]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.2.3
[1.1.2.2]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.2.2
[1.1.2.1]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.2.1
[1.1.2.0]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.2.0
[1.1.0.0]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.1.0.0
[1.0.0.4]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.0.0.4
[1.0.0.3]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.0.0.3
[1.0.0.2]: https://github.com/agentic-spring-ai/agentic-spring-ai/releases/tag/v1.0.0.2
