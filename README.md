# [Agentic Spring AI](https://agentic-spring-ai.github.io/website/en/)

[English](README.md) | [简体中文](README-zh.md)

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-2.1.0--dev-blue)](https://github.com/agentic-spring-ai/agentic-spring-ai)

Agentic Spring AI is a framework for Java developers building agents, workflows, and multi-agent applications. Built on Spring AI, it provides context engineering, human-in-the-loop, graph workflows, and distributed Agent-to-Agent (A2A) collaboration.

## Features

- **Agent orchestration**: `ReactAgent`, `SequentialAgent`, `ParallelAgent`, `RoutingAgent`, and `LoopAgent`.
- **Context engineering**: context compaction and editing, call limits, tool retries, planning, and dynamic tool selection.
- **Graph workflows**: conditional routing, parallel execution, nested graphs, state persistence, and interruption recovery.
- **Agent runtime foundation**: provider-neutral agent orchestration, workflow state, and embeddable debugging support.
- **Open integrations**: DashScope, OpenAI, tool calling, MCP, A2A, and Nacos.
- **Visual debugging**: an embeddable Agent Chat UI for Spring Boot applications.

## Quick Start

Requirements: JDK 17 or later and Maven 3.9.1 or later. The commands below use the included Maven Wrapper.

```shell
git clone --depth=1 https://github.com/agentic-spring-ai/agentic-spring-ai.git
cd agentic-spring-ai

# Install the local development modules.
./mvnw -DskipTests install

# Configure the DashScope API key and run the chatbot example.
export AI_DASHSCOPE_API_KEY=your-api-key
./mvnw -f examples/chatbot/pom.xml spring-boot:run
```

Open [http://localhost:8080/chatui/index.html](http://localhost:8080/chatui/index.html). See the [Quick Start](https://agentic-spring-ai.github.io/website/en/docs/quick-start) for other model providers.

## Modules

| Module | Description |
| --- | --- |
| [Agent Framework](agentic-spring-ai-agent-framework) | Agent development and multi-agent orchestration |
| [Graph Core](agentic-spring-ai-graph-core) | State management, persistence, and workflow runtime |
| [Studio](agentic-spring-ai-studio) | Visual debugging UI for agents |
| [Sandbox](https://github.com/agentic-spring-ai/agentic-spring-ai-extensions/tree/main/sandbox/agentic-spring-ai-sandbox) | Optional isolated execution environment for tool calls, maintained in Extensions |
| [Spring Boot Starters](spring-boot-starters) | Built-in graph nodes and graph observability |
| [Extensions](https://github.com/agentic-spring-ai/agentic-spring-ai-extensions) | Model and document contracts, A2A, Nacos, AgentScope, storage, and other optional integrations |
| [Examples](examples) | Chatbot, multi-agent, graph engineering, and documentation examples |

## Documentation

- [Overview](https://agentic-spring-ai.github.io/website/en/docs/overview)
- [Quick Start](https://agentic-spring-ai.github.io/website/en/docs/quick-start)
- [Agent Framework tutorials](https://agentic-spring-ai.github.io/website/en/docs/frameworks/agent-framework/tutorials/agents)
- [Graph Core Quick Start](https://agentic-spring-ai.github.io/website/en/docs/frameworks/graph-core/quick-start)
- [Graph Engineering guide](docs/graph-engineering.md)
- [Persistence and executor migration](docs/persistence-executor-migration.md)
- [Examples](examples)
- [Provider-specific examples](https://github.com/agentic-spring-ai/agentic-spring-ai-extensions/tree/main/examples)

## Contributing

Read the [contribution guide](CONTRIBUTING.md) before submitting changes. Report problems and suggestions through [GitHub Issues](https://github.com/agentic-spring-ai/agentic-spring-ai/issues).

## License

Agentic Spring AI is available under the [Apache License 2.0](LICENSE).
