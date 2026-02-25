package bisq.api.web_socket.subscription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TopicTest {
    @Test
    void testSupportTopicsExist() {
        assertNotNull(Topic.valueOf("SUPPORT_CHAT_MESSAGES"));
        assertNotNull(Topic.valueOf("SUPPORT_CHAT_REACTIONS"));
    }

    @Test
    void testExistingTopicsRemain() {
        assertEquals(Topic.TRADE_CHAT_MESSAGES, Topic.valueOf("TRADE_CHAT_MESSAGES"));
        assertEquals(Topic.CHAT_REACTIONS, Topic.valueOf("CHAT_REACTIONS"));
    }
}
