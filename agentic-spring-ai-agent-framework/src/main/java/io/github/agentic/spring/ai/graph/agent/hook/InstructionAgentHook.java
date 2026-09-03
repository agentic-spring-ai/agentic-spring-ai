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
package io.github.agentic.spring.ai.graph.agent.hook;

import io.github.agentic.spring.ai.graph.agent.ReactAgent;
import io.github.agentic.spring.ai.graph.agent.hook.messages.AgentCommand;
import io.github.agentic.spring.ai.graph.agent.hook.messages.MessagesAgentHook;
import io.github.agentic.spring.ai.graph.serializer.AgentInstructionMessage;
import io.github.agentic.spring.ai.graph.RunnableConfig;

import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * MessagesAgentHook that injects the ReactAgent's instruction into messages before each agent run.
 * <p>
 * When this hook is active, it runs at {@link HookPosition#BEFORE_AGENT} and reads
 * {@link ReactAgent#instruction()} from {@link #getAgent()}. If the instruction is non-empty,
 * it prepends an {@link AgentInstructionMessage} to the given messages and returns an
 * {@link AgentCommand} with {@link io.github.agentic.spring.ai.graph.agent.hook.messages.UpdatePolicy#REPLACE}.
 * This allows ReactAgent to avoid adding instruction again in the subgraph adapter when used as a subgraph node.
 * <p>
 * The hook is idempotent: if the incoming messages already contain an identical
 * {@link AgentInstructionMessage} (e.g. placed there manually by a caller such as AgentTool),
 * it is not duplicated — the output keeps exactly one copy of the current instruction (issue #77).
 * <p>
 * This hook is added by default in ReactAgent when no other hook is an InstructionAgentHook.
 * It runs first among beforeAgent hooks (lowest order).
 */
@HookPositions(HookPosition.BEFORE_AGENT)
public class InstructionAgentHook extends MessagesAgentHook {

	private ReactAgent reactAgent;

	@Override
	public AgentCommand beforeAgent(List<Message> previousMessages, RunnableConfig config) {
		if (reactAgent == null) {
			return new AgentCommand(previousMessages);
		}
		String instruction = reactAgent.instruction();
		if (!StringUtils.hasLength(instruction)) {
			return new AgentCommand(previousMessages);
		}
		// Idempotent injection: keep exactly one copy of the current instruction even when
		// a caller (e.g. AgentTool) already placed it into the messages (see issue #77).
		List<Message> newMessages = new ArrayList<>();
		for (Message message : previousMessages) {
			if (message instanceof AgentInstructionMessage existing && instruction.equals(existing.getText())) {
				continue;
			}
			newMessages.add(message);
		}
		newMessages.add(AgentInstructionMessage.builder().text(instruction).build());
		return new AgentCommand(newMessages);
	}

	@Override
	public String getName() {
		return "InstructionAgentHook";
	}

	@Override
	public int getOrder() {
		return -100;
	}

	@Override
	public ReactAgent getAgent() {
		return reactAgent;
	}

	@Override
	public void setAgent(ReactAgent agent) {
		this.reactAgent = agent;
	}

	/**
	 * Create the default InstructionAgentHook instance (used when no other hook handles instruction).
	 * @return a new InstructionAgentHook
	 */
	public static InstructionAgentHook create() {
		return new InstructionAgentHook();
	}
}
