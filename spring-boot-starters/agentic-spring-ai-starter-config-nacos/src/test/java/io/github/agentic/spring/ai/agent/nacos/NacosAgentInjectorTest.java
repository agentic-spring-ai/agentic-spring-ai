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

package io.github.agentic.spring.ai.agent.nacos;

import io.github.agentic.spring.ai.agent.nacos.vo.AgentVO;
import com.alibaba.nacos.client.config.NacosConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NacosAgentInjectorTest {

	@Test
	void loadAgentVOTreatsTypeMetadataAsUnknownField() throws Exception {
		NacosConfigService configService = mock(NacosConfigService.class);
		NacosOptions nacosOptions = mockOptions(configService);
		when(configService.getConfig("agent-base.json", "ai-agent-demo", 3000L))
			.thenReturn("""
					{"@type":"java.lang.Thread","promptKey":"prompt-a","description":"demo","maxIterations":3}
					""");

		AgentVO agentVO = NacosAgentInjector.loadAgentVO(nacosOptions);

		assertThat(agentVO.getPromptKey()).isEqualTo("prompt-a");
		assertThat(agentVO.getDescription()).isEqualTo("demo");
		assertThat(agentVO.getMaxIterations()).isEqualTo(3);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "\t\n" })
	void loadAgentVORejectsMissingOrBlankConfig(String config) throws Exception {
		NacosConfigService configService = mock(NacosConfigService.class);
		NacosOptions nacosOptions = mockOptions(configService);
		when(configService.getConfig("agent-base.json", "ai-agent-demo", 3000L)).thenReturn(config);

		assertThatThrownBy(() -> NacosAgentInjector.loadAgentVO(nacosOptions))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("agent-base.json")
			.hasMessageContaining("empty");
	}

	@Test
	void loadAgentVORejectsInvalidJsonWithContext() throws Exception {
		NacosConfigService configService = mock(NacosConfigService.class);
		NacosOptions nacosOptions = mockOptions(configService);
		when(configService.getConfig("agent-base.json", "ai-agent-demo", 3000L)).thenReturn("{invalid");

		assertThatThrownBy(() -> NacosAgentInjector.loadAgentVO(nacosOptions))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("agent-base.json")
			.hasMessageContaining("AgentVO");
	}

	private static NacosOptions mockOptions(NacosConfigService configService) {
		NacosOptions nacosOptions = mock(NacosOptions.class);
		when(nacosOptions.getNacosConfigService()).thenReturn(configService);
		when(nacosOptions.getAgentName()).thenReturn("demo");
		when(nacosOptions.isAgentBaseEncrypted()).thenReturn(false);
		return nacosOptions;
	}

}
