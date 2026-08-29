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
package io.github.agentic.spring.ai.graph.node.code;

import io.github.agentic.spring.ai.graph.node.code.entity.CodeBlock;
import io.github.agentic.spring.ai.graph.node.code.entity.CodeExecutionConfig;
import io.github.agentic.spring.ai.graph.node.code.entity.CodeExecutionResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.transport.DockerHttpClient;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class DockerCodeExecutorTest {

	private static boolean isCI() {
		return "true".equalsIgnoreCase(System.getProperty("CI", System.getenv("CI")));
	}

	@Test
	void executeCodeBlocksAppliesDefaultContainerLimits(@TempDir Path workDir) throws Exception {
		CodeExecutionConfig config = new CodeExecutionConfig().setDocker("python:3.10")
			.setWorkDir(workDir.toAbsolutePath().toString())
			.setContainerName("docker-code-exec-limits-test")
			.setTimeout(1)
			.setContainerUser("65534:65534");
		CodeBlock codeBlock = new CodeBlock("python3", "print('limited')");

		DockerClientBuilder dockerClientBuilder = mock(DockerClientBuilder.class);
		DockerClient dockerClient = mock(DockerClient.class);
		CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
		StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
		WaitContainerCmd waitContainerCmd = mock(WaitContainerCmd.class);
		WaitContainerResultCallback waitCallback = mock(WaitContainerResultCallback.class);
		RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
		LogContainerCmd logContainerCmd = mock(LogContainerCmd.class);
		InspectContainerCmd inspectContainerCmd = mock(InspectContainerCmd.class);
		InspectContainerResponse inspectContainerResponse = mock(InspectContainerResponse.class);
		InspectContainerResponse.ContainerState containerState = mock(InspectContainerResponse.ContainerState.class);
		CreateContainerResponse container = new CreateContainerResponse();
		container.setId("limited-container");

		when(dockerClientBuilder.withDockerHttpClient(any(DockerHttpClient.class))).thenReturn(dockerClientBuilder);
		when(dockerClientBuilder.build()).thenReturn(dockerClient);
		when(dockerClient.createContainerCmd("python:3.10")).thenReturn(createContainerCmd);
		when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withWorkingDir("/workspace")).thenReturn(createContainerCmd);
		when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
		when(createContainerCmd.withUser("65534:65534")).thenReturn(createContainerCmd);
		when(createContainerCmd.withCmd(anyString(), anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.exec()).thenReturn(container);
		when(dockerClient.startContainerCmd("limited-container")).thenReturn(startContainerCmd);
		when(dockerClient.waitContainerCmd("limited-container")).thenReturn(waitContainerCmd);
		when(waitContainerCmd.start()).thenReturn(waitCallback);
		when(waitCallback.awaitCompletion(1, TimeUnit.SECONDS)).thenReturn(true);
		when(dockerClient.logContainerCmd("limited-container")).thenReturn(logContainerCmd);
		when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
		when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
		when(logContainerCmd.exec(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(dockerClient.inspectContainerCmd("limited-container")).thenReturn(inspectContainerCmd);
		when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
		when(inspectContainerResponse.getState()).thenReturn(containerState);
		when(containerState.getExitCodeLong()).thenReturn(0L);
		when(dockerClient.removeContainerCmd("limited-container")).thenReturn(removeContainerCmd);
		when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);

		try (MockedStatic<DockerClientBuilder> dockerClientBuilderStatic = mockStatic(DockerClientBuilder.class)) {
			dockerClientBuilderStatic.when(DockerClientBuilder::getInstance).thenReturn(dockerClientBuilder);

			CodeExecutionResult result = new DockerCodeExecutor().executeCodeBlocks(List.of(codeBlock), config);

			assertEquals(0, result.exitCode());
		}

		ArgumentCaptor<HostConfig> hostConfigCaptor = ArgumentCaptor.forClass(HostConfig.class);
		verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
		HostConfig hostConfig = hostConfigCaptor.getValue();
		assertEquals("none", hostConfig.getNetworkMode());
		assertEquals(Boolean.TRUE, hostConfig.getReadonlyRootfs());
		assertEquals("rw,nosuid,size=64m,mode=1777", hostConfig.getTmpFs().get("/tmp"));
		assertEquals(256L * 1024L * 1024L, hostConfig.getMemory());
		assertEquals(256L * 1024L * 1024L, hostConfig.getMemorySwap());
		assertEquals(100_000L, hostConfig.getCpuPeriod());
		assertEquals(100_000L, hostConfig.getCpuQuota());
		assertEquals(128L, hostConfig.getPidsLimit());
		assertTrue(Arrays.asList(hostConfig.getCapDrop()).contains(Capability.ALL));
		assertTrue(hostConfig.getSecurityOpts().contains("no-new-privileges"));
		verify(createContainerCmd).withUser("65534:65534");
	}

	@Test
	void executeCodeBlocksFailsFastWhenContainerDoesNotFinishBeforeTimeout() throws Exception {
		Path workDir = Files.createTempDirectory("docker-code-exec-timeout-test");
		CodeExecutionConfig config = new CodeExecutionConfig().setDocker("python:3.10")
			.setWorkDir(workDir.toAbsolutePath().toString())
			.setContainerName("docker-code-exec-timeout-test")
			.setTimeout(1);
		CodeBlock codeBlock = new CodeBlock("python3", "print('still running')");

		DockerClientBuilder dockerClientBuilder = mock(DockerClientBuilder.class);
		DockerClient dockerClient = mock(DockerClient.class);
		CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
		StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
		WaitContainerCmd waitContainerCmd = mock(WaitContainerCmd.class);
		WaitContainerResultCallback waitCallback = mock(WaitContainerResultCallback.class);
		RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
		CreateContainerResponse container = new CreateContainerResponse();
		container.setId("timed-out-container");

		when(dockerClientBuilder.withDockerHttpClient(any(DockerHttpClient.class))).thenReturn(dockerClientBuilder);
		when(dockerClientBuilder.build()).thenReturn(dockerClient);
		when(dockerClient.createContainerCmd("python:3.10")).thenReturn(createContainerCmd);
		when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withWorkingDir("/workspace")).thenReturn(createContainerCmd);
		when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
		when(createContainerCmd.withCmd(anyString(), anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.exec()).thenReturn(container);
		when(dockerClient.startContainerCmd("timed-out-container")).thenReturn(startContainerCmd);
		when(dockerClient.waitContainerCmd("timed-out-container")).thenReturn(waitContainerCmd);
		when(waitContainerCmd.start()).thenReturn(waitCallback);
		when(waitCallback.awaitCompletion(1, TimeUnit.SECONDS)).thenReturn(false);
		when(dockerClient.removeContainerCmd("timed-out-container")).thenReturn(removeContainerCmd);
		when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);

		try (MockedStatic<DockerClientBuilder> dockerClientBuilderStatic = mockStatic(DockerClientBuilder.class)) {
			dockerClientBuilderStatic.when(DockerClientBuilder::getInstance).thenReturn(dockerClientBuilder);

			RuntimeException exception = assertThrows(RuntimeException.class,
					() -> new DockerCodeExecutor().executeCodeBlocks(List.of(codeBlock), config));

			assertTrue(exception.getMessage().contains("timed out"));
		}

		verify(dockerClient, never()).logContainerCmd("timed-out-container");
		verify(dockerClient, never()).inspectContainerCmd("timed-out-container");
		verify(removeContainerCmd).exec();
	}

	@Test
	void executeCodeBlocksTruncatesContainerLogs(@TempDir Path workDir) throws Exception {
		CodeExecutionConfig config = new CodeExecutionConfig().setDocker("python:3.10")
			.setWorkDir(workDir.toString())
			.setContainerName("docker-output-limit-test")
			.setMaxOutputBytes(16);
		CodeBlock codeBlock = new CodeBlock("python3", "print('x')");
		DockerClientBuilder builder = mock(DockerClientBuilder.class);
		DockerClient client = mock(DockerClient.class);
		CreateContainerCmd create = mock(CreateContainerCmd.class);
		StartContainerCmd start = mock(StartContainerCmd.class);
		WaitContainerCmd wait = mock(WaitContainerCmd.class);
		WaitContainerResultCallback waitCallback = mock(WaitContainerResultCallback.class);
		LogContainerCmd logs = mock(LogContainerCmd.class);
		InspectContainerCmd inspect = mock(InspectContainerCmd.class);
		InspectContainerResponse inspected = mock(InspectContainerResponse.class);
		InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);
		RemoveContainerCmd remove = mock(RemoveContainerCmd.class);
		CreateContainerResponse container = new CreateContainerResponse();
		container.setId("limited-output-container");

		when(builder.withDockerHttpClient(any())).thenReturn(builder);
		when(builder.build()).thenReturn(client);
		when(client.createContainerCmd("python:3.10")).thenReturn(create);
		when(create.withName(anyString())).thenReturn(create);
		when(create.withWorkingDir("/workspace")).thenReturn(create);
		when(create.withHostConfig(any())).thenReturn(create);
		when(create.withCmd(anyString(), anyString())).thenReturn(create);
		when(create.exec()).thenReturn(container);
		when(client.startContainerCmd(container.getId())).thenReturn(start);
		when(client.waitContainerCmd(container.getId())).thenReturn(wait);
		when(wait.start()).thenReturn(waitCallback);
		when(waitCallback.awaitCompletion(any(Long.class), any(TimeUnit.class))).thenReturn(true);
		when(client.logContainerCmd(container.getId())).thenReturn(logs);
		when(logs.withStdOut(true)).thenReturn(logs);
		when(logs.withStdErr(true)).thenReturn(logs);
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			com.github.dockerjava.api.async.ResultCallback<Frame> callback = invocation.getArgument(0);
			callback.onNext(new Frame(com.github.dockerjava.api.model.StreamType.STDOUT, "x".repeat(1024).getBytes()));
			callback.onComplete();
			return callback;
		}).when(logs).exec(any());
		when(client.inspectContainerCmd(container.getId())).thenReturn(inspect);
		when(inspect.exec()).thenReturn(inspected);
		when(inspected.getState()).thenReturn(state);
		when(state.getExitCodeLong()).thenReturn(0L);
		when(client.removeContainerCmd(container.getId())).thenReturn(remove);
		when(remove.withForce(true)).thenReturn(remove);

		try (MockedStatic<DockerClientBuilder> docker = mockStatic(DockerClientBuilder.class)) {
			docker.when(DockerClientBuilder::getInstance).thenReturn(builder);
			CodeExecutionResult result = new DockerCodeExecutor().executeCodeBlocks(List.of(codeBlock), config);
			assertTrue(result.logs().contains("output truncated"));
			assertTrue(result.logs().length() < 100);
		}
	}

	@EnabledIf(value = "isCI", disabledReason = "this test is designed to run only in the GitHub CI environment.")
	@Test
	void testPython3Sum() throws Exception {
		// 1. 构造 DockerCodeExecutor
		DockerCodeExecutor executor = new DockerCodeExecutor();

		// 2. 构造代码块（Python3 求和）
		String code = """
				def main(inputs):
				    return {\"result\": sum(inputs)}
				""";
		CodeBlock codeBlock = new CodeBlock("python3", code);

		// 3. 构造执行配置
		Path workDir = Files.createTempDirectory("docker-code-exec-test");
		CodeExecutionConfig config = new CodeExecutionConfig().setDocker("python:3.10")
			.setWorkDir(workDir.toAbsolutePath().toString())
			.setContainerName("docker-code-exec-test")
			.setTimeout(60);

		// 4. 执行
		CodeExecutionResult result = executor.executeCodeBlocks(List.of(codeBlock), config);

		// 5. 断言
		assertEquals(0, result.exitCode(), "Exit code should be 0");
	}

}
