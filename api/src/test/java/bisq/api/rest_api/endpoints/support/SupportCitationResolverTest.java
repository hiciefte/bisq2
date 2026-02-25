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
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportCitationResolverTest {
    private static final String FALLBACK_PROFILE_ID = "0123456789012345678901234567890123456789";
    private static final String EXPLICIT_PROFILE_ID = "1111111111111111111111111111111111111111";
    private static final String QUESTION_AUTHOR_ID = "2222222222222222222222222222222222222222";
    private static final String ANSWER_AUTHOR_ID = "3333333333333333333333333333333333333333";

    @Test
    void resolveCitationAuthorUserProfileId_prefersExplicitAuthorId() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);

        String resolved = SupportCitationResolver.resolveCitationAuthorUserProfileId(
                channel,
                "Who is behind Bisq?",
                Optional.empty(),
                Optional.of(EXPLICIT_PROFILE_ID),
                FALLBACK_PROFILE_ID
        );

        assertEquals(EXPLICIT_PROFILE_ID, resolved);
    }

    @Test
    void resolveCitationAuthorUserProfileId_resolvesFromCitationMessageId() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage question = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                channel.getId(),
                QUESTION_AUTHOR_ID,
                "Who is behind Bisq?",
                Optional.empty(),
                System.currentTimeMillis() - 1_000,
                false
        );
        channel.addChatMessage(question);

        String resolved = SupportCitationResolver.resolveCitationAuthorUserProfileId(
                channel,
                question.getText().orElseThrow(),
                Optional.of(question.getId()),
                Optional.empty(),
                FALLBACK_PROFILE_ID
        );

        assertEquals(QUESTION_AUTHOR_ID, resolved);
    }

    @Test
    void resolveCitationAuthorUserProfileId_resolvesFromLatestMatchingCitationText() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage first = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                channel.getId(),
                QUESTION_AUTHOR_ID,
                "What is Bisq?",
                Optional.empty(),
                System.currentTimeMillis() - 2_000,
                false
        );
        channel.addChatMessage(first);
        CommonPublicChatMessage second = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                channel.getId(),
                ANSWER_AUTHOR_ID,
                "What is Bisq?",
                Optional.empty(),
                System.currentTimeMillis() - 1_000,
                false
        );
        channel.addChatMessage(second);

        String resolved = SupportCitationResolver.resolveCitationAuthorUserProfileId(
                channel,
                "What is Bisq?",
                Optional.empty(),
                Optional.empty(),
                FALLBACK_PROFILE_ID
        );

        assertEquals(ANSWER_AUTHOR_ID, resolved);
    }

    @Test
    void resolveCitationAuthorUserProfileId_doesNotParseCurrentQuestionCitationPrefix() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage question = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                channel.getId(),
                QUESTION_AUTHOR_ID,
                "Who is behind Bisq?",
                Optional.empty(),
                System.currentTimeMillis() - 1_000,
                false
        );
        channel.addChatMessage(question);

        String resolved = SupportCitationResolver.resolveCitationAuthorUserProfileId(
                channel,
                """
                        Current question: Who is behind Bisq?
                        Recent chat history:
                        - user: What is Bisq?
                        - assistant: Bisq is decentralized.
                        """,
                Optional.empty(),
                Optional.empty(),
                FALLBACK_PROFILE_ID
        );

        assertEquals(FALLBACK_PROFILE_ID, resolved);
    }

    @Test
    void resolveCitationAuthorUserProfileId_fallsBackToSenderWhenNoMatchExists() {
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);

        String resolved = SupportCitationResolver.resolveCitationAuthorUserProfileId(
                channel,
                "Unknown citation",
                Optional.empty(),
                Optional.empty(),
                FALLBACK_PROFILE_ID
        );

        assertEquals(FALLBACK_PROFILE_ID, resolved);
    }
}
