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
package io.github.agentic.spring.ai.model;

import java.util.List;
import java.util.Map;

import io.github.agentic.spring.ai.document.DocumentWithScore;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.ResultMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RerankTypesTests {

	@Test
	void requestKeepsQueryDocumentsAndOptions() {
		Document document = new Document("content");
		RerankOptions options = new RerankOptions() {
			@Override
			public String getModel() {
				return "test-rerank";
			}

			@Override
			public Integer getTopN() {
				return 5;
			}
		};

		RerankRequest request = new RerankRequest("query", List.of(document), options);

		assertThat(request.getQuery()).isEqualTo("query");
		assertThat(request.getInstructions()).containsExactly(document);
		assertThat(request.getOptions()).isSameAs(options);
	}

	@Test
	void responseReturnsFirstResultAndMetadata() {
		DocumentWithScore first = DocumentWithScore.builder().withDocument(new Document("first")).withScore(0.9).build();
		DocumentWithScore second = DocumentWithScore.builder().withDocument(new Document("second")).withScore(0.8).build();
		RerankResponseMetadata metadata = new RerankResponseMetadata(new EmptyUsage(), Map.of("provider", "test"));

		RerankResponse response = new RerankResponse(List.of(first, second), metadata);

		assertThat(response.getResult()).isSameAs(first);
		assertThat(response.getResults()).containsExactly(first, second);
		assertThat(response.getMetadata()).isSameAs(metadata);
		assertThat(response.getMetadata().getUsage()).isInstanceOf(EmptyUsage.class);
	}

	@Test
	void responseReturnsNullResultWhenEmpty() {
		assertThat(new RerankResponse(List.of()).getResult()).isNull();
	}

	@Test
	void documentWithScoreRequiresDocumentAndScoreAndIgnoresMetadataForEquality() {
		Document document = new Document("document-1", "content", Map.of("source", "test"));
		RerankResultMetadata metadata = new RerankResultMetadata();

		DocumentWithScore scored = DocumentWithScore.builder()
			.withDocument(document)
			.withScore(0.95)
			.withMetadata(metadata)
			.build();
		DocumentWithScore sameOutput = DocumentWithScore.builder()
			.withDocument(document)
			.withScore(0.95)
			.withMetadata(new RerankResultMetadata())
			.build();

		assertThat(scored.getOutput()).isSameAs(document);
		assertThat(scored.getScore()).isEqualTo(0.95);
		assertThat(scored.getMetadata()).isSameAs(metadata).isInstanceOf(ResultMetadata.class);
		assertThat(scored).isEqualTo(sameOutput);
		assertThat(scored.hashCode()).isEqualTo(sameOutput.hashCode());
	}

	@Test
	void documentWithScoreBuilderRequiresRequiredFields() {
		assertThatNullPointerException().isThrownBy(() -> DocumentWithScore.builder().withScore(0.9).build())
			.withMessage("document must be set");
		assertThatNullPointerException().isThrownBy(() -> DocumentWithScore.builder().withDocument(new Document("text")).build())
			.withMessage("score must be set");
	}

}
