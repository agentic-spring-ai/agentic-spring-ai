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
package io.github.agentic.spring.ai.document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JsonDocumentParserTests {

	@Test
	void parseObjectUsesSelectedKeysAsDocumentText() {
		JsonDocumentParser parser = new JsonDocumentParser("text", "description");
		String json = """
				{
				  "text": "Sample text",
				  "description": "Sample description",
				  "other": "ignored"
				}
				""";

		List<Document> documents = parser.parse(toInputStream(json));

		assertThat(documents).singleElement().satisfies((document) -> assertThat(document.getText())
			.contains("text: Sample text")
			.contains("description: Sample description"));
	}

	@Test
	void getParsesJsonPointerTarget() {
		JsonDocumentParser parser = new JsonDocumentParser("text");
		String json = """
				{
				  "data": {
				    "items": [
				      { "text": "First" },
				      { "text": "Second" }
				    ]
				  }
				}
				""";

		List<Document> documents = parser.get("/data/items", toInputStream(json));

		assertThat(documents).extracting(Document::getText).containsExactlyInAnyOrder("text: First\n", "text: Second\n");
	}

	@Test
	void parseFallsBackToObjectTextWhenNoKeysMatch() {
		JsonDocumentParser parser = new JsonDocumentParser("missing");

		List<Document> documents = parser.parse(toInputStream("{\"name\":\"value\"}"));

		assertThat(documents).singleElement().satisfies((document) -> assertThat(document.getText()).isEqualTo("{name=value}"));
	}

	@Test
	void getRejectsMissingPointer() {
		JsonDocumentParser parser = new JsonDocumentParser("text");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> parser.get("/missing", toInputStream("{\"data\":{}}")))
			.withMessage("Invalid JSON Pointer: /missing");
	}

	private ByteArrayInputStream toInputStream(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

}
