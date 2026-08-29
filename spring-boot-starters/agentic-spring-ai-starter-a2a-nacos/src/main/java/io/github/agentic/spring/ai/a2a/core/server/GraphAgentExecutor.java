/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.agentic.spring.ai.a2a.core.server;

import io.github.agentic.spring.ai.graph.NodeOutput;
import io.github.agentic.spring.ai.graph.RunnableConfig;
import io.github.agentic.spring.ai.graph.agent.Agent;
import io.github.agentic.spring.ai.graph.agent.BaseAgent;
import io.github.agentic.spring.ai.graph.exception.GraphRunnerException;
import io.github.agentic.spring.ai.graph.exception.GraphStateException;
import io.github.agentic.spring.ai.graph.streaming.StreamingOutput;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.A2A;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class GraphAgentExecutor implements AgentExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger(GraphAgentExecutor.class);

	private static final Set<String> IGNORE_NODE_TYPE = Set.of("preLlm", "postLlm", "preTool", "tool", "postTool");

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static final String STREAMING_METADATA_KEY = "isStreaming";

	public static final String STREAMING_TASK_WAIT_TIMEOUT_MILLIS_METADATA_KEY = "streamingTaskWaitTimeoutMillis";

	private static final long DEFAULT_STREAMING_TASK_WAIT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

	private final Agent executeAgent;

	public GraphAgentExecutor(Agent executeAgent) {
		this.executeAgent = executeAgent;
	}

	private Task newTask(Message request) {
		String contextId = request.getContextId();
		if (contextId == null || contextId.isEmpty()) {
			contextId = UUID.randomUUID().toString();
		}
		String id = UUID.randomUUID().toString();
		if (request.getTaskId() != null && !request.getTaskId().isEmpty()) {
			id = request.getTaskId();
		}
		return new Task(id, contextId, new TaskStatus(TaskState.SUBMITTED), null, List.of(request), null);
	}

	@Override
	public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
		try {
			Message message = context.getParams().message();
			StringBuilder sb = new StringBuilder();
			for (Part<?> each : message.getParts()) {
				if (Part.Kind.TEXT.equals(each.getKind())) {
					sb.append(((TextPart) each).getText()).append("\n");
				}
			}
			// TODO adapter for all agent type, now only support react agent
			String input = sb.toString().trim();
			if (!StringUtils.hasLength(input)) {
				LOGGER.info("Instruction in remote agent is empty, this agent will share messages with remote agent by using the same threadId.");
			}

			if (isStreamRequest(context)) {
				executeStreamTask(input, context, eventQueue);
			}
			else {
				executeForNonStreamTask(input, context, eventQueue);
			}
		}
		catch (Exception e) {
			LOGGER.error("Agent execution failed", e);
			eventQueue.enqueueEvent(A2A.toAgentMessage("Agent execution failed: " + e.getMessage()));
		}
	}

	@Override
	public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
	}

	private boolean isStreamRequest(RequestContext context) {
		MessageSendParams params = context.getParams();
		if (null == params.metadata()) {
			return false;
		}
		if (!params.metadata().containsKey(STREAMING_METADATA_KEY)) {
			return false;
		}
		Object value = params.metadata().get(STREAMING_METADATA_KEY);
		if (value instanceof Boolean streaming) {
			return streaming;
		}
		if (value instanceof String text && StringUtils.hasText(text)) {
			if ("true".equalsIgnoreCase(text)) {
				return true;
			}
			if ("false".equalsIgnoreCase(text)) {
				return false;
			}
		}
		throw new IllegalArgumentException(
				"Invalid A2A streaming metadata: " + value + ". It must be a boolean value.");
	}

	private RunnableConfig getRunnableConfig(RequestContext context) {
		RunnableConfig.Builder builder = RunnableConfig.builder();

		// Get metadata from context
		MessageSendParams params = context.getParams();
		if (params != null && params.metadata() != null) {
			Map<String, Object> metadata = params.metadata();

			// Check if threadId exists in metadata and add it to RunnableConfig
			if (metadata.containsKey("threadId")) {
				Object threadIdObj = metadata.get("threadId");
				if (threadIdObj instanceof String) {
					builder.threadId((String) threadIdObj);
				}
			}

			// Add all metadata to RunnableConfig
			for (Map.Entry<String, Object> entry : metadata.entrySet()) {
				builder.addMetadata(entry.getKey(), entry.getValue());
			}
		}

		return builder.build();
	}

	private void executeStreamTask(String inputMessage, RequestContext context, EventQueue eventQueue)
			throws GraphStateException, GraphRunnerException {
		RunnableConfig runnableConfig = getRunnableConfig(context);
		Flux<NodeOutput> generator = executeAgent.stream(inputMessage, runnableConfig);
		Task task = context.getTask();
		if (task == null) {
			task = newTask(context.getMessage());
			eventQueue.enqueueEvent(task);
		}
		TaskUpdater taskUpdater = new TaskUpdater(context, eventQueue);
		taskUpdater.submit();
		CountDownLatch streamFinished = new CountDownLatch(1);
		AtomicBoolean terminalStatusSent = new AtomicBoolean();
		Disposable subscription = generator.subscribe(new ReactAgentNodeOutputConsumer(taskUpdater), throwable -> {
			try {
				LOGGER.error("Agent execution failed", throwable);
				failTaskOnce(taskUpdater, terminalStatusSent, throwable.getMessage());
			}
			finally {
				streamFinished.countDown();
			}
		}, () -> {
			try {
				if (terminalStatusSent.compareAndSet(false, true)) {
					taskUpdater.complete();
				}
			}
			finally {
				streamFinished.countDown();
			}
		});
		try {
			if (!waitForStreamCompletion(task.getId(), streamFinished, getStreamingTaskWaitTimeoutMillis(context))) {
				subscription.dispose();
				failTaskOnce(taskUpdater, terminalStatusSent,
						"A2A stream execution interrupted while waiting for task " + task.getId() + " to finish.");
			}
		}
		catch (RuntimeException e) {
			subscription.dispose();
			failTaskOnce(taskUpdater, terminalStatusSent, e.getMessage());
		}
	}

	private void failTaskOnce(TaskUpdater taskUpdater, AtomicBoolean terminalStatusSent, String message) {
		if (terminalStatusSent.compareAndSet(false, true)) {
			taskUpdater.fail(A2A.toAgentMessage(message));
		}
	}

	private void executeForNonStreamTask(String inputMessage, RequestContext context, EventQueue eventQueue)
			throws GraphStateException, GraphRunnerException {
		RunnableConfig runnableConfig = getRunnableConfig(context);
		var result = executeAgent.invoke(inputMessage, runnableConfig);
		// FIXME: currently only support ReactAgent and A2aRemoteAgent as the root agent
		String outputText = result.get().data().containsKey(((BaseAgent)executeAgent).getOutputKey())
				? String.valueOf(result.get().data().get(((BaseAgent)executeAgent).getOutputKey())) : "No output key in result.";

		Task task = context.getTask();
		if (task == null) {
			task = newTask(context.getMessage());
			eventQueue.enqueueEvent(task);
		}
		TaskUpdater taskUpdater = new TaskUpdater(context, eventQueue);
		boolean taskComplete = true;
		boolean requireUserInput = false;
		if (!taskComplete && !requireUserInput) {
			taskUpdater.startWork(taskUpdater.newAgentMessage(List.of(new TextPart(outputText)), Map.of()));
		}
		else if (requireUserInput) {
			taskUpdater.startWork(taskUpdater.newAgentMessage(List.of(new TextPart(outputText)), Map.of()));
		}
		else {
			taskUpdater.addArtifact(List.of(new TextPart(outputText)), UUID.randomUUID().toString(),
					"conversation_result", Map.of("output", outputText));
			taskUpdater.complete();
		}
	}

	private boolean waitForStreamCompletion(String taskId, CountDownLatch streamFinished, long timeoutMillis) {
		try {
			if (!streamFinished.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
				throw new IllegalStateException("Timed out waiting for A2A task " + taskId
						+ " stream to finish after " + timeoutMillis + " ms.");
			}
			return true;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private long getStreamingTaskWaitTimeoutMillis(RequestContext context) {
		MessageSendParams params = context.getParams();
		if (params == null || params.metadata() == null) {
			return DEFAULT_STREAMING_TASK_WAIT_TIMEOUT_MILLIS;
		}
		Object value = params.metadata().get(STREAMING_TASK_WAIT_TIMEOUT_MILLIS_METADATA_KEY);
		if (value == null) {
			return DEFAULT_STREAMING_TASK_WAIT_TIMEOUT_MILLIS;
		}
		long timeoutMillis = parsePositiveTimeoutMillis(value);
		if (timeoutMillis <= 0) {
			throw new IllegalArgumentException("Invalid A2A streaming task wait timeout: " + value
					+ ". It must be a positive number of milliseconds.");
		}
		return timeoutMillis;
	}

	private long parsePositiveTimeoutMillis(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String text && StringUtils.hasText(text)) {
			return Long.parseLong(text);
		}
		return -1;
	}

	private static class ReactAgentNodeOutputConsumer implements Consumer<NodeOutput> {

		private final TaskUpdater taskUpdater;

		private final AtomicInteger artifactNum;

		private ReactAgentNodeOutputConsumer(TaskUpdater taskUpdater) {
			this.taskUpdater = taskUpdater;
			this.artifactNum = new AtomicInteger();
		}

		@Override
		public void accept(NodeOutput nodeOutput) {
			if (nodeOutput.isSTART() || nodeOutput.isEND() || IGNORE_NODE_TYPE.contains(nodeOutput.node())) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Agent parts output: {}", buildDebugDetailInfo(nodeOutput));
				}
				return;
			}

			String content = "";
			if (nodeOutput instanceof StreamingOutput) {
				content = ((StreamingOutput) nodeOutput).chunk();
			}

			if (!StringUtils.hasLength(content)) {
				return;
			}

			taskUpdater.addArtifact(Collections.singletonList(new TextPart(content)), null,
					String.valueOf(artifactNum.incrementAndGet()), Map.of());
		}

		private String buildDebugDetailInfo(NodeOutput nodeOutput) {
			Map<String, Object> outputJson = new HashMap<>();
			outputJson.put("data", nodeOutput.state().data());
			outputJson.put("node", nodeOutput.node());
			try {
				return OBJECT_MAPPER.writeValueAsString(outputJson);
			}
			catch (JsonProcessingException ex) {
				return outputJson.toString();
			}
		}

	}

}
