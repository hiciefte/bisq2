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
 * DTO representing a citation/reply in a support chat message
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Citation or reply reference in a chat message")
public record CitationDto(
        @Schema(description = "ID of the referenced message", example = "msg_123abc")
        String messageId,

        @Schema(description = "Author username of the referenced message", example = "support_agent")
        String author,

        @Schema(description = "Author user profile ID of the referenced message", example = "user_profile_456")
        String authorId,

        @Schema(description = "Text content of the referenced message", example = "Original message text")
        String text
) {
}
