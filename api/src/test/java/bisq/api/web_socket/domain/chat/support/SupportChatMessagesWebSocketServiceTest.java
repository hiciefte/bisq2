package bisq.api.web_socket.domain.chat.support;

import bisq.api.web_socket.subscription.SubscriberRepository;
import bisq.api.web_socket.subscription.SubscriptionRequest;
import bisq.api.web_socket.subscription.Topic;
import bisq.chat.ChatChannelDomain;
import bisq.chat.Citation;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.common.SubDomain;
import bisq.common.json.JsonMapperProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.WebSocket;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportChatMessagesWebSocketServiceTest {
    private static final String CHANNEL_ID = "support.support";
    private static final String PROFILE_ID = "0123456789012345678901234567890123456789";

    @Test
    void testGetJsonPayloadContainsSupportMessageContract() throws Exception {
        SupportChatMessagesWebSocketService service = new SupportChatMessagesWebSocketService(
                new SubscriberRepository(),
                null,
                null
        );
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                CHANNEL_ID,
                PROFILE_ID,
                "Need help",
                Optional.empty(),
                System.currentTimeMillis(),
                false
        );
        channel.addChatMessage(message);

        Optional<String> payloadJson = invokeGetJsonPayload(service, Stream.of(channel));
        assertTrue(payloadJson.isPresent());

        JsonNode payload = JsonMapperProvider.get().readTree(payloadJson.get());
        assertTrue(payload.isArray());
        JsonNode first = payload.get(0);
        assertEquals(message.getId(), first.get("messageId").asText());
        assertEquals(CHANNEL_ID, first.get("channelId").asText());
        assertTrue(!first.has("conversationId"));
        assertEquals(PROFILE_ID, first.get("senderUserProfileId").asText());
        assertEquals("Need help", first.get("text").asText());
        assertEquals(message.getDate(), first.get("timestamp").asLong());
        assertTrue(first.has("citationMessageId"));
        assertTrue(first.get("citationMessageId").isNull());
    }

    @Test
    void testAddedEventPayloadContainsCitationAndNoInvocationField() throws Exception {
        SubscriberRepository subscriberRepository = new SubscriberRepository();
        WebSocket webSocket = mock(WebSocket.class);
        @SuppressWarnings("unchecked")
        GrizzlyFuture<DataFrame> sendFuture = mock(GrizzlyFuture.class);
        when(webSocket.send(anyString())).thenReturn(sendFuture);
        when(sendFuture.get()).thenReturn(mock(DataFrame.class));
        subscriberRepository.add(new SubscriptionRequest("request-1", Topic.SUPPORT_CHAT_MESSAGES, null),
                Optional.empty(),
                webSocket);

        SupportChatMessagesWebSocketService service = new SupportChatMessagesWebSocketService(
                subscriberRepository,
                null,
                null
        );

        Citation citation = new Citation(PROFILE_ID, "How do I recover?", Optional.of("question-1"));
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                CHANNEL_ID,
                PROFILE_ID,
                "Follow-up",
                Optional.of(citation),
                System.currentTimeMillis(),
                false
        );

        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        invokeHandleAddedMessage(service, channel, message);

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(webSocket, timeout(2000)).send(argumentCaptor.capture());

        JsonNode eventJson = JsonMapperProvider.get().readTree(argumentCaptor.getValue());
        assertEquals("SUPPORT_CHAT_MESSAGES", eventJson.get("topic").asText());
        assertEquals("ADDED", eventJson.get("modificationType").asText());

        JsonNode payload = JsonMapperProvider.get().readTree(eventJson.get("payload").asText());
        assertEquals(message.getId(), payload.get("messageId").asText());
        assertEquals(CHANNEL_ID, payload.get("channelId").asText());
        assertTrue(!payload.has("conversationId"));
        assertEquals(PROFILE_ID, payload.get("senderUserProfileId").asText());
        assertEquals("Follow-up", payload.get("text").asText());
        assertEquals("question-1", payload.get("citationMessageId").asText());
        assertTrue(payload.has("timestamp"));
        assertTrue(!payload.has("invocationIntent"));
    }

    private Optional<String> invokeGetJsonPayload(SupportChatMessagesWebSocketService service,
                                                  Stream<CommonPublicChatChannel> channels) throws Exception {
        Method method = SupportChatMessagesWebSocketService.class.getDeclaredMethod("getJsonPayload", Stream.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Optional<String> result = (Optional<String>) method.invoke(service, channels);
        return result;
    }

    private void invokeHandleAddedMessage(SupportChatMessagesWebSocketService service,
                                          CommonPublicChatChannel channel,
                                          CommonPublicChatMessage message) throws Exception {
        Method method = SupportChatMessagesWebSocketService.class.getDeclaredMethod("handleAddedMessage",
                CommonPublicChatChannel.class,
                CommonPublicChatMessage.class);
        method.setAccessible(true);
        method.invoke(service, channel, message);
    }
}
