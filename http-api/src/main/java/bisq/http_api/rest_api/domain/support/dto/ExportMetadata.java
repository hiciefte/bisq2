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

/**
 * DTO representing metadata about the export
 */
@Schema(description = "Metadata about the support chat export")
public record ExportMetadata(
        @Schema(description = "Number of channels in the export", example = "5")
        int channelCount,

        @Schema(description = "Total number of messages in the export", example = "1234")
        int messageCount,

        @Schema(description = "Data retention period in days", example = "30")
        int dataRetentionDays,

        @Schema(description = "Timezone used for timestamps", example = "UTC")
        String timezone
) {
}
