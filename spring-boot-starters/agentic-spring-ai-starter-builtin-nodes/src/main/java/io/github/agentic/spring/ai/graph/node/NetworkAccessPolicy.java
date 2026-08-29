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

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.time.Duration;

final class NetworkAccessPolicy {

	static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;

	static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;

	static final int DEFAULT_READ_TIMEOUT_MILLIS = 30_000;

	static final Duration DEFAULT_TOTAL_TIMEOUT = Duration.ofSeconds(60);

	private static final Set<String> METADATA_HOSTS = Set.of("metadata.google.internal", "metadata.azure.com",
			"169.254.169.254");

	private NetworkAccessPolicy() {
	}

	static void validateHttpUri(URI uri, boolean allowPrivateNetworkAccess) throws IOException {
		validateUri(uri, Set.of("http", "https"), allowPrivateNetworkAccess);
	}

	static void validateUri(URI uri, Set<String> allowedSchemes, boolean allowPrivateNetworkAccess) throws IOException {
		String scheme = uri.getScheme();
		if (scheme == null || !allowedSchemes.contains(scheme.toLowerCase(Locale.ROOT))) {
			throw new IOException("Unsupported URI scheme: " + scheme);
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new IOException("URI host is required");
		}
		if (!allowPrivateNetworkAccess) {
			validateHost(host);
		}
	}

	static Path resolveLocalPath(String filePath, Path localRoot, boolean allowAnyLocalPath) throws IOException {
		Path rawPath = Path.of(filePath);
		Path root = localRoot.toAbsolutePath().normalize().toRealPath();
		Path candidate = rawPath.isAbsolute() ? rawPath : root.resolve(rawPath).normalize();
		Path realPath = candidate.toRealPath();
		if (!allowAnyLocalPath && !realPath.startsWith(root)) {
			throw new IOException("Local file is outside the configured root: " + filePath);
		}
		if (!Files.isRegularFile(realPath)) {
			throw new IOException("Local path is not a regular file: " + filePath);
		}
		return realPath;
	}

	static InputStream limit(InputStream inputStream, long maxBytes) {
		return new BoundedInputStream(inputStream, maxBytes);
	}

	static long deadlineAfter(Duration timeout) {
		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("totalTimeout must be greater than zero");
		}
		return System.nanoTime() + timeout.toNanos();
	}

	static void checkDeadline(long deadlineNanos) throws SocketTimeoutException {
		if (System.nanoTime() >= deadlineNanos) {
			throw new SocketTimeoutException("Remote document fetch exceeded total timeout");
		}
	}

	static InputStream deadline(InputStream inputStream, long deadlineNanos) {
		return new DeadlineInputStream(inputStream, deadlineNanos);
	}

	static DnsResolver dnsResolver(boolean allowPrivateNetworkAccess) {
		return new DnsResolver() {
			@Override
			public InetAddress[] resolve(String host) throws UnknownHostException {
				InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
				if (!allowPrivateNetworkAccess) {
					validateResolvedAddresses(host, addresses);
				}
				return addresses;
			}

			@Override
			public String resolveCanonicalHostname(String host) throws UnknownHostException {
				return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
			}
		};
	}

	static void validateSize(long size, long maxBytes, String source) throws IOException {
		if (maxBytes > 0 && size > maxBytes) {
			throw new IOException("Input exceeds maximum size of " + maxBytes + " bytes: " + source);
		}
	}

	private static void validateHost(String host) throws IOException {
		String normalizedHost = stripBrackets(host).toLowerCase(Locale.ROOT);
		if (METADATA_HOSTS.contains(normalizedHost) || normalizedHost.endsWith(".metadata.google.internal")) {
			throw new IOException("Metadata service access is not allowed: " + host);
		}
		validateResolvedAddresses(host, InetAddress.getAllByName(normalizedHost));
	}

	private static void validateResolvedAddresses(String host, InetAddress[] addresses) throws UnknownHostException {
		for (InetAddress address : addresses) {
			if (isPrivateAddress(address)) {
				throw new UnknownHostException("Private network access is not allowed: " + host);
			}
		}
	}

	private static String stripBrackets(String host) {
		if (host.startsWith("[") && host.endsWith("]")) {
			return host.substring(1, host.length() - 1);
		}
		return host;
	}

	private static boolean isPrivateAddress(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		if (address instanceof Inet4Address inet4Address) {
			byte[] bytes = inet4Address.getAddress();
			int first = Byte.toUnsignedInt(bytes[0]);
			int second = Byte.toUnsignedInt(bytes[1]);
			return first == 0 || first == 127 || (first == 100 && second >= 64 && second <= 127) || first >= 224;
		}
		if (address instanceof Inet6Address inet6Address) {
			byte[] bytes = inet6Address.getAddress();
			int first = Byte.toUnsignedInt(bytes[0]);
			return (first & 0xfe) == 0xfc;
		}
		return false;
	}

	private static final class BoundedInputStream extends FilterInputStream {

		private final long maxBytes;

		private long bytesRead;

		private BoundedInputStream(InputStream inputStream, long maxBytes) {
			super(inputStream);
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException {
			int value = super.read();
			if (value != -1) {
				increment(1);
			}
			return value;
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			int count = super.read(bytes, offset, length);
			if (count > 0) {
				increment(count);
			}
			return count;
		}

		private void increment(long count) throws IOException {
			this.bytesRead += count;
			if (this.maxBytes > 0 && this.bytesRead > this.maxBytes) {
				throw new IOException("Input exceeds maximum size of " + this.maxBytes + " bytes");
			}
		}

	}

	private static final class DeadlineInputStream extends FilterInputStream {

		private final long deadlineNanos;

		private DeadlineInputStream(InputStream inputStream, long deadlineNanos) {
			super(inputStream);
			this.deadlineNanos = deadlineNanos;
		}

		@Override
		public int read() throws IOException {
			checkDeadline(deadlineNanos);
			int value = super.read();
			checkDeadline(deadlineNanos);
			return value;
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			checkDeadline(deadlineNanos);
			int count = super.read(bytes, offset, length);
			checkDeadline(deadlineNanos);
			return count;
		}

	}

}
