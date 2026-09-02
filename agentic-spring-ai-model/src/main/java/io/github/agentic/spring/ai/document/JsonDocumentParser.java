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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.EmptyJsonMetadataGenerator;
import org.springframework.ai.reader.JsonMetadataGenerator;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON parser that turns objects or arrays into Spring AI documents.
 *
 * @author HeYQ
 * @since 2024-12-08 21:13
 */
public class JsonDocumentParser implements DocumentParser {

	private final JsonMetadataGenerator jsonMetadataGenerator;

	private final JsonMapper jsonMapper = JsonMapper.shared();

	/**
	 * The keys from the JSON object that will be used as the document text.
	 */
	private final List<String> jsonKeysToUse;

	public JsonDocumentParser(String... jsonKeysToUse) {
		this(new EmptyJsonMetadataGenerator(), jsonKeysToUse);
	}

	public JsonDocumentParser(JsonMetadataGenerator jsonMetadataGenerator, String... jsonKeysToUse) {
		Objects.requireNonNull(jsonKeysToUse, "keys must not be null");
		Objects.requireNonNull(jsonMetadataGenerator, "jsonMetadataGenerator must not be null");
		this.jsonMetadataGenerator = jsonMetadataGenerator;
		this.jsonKeysToUse = List.of(jsonKeysToUse);
	}

	@Override
	public List<Document> parse(InputStream inputStream) {
		JsonNode rootNode = this.jsonMapper.readTree(inputStream);

		if (rootNode.isArray()) {
			return StreamSupport.stream(rootNode.spliterator(), true)
				.map(jsonNode -> parseJsonNode(jsonNode, this.jsonMapper))
				.toList();
		}
		else {
			return Collections.singletonList(parseJsonNode(rootNode, this.jsonMapper));
		}
	}

	protected List<Document> get(JsonNode rootNode) {
		if (rootNode.isArray()) {
			return StreamSupport.stream(rootNode.spliterator(), true)
				.map(jsonNode -> parseJsonNode(jsonNode, this.jsonMapper))
				.toList();
		}
		else {
			return Collections.singletonList(parseJsonNode(rootNode, this.jsonMapper));
		}
	}

	/**
	 * Retrieves documents from the JSON resource using a JSON Pointer.
	 * @param pointer a JSON Pointer string (RFC 6901) to locate the desired element
	 * @param inputStream stream containing the JSON content
	 * @return documents parsed from the located JSON element
	 */
	public List<Document> get(String pointer, InputStream inputStream) {
		JsonNode rootNode = this.jsonMapper.readTree(inputStream);
		JsonNode targetNode = rootNode.at(pointer);

		if (targetNode.isMissingNode()) {
			throw new IllegalArgumentException("Invalid JSON Pointer: " + pointer);
		}

		return get(targetNode);
	}

	private Document parseJsonNode(JsonNode jsonNode, ObjectMapper objectMapper) {
		Map<String, Object> item = objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
		});
		StringBuilder sb = new StringBuilder();

		this.jsonKeysToUse.stream()
			.filter(item::containsKey)
			.forEach((key) -> sb.append(key).append(": ").append(item.get(key)).append(System.lineSeparator()));

		Map<String, Object> metadata = this.jsonMetadataGenerator.generate(item);
		String content = sb.isEmpty() ? item.toString() : sb.toString();
		return new Document(content, metadata);
	}

}
