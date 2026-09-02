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
package io.github.agentic.spring.ai.graph.node;

import io.github.agentic.spring.ai.document.DocumentWithScore;
import io.github.agentic.spring.ai.model.RerankModel;
import io.github.agentic.spring.ai.model.RerankOptions;
import io.github.agentic.spring.ai.model.RerankRequest;
import io.github.agentic.spring.ai.model.RerankResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class KnowledgeRetrievalNodeTest {

	@Test
	void documentRankerPassesProviderNeutralOptionsToRerankRequest() {
		RerankOptions options = new TestRerankOptions("test-rerank", 2);
		AtomicReference<RerankRequest> capturedRequest = new AtomicReference<>();
		List<Document> documents = List.of(document("doc-1", "first"), document("doc-2", "second"));
		RerankModel rerankModel = request -> {
			capturedRequest.set(request);
			return new RerankResponse(List.of(scored(documents.get(1), 0.9), scored(documents.get(0), 0.7)));
		};

		var ranker = new KnowledgeRetrievalNode.KnowledgeRetrievalDocumentRanker(rerankModel, options);

		List<Document> rankedDocuments = ranker.process(new Query("query"), documents);

		assertSame(options, capturedRequest.get().getOptions());
		assertEquals("query", capturedRequest.get().getQuery());
		assertEquals(documents, capturedRequest.get().getInstructions());
		assertEquals(List.of("doc-2", "doc-1"), rankedDocuments.stream().map(Document::getId).toList());
	}

	@Test
	void builderAcceptsProviderNeutralRerankOptions() {
		RerankOptions options = new TestRerankOptions("test-rerank", 1);

		KnowledgeRetrievalNode node = KnowledgeRetrievalNode.builder().rerankOptions(options).build();

		assertEquals(KnowledgeRetrievalNode.class, node.getClass());
	}

	private static Document document(String id, String text) {
		return Document.builder().id(id).text(text).build();
	}

	private static DocumentWithScore scored(Document document, double score) {
		return DocumentWithScore.builder().withDocument(document).withScore(score).build();
	}

	private record TestRerankOptions(String model, Integer topN) implements RerankOptions {

		@Override
		public String getModel() {
			return this.model;
		}

		@Override
		public Integer getTopN() {
			return this.topN;
		}

	}

}
