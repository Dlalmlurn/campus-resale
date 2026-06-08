package com.campusresale.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusresale.platform.config.CampusResaleProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LlmGoodsAssistClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateReturnsEmptyWhenAiDisabled() {
        CampusResaleProperties.Ai ai = new CampusResaleProperties.Ai(
                false, "deepseek", "https://api.deepseek.com", "secret-key", "deepseek-chat", 6000);
        LlmGoodsAssistClient client = new LlmGoodsAssistClient(propertiesWith(ai), objectMapper);

        assertThat(client.generate("键盘", "机械键盘出售", "120.00")).isEqualTo(Optional.empty());
    }

    @Test
    void generateReturnsEmptyWhenApiKeyMissing() {
        CampusResaleProperties.Ai ai = new CampusResaleProperties.Ai(
                true, "deepseek", "https://api.deepseek.com", "", "deepseek-chat", 6000);
        LlmGoodsAssistClient client = new LlmGoodsAssistClient(propertiesWith(ai), objectMapper);

        assertThat(client.generate("键盘", "机械键盘出售", "120.00")).isEqualTo(Optional.empty());
    }

    @Test
    void aiUsableRequiresEnabledBaseUrlAndApiKey() {
        assertThat(new CampusResaleProperties.Ai(true, "deepseek", "https://api.deepseek.com", "key", "deepseek-chat", 6000).usable()).isTrue();
        assertThat(new CampusResaleProperties.Ai(true, "deepseek", "https://api.deepseek.com", " ", "deepseek-chat", 6000).usable()).isFalse();
        assertThat(new CampusResaleProperties.Ai(false, "deepseek", "https://api.deepseek.com", "key", "deepseek-chat", 6000).usable()).isFalse();
        assertThat(new CampusResaleProperties.Ai(true, "deepseek", "", "key", "deepseek-chat", 6000).usable()).isFalse();
    }

    private CampusResaleProperties propertiesWith(CampusResaleProperties.Ai ai) {
        return new CampusResaleProperties(null, null, ai);
    }
}
