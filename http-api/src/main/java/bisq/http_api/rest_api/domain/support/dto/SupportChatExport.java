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

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO representing the complete support chat export
 */
@Schema(description = "Complete support chat export with metadata and messages")
public record SupportChatExport(
        @Schema(description = "ISO 8601 timestamp of when the export was generated", example = "2025-10-14T15:30:00Z")
        String exportDate,

        @Schema(description = "Metadata about the export")
        ExportMetadata exportMetadata,

        @Schema(description = "List of all support chat messages")
        List<MessageDto> messages
) {
}
