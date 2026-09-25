/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.agentic.spring.ai.graph.agent;

import io.github.agentic.spring.ai.graph.NodeOutput;
import io.github.agentic.spring.ai.graph.OverAllStateBuilder;
import io.github.agentic.spring.ai.graph.RunnableConfig;
import io.github.agentic.spring.ai.graph.StateGraph;
import io.github.agentic.spring.ai.graph.exception.GraphStateException;
import io.github.agentic.spring.ai.graph.streaming.OutputType;
import io.github.agentic.spring.ai.graph.streaming.StreamingOutput;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStreamMessagesOutputTypeTest {

	@Test
	void streamMessagesShouldExposeAgentToolStreamingMessages() throws Exception {
		ToolResponseMessage toolStreamingMessage = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "lookup", "partial-result")))
			.build();
		Agent agent = new StubStreamingAgent(new StreamingOutput<>(toolStreamingMessage, "agent_tool",
				"stub_agent", OverAllStateBuilder.builder().build(), OutputType.AGENT_TOOL_STREAMING));

		List<Message> messages = agent.streamMessages("run").collectList().block();

		assertThat(messages).containsExactly(toolStreamingMessage);
	}

	private static final class StubStreamingAgent extends Agent {

		private final Flux<NodeOutput> outputs;

		private StubStreamingAgent(NodeOutput... outputs) {
			super("stub_agent", "stub streaming agent");
			this.outputs = Flux.fromArray(outputs);
		}

		@Override
		protected Flux<NodeOutput> doStream(Map<String, Object> input, RunnableConfig runnableConfig) {
			return outputs;
		}

		@Override
		protected StateGraph initGraph() throws GraphStateException {
			throw new UnsupportedOperationException("Stub agent does not compile a graph");
		}

	}

}
