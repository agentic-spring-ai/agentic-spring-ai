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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

final class NacosJsonSupport {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	private NacosJsonSupport() {
	}

	static <T> T parseObject(String json, String source, Class<T> targetType) {
		if (json == null || json.isBlank()) {
			throw new IllegalArgumentException("Nacos JSON config is empty: " + source);
		}
		try {
			return OBJECT_MAPPER.readValue(json, targetType);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException(
					"Invalid Nacos JSON config for " + targetType.getSimpleName() + ": " + source, ex);
		}
	}

}
