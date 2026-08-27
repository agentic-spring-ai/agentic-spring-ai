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
package io.github.agentic.spring.ai.graph.agent.extension.interceptor;

import io.github.agentic.spring.ai.graph.agent.extension.file.LocalFilesystemBackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesystemInterceptorTest {

	@TempDir
	Path root;

	@Test
	void defaultBackendDoesNotExposeHostAbsolutePaths() {
		FilesystemInterceptor interceptor = FilesystemInterceptor.builder().readOnly(true).build();

		String result = tool(interceptor, "read_file").call(
				"{\"file_path\":\"/etc/passwd\",\"offset\":0,\"limit\":10}",
				new ToolContext(Collections.emptyMap()));

		assertFalse(result.contains("root:"));
		assertTrue(result.contains("not found"));
	}

	@Test
	void backendKeepsReadToolInsideVirtualRoot() throws Exception {
		Files.writeString(this.root.resolve("allowed.txt"), "inside\n");

		FilesystemInterceptor interceptor = FilesystemInterceptor.builder()
			.backend(new LocalFilesystemBackend(this.root.toString(), true, 10))
			.readOnly(true)
			.build();

		String inside = tool(interceptor, "read_file").call(
				"{\"file_path\":\"/allowed.txt\",\"offset\":0,\"limit\":10}",
				new ToolContext(Collections.emptyMap()));
		String traversal = tool(interceptor, "read_file").call(
				"{\"file_path\":\"../allowed.txt\",\"offset\":0,\"limit\":10}",
				new ToolContext(Collections.emptyMap()));

		assertTrue(inside.contains("inside"));
		assertTrue(traversal.contains("Path traversal not allowed"));
	}

	@Test
	void backendKeepsWriteToolInsideVirtualRoot() throws Exception {
		Path escaped = this.root.getParent().resolve("escape.txt");
		Files.deleteIfExists(escaped);

		FilesystemInterceptor interceptor = FilesystemInterceptor.builder()
			.backend(new LocalFilesystemBackend(this.root.toString(), true, 10))
			.build();

		String created = tool(interceptor, "write_file").call(
				"{\"file_path\":\"/sub/new.txt\",\"content\":\"inside\"}",
				new ToolContext(Collections.emptyMap()));
		String traversal = tool(interceptor, "write_file").call(
				"{\"file_path\":\"../escape.txt\",\"content\":\"outside\"}",
				new ToolContext(Collections.emptyMap()));

		assertTrue(created.contains("Successfully created file"));
		assertTrue(Files.readString(this.root.resolve("sub/new.txt")).contains("inside"));
		assertTrue(traversal.contains("Path traversal not allowed"));
		assertFalse(Files.exists(escaped));
	}

	@Test
	void backendKeepsListGlobAndGrepToolsInsideVirtualRoot() throws Exception {
		Files.createDirectories(this.root.resolve("dir"));
		Files.writeString(this.root.resolve("dir/inside.txt"), "needle\n");

		FilesystemInterceptor interceptor = FilesystemInterceptor.builder()
			.backend(new LocalFilesystemBackend(this.root.toString(), true, 10))
			.readOnly(true)
			.build();

		String listed = tool(interceptor, "ls").call("\"/\"", new ToolContext(Collections.emptyMap()));
		String globbed = tool(interceptor, "glob").call("\"**/*.txt\"", new ToolContext(Collections.emptyMap()));
		String grep = tool(interceptor, "grep").call(
				"{\"pattern\":\"needle\",\"path\":\"/\",\"glob\":\"*.txt\",\"output_mode\":\"content\"}",
				new ToolContext(Collections.emptyMap()));

		assertTrue(listed.contains("/dir/"));
		assertTrue(globbed.contains("/dir/inside.txt"));
		assertTrue(grep.contains("/dir/inside.txt:1: needle"));
	}

	@Test
	void backendKeepsEditToolInsideVirtualRoot() throws Exception {
		Files.writeString(this.root.resolve("inside.txt"), "before\n");

		FilesystemInterceptor interceptor = FilesystemInterceptor.builder()
			.backend(new LocalFilesystemBackend(this.root.toString(), true, 10))
			.build();

		String edited = tool(interceptor, "edit_file").call(
				"{\"file_path\":\"/inside.txt\",\"old_string\":\"before\",\"new_string\":\"after\",\"replace_all\":false}",
				new ToolContext(Collections.emptyMap()));
		String traversal = tool(interceptor, "edit_file").call(
				"{\"file_path\":\"../inside.txt\",\"old_string\":\"before\",\"new_string\":\"after\",\"replace_all\":false}",
				new ToolContext(Collections.emptyMap()));

		assertTrue(edited.contains("Successfully edited file"));
		assertTrue(Files.readString(this.root.resolve("inside.txt")).contains("after"));
		assertTrue(traversal.contains("Path traversal not allowed"));
	}

	private static ToolCallback tool(FilesystemInterceptor interceptor, String name) {
		return interceptor.getTools()
			.stream()
			.filter(tool -> name.equals(tool.getToolDefinition().name()))
			.findFirst()
			.orElseThrow();
	}

}
