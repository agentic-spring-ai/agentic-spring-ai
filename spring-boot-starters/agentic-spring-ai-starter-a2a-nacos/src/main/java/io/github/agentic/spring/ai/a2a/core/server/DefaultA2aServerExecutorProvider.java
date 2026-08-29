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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

/**
 * @author xiweng.yy
 */
public class DefaultA2aServerExecutorProvider implements A2aServerExecutorProvider {

	private static final int DEFAULT_CORE_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());

	private static final int DEFAULT_MAX_POOL_SIZE = Math.max(16, DEFAULT_CORE_POOL_SIZE * 4);

	private static final int DEFAULT_QUEUE_CAPACITY = 1000;

	private final ExecutorService executor;

	public DefaultA2aServerExecutorProvider() {
		this(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY, "a2a-server-");
	}

	public DefaultA2aServerExecutorProvider(int corePoolSize, int maxPoolSize, int queueCapacity,
			String threadNamePrefix) {
		validate(corePoolSize, maxPoolSize, queueCapacity);
		this.executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(queueCapacity), new NamedThreadFactory(threadNamePrefix),
				new ThreadPoolExecutor.CallerRunsPolicy());
	}

	@PreDestroy
	public void close() {
		executor.shutdown();
	}

	@Override
	public ExecutorService getA2aServerExecutor() {
		return executor;
	}

	private static void validate(int corePoolSize, int maxPoolSize, int queueCapacity) {
		if (corePoolSize < 1) {
			throw new IllegalArgumentException("corePoolSize must be greater than zero");
		}
		if (maxPoolSize < corePoolSize) {
			throw new IllegalArgumentException("maxPoolSize must be greater than or equal to corePoolSize");
		}
		if (queueCapacity < 1) {
			throw new IllegalArgumentException("queueCapacity must be greater than zero");
		}
	}

	private static class NamedThreadFactory implements ThreadFactory {

		private final AtomicInteger sequence = new AtomicInteger();

		private final String threadNamePrefix;

		NamedThreadFactory(String threadNamePrefix) {
			this.threadNamePrefix = threadNamePrefix == null || threadNamePrefix.isBlank() ? "a2a-server-"
					: threadNamePrefix;
		}

		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable);
			thread.setName(threadNamePrefix + sequence.incrementAndGet());
			return thread;
		}

	}

}
