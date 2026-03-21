package bisq.api.web_socket.domain.chat.support;

import bisq.api.web_socket.subscription.ModificationType;
import bisq.api.web_socket.subscription.SubscriberRepository;
import bisq.api.web_socket.subscription.SubscriptionRequest;
import bisq.api.web_socket.subscription.Topic;
import bisq.chat.ChatChannelDomain;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.common.SubDomain;
import bisq.chat.reactions.CommonPublicChatMessageReaction;
import bisq.chat.reactions.Reaction;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportChatReactionsWebSocketServiceTest {
    private static final String CHANNEL_ID = "support.support";
    private static final String AUTHOR_PROFILE_ID = "0123456789012345678901234567890123456789";
    private static final String REACTOR_PROFILE_ID = "fedcba9876543210fedcba9876543210fedcba98";

    @Test
    void testGetJsonPayloadContainsReactionContract() throws Exception {
        SupportChatReactionsWebSocketService service = new SupportChatReactionsWebSocketService(
                new SubscriberRepository(),
                null
        );

        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                CHANNEL_ID,
                AUTHOR_PROFILE_ID,
                "Need help",
                Optional.empty(),
                System.currentTimeMillis(),
                false
        );
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "reaction-1",
                REACTOR_PROFILE_ID,
                CHANNEL_ID,
                ChatChannelDomain.SUPPORT,
                message.getId(),
                Reaction.PARTY.ordinal(),
                System.currentTimeMillis()
        );
        message.addChatMessageReaction(reaction);
        channel.addChatMessage(message);

        Optional<String> payloadJson = invokeGetJsonPayload(service, Stream.of(channel));
        assertTrue(payloadJson.isPresent());

        JsonNode payload = JsonMapperProvider.get().readTree(payloadJson.get());
        assertTrue(payload.isArray());
        JsonNode first = payload.get(0);
        assertEquals("PARTY", first.get("reaction").asText());
        assertEquals(message.getId(), first.get("messageId").asText());
        assertEquals(REACTOR_PROFILE_ID, first.get("senderUserProfileId").asText());
        assertEquals(CHANNEL_ID, first.get("channelId").asText());
        assertTrue(!first.has("conversationId"));
    }

    @Test
    void testAddedEventPayloadContainsReactionContract() throws Exception {
        SubscriberRepository subscriberRepository = new SubscriberRepository();
        WebSocket webSocket = mock(WebSocket.class);
        @SuppressWarnings("unchecked")
        GrizzlyFuture<DataFrame> sendFuture = mock(GrizzlyFuture.class);
        when(webSocket.send(anyString())).thenReturn(sendFuture);
        when(sendFuture.get()).thenReturn(mock(DataFrame.class));
        subscriberRepository.add(new SubscriptionRequest("request-1", Topic.SUPPORT_CHAT_REACTIONS, null), webSocket);

        SupportChatReactionsWebSocketService service = new SupportChatReactionsWebSocketService(
                subscriberRepository,
                null
        );
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "reaction-2",
                REACTOR_PROFILE_ID,
                CHANNEL_ID,
                ChatChannelDomain.SUPPORT,
                "message-42",
                Reaction.PARTY.ordinal(),
                System.currentTimeMillis()
        );

        invokeHandleReaction(service, reaction, ModificationType.ADDED);

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(webSocket, timeout(2000)).send(argumentCaptor.capture());

        JsonNode eventJson = JsonMapperProvider.get().readTree(argumentCaptor.getValue());
        assertEquals("SUPPORT_CHAT_REACTIONS", eventJson.get("topic").asText());
        assertEquals("ADDED", eventJson.get("modificationType").asText());

        JsonNode payload = JsonMapperProvider.get().readTree(eventJson.get("payload").asText());
        assertEquals("PARTY", payload.get("reaction").asText());
        assertEquals("message-42", payload.get("messageId").asText());
        assertEquals(REACTOR_PROFILE_ID, payload.get("senderUserProfileId").asText());
        assertEquals(CHANNEL_ID, payload.get("channelId").asText());
        assertTrue(!payload.has("conversationId"));
    }

    @Test
    void testAddedEventIgnoresUnsupportedReactionId() throws Exception {
        SubscriberRepository subscriberRepository = new SubscriberRepository();
        WebSocket webSocket = mock(WebSocket.class);
        @SuppressWarnings("unchecked")
        GrizzlyFuture<DataFrame> sendFuture = mock(GrizzlyFuture.class);
        when(webSocket.send(anyString())).thenReturn(sendFuture);
        when(sendFuture.get()).thenReturn(mock(DataFrame.class));
        subscriberRepository.add(new SubscriptionRequest("request-1", Topic.SUPPORT_CHAT_REACTIONS, null), webSocket);

        SupportChatReactionsWebSocketService service = new SupportChatReactionsWebSocketService(
                subscriberRepository,
                null
        );
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "reaction-3",
                REACTOR_PROFILE_ID,
                CHANNEL_ID,
                ChatChannelDomain.SUPPORT,
                "message-99",
                999,
                System.currentTimeMillis()
        );

        invokeHandleReaction(service, reaction, ModificationType.ADDED);

        verify(webSocket, never()).send(anyString());
    }

    private Optional<String> invokeGetJsonPayload(SupportChatReactionsWebSocketService service,
                                                  Stream<CommonPublicChatChannel> channels) throws Exception {
        Method method = SupportChatReactionsWebSocketService.class.getDeclaredMethod("getJsonPayload", Stream.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Optional<String> result = (Optional<String>) method.invoke(service, channels);
        return result;
    }

    private void invokeHandleReaction(SupportChatReactionsWebSocketService service,
                                      CommonPublicChatMessageReaction reaction,
                                      ModificationType modificationType) throws Exception {
        Method method = SupportChatReactionsWebSocketService.class.getDeclaredMethod("handleReaction",
                CommonPublicChatMessageReaction.class,
                ModificationType.class);
        method.setAccessible(true);
        method.invoke(service, reaction, modificationType);
    }
}
