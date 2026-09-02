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

import java.util.Objects;

import io.github.agentic.spring.ai.model.RerankResultMetadata;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.ModelResult;
import org.springframework.ai.model.ResultMetadata;

/**
 * Document model result with a rerank score.
 *
 * @author yuanci.ytb
 * @since 1.0.0-M2
 */
public class DocumentWithScore implements ModelResult<Document> {

	/**
	 * Score of document.
	 */
	private @Nullable Double score;

	/**
	 * Document information.
	 */
	private @Nullable Document document;

	private RerankResultMetadata metadata = new RerankResultMetadata();

	public Double getScore() {
		return Objects.requireNonNull(this.score, "score must be set");
	}

	public void setScore(Double score) {
		this.score = Objects.requireNonNull(score, "score must not be null");
	}

	public void setDocument(Document document) {
		this.document = Objects.requireNonNull(document, "document must not be null");
	}

	public void setMetadata(@Nullable RerankResultMetadata metadata) {
		this.metadata = (metadata != null) ? metadata : new RerankResultMetadata();
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public Document getOutput() {
		return Objects.requireNonNull(this.document, "document must be set");
	}

	@Override
	public ResultMetadata getMetadata() {
		return this.metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DocumentWithScore that = (DocumentWithScore) o;
		return Objects.equals(this.score, that.score) && Objects.equals(this.document, that.document);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.score, this.document);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("DocumentWithScore{");
		sb.append("score=").append(this.score);
		sb.append(", document=").append(this.document);
		sb.append('}');
		return sb.toString();
	}

	public static final class Builder {

		private final DocumentWithScore documentWithScore;

		private Builder() {
			this.documentWithScore = new DocumentWithScore();
		}

		public Builder withScore(Double score) {
			this.documentWithScore.setScore(score);
			return this;
		}

		public Builder withDocument(Document document) {
			this.documentWithScore.setDocument(document);
			return this;
		}

		public Builder withMetadata(RerankResultMetadata metadata) {
			this.documentWithScore.setMetadata(metadata);
			return this;
		}

		public DocumentWithScore build() {
			this.documentWithScore.getScore();
			this.documentWithScore.getOutput();
			return this.documentWithScore;
		}

	}

}
