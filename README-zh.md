# [Agentic Spring AI](https://agentic-spring-ai.github.io/website/)

[English](README.md) | [简体中文](README-zh.md)

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-2.1.0--dev-blue)](https://github.com/agentic-spring-ai/agentic-spring-ai)

Agentic Spring AI 是面向 Java 开发者的智能体应用框架，用于构建 Agent、工作流和多智能体应用。项目基于 Spring AI，提供上下文工程、人工介入、图工作流和分布式 Agent-to-Agent（A2A）协作能力。

## 核心能力

- **智能体编排**：提供 `ReactAgent`、`SequentialAgent`、`ParallelAgent`、`RoutingAgent` 和 `LoopAgent`。
- **上下文工程**：支持上下文压缩与编辑、调用限制、工具重试、规划和动态工具选择。
- **图工作流**：支持条件路由、并行执行、嵌套图、状态持久化和中断恢复。
- **智能体运行时基础**：提供模型无关的智能体编排、工作流状态和可嵌入调试支持。
- **开放集成**：兼容 DashScope、OpenAI 等模型，以及工具调用、MCP、A2A 和 Nacos。
- **可视化调试**：提供可嵌入 Spring Boot 应用的 Agent Chat UI。

## 快速开始

环境要求：JDK 17 或更高版本、Maven 3.9.1 或更高版本。以下命令使用仓库自带的 Maven Wrapper。

```shell
git clone --depth=1 https://github.com/agentic-spring-ai/agentic-spring-ai.git
cd agentic-spring-ai

# 安装本地开发版本
./mvnw -DskipTests install

# 配置 DashScope API Key 并运行 Chatbot 示例
export AI_DASHSCOPE_API_KEY=your-api-key
./mvnw -f examples/chatbot/pom.xml spring-boot:run
```

启动后访问 [http://localhost:8080/chatui/index.html](http://localhost:8080/chatui/index.html)。其他模型的配置方式请参阅[快速开始](https://agentic-spring-ai.github.io/website/docs/quick-start)。

## 项目模块

| 模块 | 说明 |
| --- | --- |
| [Agent Framework](agentic-spring-ai-agent-framework) | 智能体开发与多智能体编排 |
| [Graph Core](agentic-spring-ai-graph-core) | 状态管理、持久化和工作流运行时 |
| [Studio](agentic-spring-ai-studio) | Agent 可视化调试界面 |
| [Sandbox](https://github.com/agentic-spring-ai/agentic-spring-ai-extensions/tree/main/sandbox/agentic-spring-ai-sandbox) | 工具调用的可选隔离执行环境，由 Extensions 维护 |
| [Spring Boot Starters](spring-boot-starters) | 内置图节点和图可观测性 |
| [Extensions](https://github.com/agentic-spring-ai/agentic-spring-ai-extensions) | 模型与文档契约、A2A、Nacos、AgentScope、存储等可选扩展 |
| [Examples](examples) | Chatbot、多智能体、图工程和文档示例 |

## 文档

- [项目概览](https://agentic-spring-ai.github.io/website/docs/overview)
- [快速开始](https://agentic-spring-ai.github.io/website/docs/quick-start)
- [Agent Framework 教程](https://agentic-spring-ai.github.io/website/docs/frameworks/agent-framework/tutorials/agents)
- [Graph Core 快速开始](https://agentic-spring-ai.github.io/website/docs/frameworks/graph-core/quick-start)
- [Graph Engineering 指南](docs/graph-engineering.md)
- [持久化与执行器迁移方案](docs/persistence-executor-migration.md)
- [示例项目](examples)
- [模型厂商相关示例](https://github.com/agentic-spring-ai/agentic-spring-ai-extensions/tree/main/examples)

## 参与贡献

提交代码前请阅读[贡献指南](CONTRIBUTING-zh.md)。问题和建议可通过 [GitHub Issues](https://github.com/agentic-spring-ai/agentic-spring-ai/issues) 反馈。

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。
