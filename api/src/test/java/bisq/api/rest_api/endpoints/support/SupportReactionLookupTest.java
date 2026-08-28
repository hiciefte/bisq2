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

import bisq.chat.ChatChannelDomain;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.common.SubDomain;
import bisq.chat.reactions.CommonPublicChatMessageReaction;
import bisq.chat.reactions.Reaction;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportReactionLookupTest {
    private static final String AUTHOR_PROFILE_ID = "0123456789012345678901234567890123456789";
    private static final String USER_PROFILE_ID = "1234567890123456789012345678901234567890";
    private static final String OTHER_PROFILE_ID = "2345678901234567890123456789012345678901";

    @Test
    void findSupportMessage_returnsMessage_whenPresent() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                channel.getId(),
                AUTHOR_PROFILE_ID,
                "hello",
                Optional.empty(),
                System.currentTimeMillis(),
                false
        );
        channel.addChatMessage(message);

        Optional<CommonPublicChatMessage> result = SupportReactionLookup.findSupportMessage(channel, message.getId());

        assertTrue(result.isPresent());
        assertEquals(message.getId(), result.get().getId());
    }

    @Test
    void findOwnReaction_returnsReaction_whenUserAndReactionMatch() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                channel.getId(),
                AUTHOR_PROFILE_ID,
                "hello",
                Optional.empty(),
                System.currentTimeMillis(),
                false
        );
        channel.addChatMessage(message);
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "reaction-id-1",
                USER_PROFILE_ID,
                channel.getId(),
                ChatChannelDomain.SUPPORT,
                message.getId(),
                Reaction.THUMBS_UP.ordinal(),
                System.currentTimeMillis()
        );
        message.addChatMessageReaction(reaction);

        Optional<CommonPublicChatMessageReaction> result = SupportReactionLookup.findOwnReaction(
                message, USER_PROFILE_ID, Reaction.THUMBS_UP.ordinal());

        assertTrue(result.isPresent());
        assertEquals("reaction-id-1", result.get().getId());
    }

    @Test
    void findOwnReaction_returnsEmpty_whenOnlyOtherUsersReactionExists() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                channel.getId(),
                AUTHOR_PROFILE_ID,
                "hello",
                Optional.empty(),
                System.currentTimeMillis(),
                false
        );
        channel.addChatMessage(message);
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "reaction-id-1",
                OTHER_PROFILE_ID,
                channel.getId(),
                ChatChannelDomain.SUPPORT,
                message.getId(),
                Reaction.THUMBS_UP.ordinal(),
                System.currentTimeMillis()
        );
        message.addChatMessageReaction(reaction);

        Optional<CommonPublicChatMessageReaction> result = SupportReactionLookup.findOwnReaction(
                message, USER_PROFILE_ID, Reaction.THUMBS_UP.ordinal());

        assertTrue(result.isEmpty());
    }

    @Test
    void findOwnReaction_returnsEmpty_whenUserMatchesButReactionDiffers() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                channel.getId(),
                AUTHOR_PROFILE_ID,
                "hello",
                Optional.empty(),
                System.currentTimeMillis(),
                false
        );
        channel.addChatMessage(message);
        CommonPublicChatMessageReaction reaction = new CommonPublicChatMessageReaction(
                "reaction-id-2",
                USER_PROFILE_ID,
                channel.getId(),
                ChatChannelDomain.SUPPORT,
                message.getId(),
                Reaction.THUMBS_DOWN.ordinal(),
                System.currentTimeMillis()
        );
        message.addChatMessageReaction(reaction);

        Optional<CommonPublicChatMessageReaction> result = SupportReactionLookup.findOwnReaction(
                message, USER_PROFILE_ID, Reaction.THUMBS_UP.ordinal());

        assertTrue(result.isEmpty());
    }
}
