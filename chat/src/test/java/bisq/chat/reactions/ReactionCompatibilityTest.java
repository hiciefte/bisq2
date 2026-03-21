package bisq.chat.reactions;

import bisq.chat.ChatChannelDomain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionCompatibilityTest {
    private static final String PROFILE_ID = "0123456789012345678901234567890123456789";

    @Test
    void fromOrdinalReturnsEmptyForUnsupportedFutureReaction() {
        assertTrue(Reaction.fromOrdinal(999).isEmpty());
    }

    @Test
    void commonPublicChatMessageReactionAllowsFutureReactionIds() {
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "reaction-1",
                PROFILE_ID,
                "support.support",
                ChatChannelDomain.SUPPORT,
                "message-1",
                999,
                System.currentTimeMillis()
        );

        reaction.verify();

        assertEquals(999, reaction.getReactionId());
    }

    @Test
    void commonPublicChatMessageReactionStillRejectsNegativeReactionIds() {
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "reaction-2",
                PROFILE_ID,
                "support.support",
                ChatChannelDomain.SUPPORT,
                "message-2",
                -1,
                System.currentTimeMillis()
        );

        assertThrows(IllegalArgumentException.class, reaction::verify);
    }
}
