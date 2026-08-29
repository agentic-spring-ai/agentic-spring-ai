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
package io.github.agentic.spring.ai.graph.node;

import io.github.agentic.spring.ai.graph.OverAllState;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentExtractorNodeTest {

	private MockWebServer mockWebServer;

	@BeforeEach
	void setUp() throws IOException {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.mockWebServer.shutdown();
	}

	@Test
	void readsLocalFileInsideConfiguredRoot() throws Exception {
		Path localRoot = Files.createTempDirectory("document-extractor-root");
		Path document = Files.writeString(localRoot.resolve("sample.txt"), "hello from root");
		DocumentExtractorNode node = DocumentExtractorNode.builder()
			.fileList(List.of(document.toString()))
			.localRoot(localRoot)
			.build();

		Map<String, Object> result = node.apply(new OverAllState());

		assertEquals("hello from root", result.get("text"));
	}

	@Test
	void blocksLocalFileOutsideConfiguredRoot() throws Exception {
		Path localRoot = Files.createTempDirectory("document-extractor-root");
		Path outside = Files.writeString(Files.createTempDirectory("document-extractor-outside").resolve("secret.txt"),
				"secret");
		DocumentExtractorNode node = DocumentExtractorNode.builder()
			.fileList(List.of(outside.toString()))
			.localRoot(localRoot)
			.build();

		RuntimeException exception = assertThrows(RuntimeException.class, () -> node.apply(new OverAllState()));

		assertTrue(exception.getMessage().contains("Failed to parse"));
	}

	@Test
	void blocksFtpRemoteDocuments() {
		DocumentExtractorNode node = DocumentExtractorNode.builder().fileList(List.of("ftp://example.com/file.txt")).build();

		RuntimeException exception = assertThrows(RuntimeException.class, () -> node.apply(new OverAllState()));

		assertTrue(exception.getCause().getMessage().contains("Unsupported URI scheme"));
	}

	@Test
	void blocksPrivateRemoteDocumentsByDefault() {
		DocumentExtractorNode node = DocumentExtractorNode.builder()
			.fileList(List.of(this.mockWebServer.url("/doc.txt").toString()))
			.build();

		RuntimeException exception = assertThrows(RuntimeException.class, () -> node.apply(new OverAllState()));

		assertTrue(exception.getCause().getMessage().contains("Private network access"));
		assertEquals(0, this.mockWebServer.getRequestCount());
	}

	@Test
	void allowsPrivateRemoteDocumentsWhenExplicitlyEnabled() throws Exception {
		this.mockWebServer
			.enqueue(new MockResponse().setBody("remote text").setHeader(HttpHeaders.CONTENT_TYPE, "text/plain"));
		DocumentExtractorNode node = DocumentExtractorNode.builder()
			.fileList(List.of(this.mockWebServer.url("/doc.txt").toString()))
			.allowPrivateNetworkAccess(true)
			.build();

		Map<String, Object> result = node.apply(new OverAllState());

		assertEquals("remote text", result.get("text"));
		assertEquals(1, this.mockWebServer.getRequestCount());
	}

	@Test
	void extractsRemoteDocumentExtensionFromUrlPathWhenQueryIsPresent() throws Exception {
		this.mockWebServer
			.enqueue(new MockResponse().setBody("remote text").setHeader(HttpHeaders.CONTENT_TYPE, "text/plain"));
		DocumentExtractorNode node = DocumentExtractorNode.builder()
			.fileList(List.of(this.mockWebServer.url("/doc.txt?signature=abc").toString()))
			.allowPrivateNetworkAccess(true)
			.build();

		Map<String, Object> result = node.apply(new OverAllState());

		assertEquals("remote text", result.get("text"));
	}

	@Test
	void rejectsDocumentsLargerThanMaxBytes() throws Exception {
		Path localRoot = Files.createTempDirectory("document-extractor-root");
		Path document = Files.writeString(localRoot.resolve("large.txt"), "0123456789");
		DocumentExtractorNode node = DocumentExtractorNode.builder()
			.fileList(List.of(document.toString()))
			.localRoot(localRoot)
			.maxBytes(4)
			.build();

		RuntimeException exception = assertThrows(RuntimeException.class, () -> node.apply(new OverAllState()));

		assertTrue(exception.getCause().getMessage().contains("maximum size"));
	}

	@Test
	void abortsSlowRemoteDocumentAtTotalTimeout() {
		this.mockWebServer.enqueue(new MockResponse()
			.setBody("slow document")
			.throttleBody(1, 100, java.util.concurrent.TimeUnit.MILLISECONDS));
		DocumentExtractorNode node = DocumentExtractorNode.builder()
			.fileList(List.of(this.mockWebServer.url("/slow.txt").toString()))
			.allowPrivateNetworkAccess(true)
			.readTimeoutMillis(1000)
			.totalTimeout(Duration.ofMillis(150))
			.build();

		long startedAt = System.nanoTime();
		RuntimeException exception = assertThrows(RuntimeException.class, () -> node.apply(new OverAllState()));
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

		assertTrue(exception.getCause().getMessage().contains("total timeout"));
		assertTrue(elapsedMillis < 750, "total timeout should cancel the active network read");
	}

	@Test
	void deadlineSchedulerRemovesCancelledTasksImmediately() throws Exception {
		this.mockWebServer.enqueue(new MockResponse().setBody("done"));
		DocumentExtractorNode node = DocumentExtractorNode.builder()
			.fileList(List.of(this.mockWebServer.url("/quick.txt").toString()))
			.allowPrivateNetworkAccess(true)
			.build();

		node.apply(new OverAllState());

		Field field = DocumentExtractorNode.class.getDeclaredField("DEADLINE_SCHEDULER");
		field.setAccessible(true);
		Object scheduler = field.get(null);
		assertTrue(scheduler instanceof ScheduledThreadPoolExecutor,
				"deadline scheduler must expose remove-on-cancel queue semantics");
		ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) scheduler;
		assertTrue(executor.getRemoveOnCancelPolicy());
		assertTrue(executor.getQueue().isEmpty());
	}

}
