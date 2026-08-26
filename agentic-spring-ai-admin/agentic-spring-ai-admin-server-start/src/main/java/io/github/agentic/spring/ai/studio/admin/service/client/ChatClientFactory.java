package io.github.agentic.spring.ai.studio.admin.service.client;

import io.github.agentic.spring.ai.studio.admin.dto.ModelConfigInfo;
import io.github.agentic.spring.ai.studio.admin.entity.ModelConfigDO;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Map;

public interface ChatClientFactory {
    
    String getSupportProvider();
    
    ChatModel buildChatModel(ModelConfigDO modelConfig);

    ChatOptions buildChatOptions(ModelConfigDO modelConfig,Map<String, Object> userParameters, Map<String, String> observationMetadata);
    
}
