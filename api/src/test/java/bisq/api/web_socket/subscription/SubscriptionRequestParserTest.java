package bisq.api.web_socket.subscription;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionRequestParserTest {
    private final SubscriptionRequestParser parser = new SubscriptionRequestParser();

    @Test
    void testParseTypedRequest() {
        String json = """
                {
                  "type":"SubscriptionRequest",
                  "requestId":"1",
                  "topic":"SUPPORT_CHAT_MESSAGES"
                }
                """;

        Optional<SubscriptionRequest> result = parser.parse(json);
        assertTrue(result.isPresent());
        assertEquals("1", result.get().getRequestId());
        assertEquals(Topic.SUPPORT_CHAT_MESSAGES, result.get().getTopic());
    }

    @Test
    void testParsePythonRequestType() {
        String json = """
                {
                  "requestType":"Subscribe",
                  "requestId":"2",
                  "topic":"SUPPORT_CHAT_REACTIONS"
                }
                """;

        Optional<SubscriptionRequest> result = parser.parse(json);
        assertTrue(result.isPresent());
        assertEquals("2", result.get().getRequestId());
        assertEquals(Topic.SUPPORT_CHAT_REACTIONS, result.get().getTopic());
    }

    @Test
    void testRejectInvalidRequestShape() {
        String json = """
                {
                  "requestType":"Ping",
                  "requestId":"3",
                  "topic":"SUPPORT_CHAT_REACTIONS"
                }
                """;

        assertTrue(parser.parse(json).isEmpty());
    }
}
