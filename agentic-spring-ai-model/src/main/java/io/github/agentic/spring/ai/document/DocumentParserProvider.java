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

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service Provider Interface for document parser implementations.
 */
public interface DocumentParserProvider {

	/**
	 * File extensions supported by this provider. Extensions must be normalized,
	 * lower-case values without a leading dot.
	 * @return immutable set of supported extensions
	 */
	Set<String> getSupportedExtensions();

	/**
	 * Create a parser instance for this provider.
	 * @return document parser
	 */
	DocumentParser createParser();

	/**
	 * Return whether this provider supports the supplied file extension.
	 * @param extension extension to test, with or without leading dot
	 * @return whether the normalized extension is supported
	 */
	default boolean supports(String extension) {
		return getSupportedExtensions().contains(normalizeExtension(extension));
	}

	/**
	 * Build an immutable, normalized extension set for provider implementations.
	 * @param extensions supported extensions
	 * @return immutable normalized extension set
	 */
	static Set<String> extensions(String... extensions) {
		Objects.requireNonNull(extensions, "extensions must not be null");
		if (extensions.length == 0) {
			throw new IllegalArgumentException("extensions must not be empty");
		}

		return Arrays.stream(extensions).map(DocumentParserProvider::normalizeExtension).collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Normalize an extension to lower-case without a leading dot.
	 * @param extension extension to normalize
	 * @return normalized extension
	 */
	static String normalizeExtension(String extension) {
		Objects.requireNonNull(extension, "extension must not be null");
		String normalized = extension.trim();
		if (normalized.startsWith(".")) {
			normalized = normalized.substring(1);
		}
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("extension must not be blank");
		}
		return normalized.toLowerCase(Locale.ROOT);
	}

}
