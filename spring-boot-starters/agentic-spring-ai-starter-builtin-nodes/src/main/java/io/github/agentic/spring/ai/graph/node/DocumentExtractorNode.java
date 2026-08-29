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

import io.github.agentic.spring.ai.document.JsonDocumentParser;
import io.github.agentic.spring.ai.document.TextDocumentParser;
import io.github.agentic.spring.ai.graph.OverAllState;
import io.github.agentic.spring.ai.graph.action.NodeAction;
import io.github.agentic.spring.ai.parser.bshtml.BsHtmlDocumentParser;
import io.github.agentic.spring.ai.parser.markdown.MarkdownDocumentParser;
import io.github.agentic.spring.ai.parser.tika.TikaDocumentParser;
import io.github.agentic.spring.ai.parser.yaml.YamlDocumentParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.ai.document.Document;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;

/**
 * @author HeYQ
 * @since 2025-05-02 17:03
 */
public class DocumentExtractorNode implements NodeAction {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final ScheduledThreadPoolExecutor DEADLINE_SCHEDULER = createDeadlineScheduler();

	private static ScheduledThreadPoolExecutor createDeadlineScheduler() {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
			Thread thread = new Thread(runnable, "document-extractor-deadline");
			thread.setDaemon(true);
			return thread;
		});
		executor.setRemoveOnCancelPolicy(true);
		return executor;
	}

	private final String paramsKey;

	private final String outputKey;

	private final List<String> fileList;

	private final boolean inputIsArray;

	private final Path localRoot;

	private final boolean allowAnyLocalPath;

	private final boolean allowRemoteAccess;

	private final boolean allowPrivateNetworkAccess;

	private final int connectTimeoutMillis;

	private final int readTimeoutMillis;

	private final long maxBytes;

	private final Duration totalTimeout;

	private final Map<String, Function<InputStream, List<Document>>> extractors = new HashMap<>();

	public DocumentExtractorNode(String paramsKey, String outputKey, List<String> fileList, boolean inputIsArray) {
		this(paramsKey, outputKey, fileList, inputIsArray, Paths.get(""), false, true, false,
				NetworkAccessPolicy.DEFAULT_CONNECT_TIMEOUT_MILLIS, NetworkAccessPolicy.DEFAULT_READ_TIMEOUT_MILLIS,
				NetworkAccessPolicy.DEFAULT_MAX_BYTES, NetworkAccessPolicy.DEFAULT_TOTAL_TIMEOUT);
	}

	private DocumentExtractorNode(String paramsKey, String outputKey, List<String> fileList, boolean inputIsArray,
			Path localRoot, boolean allowAnyLocalPath, boolean allowRemoteAccess, boolean allowPrivateNetworkAccess,
			int connectTimeoutMillis, int readTimeoutMillis, long maxBytes, Duration totalTimeout) {
		this.paramsKey = paramsKey;
		this.outputKey = outputKey;
		this.fileList = fileList;
		this.inputIsArray = inputIsArray;
		this.localRoot = localRoot;
		this.allowAnyLocalPath = allowAnyLocalPath;
		this.allowRemoteAccess = allowRemoteAccess;
		this.allowPrivateNetworkAccess = allowPrivateNetworkAccess;
		this.connectTimeoutMillis = connectTimeoutMillis;
		this.readTimeoutMillis = readTimeoutMillis;
		this.maxBytes = maxBytes;
		this.totalTimeout = totalTimeout;
		extractors.put("txt", inputStream -> new TextDocumentParser().parse(inputStream));
		extractors.put("markdown", inputStream -> new MarkdownDocumentParser().parse(inputStream));
		extractors.put("md", inputStream -> new MarkdownDocumentParser().parse(inputStream));
		extractors.put("html", inputStream -> new BsHtmlDocumentParser().parse(inputStream));
		extractors.put("htm", inputStream -> new BsHtmlDocumentParser().parse(inputStream));
		extractors.put("xml", inputStream -> new BsHtmlDocumentParser().parse(inputStream));
		extractors.put("json", inputStream -> new JsonDocumentParser().parse(inputStream));
		extractors.put("yaml", inputStream -> new YamlDocumentParser().parse(inputStream));
		extractors.put("yml", inputStream -> new YamlDocumentParser().parse(inputStream));
		extractors.put("pdf", inputStream -> new TikaDocumentParser().parse(inputStream));
		extractors.put("doc", inputStream -> new TikaDocumentParser().parse(inputStream));
		extractors.put("docx", inputStream -> new TikaDocumentParser().parse(inputStream));
		extractors.put("csv", inputStream -> new TikaDocumentParser().parse(inputStream));
		extractors.put("xls", inputStream -> new TikaDocumentParser().parse(inputStream));
		extractors.put("xlsx", inputStream -> new TikaDocumentParser().parse(inputStream));
		extractors.put("ppt", inputStream -> new TikaDocumentParser().parse(inputStream));
		extractors.put("pptx", inputStream -> new TikaDocumentParser().parse(inputStream));
	}

	/**
	 * Supports obtaining input stream from local or network sources
	 */
	private InputStream getInputStream(String filePath) throws IOException {
		URI uri;
		if (filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("ftp://")) {
			uri = URI.create(filePath);
		}
		else {
			Path resolvedPath = NetworkAccessPolicy.resolveLocalPath(filePath, this.localRoot, this.allowAnyLocalPath);
			NetworkAccessPolicy.validateSize(Files.size(resolvedPath), this.maxBytes, filePath);
			return NetworkAccessPolicy.limit(new BufferedInputStream(Files.newInputStream(resolvedPath)), this.maxBytes);
		}

		if (uri.getScheme().equals("file")) {
			Path resolvedPath = NetworkAccessPolicy.resolveLocalPath(Paths.get(uri).toString(), this.localRoot,
					this.allowAnyLocalPath);
			NetworkAccessPolicy.validateSize(Files.size(resolvedPath), this.maxBytes, filePath);
			return NetworkAccessPolicy.limit(new BufferedInputStream(Files.newInputStream(resolvedPath)), this.maxBytes);
		}
		if (!this.allowRemoteAccess) {
			throw new IOException("Remote document access is disabled: " + filePath);
		}
		return openRemoteInputStream(uri);
	}

	private InputStream openRemoteInputStream(URI uri) throws IOException {
		long deadlineNanos = NetworkAccessPolicy.deadlineAfter(this.totalTimeout);
		PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
			.setDnsResolver(NetworkAccessPolicy.dnsResolver(this.allowPrivateNetworkAccess))
			.setDefaultConnectionConfig(ConnectionConfig.custom()
				.setConnectTimeout(Timeout.ofMilliseconds(this.connectTimeoutMillis))
				.setSocketTimeout(Timeout.ofMilliseconds(this.readTimeoutMillis))
				.build())
			.build();
		try (CloseableHttpClient httpClient = HttpClients.custom()
			.setConnectionManager(connectionManager)
			.disableRedirectHandling()
			.build()) {
			URI currentUri = uri;
			for (int redirects = 0; redirects <= 5; redirects++) {
				NetworkAccessPolicy.checkDeadline(deadlineNanos);
				NetworkAccessPolicy.validateUri(currentUri, Set.of("http", "https"), this.allowPrivateNetworkAccess);
				HttpGet request = new HttpGet(currentUri);
				long remainingNanos = deadlineNanos - System.nanoTime();
				NetworkAccessPolicy.checkDeadline(deadlineNanos);
				request.setConfig(RequestConfig.custom()
					.setRedirectsEnabled(false)
					.setResponseTimeout(this.readTimeoutMillis, TimeUnit.MILLISECONDS)
					.build());
				ScheduledFuture<?> deadlineTask = DEADLINE_SCHEDULER.schedule(request::cancel, remainingNanos,
						TimeUnit.NANOSECONDS);
				try (CloseableHttpResponse response = httpClient.execute(request)) {
					int status = response.getCode();
					if (status >= 300 && status < 400) {
						Header locationHeader = response.getFirstHeader("Location");
						if (locationHeader == null || locationHeader.getValue().isBlank()) {
							throw new IOException("Redirect response missing Location header: " + currentUri);
						}
						currentUri = currentUri.resolve(locationHeader.getValue());
						continue;
					}
					if (status >= 400) {
						throw new IOException("Failed to fetch remote document: HTTP " + status);
					}
					HttpEntity entity = response.getEntity();
					if (entity == null) {
						return new ByteArrayInputStream(new byte[0]);
					}
					long contentLength = entity.getContentLength();
					if (contentLength >= 0) {
						NetworkAccessPolicy.validateSize(contentLength, this.maxBytes, currentUri.toString());
					}
					try (InputStream input = NetworkAccessPolicy.limit(
							NetworkAccessPolicy.deadline(new BufferedInputStream(entity.getContent()), deadlineNanos),
							this.maxBytes)) {
						return new ByteArrayInputStream(input.readAllBytes());
					}
				}
				catch (IOException e) {
					if (System.nanoTime() >= deadlineNanos) {
						java.net.SocketTimeoutException timeout = new java.net.SocketTimeoutException(
								"Remote document fetch exceeded total timeout");
						timeout.initCause(e);
						throw timeout;
					}
					throw e;
				}
				finally {
					deadlineTask.cancel(false);
				}
			}
		}
		throw new IOException("Too many redirects while fetching remote document: " + uri);
	}

	private List<String> getDocument(List<String> fileList) {
		return fileList.stream().map(String::trim).map(file -> {
			try (InputStream inputStream = this.getInputStream(file.trim())) {
				return this.extractTextByFileExtension(inputStream, getFileExtension(file));
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to parse test file: " + file, e);
			}
		}).toList();
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		if (paramsKey == null && fileList == null) {
			throw new RuntimeException("File variable not found for selector");
		}
		List<String> fileList;
		Object fileObj = paramsKey != null ? state.value(paramsKey).orElse(this.fileList) : this.fileList;
		if (fileObj == null) {
			throw new RuntimeException("File variable not found for selector");
		}
		if (paramsKey == null && this.fileList != null) {
			fileList = this.fileList;
		}
		else if (this.inputIsArray) {
			if (fileObj instanceof List<?>) {
				fileList = (List<String>) fileObj;
			}
			else if (fileObj instanceof String[]) {
				fileList = Arrays.asList((String[]) fileObj);
			}
			else {
				// Try to parse as Json string, if failed the input is invalid
				try {
					fileList = OBJECT_MAPPER.readValue(fileObj.toString(), new TypeReference<List<String>>() {
					});
				}
				catch (Exception ignore) {
					fileList = null;
				}
			}
			if (fileList == null || fileList.isEmpty()) {
				throw new RuntimeException("Variable fileList is not an ArrayFileSegment");
			}
		}
		else {
			// Single file, add directly to the list
			fileList = List.of(fileObj.toString());
		}
		List<String> documentContents = this.getDocument(fileList);

		String key = Optional.ofNullable(this.outputKey).orElse("text");
		if (!this.inputIsArray) {
			return Map.of(key, documentContents.get(0));
		}
		else {
			return Map.of(key, documentContents);
		}
	}

	private String extractTextByFileExtension(InputStream fileContent, String fileExtension) {

		Function<InputStream, List<Document>> extractor = this.extractors.get(fileExtension);
		if (extractor == null) {
			throw new RuntimeException("Unsupported Extension Type: " + fileExtension);
		}

		return extractor.apply(fileContent).get(0).getText();
	}

	private String getFileExtension(String filePath) {
		String pathValue = filePath;
		if (filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("ftp://")) {
			pathValue = URI.create(filePath).getPath();
		}
		Path path = Paths.get(pathValue);
		String fileName = path.getFileName().toString();
		int dotIndex = fileName.lastIndexOf('.');

		return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String paramsKey;

		private String outputKey;

		private List<String> fileList;

		private boolean inputIsArray = false;

		private Path localRoot = Paths.get("");

		private boolean allowAnyLocalPath = false;

		private boolean allowRemoteAccess = true;

		private boolean allowPrivateNetworkAccess = false;

		private int connectTimeoutMillis = NetworkAccessPolicy.DEFAULT_CONNECT_TIMEOUT_MILLIS;

		private int readTimeoutMillis = NetworkAccessPolicy.DEFAULT_READ_TIMEOUT_MILLIS;

		private long maxBytes = NetworkAccessPolicy.DEFAULT_MAX_BYTES;

		private Duration totalTimeout = NetworkAccessPolicy.DEFAULT_TOTAL_TIMEOUT;

		public Builder paramsKey(String paramsKey) {
			this.paramsKey = paramsKey;
			return this;
		}

		public Builder outputKey(String outputKey) {
			this.outputKey = outputKey;
			return this;
		}

		public Builder fileList(List<String> fileList) {
			this.fileList = fileList;
			return this;
		}

		public Builder inputIsArray(boolean inputIsArray) {
			this.inputIsArray = inputIsArray;
			return this;
		}

		public Builder localRoot(Path localRoot) {
			this.localRoot = localRoot;
			return this;
		}

		public Builder allowAnyLocalPath(boolean allowAnyLocalPath) {
			this.allowAnyLocalPath = allowAnyLocalPath;
			return this;
		}

		public Builder allowRemoteAccess(boolean allowRemoteAccess) {
			this.allowRemoteAccess = allowRemoteAccess;
			return this;
		}

		public Builder allowPrivateNetworkAccess(boolean allowPrivateNetworkAccess) {
			this.allowPrivateNetworkAccess = allowPrivateNetworkAccess;
			return this;
		}

		public Builder connectTimeoutMillis(int connectTimeoutMillis) {
			this.connectTimeoutMillis = connectTimeoutMillis;
			return this;
		}

		public Builder readTimeoutMillis(int readTimeoutMillis) {
			this.readTimeoutMillis = readTimeoutMillis;
			return this;
		}

		public Builder maxBytes(long maxBytes) {
			this.maxBytes = maxBytes;
			return this;
		}

		public Builder totalTimeout(Duration totalTimeout) {
			if (totalTimeout == null || totalTimeout.isZero() || totalTimeout.isNegative()) {
				throw new IllegalArgumentException("totalTimeout must be greater than zero");
			}
			this.totalTimeout = totalTimeout;
			return this;
		}

		public DocumentExtractorNode build() {
			return new DocumentExtractorNode(paramsKey, outputKey, fileList, inputIsArray, localRoot, allowAnyLocalPath,
					allowRemoteAccess, allowPrivateNetworkAccess, connectTimeoutMillis, readTimeoutMillis, maxBytes,
					totalTimeout);
		}

	}

}
