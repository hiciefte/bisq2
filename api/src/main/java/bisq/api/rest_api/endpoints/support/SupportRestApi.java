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

import bisq.api.rest_api.endpoints.RestApiBase;
import bisq.api.rest_api.endpoints.support.dto.CitationDto;
import bisq.api.rest_api.endpoints.support.dto.ExportMetadata;
import bisq.api.rest_api.endpoints.support.dto.MessageDto;
import bisq.api.rest_api.endpoints.support.dto.SupportChatExport;
import bisq.api.util.LoggingUtils;
import bisq.chat.ChatChannelDomain;
import bisq.chat.ChatService;
import bisq.chat.Citation;
import bisq.chat.common.CommonPublicChatChannel;
import bisq.chat.common.CommonPublicChatChannelService;
import bisq.chat.common.CommonPublicChatMessage;
import bisq.chat.reactions.CommonPublicChatMessageReaction;
import bisq.chat.reactions.Reaction;
import bisq.user.identity.UserIdentity;
import bisq.user.identity.UserIdentityService;
import bisq.user.profile.UserProfile;
import bisq.user.profile.UserProfileService;
import io.github.bucket4j.Bucket;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    // Rate limiter configuration
    private static final long RATE_LIMIT_BUCKET_TTL_MS = 300_000;  // 5 minutes
    private static final int MAX_RATE_LIMIT_BUCKETS = 1000;
    private static final int CLEANUP_INTERVAL_SECONDS = 60;

    private final CommonPublicChatChannelService supportChatChannelService;
    private final UserProfileService userProfileService;
    private final SupportChannelResolver supportChannelResolver;

    // Rate limiter with last access tracking to prevent memory leaks
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitBuckets = new ConcurrentHashMap<>();

    // Scheduled cleanup for rate limit buckets
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("rate-limit-cleanup");
        t.setDaemon(true);
        return t;
    });

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

    private static final Gauge rateLimitBucketCount = Gauge.build()
            .name("support_rate_limit_buckets")
            .help("Number of active rate limit buckets")
            .register();

    /**
     * Rate limit entry with last access tracking for cleanup.
     */
    private static class RateLimitEntry {
        final Bucket bucket;
        volatile long lastAccessTime;

        RateLimitEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - lastAccessTime > ttlMs;
        }
    }

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

    public SupportRestApi(ChatService chatService,
                          UserIdentityService userIdentityService,
                          UserProfileService userProfileService) {
        this.supportChatChannelService = chatService.getCommonPublicChatChannelServices().get(ChatChannelDomain.SUPPORT);
        this.userProfileService = userProfileService;
        this.supportChannelResolver = new SupportChannelResolver(this.supportChatChannelService, userIdentityService);

        // Schedule periodic cleanup of expired rate limit buckets
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredBuckets,
                CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Removes rate limit buckets that haven't been accessed within the TTL.
     * This prevents unbounded memory growth from unique client identifiers.
     */
    private void cleanupExpiredBuckets() {
        try {
            int removedCount = 0;
            Iterator<Map.Entry<String, RateLimitEntry>> iterator = rateLimitBuckets.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, RateLimitEntry> entry = iterator.next();
                if (entry.getValue().isExpired(RATE_LIMIT_BUCKET_TTL_MS)) {
                    iterator.remove();
                    removedCount++;
                }
            }
            if (removedCount > 0) {
                log.debug("Cleaned up {} expired rate limit buckets, {} remaining",
                        removedCount, rateLimitBuckets.size());
            }
            rateLimitBucketCount.set(rateLimitBuckets.size());
        } catch (Exception e) {
            log.warn("Error during rate limit bucket cleanup", e);
        }
    }

    private Bucket createRateLimitBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(10).refillIntervally(10, Duration.ofMinutes(1)))
                .build();
    }

    /**
     * Gets a client identifier from the request context.
     * Uses X-Forwarded-For header if available, otherwise generates a hash-based identifier
     * to prevent all requests sharing a single bucket.
     */
    private String getClientIdentifier(ContainerRequestContext requestContext) {
        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // Take first IP if multiple proxies, sanitize for safety
            String clientIp = forwardedFor.split(",")[0].trim();
            // Basic validation: only allow alphanumeric, dots, and colons (IPv6)
            if (clientIp.matches("[a-fA-F0-9.:]+")) {
                return clientIp;
            }
        }

        // Generate identifier from available request properties to avoid shared bucket
        String userAgent = requestContext.getHeaderString("User-Agent");
        String acceptLanguage = requestContext.getHeaderString("Accept-Language");
        int hash = 0;
        if (userAgent != null) {
            hash = hash * 31 + userAgent.hashCode();
        }
        if (acceptLanguage != null) {
            hash = hash * 31 + acceptLanguage.hashCode();
        }
        // Use absolute value and prefix to make it clear this is a generated ID
        return "client-" + Math.abs(hash);
    }

    public void shutdown() {
        // Shutdown cleanup scheduler
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Shutdown async executor
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

    /**
     * Adds security headers to the response builder.
     */
    private Response.ResponseBuilder addSecurityHeaders(Response.ResponseBuilder builder) {
        return builder
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Referrer-Policy", "no-referrer")
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache");
    }

    @POST
    @Path("/channels/{channelId}/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Send a support chat message",
            description = "Accepts a support message for asynchronous publication and returns the local message id and timestamp.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Message accepted for asynchronous publication"),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "No support channel or selected identity found"),
                    @ApiResponse(responseCode = "503", description = "Support chat service unavailable")
            }
    )
    public void sendSupportMessage(@PathParam("channelId") String channelId,
                                   SendSupportMessageRequest request,
                                   @Suspended AsyncResponse asyncResponse) {
        asyncResponse.setTimeout(30, TimeUnit.SECONDS);
        asyncResponse.setTimeoutHandler(response ->
                response.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
                        .entity("Request timed out")
                        .build()));
        try {
            if (supportChatChannelService == null) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
                        .entity("Support chat service not available")
                        .build());
                return;
            }
            if (request == null || request.text() == null || request.text().trim().isEmpty()) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.BAD_REQUEST))
                        .entity("Invalid input: text must be provided")
                        .build());
                return;
            }
            Optional<CommonPublicChatChannel> optionalChannel = supportChannelResolver.findSupportChannel(channelId);
            if (optionalChannel.isEmpty()) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.NOT_FOUND))
                        .entity("No support channel found for channel ID " + channelId)
                        .build());
                return;
            }

            Optional<UserIdentity> optionalIdentity = supportChannelResolver.findSelectedIdentity();
            if (optionalIdentity.isEmpty()) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.NOT_FOUND))
                        .entity("No selected user identity found")
                        .build());
                return;
            }

            UserIdentity userIdentity = optionalIdentity.get();
            CommonPublicChatChannel channel = optionalChannel.get();
            Optional<Citation> citation = Optional.ofNullable(request.citation())
                    .map(String::trim)
                    .filter(text -> !text.isEmpty())
                    .map(text -> {
                        Optional<String> citationMessageId = normalizeNonBlank(request.citationMessageId());
                        Optional<String> explicitCitationAuthorUserProfileId = normalizeNonBlank(request.citationAuthorUserProfileId());
                        String citationAuthorUserProfileId = SupportCitationResolver.resolveCitationAuthorUserProfileId(
                                channel,
                                text,
                                citationMessageId,
                                explicitCitationAuthorUserProfileId,
                                userIdentity.getId());
                        return new Citation(citationAuthorUserProfileId, text, citationMessageId);
                    });

            CommonPublicChatMessage message = new CommonPublicChatMessage(
                    ChatChannelDomain.SUPPORT,
                    channel.getId(),
                    userIdentity.getId(),
                    request.text(),
                    citation,
                    System.currentTimeMillis(),
                    false
            );
            CompletableFuture<?> publishFuture = supportChatChannelService.publishChatMessage(message, userIdentity);
            if (isImmediateFailure(publishFuture)) {
                log.warn("Support message publish failed before accept for channelId={}",
                        LoggingUtils.sanitizeForLog(channelId));
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
                        .entity("Failed to enqueue support message")
                        .build());
                return;
            }

            publishFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.warn("Support message publish failed asynchronously for channelId={}",
                            LoggingUtils.sanitizeForLog(channelId), throwable);
                }
            });

            asyncResponse.resume(addSecurityHeaders(Response.accepted(new SendSupportMessageResponse(
                    message.getId(),
                    message.getDate())))
                    .build());
        } catch (IllegalArgumentException e) {
            asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.BAD_REQUEST))
                    .entity("Invalid input: " + e.getMessage())
                    .build());
        } catch (Exception e) {
            asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.INTERNAL_SERVER_ERROR))
                    .entity("Internal server error")
                    .build());
        }
    }


    @POST
    @Path("/channels/{channelId}/{messageId}/reactions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Send or remove a support chat reaction",
            description = "Accepts an add/remove reaction request for asynchronous processing in a support channel.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Reaction accepted for asynchronous processing"),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "No support channel, message, reaction or selected identity found"),
                    @ApiResponse(responseCode = "503", description = "Support chat service unavailable")
            }
    )
    public void sendSupportMessageReaction(@PathParam("channelId") String channelId,
                                           @PathParam("messageId") String messageId,
                                           SendSupportMessageReactionRequest request,
                                           @Suspended AsyncResponse asyncResponse) {
        asyncResponse.setTimeout(30, TimeUnit.SECONDS);
        asyncResponse.setTimeoutHandler(response ->
                response.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
                        .entity("Request timed out")
                        .build()));

        try {
            if (supportChatChannelService == null) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
                        .entity("Support chat service not available")
                        .build());
                return;
            }
            if (request == null) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.BAD_REQUEST))
                        .entity("Invalid input: request body must be provided")
                        .build());
                return;
            }
            if (request.reactionId() < 0 || request.reactionId() >= Reaction.values().length) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.BAD_REQUEST))
                        .entity("Invalid input: reactionId must be between 0 and " + (Reaction.values().length - 1))
                        .build());
                return;
            }

            Optional<CommonPublicChatChannel> optionalChannel = supportChannelResolver.findSupportChannel(channelId);
            if (optionalChannel.isEmpty()) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.NOT_FOUND))
                        .entity("No support channel found for channel ID " + channelId)
                        .build());
                return;
            }

            Optional<UserIdentity> optionalIdentity = supportChannelResolver.findSelectedIdentity();
            if (optionalIdentity.isEmpty()) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.NOT_FOUND))
                        .entity("No selected user identity found")
                        .build());
                return;
            }

            CommonPublicChatChannel channel = optionalChannel.get();
            UserIdentity userIdentity = optionalIdentity.get();
            Optional<CommonPublicChatMessage> optionalMessage = SupportReactionLookup.findSupportMessage(channel, messageId);
            if (optionalMessage.isEmpty()) {
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.NOT_FOUND))
                        .entity("No message found for message ID " + messageId)
                        .build());
                return;
            }

            CommonPublicChatMessage message = optionalMessage.get();
            Optional<CommonPublicChatMessageReaction> optionalOwnReaction = SupportReactionLookup.findOwnReaction(
                    message, userIdentity.getId(), request.reactionId());

            if (request.isRemoved()) {
                if (optionalOwnReaction.isEmpty()) {
                    asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.NOT_FOUND))
                            .entity("No matching reaction found to remove")
                            .build());
                    return;
                }

                CompletableFuture<?> removeFuture = supportChatChannelService.deleteChatMessageReaction(
                        optionalOwnReaction.get(), userIdentity.getNetworkIdWithKeyPair());
                if (isImmediateFailure(removeFuture)) {
                    log.warn("Support reaction remove failed before accept for channelId={}, messageId={}",
                            LoggingUtils.sanitizeForLog(channelId),
                            LoggingUtils.sanitizeForLog(messageId));
                    asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
                            .entity("Failed to enqueue reaction removal")
                            .build());
                    return;
                }

                removeFuture.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.warn("Support reaction remove failed asynchronously for channelId={}, messageId={}",
                                LoggingUtils.sanitizeForLog(channelId),
                                LoggingUtils.sanitizeForLog(messageId),
                                throwable);
                    }
                });
                asyncResponse.resume(addSecurityHeaders(Response.accepted()).build());
                return;
            }

            if (optionalOwnReaction.isPresent()) {
                asyncResponse.resume(addSecurityHeaders(Response.accepted()).build());
                return;
            }

            Reaction reaction = Reaction.values()[request.reactionId()];
            CompletableFuture<?> publishReactionFuture = supportChatChannelService.publishChatMessageReaction(message, reaction, userIdentity);
            if (isImmediateFailure(publishReactionFuture)) {
                log.warn("Support reaction publish failed before accept for channelId={}, messageId={}",
                        LoggingUtils.sanitizeForLog(channelId),
                        LoggingUtils.sanitizeForLog(messageId));
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
                        .entity("Failed to enqueue reaction")
                        .build());
                return;
            }

            publishReactionFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.warn("Support reaction publish failed asynchronously for channelId={}, messageId={}",
                            LoggingUtils.sanitizeForLog(channelId),
                            LoggingUtils.sanitizeForLog(messageId),
                            throwable);
                }
            });

            asyncResponse.resume(addSecurityHeaders(Response.accepted()).build());
        } catch (IllegalArgumentException e) {
            asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.BAD_REQUEST))
                    .entity("Invalid input: " + e.getMessage())
                    .build());
        } catch (Exception e) {
            asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.INTERNAL_SERVER_ERROR))
                    .entity("Internal server error")
                    .build());
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
                                                          "channelId": "support.support",
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
                            description = "Support chat service not available or too many clients"
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
            ar.resume(addSecurityHeaders(Response.status(408))
                    .entity("Request timeout")
                    .build());
        });

        Histogram.Timer timer = requestDuration.labels("unknown").startTimer();

        try {
            // Get client identifier for rate limiting
            String clientId = getClientIdentifier(requestContext);

            // Check if we've exceeded max buckets (DoS protection)
            if (rateLimitBuckets.size() >= MAX_RATE_LIMIT_BUCKETS &&
                    !rateLimitBuckets.containsKey(clientId)) {
                log.warn("Max rate limit buckets reached ({}), rejecting new client",
                        MAX_RATE_LIMIT_BUCKETS);
                requestTotal.labels("error").inc();
                timer.close();
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
                        .entity("Service temporarily unavailable due to high load")
                        .build());
                return;
            }

            // Get or create rate limit bucket with access tracking
            RateLimitEntry entry = rateLimitBuckets.computeIfAbsent(clientId,
                    k -> new RateLimitEntry(createRateLimitBucket()));
            entry.touch();  // Update last access time

            if (!entry.bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for client: {}", LoggingUtils.sanitizeForLog(clientId));
                requestTotal.labels("rate_limited").inc();
                timer.close();
                asyncResponse.resume(addSecurityHeaders(Response.status(429))
                        .entity("Rate limit exceeded. Try again in 1 minute.")
                        .build());
                return;
            }

            // Input validation
            if (supportChatChannelService == null) {
                log.error("Support chat service is not available");
                requestTotal.labels("error").inc();
                timer.close();
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.SERVICE_UNAVAILABLE))
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
                asyncResponse.resume(addSecurityHeaders(Response.ok(cached.data))
                        .header("X-Cache-Hit", "true")
                        .header("X-Cache-Age", cacheAgeMs)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .build());
                return;
            }

            var channels = supportChatChannelService.getChannels();
            if (channels == null || channels.isEmpty()) {
                log.warn("No support channels found for export");
                requestTotal.labels("error").inc();
                timer.close();
                asyncResponse.resume(addSecurityHeaders(Response.status(Response.Status.NOT_FOUND))
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
                        log.warn("Message {} has null authorId, skipping",
                                LoggingUtils.truncateId(message.getId()));
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
                            channel.getId(),
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
                asyncResponse.resume(addSecurityHeaders(Response.ok(export))
                        .header("X-Cache-Hit", "false")
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
                        .build());
            })
            .exceptionally(ex -> {
                log.error("Error exporting support chat messages", ex);
                requestTotal.labels("error").inc();
                timer.observeDuration();
                asyncResponse.resume(addSecurityHeaders(Response.status(500))
                        .entity("Internal server error")
                        .build());
                return null;
            });

        } catch (Exception e) {
            log.error("Error setting up export", e);
            requestTotal.labels("error").inc();
            timer.close();
            asyncResponse.resume(addSecurityHeaders(Response.status(500))
                    .entity("Internal server error")
                    .build());
        }
    }

    private static Optional<String> normalizeNonBlank(String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(trimmed -> !trimmed.isEmpty());
    }

    private static boolean isImmediateFailure(CompletableFuture<?> future) {
        if (future == null) {
            return true;
        }
        if (!future.isDone()) {
            return false;
        }
        try {
            future.join();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

}
