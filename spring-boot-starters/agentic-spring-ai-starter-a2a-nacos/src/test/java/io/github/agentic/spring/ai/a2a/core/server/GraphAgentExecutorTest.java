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

import io.github.agentic.spring.ai.graph.RunnableConfig;
import io.github.agentic.spring.ai.graph.agent.Agent;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.spec.Event;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphAgentExecutorTest {

	@Test
	void executeCompletesFiniteStreamWithoutTimeoutFailure() throws Exception {
		Agent agent = mock(Agent.class);
		when(agent.stream(eq("hello"), any(RunnableConfig.class))).thenReturn(Flux.empty());
		GraphAgentExecutor executor = new GraphAgentExecutor(agent);
		Message message = new Message(Message.Role.USER, List.of(new TextPart("hello")), "message-id", "context-id",
				"task-id", null, null);
		Task task = new Task("task-id", "context-id", new TaskStatus(TaskState.WORKING), null, List.of(message), null);
		MessageSendParams params = new MessageSendParams(message, null,
				Map.of(GraphAgentExecutor.STREAMING_METADATA_KEY, true, "streamingTaskWaitTimeoutMillis", 50L));
		RequestContext context = new RequestContext(params, "task-id", "context-id", task, List.of());
		EventQueue eventQueue = mock(EventQueue.class);

		assertTimeoutPreemptively(Duration.ofMillis(700), () -> executor.execute(context, eventQueue));

		ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
		verify(eventQueue, atLeastOnce()).enqueueEvent(eventCaptor.capture());
		assertFalse(eventCaptor.getAllValues()
			.stream()
			.anyMatch(event -> eventContainsText(event, "Timed out waiting for A2A task task-id")),
				"finite stream completion should not enqueue a timeout failure message");
	}

	@Test
	void executeAcceptsStringStreamingMetadata() throws Exception {
		Agent agent = mock(Agent.class);
		when(agent.stream(eq("hello"), any(RunnableConfig.class))).thenReturn(Flux.empty());
		GraphAgentExecutor executor = new GraphAgentExecutor(agent);
		Message message = new Message(Message.Role.USER, List.of(new TextPart("hello")), "message-id", "context-id",
				"task-id", null, null);
		Task task = new Task("task-id", "context-id", new TaskStatus(TaskState.WORKING), null, List.of(message), null);
		MessageSendParams params = new MessageSendParams(message, null,
				Map.of(GraphAgentExecutor.STREAMING_METADATA_KEY, "true", "streamingTaskWaitTimeoutMillis", 50L));
		RequestContext context = new RequestContext(params, "task-id", "context-id", task, List.of());

		executor.execute(context, mock(EventQueue.class));

		verify(agent).stream(eq("hello"), any(RunnableConfig.class));
	}

	@Test
	void executeReportsInvalidStreamingMetadata() throws Exception {
		Agent agent = mock(Agent.class);
		GraphAgentExecutor executor = new GraphAgentExecutor(agent);
		Message message = new Message(Message.Role.USER, List.of(new TextPart("hello")), "message-id", "context-id",
				"task-id", null, null);
		Task task = new Task("task-id", "context-id", new TaskStatus(TaskState.WORKING), null, List.of(message), null);
		MessageSendParams params = new MessageSendParams(message, null,
				Map.of(GraphAgentExecutor.STREAMING_METADATA_KEY, "sometimes"));
		RequestContext context = new RequestContext(params, "task-id", "context-id", task, List.of());
		EventQueue eventQueue = mock(EventQueue.class);

		executor.execute(context, eventQueue);

		ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
		verify(eventQueue).enqueueEvent(eventCaptor.capture());
		assertTrue(eventContainsText(eventCaptor.getValue(), "Invalid A2A streaming metadata"));
	}

	@Test
	void executeReturnsTimeoutFailureWhenStreamingTaskNeverReachesFinalState() throws Exception {
		Agent agent = mock(Agent.class);
		when(agent.stream(eq("hello"), any(RunnableConfig.class))).thenReturn(Flux.never());
		GraphAgentExecutor executor = new GraphAgentExecutor(agent);
		Message message = new Message(Message.Role.USER, List.of(new TextPart("hello")), "message-id", "context-id",
				"task-id", null, null);
		Task task = new Task("task-id", "context-id", new TaskStatus(TaskState.WORKING), null, List.of(message), null);
		MessageSendParams params = new MessageSendParams(message, null,
				Map.of(GraphAgentExecutor.STREAMING_METADATA_KEY, true, "streamingTaskWaitTimeoutMillis", 50L));
		RequestContext context = new RequestContext(params, "task-id", "context-id", task, List.of());
		EventQueue eventQueue = mock(EventQueue.class);

		assertTimeoutPreemptively(Duration.ofMillis(700), () -> executor.execute(context, eventQueue));

		ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
		verify(eventQueue, atLeastOnce()).enqueueEvent(eventCaptor.capture());
		assertTrue(hasFinalStatusEvent(eventCaptor.getAllValues(), TaskState.FAILED),
				"stream timeout should enqueue a final FAILED task status update");
	}

	@Test
	void executeMarksStreamingTaskFailedWhenWaitIsInterrupted() throws Exception {
		Agent agent = mock(Agent.class);
		when(agent.stream(eq("hello"), any(RunnableConfig.class))).thenReturn(Flux.never());
		GraphAgentExecutor executor = new GraphAgentExecutor(agent);
		Message message = new Message(Message.Role.USER, List.of(new TextPart("hello")), "message-id", "context-id",
				"task-id", null, null);
		Task task = new Task("task-id", "context-id", new TaskStatus(TaskState.WORKING), null, List.of(message), null);
		MessageSendParams params = new MessageSendParams(message, null,
				Map.of(GraphAgentExecutor.STREAMING_METADATA_KEY, true, "streamingTaskWaitTimeoutMillis", 5000L));
		RequestContext context = new RequestContext(params, "task-id", "context-id", task, List.of());
		EventQueue eventQueue = mock(EventQueue.class);
		CountDownLatch returned = new CountDownLatch(1);
		AtomicBoolean interruptedAfterReturn = new AtomicBoolean(false);

		Thread executionThread = new Thread(() -> {
			try {
				executor.execute(context, eventQueue);
				interruptedAfterReturn.set(Thread.currentThread().isInterrupted());
			}
			catch (Exception e) {
				throw new AssertionError(e);
			}
			finally {
				returned.countDown();
			}
		});
		executionThread.setDaemon(true);
		executionThread.start();

		waitUntilSleeping(executionThread);
		executionThread.interrupt();

		assertTrue(returned.await(700, TimeUnit.MILLISECONDS), "stream execution should return after interruption");
		assertTrue(interruptedAfterReturn.get(), "stream execution should preserve the interrupt status");
		ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
		verify(eventQueue, atLeastOnce()).enqueueEvent(eventCaptor.capture());
		assertTrue(hasFinalStatusEvent(eventCaptor.getAllValues(), TaskState.FAILED),
				"interrupted stream execution should enqueue a final FAILED task status update");
	}

	@Test
	void waitTaskCompletedReturnsAndPreservesInterruptWhenInterrupted() throws Exception {
		GraphAgentExecutor executor = new GraphAgentExecutor(null);
		CountDownLatch streamFinished = new CountDownLatch(1);
		Method waitForStreamCompletion = GraphAgentExecutor.class.getDeclaredMethod("waitForStreamCompletion",
				String.class, CountDownLatch.class, long.class);
		waitForStreamCompletion.setAccessible(true);
		CountDownLatch returned = new CountDownLatch(1);
		AtomicBoolean interruptedAfterReturn = new AtomicBoolean(false);

		Thread waitingThread = new Thread(() -> {
			try {
				waitForStreamCompletion.invoke(executor, "task-id", streamFinished, TimeUnit.MINUTES.toMillis(5));
				interruptedAfterReturn.set(Thread.currentThread().isInterrupted());
			}
			catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
			finally {
				returned.countDown();
			}
		});
		waitingThread.setDaemon(true);
		waitingThread.start();

		waitUntilSleeping(waitingThread);
		waitingThread.interrupt();

		assertTrue(returned.await(300, TimeUnit.MILLISECONDS),
				"waitTaskCompleted should return promptly when the waiting thread is interrupted");
		assertTrue(interruptedAfterReturn.get(), "waitTaskCompleted should preserve the interrupt status");
	}

	private void waitUntilSleeping(Thread thread) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
		while (System.nanoTime() < deadline && thread.getState() != Thread.State.TIMED_WAITING) {
			TimeUnit.MILLISECONDS.sleep(10);
		}
	}

	private boolean eventContainsText(Event event, String text) {
		if (event instanceof Message message) {
			return message.getParts()
				.stream()
				.filter(part -> Part.Kind.TEXT.equals(part.getKind()))
				.map(part -> ((TextPart) part).getText())
				.anyMatch(content -> content.contains(text));
		}
		return false;
	}

	private boolean hasFinalStatusEvent(List<Event> events, TaskState state) {
		return events.stream().anyMatch(event -> event instanceof TaskStatusUpdateEvent statusUpdate
				&& statusUpdate.getStatus().state() == state && statusUpdate.getStatus().state().isFinal()
				&& statusUpdate.isFinal());
	}

}
