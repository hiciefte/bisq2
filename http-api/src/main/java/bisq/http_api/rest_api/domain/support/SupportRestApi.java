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
import bisq.chat.Citation;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatChannelService;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.http_api.rest_api.domain.RestApiBase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
import java.util.stream.Collectors;

@Slf4j
@Path("/support")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Support API", description = "API for managing support chat messages")
public class SupportRestApi extends RestApiBase {
    private final CommonPublicChatChannelService supportChatChannelService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public SupportRestApi(ChatService chatService) {
        this.supportChatChannelService = chatService.getCommonPublicChatChannelServices().get(ChatChannelDomain.SUPPORT);
    }

    @GET
    @Path("/export/csv")
    @Operation(
            summary = "Export support chat messages",
            description = "Export all support chat messages as CSV file",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Support chat messages exported successfully",
                            content = @Content(mediaType = "text/csv")),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response exportSupportChatToCsv() {
        try {
            List<List<String>> allMessages = new ArrayList<>();

            // Add CSV headers
            List<String> headers = List.of(
                    "Date",
                    "Channel",
                    "Author",
                    "Message",
                    "Message ID",
                    "Was Edited",
                    "Referenced Message ID",
                    "Referenced Message Author",
                    "Referenced Message Text"
            );
            allMessages.add(headers);

            // Get all support channels
            supportChatChannelService.getChannels().forEach(channel -> {
                String channelName = channel.getChannelTitle();

                // Get all messages from the channel
                channel.getChatMessages().stream()
                        .map(msg -> msg)
                        .forEach(message -> {
                            List<String> row = List.of(
                                    DATE_FORMATTER.format(Instant.ofEpochMilli(message.getDate())),
                                    channelName,
                                    message.getAuthorUserProfileId(),
                                    message.getText().orElse(""),
                                    message.getId(),
                                    String.valueOf(message.isWasEdited()),
                                    message.getCitation().map(Citation::getAuthorUserProfileId).orElse(""),
                                    message.getCitation().map(Citation::getAuthorUserProfileId).orElse(""),
                                    message.getCitation().map(Citation::getText).orElse("")
                            );
                            allMessages.add(row);
                        });
            });

            // Convert to CSV
            String csv = allMessages.stream()
                    .map(row -> row.stream()
                            .map(this::escapeSpecialCharacters)
                            .collect(Collectors.joining(","))
                    )
                    .collect(Collectors.joining("\n"));

            // Generate filename with timestamp
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now());
            String filename = "support_chat_export_" + timestamp + ".csv";

            return Response.ok(csv)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "text/csv")
                    .build();

        } catch (Exception e) {
            log.error("Error exporting support chat messages", e);
            return buildErrorResponse("Failed to export support chat messages: " + e.getMessage());
        }
    }

    private String escapeSpecialCharacters(String data) {
        if (data == null) {
            return "";
        }
        String escapedData = data.replaceAll("\"", "\"\"");
        if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("\n")) {
            escapedData = "\"" + escapedData + "\"";
        }
        return escapedData;
    }
} 