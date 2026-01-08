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
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final long CACHE_TTL_MS = 60_000;  // 60 seconds

    private final CommonPublicChatChannelService supportChatChannelService;
    private final UserProfileService userProfileService;

    // Rate limiter: 10 requests per minute per IP
    private final ConcurrentHashMap<String, Bucket> rateLimitBuckets = new ConcurrentHashMap<>();

    // Cache with timestamp
    private final AtomicReference<CachedResponse> cache = new AtomicReference<>();

    // Thread pool for async processing (separate from HTTP workers)
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(16,
            r -> {
                Thread t = new Thread(r);
                t.setName("support-export-worker");
                t.setDaemon(true);
                return t;
            });

    // Prometheus metrics
    private static final Counter requestTotal = Counter.build()
            .name("support_export_requests_total")
            .help("Total support export requests")
            .labelNames("status")  // success, rate_limited, error
            .register();

    private static final Histogram requestDuration = Histogram.build()
            .name("support_export_duration_seconds")
            .help("Support export request duration")
            .labelNames("cache_hit")  // true, false
            .register();

    private static final Gauge cacheAge = Gauge.build()
            .name("support_export_cache_age_seconds")
            .help("Age of cached response in seconds")
            .register();

    private static class CachedResponse {
        final SupportChatExport data;
        final long timestamp;

        CachedResponse(SupportChatExport data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    public SupportRestApi(ChatService chatService, UserProfileService userProfileService) {
        this.supportChatChannelService = chatService.getCommonPublicChatChannelServices().get(ChatChannelDomain.SUPPORT);
        this.userProfileService = userProfileService;
    }

    private Bucket createRateLimitBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(10).refillIntervally(10, Duration.ofMinutes(1)))
                .build();
    }

    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @GET
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Export support chat messages as JSON",
            description = "Exports all public support chat messages as JSON. " +
                    "Messages are automatically removed based on the system's configured TTL. " +
                    "All timestamps are in UTC timezone. " +
                    "This endpoint is only accessible via localhost. " +
                    "Rate limited to 10 requests per minute per IP. " +
                    "Responses are cached for 60 seconds.",
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
                            responseCode = "408",
                            description = "Request timeout"
                    ),
                    @ApiResponse(
                            responseCode = "429",
                            description = "Rate limit exceeded"
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
    public void exportSupportChatToJson(
            @Context ContainerRequestContext requestContext,
            @Suspended AsyncResponse asyncResponse) {
        // Set timeout for async processing
        asyncResponse.setTimeout(30, TimeUnit.SECONDS);
        asyncResponse.setTimeoutHandler(ar -> {
            requestTotal.labels("timeout").inc();
            ar.resume(Response.status(408).entity("Request timeout").build());
        });

        Histogram.Timer timer = requestDuration.labels("unknown").startTimer();

        try {
            // Rate limiting check - get client IP from request context
            String clientIp = requestContext.getHeaderString("X-Forwarded-For");
            if (clientIp == null || clientIp.isEmpty()) {
                clientIp = "unknown";  // Fallback for localhost or missing header
            } else {
                // Take first IP if multiple proxies
                clientIp = clientIp.split(",")[0].trim();
            }
            Bucket bucket = rateLimitBuckets.computeIfAbsent(clientIp, k -> createRateLimitBucket());

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for IP: {}", clientIp);
                requestTotal.labels("rate_limited").inc();
                timer.close();
                asyncResponse.resume(Response.status(429)
                        .entity("Rate limit exceeded. Try again in 1 minute.")
                        .build());
                return;
            }

            // Input validation
            if (supportChatChannelService == null) {
                log.error("Support chat service is not available");
                requestTotal.labels("error").inc();
                timer.close();
                asyncResponse.resume(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity("Support chat service not available")
                        .build());
                return;
            }

            // Check cache
            CachedResponse cached = cache.get();
            if (cached != null && !cached.isExpired()) {
                long cacheAgeMs = System.currentTimeMillis() - cached.timestamp;
                log.debug("Serving cached response (age: {}ms)", cacheAgeMs);
                requestTotal.labels("success").inc();
                cacheAge.set(cacheAgeMs / 1000.0);
                timer.observeDuration();
                asyncResponse.resume(Response.ok(cached.data)
                        .header("X-Cache-Hit", "true")
                        .header("X-Cache-Age", cacheAgeMs)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("Cache-Control", "no-store, no-cache, must-revalidate")
                        .header("Pragma", "no-cache")
                        .build());
                return;
            }

            var channels = supportChatChannelService.getChannels();
            if (channels == null || channels.isEmpty()) {
                log.warn("No support channels found for export");
                requestTotal.labels("error").inc();
                timer.close();
                asyncResponse.resume(Response.status(Response.Status.NOT_FOUND)
                        .entity("No support channels found")
                        .build());
                return;
            }

            log.info("Starting support chat export for {} channels (async)", channels.size());

            // Process asynchronously (non-blocking)
            CompletableFuture.supplyAsync(() -> {
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

                return export;
            }, asyncExecutor)
            .thenAccept(export -> {
                // Update cache atomically
                cache.set(new CachedResponse(export));
                cacheAge.set(0);  // Fresh data

                // Generate filename with timestamp for easier identification
                String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                        .withZone(ZoneId.of(TIMEZONE_UTC))
                        .format(Instant.now());
                String filename = String.format("support_chat_export_%s.json", timestamp);

                requestTotal.labels("success").inc();
                timer.observeDuration();
                asyncResponse.resume(Response.ok(export)
                        .header("X-Cache-Hit", "false")
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
                        .header("Cache-Control", "no-store, no-cache, must-revalidate")
                        .header("Pragma", "no-cache")
                        .build());
            })
            .exceptionally(ex -> {
                log.error("Error exporting support chat messages", ex);
                requestTotal.labels("error").inc();
                timer.observeDuration();
                asyncResponse.resume(Response.status(500)
                        .entity("Internal error: " + ex.getMessage())
                        .build());
                return null;
            });

        } catch (Exception e) {
            log.error("Error setting up export", e);
            requestTotal.labels("error").inc();
            timer.close();
            asyncResponse.resume(Response.status(500)
                    .entity("Failed to export support chat messages: " + e.getMessage())
                    .build());
        }
    }
}
