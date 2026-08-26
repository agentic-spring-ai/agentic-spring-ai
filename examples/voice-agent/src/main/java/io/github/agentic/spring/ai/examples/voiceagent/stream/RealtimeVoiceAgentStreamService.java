/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.agentic.spring.ai.examples.voiceagent.stream;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import io.github.agentic.spring.ai.dashscope.audio.transcription.DashScopeAudioTranscriptionOptions;
import io.github.agentic.spring.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import io.github.agentic.spring.ai.dashscope.audio.transcription.DashScopeAudioTranscriptionModel;
import io.github.agentic.spring.ai.dashscope.audio.transcription.RecognitionResult;
import io.github.agentic.spring.ai.dashscope.audio.tts.DashScopeAudioSpeechModel;
import io.github.agentic.spring.ai.dashscope.audio.tts.StreamingInputTextToSpeechModel;
import io.github.agentic.spring.ai.dashscope.spec.DashScopeModel;
import io.github.agentic.spring.ai.graph.agent.ReactAgent;
import io.github.agentic.spring.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Realtime bidirectional streaming voice agent: stream audio in → stream recognition → Agent →
 * stream TTS out.
 *
 * <p>Uses DashScope streamRecognition (Paraformer/Fun-ASR realtime) for ASR and
 * stream(Flux&lt;String&gt;, options) (CosyVoice/Qwen TTS Realtime) for TTS.
 */
@Service
public class RealtimeVoiceAgentStreamService {

	private final ReactAgent sandwichAgent;

	private final DashScopeAudioSpeechModel ttsModel;

	private final DashScopeAudioTranscriptionModel asrModel;

	public RealtimeVoiceAgentStreamService(@Qualifier("sandwichAgent") ReactAgent sandwichAgent,
			@Qualifier("dashScopeSpeechSynthesisModel") DashScopeAudioSpeechModel ttsModel,
			@Autowired(required = false) DashScopeAudioTranscriptionModel asrModel) {
		this.sandwichAgent = sandwichAgent;
		this.ttsModel = ttsModel;
		this.asrModel = asrModel;
	}

	/**
	 * Full realtime pipeline: stream audio in → stream recognition (stt_chunk, stt_output) → Agent
	 * (agent_chunk, tool_call, tool_result, agent_end) → stream TTS out (tts_chunk).
	 */
	public Flux<VoiceAgentEvent> streamWithAudioRealtime(Flux<ByteBuffer> audioStream)
			throws GraphRunnerException {
		if (asrModel == null) {
			return Flux.error(new IllegalStateException("DashScopeAudioTranscriptionModel not available."));
		}
		if (!(ttsModel instanceof StreamingInputTextToSpeechModel)) {
			return Flux.error(new IllegalStateException(
					"TTS model must support streaming input (CosyVoice or Qwen TTS Realtime)."));
		}
		StreamingInputTextToSpeechModel streamingTts = ttsModel;

		DashScopeAudioTranscriptionOptions asrOptions = DashScopeAudioTranscriptionOptions.builder()
				.model(DashScopeModel.AudioModel.PARAFORMER_REALTIME_V2.getValue())
				.sampleRate(16000)
				.format("pcm")
				.disfluencyRemovalEnabled(false)
				.languageHints(List.of("zh", "en"))
				.build();

		Flux<RecognitionResult> recognitionFlux = asrModel.streamRecognition(audioStream, asrOptions);

		StringBuilder transcriptBuffer = new StringBuilder();
		Flux<VoiceAgentEvent> sttEvents = recognitionFlux.flatMap(result -> {
			if (result.sentence() == null) {
				return Flux.empty();
			}
			// Partial (stash)
			if (result.sentence().stash() != null) {
				String stashText = result.sentence().stash().text();
				if (stashText != null && !stashText.isBlank()) {
					return Flux.just(VoiceAgentEvent.SttChunkEvent.create(stashText));
				}
			}
			// Final (sentence)
			String text = result.getText();
			if (text != null && !text.isBlank()) {
				transcriptBuffer.append(text);
				return Flux.just(VoiceAgentEvent.SttOutputEvent.create(text));
			}
			return Flux.empty();
		});

		Flux<VoiceAgentEvent> agentAndTtsEvents = Flux.defer(() -> {
			String transcript = transcriptBuffer.toString().trim();
			if (transcript.isBlank()) {
				return Flux.just(VoiceAgentEvent.SttOutputEvent.create(""));
			}
			try {
				return runAgentWithStreamingTts(transcript, streamingTts);
			}
			catch (GraphRunnerException e) {
				throw new RuntimeException(e);
			}
		});

		return sttEvents.concatWith(agentAndTtsEvents);
	}

	private Flux<VoiceAgentEvent> runAgentWithStreamingTts(String transcript,
			StreamingInputTextToSpeechModel streamingTts) throws GraphRunnerException {
		Sinks.Many<String> textSink = Sinks.many().unicast().onBackpressureBuffer();
		Flux<String> agentTextFlux = textSink.asFlux();

		DashScopeAudioSpeechOptions ttsOptions = DashScopeAudioSpeechOptions.builder()
				.model(DashScopeModel.AudioModel.COSYVOICE_V3_FLASH.getValue())
				.textType("PlainText")
				.voice("longanyang")
				.format("mp3")
				.sampleRate(22050)
				.build();

		Flux<VoiceAgentEvent> agentEvents = sandwichAgent.streamMessages(transcript).flatMap(msg -> {
			if (msg instanceof AssistantMessage am) {
				if (am.hasToolCalls()) {
					return Flux.fromIterable(am.getToolCalls())
							.map(tc -> VoiceAgentEvent.ToolCallEvent.create(tc.id(), tc.name(),
									parseArgs(tc.arguments())));
				}
				String chunk = am.getText();
				if (chunk != null && !chunk.isBlank()) {
					textSink.tryEmitNext(chunk);
					return Flux.just(VoiceAgentEvent.AgentChunkEvent.create(chunk));
				}
			}
			else if (msg instanceof ToolResponseMessage trm) {
				return Flux.fromIterable(trm.getResponses())
						.map(r -> VoiceAgentEvent.ToolResultEvent.create(r.id(), r.name(),
								r.responseData() != null ? r.responseData() : ""));
			}
			return Flux.<VoiceAgentEvent>empty();
		}).doOnComplete(() -> textSink.tryEmitComplete());

		Flux<VoiceAgentEvent> agentEnd = Flux.just(VoiceAgentEvent.AgentEndEvent.create());

		Flux<VoiceAgentEvent> ttsEvents = streamingTts.stream(agentTextFlux, ttsOptions)
				.map(r -> extractAudio(r))
				.filter(bytes -> bytes != null && bytes.length > 0)
				.map(VoiceAgentEvent.TtsChunkEvent::create);

		// Merge so TTS receives streamed text; emit tts_end after TTS completes so client can flush
		return Flux.merge(agentEvents.concatWith(agentEnd), ttsEvents)
				.concatWith(Flux.just(VoiceAgentEvent.TtsEndEvent.create()))
				.cast(VoiceAgentEvent.class);
	}

	private static byte[] extractAudio(TextToSpeechResponse r) {
		if (r == null || r.getResult() == null) {
			return null;
		}
		Speech speech = r.getResult();
		return speech != null ? speech.getOutput() : null;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> parseArgs(String arguments) {
		if (arguments == null || arguments.isBlank()) {
			return Map.of();
		}
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readValue(arguments, Map.class);
		}
		catch (Exception e) {
			return Map.of("raw", arguments);
		}
	}
}
