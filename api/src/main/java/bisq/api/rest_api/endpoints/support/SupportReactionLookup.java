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

package bisq.api.rest_api.endpoints.support;

import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.reactions.CommonPublicChatMessageReaction;

import java.util.Optional;

class SupportReactionLookup {
    static Optional<CommonPublicChatMessage> findSupportMessage(CommonPublicChatChannel channel,
                                                                String messageId) {
        return channel.getChatMessages().stream()
                .filter(message -> message.getId().equals(messageId))
                .findFirst();
    }

    static Optional<CommonPublicChatMessageReaction> findOwnReaction(CommonPublicChatMessage message,
                                                                     String userProfileId,
                                                                     int reactionId) {
        return message.getChatMessageReactions().stream()
                .filter(CommonPublicChatMessageReaction.class::isInstance)
                .map(CommonPublicChatMessageReaction.class::cast)
                .filter(reaction -> reaction.getReactionId() == reactionId)
                .filter(reaction -> reaction.getUserProfileId().equals(userProfileId))
                .findFirst();
    }
}
