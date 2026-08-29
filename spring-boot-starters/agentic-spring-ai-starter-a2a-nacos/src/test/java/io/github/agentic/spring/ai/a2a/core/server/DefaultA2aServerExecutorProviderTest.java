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

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultA2aServerExecutorProviderTest {

	@Test
	void defaultExecutorShouldBeBounded() {
		DefaultA2aServerExecutorProvider provider = new DefaultA2aServerExecutorProvider();

		try {
			ExecutorService executor = provider.getA2aServerExecutor();

			assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
			ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
			assertThat(threadPoolExecutor.getMaximumPoolSize()).isLessThan(Integer.MAX_VALUE);
			assertThat(threadPoolExecutor.getQueue().remainingCapacity()).isLessThan(Integer.MAX_VALUE);
		}
		finally {
			provider.close();
		}
	}

	@Test
	void customExecutorShouldUseConfiguredBounds() {
		DefaultA2aServerExecutorProvider provider = new DefaultA2aServerExecutorProvider(2, 4, 8, "test-a2a-");

		try {
			ThreadPoolExecutor executor = (ThreadPoolExecutor) provider.getA2aServerExecutor();

			assertThat(executor.getCorePoolSize()).isEqualTo(2);
			assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
			assertThat(executor.getQueue().remainingCapacity()).isEqualTo(8);
		}
		finally {
			provider.close();
		}
	}

}
