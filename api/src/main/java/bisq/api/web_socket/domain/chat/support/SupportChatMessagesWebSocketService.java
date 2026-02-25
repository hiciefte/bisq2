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

import bisq.api.dto.chat.support.SupportChatMessageDto;
import bisq.api.util.LoggingUtils;
import bisq.api.web_socket.domain.BaseWebSocketService;
import bisq.api.web_socket.subscription.ModificationType;
import bisq.api.web_socket.subscription.SubscriberRepository;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatChannelService;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.common.observable.Pin;
import bisq.common.observable.collection.CollectionObserver;
import bisq.user.identity.UserIdentityService;
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

import static bisq.api.web_socket.subscription.Topic.SUPPORT_CHAT_MESSAGES;

@Slf4j
public class SupportChatMessagesWebSocketService extends BaseWebSocketService {
    private final CommonPublicChatChannelService supportChatChannelService;
    private final UserIdentityService userIdentityService;
    @Nullable
    private Pin channelsPin;
    private final Map<String, Pin> messagesByChannelIdPins = new ConcurrentHashMap<>();

    public SupportChatMessagesWebSocketService(SubscriberRepository subscriberRepository,
                                               CommonPublicChatChannelService supportChatChannelService,
                                               UserIdentityService userIdentityService) {
        super(subscriberRepository, SUPPORT_CHAT_MESSAGES);
        this.supportChatChannelService = supportChatChannelService;
        this.userIdentityService = userIdentityService;
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        channelsPin = supportChatChannelService.getChannels().addObserver(new CollectionObserver<>() {
            @Override
            public void add(CommonPublicChatChannel channel) {
                String channelId = channel.getId();
                messagesByChannelIdPins.compute(channelId, (key, oldPin) -> {
                    if (oldPin != null) {
                        oldPin.unbind();
                    }

                    return channel.getChatMessages().addObserver(new CollectionObserver<>() {
                        @Override
                        public void add(CommonPublicChatMessage message) {
                            handleAddedMessage(channel, message);
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
                    messagesByChannelIdPins.computeIfPresent(channel.getId(), (key, pin) -> {
                        pin.unbind();
                        return null;
                    });
                }
            }

            @Override
            public void clear() {
                new ArrayList<>(messagesByChannelIdPins.values()).forEach(Pin::unbind);
                messagesByChannelIdPins.clear();
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
        new ArrayList<>(messagesByChannelIdPins.values()).forEach(Pin::unbind);
        messagesByChannelIdPins.clear();
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Optional<String> getJsonPayload() {
        return getJsonPayload(supportChatChannelService.getChannels().stream());
    }

    private Optional<String> getJsonPayload(Stream<CommonPublicChatChannel> channels) {
        ArrayList<SupportChatMessageDto> payload = channels
                .flatMap(channel -> channel.getChatMessages().stream()
                        .map(message -> {
                            try {
                                return SupportWsPayloadMapper.fromMessage(channel, message, userIdentityService);
                            } catch (Exception e) {
                                log.warn("Support ws payload mapping failed for messageId={}, channelId={}",
                                        LoggingUtils.truncateId(message.getId()),
                                        LoggingUtils.truncateId(message.getChannelId()),
                                        e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull))
                .collect(Collectors.toCollection(ArrayList::new));
        return toJson(payload);
    }

    private void handleAddedMessage(CommonPublicChatMessage message) {
        handleAddedMessage(null, message);
    }

    private void handleAddedMessage(CommonPublicChatChannel channel, CommonPublicChatMessage message) {
        SupportChatMessageDto dto = SupportWsPayloadMapper.fromMessage(channel, message, userIdentityService);
        if (log.isDebugEnabled()) {
            log.debug("Support ws-dispatch messageId={}, channelId={}, senderUserProfileId={}",
                    LoggingUtils.truncateId(dto.messageId()),
                    LoggingUtils.truncateId(dto.channelId()),
                    LoggingUtils.truncateId(dto.senderUserProfileId()));
        }
        subscriberRepository.findSubscribers(topic).ifPresent(subscribers -> {
            toJson(dto).ifPresent(json ->
                    subscribers.forEach(subscriber -> send(json, subscriber, ModificationType.ADDED)));
        });
    }
}
