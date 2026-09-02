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

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParserProviderTests {

	@Test
	void extensionsReturnsNormalizedImmutableSet() {
		Set<String> extensions = DocumentParserProvider.extensions("TXT", ".json", " md ");

		assertThat(extensions).containsExactlyInAnyOrder("txt", "json", "md");
		assertThatThrownBy(() -> extensions.add("pdf")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void extensionsRejectsInvalidInput() {
		assertThatNullPointerException().isThrownBy(() -> DocumentParserProvider.extensions((String[]) null))
			.withMessage("extensions must not be null");
		assertThatIllegalArgumentException().isThrownBy(DocumentParserProvider::extensions)
			.withMessage("extensions must not be empty");
		assertThatNullPointerException().isThrownBy(() -> DocumentParserProvider.extensions("txt", null))
			.withMessage("extension must not be null");
		assertThatIllegalArgumentException().isThrownBy(() -> DocumentParserProvider.extensions("."))
			.withMessage("extension must not be blank");
	}

	@Test
	void supportsUsesNormalizedExtension() {
		DocumentParserProvider provider = new TextParserProvider();

		assertThat(provider.getSupportedExtensions()).containsExactly("txt");
		assertThat(provider.supports(".TXT")).isTrue();
		assertThat(provider.supports("md")).isFalse();
		assertThat(provider.createParser()).isInstanceOf(TextDocumentParser.class);
	}

	@Test
	void supportsRejectsInvalidExtension() {
		DocumentParserProvider provider = new TextParserProvider();

		assertThatNullPointerException().isThrownBy(() -> provider.supports(null))
			.withMessage("extension must not be null");
		assertThatIllegalArgumentException().isThrownBy(() -> provider.supports(" "))
			.withMessage("extension must not be blank");
	}

	private static final class TextParserProvider implements DocumentParserProvider {

		private static final Set<String> SUPPORTED_EXTENSIONS = DocumentParserProvider.extensions("txt");

		@Override
		public Set<String> getSupportedExtensions() {
			return SUPPORTED_EXTENSIONS;
		}

		@Override
		public DocumentParser createParser() {
			return new TextDocumentParser();
		}

	}

}
