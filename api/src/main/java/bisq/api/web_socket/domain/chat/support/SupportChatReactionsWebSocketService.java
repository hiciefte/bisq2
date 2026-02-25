/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.api.web_socket.domain.chat.support;

import bisq.api.dto.chat.support.SupportChatReactionDto;
import bisq.api.util.LoggingUtils;
import bisq.api.web_socket.domain.BaseWebSocketService;
import bisq.api.web_socket.subscription.ModificationType;
import bisq.api.web_socket.subscription.SubscriberRepository;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatChannelService;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.reactions.CommonPublicChatMessageReaction;
import bisq.chat.reactions.ChatMessageReaction;
import bisq.common.observable.Pin;
import bisq.common.observable.collection.CollectionObserver;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bisq.api.web_socket.subscription.Topic.SUPPORT_CHAT_REACTIONS;

@Slf4j
public class SupportChatReactionsWebSocketService extends BaseWebSocketService {
    private final CommonPublicChatChannelService supportChatChannelService;
    @Nullable
    private Pin channelsPin;
    private final Map<String, Pin> chatMessagesPinsByChannelId = new ConcurrentHashMap<>();
    private final Map<String, Pin> chatMessageReactionsPinsByMessageId = new ConcurrentHashMap<>();

    public SupportChatReactionsWebSocketService(SubscriberRepository subscriberRepository,
                                                CommonPublicChatChannelService supportChatChannelService) {
        super(subscriberRepository, SUPPORT_CHAT_REACTIONS);
        this.supportChatChannelService = supportChatChannelService;
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        channelsPin = supportChatChannelService.getChannels().addObserver(new CollectionObserver<>() {
            @Override
            public void add(CommonPublicChatChannel channel) {
                String channelId = channel.getId();
                chatMessagesPinsByChannelId.compute(channelId, (key, oldPin) -> {
                    if (oldPin != null) {
                        oldPin.unbind();
                    }
                    return channel.getChatMessages().addObserver(new CollectionObserver<>() {
                        @Override
                        public void add(CommonPublicChatMessage message) {
                            String messageId = message.getId();
                            chatMessageReactionsPinsByMessageId.compute(messageId, (reactionKey, oldReactionPin) -> {
                                if (oldReactionPin != null) {
                                    oldReactionPin.unbind();
                                }
                                return message.getChatMessageReactions().addObserver(new CollectionObserver<>() {
                                    @Override
                                    public void add(ChatMessageReaction element) {
                                        if (element instanceof CommonPublicChatMessageReaction reaction) {
                                            handleReaction(reaction, ModificationType.ADDED);
                                        }
                                    }

                                    @Override
                                    public void remove(Object element) {
                                        if (element instanceof CommonPublicChatMessageReaction reaction) {
                                            handleReaction(reaction, ModificationType.REMOVED);
                                        }
                                    }

                                    @Override
                                    public void clear() {
                                        // Public support message reactions are not cleared in bulk.
                                    }
                                });
                            });
                        }

                        @Override
                        public void remove(Object element) {
                            // Public support messages are not removed from this stream.
                        }

                        @Override
                        public void clear() {
                            // Public support messages are not cleared from this stream.
                        }
                    });
                });
            }

            @Override
            public void remove(Object element) {
                if (element instanceof CommonPublicChatChannel channel) {
                    chatMessagesPinsByChannelId.computeIfPresent(channel.getId(), (key, pin) -> {
                        pin.unbind();
                        return null;
                    });
                }
            }

            @Override
            public void clear() {
                new ArrayList<>(chatMessagesPinsByChannelId.values()).forEach(Pin::unbind);
                new ArrayList<>(chatMessageReactionsPinsByMessageId.values()).forEach(Pin::unbind);
                chatMessagesPinsByChannelId.clear();
                chatMessageReactionsPinsByMessageId.clear();
            }
        });
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> shutdown() {
        if (channelsPin != null) {
            channelsPin.unbind();
            channelsPin = null;
        }
        new ArrayList<>(chatMessagesPinsByChannelId.values()).forEach(Pin::unbind);
        new ArrayList<>(chatMessageReactionsPinsByMessageId.values()).forEach(Pin::unbind);
        chatMessagesPinsByChannelId.clear();
        chatMessageReactionsPinsByMessageId.clear();
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Optional<String> getJsonPayload() {
        return getJsonPayload(supportChatChannelService.getChannels().stream());
    }

    private Optional<String> getJsonPayload(Stream<CommonPublicChatChannel> channels) {
        ArrayList<SupportChatReactionDto> payload = channels
                .flatMap(channel -> channel.getChatMessages().stream()
                        .flatMap(message -> message.getChatMessageReactions().stream()
                                .filter(Objects::nonNull)
                                .filter(CommonPublicChatMessageReaction.class::isInstance)
                                .map(CommonPublicChatMessageReaction.class::cast)
                                .map(SupportWsPayloadMapper::fromReaction)))
                .collect(Collectors.toCollection(ArrayList::new));
        return toJson(payload);
    }

    private void handleReaction(CommonPublicChatMessageReaction reaction, ModificationType modificationType) {
        SupportChatReactionDto dto = SupportWsPayloadMapper.fromReaction(reaction);
        if (log.isDebugEnabled()) {
            log.debug("Support ws trace: ws-dispatch reaction messageId={}, channelId={}, senderUserProfileId={}, reaction={}, modificationType={}",
                    LoggingUtils.truncateId(dto.messageId()),
                    LoggingUtils.truncateId(dto.channelId()),
                    LoggingUtils.truncateId(dto.senderUserProfileId()),
                    dto.reaction(),
                    modificationType);
        }
        subscriberRepository.findSubscribers(topic).ifPresent(subscribers -> {
            toJson(dto).ifPresent(json ->
                    subscribers.forEach(subscriber -> send(json, subscriber, modificationType)));
        });
    }
}
