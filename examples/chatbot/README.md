# ReAct Agent Example

This example showcases basic ReactAgent usage in Spring AI Alibaba.

## Quick Start

### Prerequisites

* Requires JDK 17+.
* Choose your LLM provider and get the API-KEY.

```shell
export AI_DASHSCOPE_API_KEY=your-api-key
```

### Run the ChatBot

1. Download the code.

```shell
git clone https://github.com/alibaba/agentic-spring-ai.git
cd agentic-spring-ai
```

2. Start the ChatBot.

```shell
# Run from repository root.
./mvnw -pl examples/chatbot spring-boot:run
```

3. Chat with ChatBot.
Open the browser and visit [http://localhost:8080/chatui/index.html](http://localhost:8080/chatui/index.html) to chat with the ChatBot.

<p align="center">
    <img src="../../docs/imgs/chatbot-chat-ui.gif" alt="chatbot-ui" style="max-width: 740px; height: 508px" />
</p>

## More Examples
Check [agentic-spring-ai-examples](https://github.com/agentic-spring-ai/examples/tree/main/agentic-spring-ai-agent-example) for more sophisticated examples.
