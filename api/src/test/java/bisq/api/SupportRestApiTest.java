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

package bisq.api;

import bisq.api.rest_api.endpoints.support.SendSupportMessageReactionRequest;
import bisq.api.rest_api.endpoints.support.SendSupportMessageRequest;
import bisq.api.rest_api.endpoints.support.SupportRestApi;
import bisq.api.rest_api.endpoints.support.dto.SupportChatExport;
import bisq.chat.ChatChannelDomain;
import bisq.chat.ChatService;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatChannelService;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.common.SubDomain;
import bisq.common.observable.collection.ObservableSet;
import bisq.network.NetworkService;
import bisq.persistence.PersistenceService;
import bisq.user.UserService;
import bisq.user.banned.BannedUserService;
import bisq.user.identity.UserIdentityService;
import bisq.user.profile.UserProfileService;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportRestApiTest {
    private static final String CHANNEL_ID = "support.support";
    private static final String AUTHOR_ID = "0123456789012345678901234567890123456789";
    private static final String MESSAGE_TEXT = "How do I back up Bisq?";

    @TempDir
    private Path tempDir;

    @Mock
    private ChatService chatService;
    @Mock
    private UserIdentityService userIdentityService;
    @Mock
    private UserProfileService userProfileService;
    @Mock
    private AsyncResponse asyncResponse;
    @Mock
    private ContainerRequestContext requestContext;

    private AutoCloseable mocks;
    private final List<SupportRestApi> apiInstances = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(asyncResponse.resume(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (SupportRestApi supportRestApi : apiInstances) {
            supportRestApi.shutdown();
        }
        apiInstances.clear();
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void sendSupportMessageReturns503WhenSupportServiceUnavailable() {
        SupportRestApi supportRestApi = createApi(null);

        supportRestApi.sendSupportMessage(
                CHANNEL_ID,
                new SendSupportMessageRequest(MESSAGE_TEXT, null, null, null),
                asyncResponse
        );

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(asyncResponse).setTimeout(30, TimeUnit.SECONDS);
        verify(asyncResponse).setTimeoutHandler(any());
        verify(asyncResponse).resume(responseCaptor.capture());
        assertEquals(503, responseCaptor.getValue().getStatus());
    }

    @Test
    void sendSupportMessageRejectsBlankTextBeforeChannelLookup() {
        SupportRestApi supportRestApi = createApi(createSupportService());

        supportRestApi.sendSupportMessage(
                CHANNEL_ID,
                new SendSupportMessageRequest("  ", null, null, null),
                asyncResponse
        );

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(asyncResponse).setTimeout(30, TimeUnit.SECONDS);
        verify(asyncResponse).setTimeoutHandler(any());
        verify(asyncResponse).resume(responseCaptor.capture());
        assertEquals(400, responseCaptor.getValue().getStatus());
    }

    @Test
    void sendSupportMessageReactionRejectsOutOfRangeReactionId() {
        SupportRestApi supportRestApi = createApi(createSupportService());

        supportRestApi.sendSupportMessageReaction(
                CHANNEL_ID,
                "message-1",
                new SendSupportMessageReactionRequest(-1, false),
                asyncResponse
        );

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(asyncResponse).setTimeout(30, TimeUnit.SECONDS);
        verify(asyncResponse).setTimeoutHandler(any());
        verify(asyncResponse).resume(responseCaptor.capture());
        assertEquals(400, responseCaptor.getValue().getStatus());
    }

    @Test
    void exportSupportChatToJsonReturnsMetadataAndMessages() {
        CommonPublicChatChannelService supportService = createSupportService();
        CommonPublicChatChannel channel = new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT);
        CommonPublicChatMessage message = new CommonPublicChatMessage(
                ChatChannelDomain.SUPPORT,
                CHANNEL_ID,
                AUTHOR_ID,
                MESSAGE_TEXT,
                Optional.empty(),
                System.currentTimeMillis(),
                false
        );
        channel.addChatMessage(message);
        ObservableSet<CommonPublicChatChannel> channels = supportService.getChannels();
        channels.add(channel);

        SupportRestApi supportRestApi = createApi(supportService);
        when(requestContext.getHeaderString(anyString())).thenReturn(null);
        when(userProfileService.findUserProfile(AUTHOR_ID)).thenReturn(Optional.empty());

        supportRestApi.exportSupportChatToJson(requestContext, asyncResponse);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(asyncResponse).setTimeout(30, TimeUnit.SECONDS);
        verify(asyncResponse).setTimeoutHandler(any());
        verify(asyncResponse, timeout(2000)).resume(responseCaptor.capture());

        Response response = responseCaptor.getValue();
        assertEquals(200, response.getStatus());
        assertTrue(response.getEntity() instanceof SupportChatExport);

        SupportChatExport export = (SupportChatExport) response.getEntity();
        assertNotNull(export.exportDate());
        assertNotNull(export.exportMetadata());
        assertEquals(1, export.exportMetadata().channelCount());
        assertEquals(1, export.exportMetadata().messageCount());
        assertEquals("UTC", export.exportMetadata().timezone());
        assertEquals(1, export.messages().size());
        assertEquals(MESSAGE_TEXT, export.messages().get(0).message());
        assertEquals(AUTHOR_ID, export.messages().get(0).authorId());
        assertEquals(CHANNEL_ID, export.messages().get(0).channelId());
    }

    @Test
    void exportSupportChatToJsonRejectsUnavailableSupportService() {
        SupportRestApi supportRestApi = createApi(null);

        supportRestApi.exportSupportChatToJson(requestContext, asyncResponse);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(asyncResponse).setTimeout(anyLong(), eq(TimeUnit.SECONDS));
        verify(asyncResponse).setTimeoutHandler(any());
        verify(asyncResponse).resume(responseCaptor.capture());
        assertEquals(503, responseCaptor.getValue().getStatus());
    }

    private SupportRestApi createApi(CommonPublicChatChannelService supportService) {
        Map<ChatChannelDomain, CommonPublicChatChannelService> services = new EnumMap<>(ChatChannelDomain.class);
        services.put(ChatChannelDomain.SUPPORT, supportService);
        when(chatService.getCommonPublicChatChannelServices()).thenReturn(services);
        SupportRestApi supportRestApi = new SupportRestApi(chatService, userIdentityService, userProfileService);
        apiInstances.add(supportRestApi);
        return supportRestApi;
    }

    private CommonPublicChatChannelService createSupportService() {
        PersistenceService persistenceService = new PersistenceService(tempDir);
        NetworkService networkService = org.mockito.Mockito.mock(NetworkService.class);
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        UserIdentityService localUserIdentityService = org.mockito.Mockito.mock(UserIdentityService.class);
        UserProfileService localUserProfileService = org.mockito.Mockito.mock(UserProfileService.class);
        BannedUserService bannedUserService = org.mockito.Mockito.mock(BannedUserService.class);
        when(userService.getUserIdentityService()).thenReturn(localUserIdentityService);
        when(userService.getUserProfileService()).thenReturn(localUserProfileService);
        when(userService.getBannedUserService()).thenReturn(bannedUserService);
        when(bannedUserService.isUserProfileBanned(anyString())).thenReturn(false);
        when(bannedUserService.isRateLimitExceeding(anyString())).thenReturn(false);

        return new CommonPublicChatChannelService(
                persistenceService,
                networkService,
                userService,
                ChatChannelDomain.SUPPORT,
                List.of(new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, SubDomain.SUPPORT_SUPPORT))
        );
    }
}
