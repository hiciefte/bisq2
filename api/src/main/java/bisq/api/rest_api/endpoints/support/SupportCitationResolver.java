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

import java.util.Comparator;
import java.util.Optional;

class SupportCitationResolver {
    static String resolveCitationAuthorUserProfileId(CommonPublicChatChannel channel,
                                                     String citationText,
                                                     Optional<String> citationMessageId,
                                                     Optional<String> explicitCitationAuthorUserProfileId,
                                                     String fallbackAuthorUserProfileId) {
        if (explicitCitationAuthorUserProfileId.isPresent()) {
            return explicitCitationAuthorUserProfileId.get();
        }

        if (citationMessageId.isPresent()) {
            Optional<CommonPublicChatMessage> byId = SupportReactionLookup.findSupportMessage(channel, citationMessageId.get());
            if (byId.isPresent()) {
                return byId.get().getAuthorUserProfileId();
            }
        }

        Optional<CommonPublicChatMessage> byText = findLatestByCitationText(channel, citationText);
        if (byText.isPresent()) {
            return byText.get().getAuthorUserProfileId();
        }

        return fallbackAuthorUserProfileId;
    }

    private static Optional<CommonPublicChatMessage> findLatestByCitationText(CommonPublicChatChannel channel, String citationText) {
        String target = citationText == null ? "" : citationText.trim();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        return channel.getChatMessages().stream()
                .filter(message -> message.getText().isPresent())
                .filter(message -> target.equals(message.getText().orElse("").trim()))
                .max(Comparator.comparingLong(CommonPublicChatMessage::getDate));
    }
}
