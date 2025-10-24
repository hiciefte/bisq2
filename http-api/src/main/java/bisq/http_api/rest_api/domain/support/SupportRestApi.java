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

package bisq.http_api.rest_api.domain.support;

import bisq.chat.ChatChannelDomain;
import bisq.chat.ChatService;
import bisq.chat.common.CommonPublicChatChannelService;
import bisq.http_api.rest_api.domain.RestApiBase;
import bisq.http_api.rest_api.domain.support.dto.CitationDto;
import bisq.http_api.rest_api.domain.support.dto.ExportMetadata;
import bisq.http_api.rest_api.domain.support.dto.MessageDto;
import bisq.http_api.rest_api.domain.support.dto.SupportChatExport;
import bisq.user.profile.UserProfile;
import bisq.user.profile.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * REST API endpoint for exporting support chat messages.
 * Provides JSON export functionality for public support chat channels with metadata.
 * All exports include author nicknames, timestamps in UTC, and message citations.
 * Access is restricted to localhost only for security.
 */
@Slf4j
@Path("/support")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Support API", description = "API for exporting support chat messages")
public class SupportRestApi extends RestApiBase {
    private static final String TIMEZONE_UTC = "UTC";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of(TIMEZONE_UTC));

    private final CommonPublicChatChannelService supportChatChannelService;
    private final UserProfileService userProfileService;

    public SupportRestApi(ChatService chatService, UserProfileService userProfileService) {
        this.supportChatChannelService = chatService.getCommonPublicChatChannelServices().get(ChatChannelDomain.SUPPORT);
        this.userProfileService = userProfileService;
    }

    @GET
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Export support chat messages as JSON",
            description = "Exports all public support chat messages as JSON. " +
                    "Messages are automatically removed based on the system's configured TTL. " +
                    "All timestamps are in UTC timezone. " +
                    "This endpoint is only accessible via localhost.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Support chat messages exported successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = SupportChatExport.class),
                                    examples = @ExampleObject(
                                            name = "Sample Export",
                                            value = """
                                                    {
                                                      "exportDate": "2025-10-14T15:30:00Z",
                                                      "exportMetadata": {
                                                        "channelCount": 2,
                                                        "messageCount": 3,
                                                        "dataRetentionDays": 10,
                                                        "timezone": "UTC"
                                                      },
                                                      "messages": [
                                                        {
                                                          "date": "2025-10-14T12:00:00Z",
                                                          "dateFormatted": "2025-10-14 12:00:00",
                                                          "channel": "General Support",
                                                          "author": "user123",
                                                          "authorId": "user123",
                                                          "message": "How do I reset my password?",
                                                          "messageId": "msg_789xyz",
                                                          "wasEdited": false,
                                                          "citation": null
                                                        }
                                                      ]
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "No support channels found"
                    ),
                    @ApiResponse(
                            responseCode = "503",
                            description = "Support chat service not available"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error"
                    )
            }
    )
    public Response exportSupportChatToJson() {
        try {
            // Input validation
            if (supportChatChannelService == null) {
                log.error("Support chat service is not available");
                return buildResponse(Response.Status.SERVICE_UNAVAILABLE,
                        "Support chat service not available");
            }

            var channels = supportChatChannelService.getChannels();
            if (channels == null || channels.isEmpty()) {
                log.warn("No support channels found for export");
                return buildResponse(Response.Status.NOT_FOUND,
                        "No support channels found");
            }

            log.info("Starting support chat export for {} channels", channels.size());

            // Collect all messages and calculate maximum TTL across all messages
            List<MessageDto> messages = new ArrayList<>();
            long dataRetentionDays = 10; // Default fallback (TTL_10_DAYS is the standard for CommonPublicChatMessage)

            for (var channel : channels) {
                String channelName = channel.getChannelTitle();

                for (var message : channel.getChatMessages()) {
                    if (message == null) {
                        continue;
                    }

                    // Calculate TTL and track maximum across all messages
                    var meta = message.getMetaData();
                    if (meta != null) {
                        long ttlMillis = meta.getTtl();
                        long days = TimeUnit.MILLISECONDS.toDays(ttlMillis)
                                + ((ttlMillis % TimeUnit.DAYS.toMillis(1) != 0) ? 1 : 0); // ceil
                        dataRetentionDays = Math.max(dataRetentionDays, Math.max(1, days));
                    }

                    // Look up author nickname from user profile
                    String authorId = message.getAuthorUserProfileId();
                    if (authorId == null) {
                        log.warn("Message {} has null authorId, skipping", message.getId());
                        continue;
                    }
                    String authorNickname = userProfileService.findUserProfile(authorId)
                            .map(UserProfile::getNickName)
                            .orElse(authorId);  // Fallback to ID if profile not found

                    // Map citation if present
                    CitationDto citation = message.getCitation()
                            .map(c -> {
                                String citationAuthorId = c.getAuthorUserProfileId();
                                if (citationAuthorId == null) {
                                    return null; // Skip citation with null author
                                }
                                String citationAuthorNickname = userProfileService.findUserProfile(citationAuthorId)
                                        .map(UserProfile::getNickName)
                                        .orElse(citationAuthorId);
                                return new CitationDto(
                                        c.getChatMessageId().orElse(null),
                                        citationAuthorNickname,  // Use nickname
                                        citationAuthorId,        // Keep ID for reference
                                        c.getText() != null ? c.getText() : "" // Null-safe text
                                );
                            })
                            .orElse(null);

                    // Create message DTO
                    var messageDto = new MessageDto(
                            Instant.ofEpochMilli(message.getDate()).toString(),
                            DATE_FORMATTER.format(Instant.ofEpochMilli(message.getDate())),
                            channelName,
                            authorNickname,      // Use nickname for readability
                            authorId,            // Keep hash for reference
                            message.getText().orElse(""),
                            message.getId(),
                            message.isWasEdited(),
                            citation
                    );

                    messages.add(messageDto);
                }
            }

            // Create export metadata with system-configured TTL
            var metadata = new ExportMetadata(
                    channels.size(),
                    messages.size(),
                    (int) dataRetentionDays,
                    TIMEZONE_UTC
            );

            // Create complete export
            var export = new SupportChatExport(
                    Instant.now().toString(),
                    metadata,
                    messages
            );

            log.info("Support chat export completed: {} channels, {} messages",
                    metadata.channelCount(), metadata.messageCount());

            // Generate filename with timestamp for easier identification
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.of(TIMEZONE_UTC))
                    .format(Instant.now());
            String filename = String.format("support_chat_export_%s.json", timestamp);

            return Response.ok(export)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
                    .header("Cache-Control", "no-store, no-cache, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid input during export", e);
            return buildResponse(Response.Status.BAD_REQUEST,
                    "Invalid input: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error exporting support chat messages", e);
            return buildErrorResponse(
                    "Failed to export support chat messages");
        }
    }
}
