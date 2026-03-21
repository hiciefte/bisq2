package bisq.api.rest_api.endpoints.support;

import bisq.api.dto.chat.support.SupportChatMessageDto;
import bisq.api.dto.chat.support.SupportChatReactionDto;
import bisq.common.json.JsonMapperProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportJsonCompatibilityTest {
    @Test
    void supportChatMessageDto_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "messageId": "message-1",
                  "channelId": "support.support",
                  "senderUserProfileId": "0123456789012345678901234567890123456789",
                  "text": "Need help",
                  "timestamp": 42,
                  "isMyMessage": false,
                  "citationMessageId": null,
                  "supportState": "ai_answered"
                }
                """;

        SupportChatMessageDto dto = JsonMapperProvider.get().readValue(json, SupportChatMessageDto.class);

        assertEquals("message-1", dto.messageId());
        assertEquals("Need help", dto.text());
    }

    @Test
    void supportChatReactionDto_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "reaction": "HEART",
                  "messageId": "message-1",
                  "senderUserProfileId": "0123456789012345678901234567890123456789",
                  "channelId": "support.support",
                  "supportState": "observed"
                }
                """;

        SupportChatReactionDto dto = JsonMapperProvider.get().readValue(json, SupportChatReactionDto.class);

        assertEquals("HEART", dto.reaction());
        assertEquals("message-1", dto.messageId());
    }

    @Test
    void sendSupportMessageRequest_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "text": "Answer body",
                  "citation": null,
                  "citationAuthorUserProfileId": null,
                  "citationMessageId": null,
                  "supportState": "escalated"
                }
                """;

        SendSupportMessageRequest dto = JsonMapperProvider.get().readValue(json, SendSupportMessageRequest.class);

        assertEquals("Answer body", dto.text());
    }

    @Test
    void sendSupportMessageReactionRequest_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "reactionId": 4,
                  "isRemoved": false,
                  "supportState": "observed"
                }
                """;

        SendSupportMessageReactionRequest dto = JsonMapperProvider.get().readValue(json, SendSupportMessageReactionRequest.class);

        assertEquals(4, dto.reactionId());
    }
}
