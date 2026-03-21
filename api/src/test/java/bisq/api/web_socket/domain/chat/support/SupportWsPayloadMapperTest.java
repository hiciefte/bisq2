package bisq.api.web_socket.domain.chat.support;

import bisq.api.dto.chat.support.SupportChatMessageDto;
import bisq.api.dto.chat.support.SupportChatReactionDto;
import bisq.chat.ChatChannelDomain;
import bisq.chat.Citation;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.reactions.CommonPublicChatMessageReaction;
import bisq.chat.reactions.Reaction;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SupportWsPayloadMapperTest {
    private static final String CHANNEL_ID = "support.support";
    private static final String PROFILE_ID = "0123456789012345678901234567890123456789";

    @Test
    void testMapSupportMessage() {
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                CHANNEL_ID,
                PROFILE_ID,
                "Need help",
                Optional.empty(),
                System.currentTimeMillis(),
                false
        );

        SupportChatMessageDto dto = SupportWsPayloadMapper.fromMessage(message, null);
        assertEquals(message.getId(), dto.messageId());
        assertEquals(CHANNEL_ID, dto.channelId());
        assertEquals(PROFILE_ID, dto.senderUserProfileId());
        assertEquals("Need help", dto.text());
        assertEquals(message.getDate(), dto.timestamp());
        assertFalse(dto.isMyMessage());
        assertNull(dto.citationMessageId());
    }

    @Test
    void testMapSupportMessageWithCitationKeepsCitationMessageId() {
        Citation citation = new Citation(PROFILE_ID, "Original question", Optional.of("quoted-message-id"));
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                CHANNEL_ID,
                PROFILE_ID,
                "Follow-up question",
                Optional.of(citation),
                System.currentTimeMillis(),
                false
        );

        SupportChatMessageDto dto = SupportWsPayloadMapper.fromMessage(message, null);
        assertEquals("Follow-up question", dto.text());
        assertEquals("quoted-message-id", dto.citationMessageId());
    }

    @Test
    void testMapSupportReaction() {
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "r1",
                PROFILE_ID,
                CHANNEL_ID,
                ChatChannelDomain.SUPPORT,
                "m1",
                Reaction.HEART.ordinal(),
                System.currentTimeMillis()
        );

        SupportChatReactionDto dto = SupportWsPayloadMapper.fromReaction(reaction).orElseThrow();
        assertEquals("HEART", dto.reaction());
        assertEquals("m1", dto.messageId());
        assertEquals(PROFILE_ID, dto.senderUserProfileId());
        assertEquals(CHANNEL_ID, dto.channelId());
    }

    @Test
    void testMapSupportReaction_returnsEmptyForInvalidReactionId() {
        CommonPublicChatMessageReaction reaction = Mockito.mock(CommonPublicChatMessageReaction.class);
        when(reaction.getReactionId()).thenReturn(999);
        when(reaction.getChatMessageId()).thenReturn("m2");
        when(reaction.getUserProfileId()).thenReturn(PROFILE_ID);
        when(reaction.getChatChannelId()).thenReturn(CHANNEL_ID);

        assertTrue(SupportWsPayloadMapper.fromReaction(reaction).isEmpty());
    }
}
