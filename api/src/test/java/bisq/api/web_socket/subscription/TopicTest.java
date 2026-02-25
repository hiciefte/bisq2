package bisq.api.web_socket.subscription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopicTest {
    @Test
    void testSupportTopicsExist() {
        assertEquals(Topic.SUPPORT_CHAT_MESSAGES, Topic.valueOf("SUPPORT_CHAT_MESSAGES"));
        assertEquals(Topic.SUPPORT_CHAT_REACTIONS, Topic.valueOf("SUPPORT_CHAT_REACTIONS"));
    }

    @Test
    void testExistingTopicsRemain() {
        assertEquals(Topic.TRADE_CHAT_MESSAGES, Topic.valueOf("TRADE_CHAT_MESSAGES"));
        assertEquals(Topic.CHAT_REACTIONS, Topic.valueOf("CHAT_REACTIONS"));
    }
}
