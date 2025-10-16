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

package bisq.http_api.rest_api.domain.support.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing a support chat message
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Support chat message with metadata")
public record MessageDto(
        @Schema(description = "ISO 8601 timestamp in UTC", example = "2025-10-14T12:00:00Z")
        String date,

        @Schema(description = "Human-readable formatted date", example = "2025-10-14 12:00:00")
        String dateFormatted,

        @Schema(description = "Support channel name", example = "General Support")
        String channel,

        @Schema(description = "Message author username", example = "user123")
        String author,

        @Schema(description = "Message author user profile ID", example = "user_profile_123")
        String authorId,

        @Schema(description = "Message text content", example = "How do I reset my password?")
        String message,

        @Schema(description = "Unique message identifier", example = "msg_789xyz")
        String messageId,

        @Schema(description = "Whether the message was edited", example = "false")
        boolean wasEdited,

        @Schema(description = "Citation reference if this message is a reply")
        CitationDto citation
) {
}
