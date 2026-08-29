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

package io.github.agentic.spring.ai.a2a.autoconfigure;

import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdditionalSpringConfigurationMetadataTest {

	@Test
	void allSourceTypesReferenceLoadableClasses() throws Exception {
		try (InputStream input = getClass().getResourceAsStream(
				"/META-INF/additional-spring-configuration-metadata.json")) {
			assertNotNull(input);
			JsonNode metadata = new ObjectMapper().readTree(input);
			assertLoadableSourceTypes(metadata.path("groups"));
			assertLoadableSourceTypes(metadata.path("properties"));
		}
	}

	private static void assertLoadableSourceTypes(JsonNode entries) {
		entries.forEach(entry -> {
			String sourceType = entry.path("sourceType").asText();
			if (sourceType.isBlank()) {
				return;
			}
			assertDoesNotThrow(() -> Class.forName(sourceType), () -> "Unknown metadata sourceType: " + sourceType);
		});
	}

}
