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

class TextDocumentParserTests {

	@Test
	void parseUsesConfiguredCharset() {
		TextDocumentParser parser = new TextDocumentParser(StandardCharsets.ISO_8859_1);
		String text = "Sample text with special characters: e";

		List<Document> documents = parser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));

		assertThat(documents).singleElement().satisfies((document) -> assertThat(document.getText()).isEqualTo(text));
	}

	@Test
	void parseRejectsBlankText() {
		TextDocumentParser parser = new TextDocumentParser();

		assertThatIllegalArgumentException()
			.isThrownBy(() -> parser.parse(new ByteArrayInputStream(" \n\t ".getBytes(StandardCharsets.UTF_8))))
			.withMessage("text must not be blank");
	}

}
