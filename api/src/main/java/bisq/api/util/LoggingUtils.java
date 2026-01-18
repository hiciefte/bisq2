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

package bisq.api.util;

/**
 * Utility class for safe logging operations.
 * Provides methods to sanitize and truncate data before logging
 * to prevent log injection attacks and limit sensitive data exposure.
 */
public final class LoggingUtils {

    private static final int DEFAULT_TRUNCATE_LENGTH = 8;

    private LoggingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Truncates an ID for safe logging (first 8 characters + "...").
     * Prevents excessive data exposure in logs while maintaining identifiability.
     *
     * @param id the ID to truncate
     * @return truncated ID, or original if already short enough, or null if input was null
     */
    public static String truncateId(String id) {
        if (id == null || id.length() <= DEFAULT_TRUNCATE_LENGTH) {
            return id;
        }
        return id.substring(0, DEFAULT_TRUNCATE_LENGTH) + "...";
    }

    /**
     * Sanitizes a string for safe logging by escaping control characters.
     * Prevents log injection attacks by replacing newlines, carriage returns,
     * and tabs with their escaped representations.
     *
     * @param input the string to sanitize
     * @return sanitized string safe for logging, or null if input was null
     */
    public static String sanitizeForLog(String input) {
        if (input == null) {
            return null;
        }
        return input
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
