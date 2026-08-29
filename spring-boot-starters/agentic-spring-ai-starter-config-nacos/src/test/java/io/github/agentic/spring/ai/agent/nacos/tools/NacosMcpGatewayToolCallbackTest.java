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

package io.github.agentic.spring.ai.agent.nacos.tools;

import java.time.Duration;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.nacos.api.config.ConfigService;
import io.github.agentic.spring.ai.agent.nacos.vo.McpServersVO;
import io.github.agentic.spring.ai.mcp.nacos.service.NacosMcpOperationService;
import io.modelcontextprotocol.client.McpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(OutputCaptureExtension.class)
class NacosMcpGatewayToolCallbackTest {

	@Test
	void shouldApplyRequestAndInitializationTimeoutsWhenConfigured() {
		NacosMcpGatewayToolCallback callback = new NacosMcpGatewayToolCallback(toolDefinition(),
				mock(NacosMcpOperationService.class), new McpServersVO.McpServerVO(), Duration.ofSeconds(90));
		McpClient.SyncSpec clientSpec = mock(McpClient.SyncSpec.class, RETURNS_SELF);

		callback.configureTimeouts(clientSpec);

		verify(clientSpec).requestTimeout(Duration.ofSeconds(90));
		verify(clientSpec).initializationTimeout(Duration.ofSeconds(90));
	}

	@Test
	void shouldNotOverrideSdkTimeoutsWhenUnset() {
		NacosMcpGatewayToolCallback callback = new NacosMcpGatewayToolCallback(toolDefinition(),
				mock(NacosMcpOperationService.class), new McpServersVO.McpServerVO(), null);
		McpClient.SyncSpec clientSpec = mock(McpClient.SyncSpec.class, RETURNS_SELF);

		callback.configureTimeouts(clientSpec);

		verifyNoInteractions(clientSpec);
	}

	@Test
	void callShouldNotLogRawSensitiveInputAtInfo(CapturedOutput output) {
		NacosMcpGatewayToolCallback callback = new NacosMcpGatewayToolCallback(
				toolDefinition("mcp-streamable"), mock(NacosMcpOperationService.class), new McpServersVO.McpServerVO());
		String secret = "super-secret-token";

		callback.call("{\"authorization\":\"Bearer " + secret + "\",\"password\":\"p@ssw0rd\"}",
				new ToolContext(Map.of("apiKey", secret)));

		assertThat(output).doesNotContain(secret)
			.doesNotContain("p@ssw0rd")
			.doesNotContain("Bearer " + secret);
	}

	@Test
	void shouldRedactCompleteSensitiveValuesContainingWhitespace() {
		String sanitized = NacosMcpGatewayToolCallback.redactSensitiveText(
				"password=my secret value; {\"apiKey\":\"abc def ghi\"}, authorization=Bearer token suffix");

		assertThat(sanitized).doesNotContain("my secret value", "abc def ghi", "token suffix")
			.contains("password=******", "\"apiKey\":\"******\"", "authorization=******");
	}

	@Test
	void closeWaitsForInFlightListenerRegistrationBeforeRemovingIt() throws Exception {
		NacosMcpOperationService operationService = mock(NacosMcpOperationService.class);
		ConfigService configService = mock(ConfigService.class);
		when(operationService.getConfigService()).thenReturn(configService);
		when(configService.getConfig("data", "group", 3000)).thenReturn("value");
		CountDownLatch addEntered = new CountDownLatch(1);
		CountDownLatch allowAddToComplete = new CountDownLatch(1);
		CountDownLatch removeCalled = new CountDownLatch(1);
		AtomicBoolean addCompleted = new AtomicBoolean();
		AtomicBoolean removedBeforeAddCompleted = new AtomicBoolean();
		doAnswer(invocation -> {
			addEntered.countDown();
			allowAddToComplete.await(2, TimeUnit.SECONDS);
			addCompleted.set(true);
			return null;
		}).when(configService).addListener(org.mockito.ArgumentMatchers.eq("data"),
				org.mockito.ArgumentMatchers.eq("group"), org.mockito.ArgumentMatchers.any());
		doAnswer(invocation -> {
			removedBeforeAddCompleted.set(!addCompleted.get());
			removeCalled.countDown();
			return null;
		}).when(configService).removeListener(org.mockito.ArgumentMatchers.eq("data"),
				org.mockito.ArgumentMatchers.eq("group"), org.mockito.ArgumentMatchers.any());
		NacosMcpGatewayToolCallback callback = new NacosMcpGatewayToolCallback(toolDefinition(), operationService,
				new McpServersVO.McpServerVO());
		Method getConfigContent = NacosMcpGatewayToolCallback.class.getDeclaredMethod("getConfigContent", String.class,
				String.class);
		getConfigContent.setAccessible(true);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> registration = executor.submit(() -> {
				try {
					getConfigContent.invoke(callback, "data", "group");
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertThat(addEntered.await(1, TimeUnit.SECONDS)).isTrue();
			Future<?> close = executor.submit(callback::close);

			assertThat(removeCalled.await(150, TimeUnit.MILLISECONDS)).isFalse();
			allowAddToComplete.countDown();
			registration.get(2, TimeUnit.SECONDS);
			close.get(2, TimeUnit.SECONDS);
			assertThat(removeCalled.getCount()).isZero();
			assertThat(removedBeforeAddCompleted).isFalse();
		}
		finally {
			allowAddToComplete.countDown();
			executor.shutdownNow();
		}
	}

	private static NacosMcpGatewayToolDefinition toolDefinition() {
		return toolDefinition(null);
	}

	private static NacosMcpGatewayToolDefinition toolDefinition(String protocol) {
		return NacosMcpGatewayToolDefinition.builder()
				.name("order-service_tools_query")
				.description("Query orders")
				.inputSchema(Map.of("type", "object", "properties", Map.of()))
				.protocol(protocol)
				.build();
	}

}
