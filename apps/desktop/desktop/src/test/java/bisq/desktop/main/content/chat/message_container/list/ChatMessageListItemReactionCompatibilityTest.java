package bisq.desktop.main.content.chat.message_container.list;

import bisq.chat.reactions.Reaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageListItemReactionCompatibilityTest {
    @Test
    void resolveReactionFromOrdinalReturnsKnownReaction() {
        assertEquals(Reaction.HEART, ChatMessageListItem.resolveReactionFromOrdinal(Reaction.HEART.ordinal()).orElseThrow());
    }

    @Test
    void resolveReactionFromOrdinalReturnsEmptyForUnsupportedReaction() {
        assertTrue(ChatMessageListItem.resolveReactionFromOrdinal(999).isEmpty());
    }
}
