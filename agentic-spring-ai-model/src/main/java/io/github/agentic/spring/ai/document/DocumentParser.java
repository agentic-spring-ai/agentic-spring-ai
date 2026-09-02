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

import java.io.InputStream;
import java.util.List;

import org.springframework.ai.document.Document;

/**
 * Parses an input stream into Spring AI documents.
 *
 * @author HeYQ
 * @since 2024-12-02 11:25
 */
public interface DocumentParser {

	/**
	 * Parses a given {@link InputStream} into {@link Document Documents}. This method
	 * does not close the provided {@link InputStream}; callers own stream lifecycle.
	 * @param inputStream stream containing the document content
	 * @return parsed documents
	 */
	List<Document> parse(InputStream inputStream);

}
