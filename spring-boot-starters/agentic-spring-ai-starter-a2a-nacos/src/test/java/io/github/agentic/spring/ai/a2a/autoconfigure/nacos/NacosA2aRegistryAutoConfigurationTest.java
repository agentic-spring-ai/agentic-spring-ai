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

package io.github.agentic.spring.ai.a2a.autoconfigure.nacos;

import io.github.agentic.spring.ai.a2a.registry.nacos.register.NacosAgentRegistry;
import io.github.agentic.spring.ai.a2a.registry.nacos.service.NacosA2aOperationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.alibaba.nacos.api.ai.A2aService;

import static org.assertj.core.api.Assertions.assertThat;

class NacosA2aRegistryAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(NacosA2aRegistryAutoConfiguration.class));

	@Test
	void shouldBackOffWhenRegistryDisabled() {
		contextRunner.withPropertyValues("spring.ai.alibaba.a2a.nacos.registry.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(A2aService.class);
			assertThat(context).doesNotHaveBean(NacosA2aOperationService.class);
			assertThat(context).doesNotHaveBean(NacosAgentRegistry.class);
		});
	}

}
