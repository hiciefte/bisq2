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
import bisq.api.dto.chat.support.SupportChatReactionDto;
import bisq.api.util.LoggingUtils;
import bisq.chat.Citation;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.reactions.CommonPublicChatMessageReaction;
import bisq.chat.reactions.Reaction;
import bisq.user.identity.UserIdentityService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SupportWsPayloadMapper {
    public static SupportChatMessageDto fromMessage(CommonPublicChatMessage message,
                                                    UserIdentityService userIdentityService) {
        return fromMessage(null, message, userIdentityService);
    }

    public static SupportChatMessageDto fromMessage(CommonPublicChatChannel channel,
                                                    CommonPublicChatMessage message,
                                                    UserIdentityService userIdentityService) {
        String channelId = message.getChannelId();
        boolean isMyMessage = userIdentityService != null && message.isMyMessage(userIdentityService);
        String citationMessageId = message.getCitation()
                .flatMap(Citation::getChatMessageId)
                .orElse(null);
        if (log.isDebugEnabled()) {
            log.debug("Support ws-map: messageId={}, channelId={}, authorUserProfileId={}, hasCitation={}, citationMessageId={}",
                    LoggingUtils.truncateId(message.getId()),
                    LoggingUtils.truncateId(channelId),
                    LoggingUtils.truncateId(message.getAuthorUserProfileId()),
                    message.getCitation().isPresent(),
                    LoggingUtils.truncateId(citationMessageId));
        }
        return new SupportChatMessageDto(
                message.getId(),
                channelId,
                message.getAuthorUserProfileId(),
                message.getText().orElse(null),
                message.getDate(),
                isMyMessage,
                citationMessageId
        );
    }

    public static java.util.Optional<SupportChatReactionDto> fromReaction(CommonPublicChatMessageReaction reaction) {
        String channelId = reaction.getChatChannelId();
        int reactionId = reaction.getReactionId();
        java.util.Optional<Reaction> resolvedReaction = Reaction.fromOrdinal(reactionId);
        if (resolvedReaction.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Support ws trace: ignoring unsupported reactionId={} for messageId={}, channelId={}",
                        reactionId,
                        LoggingUtils.truncateId(reaction.getChatMessageId()),
                        LoggingUtils.truncateId(channelId));
            }
            return java.util.Optional.empty();
        }
        String reactionName = resolvedReaction.get().name();
        if (log.isDebugEnabled()) {
            log.debug("Support ws trace: ws-map reaction messageId={}, channelId={}, senderUserProfileId={}, reaction={}",
                    LoggingUtils.truncateId(reaction.getChatMessageId()),
                    LoggingUtils.truncateId(channelId),
                    LoggingUtils.truncateId(reaction.getUserProfileId()),
                    reactionName);
        }
        return java.util.Optional.of(new SupportChatReactionDto(
                reactionName,
                reaction.getChatMessageId(),
                reaction.getUserProfileId(),
                channelId
        ));
    }
}
