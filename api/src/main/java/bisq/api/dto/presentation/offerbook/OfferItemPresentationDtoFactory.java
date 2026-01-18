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

package bisq.api.dto.presentation.offerbook;


import bisq.account.payment_method.PaymentMethod;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.chat.bisq_easy.offerbook.BisqEasyOfferbookMessage;
import bisq.common.market.Market;
import bisq.api.dto.DtoMappings;
import bisq.api.dto.offer.bisq_easy.BisqEasyOfferDto;
import bisq.api.dto.user.profile.UserProfileDto;
import bisq.api.dto.user.reputation.ReputationScoreDto;
import bisq.i18n.Res;
import bisq.offer.Direction;
import bisq.offer.amount.OfferAmountFormatter;
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.amount.spec.RangeAmountSpec;
import bisq.offer.bisq_easy.BisqEasyOffer;
import bisq.account.payment_method.PaymentMethodSpecUtil;
import bisq.offer.price.PriceUtil;
import bisq.offer.price.spec.PriceSpec;
import bisq.offer.price.spec.PriceSpecFormatter;
import bisq.presentation.formatters.DateFormatter;
import bisq.presentation.formatters.PriceFormatter;
import bisq.user.identity.UserIdentityService;
import bisq.user.profile.UserProfile;
import bisq.user.profile.UserProfileService;
import bisq.user.reputation.ReputationScore;
import bisq.user.reputation.ReputationService;

import lombok.extern.slf4j.Slf4j;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class OfferItemPresentationDtoFactory {
    public static OfferItemPresentationDto create(UserProfileService userProfileService,
                                                  UserIdentityService userIdentityService,
                                                  ReputationService reputationService,
                                                  MarketPriceService marketPriceService,
                                                  BisqEasyOfferbookMessage bisqEasyOfferbookMessage) {
        BisqEasyOffer bisqEasyOffer = bisqEasyOfferbookMessage.getBisqEasyOffer().orElseThrow();
        boolean isMyOffer = bisqEasyOfferbookMessage.isMyMessage(userIdentityService);
        Direction direction = bisqEasyOffer.getDirection();
        String messageId = bisqEasyOfferbookMessage.getId();
        String offerId = bisqEasyOffer.getId();
        BisqEasyOfferDto bisqEasyOfferDto = DtoMappings.BisqEasyOfferMapping.fromBisq2Model(bisqEasyOffer);
        String authorUserProfileId = bisqEasyOfferbookMessage.getAuthorUserProfileId();

        // For now, we send also the formatted values as we have not the complex formatters in mobile impl. yet.
        // We might need to replicate the formatters anyway later and then those fields could be removed
        long date = bisqEasyOfferbookMessage.getDate();
        String formattedDate = DateFormatter.formatDateTime(new Date(date), DateFormat.MEDIUM, DateFormat.SHORT,
                true, " " + Res.get("temporal.at") + " ");
        AmountSpec amountSpec = bisqEasyOffer.getAmountSpec();
        PriceSpec priceSpec = bisqEasyOffer.getPriceSpec();
        boolean hasAmountRange = amountSpec instanceof RangeAmountSpec;
        Market market = bisqEasyOffer.getMarket();
        String formattedQuoteAmount = OfferAmountFormatter.formatQuoteAmount(
                marketPriceService,
                amountSpec,
                priceSpec,
                market,
                hasAmountRange,
                true
        );
        String formattedBaseAmount = OfferAmountFormatter.formatBaseAmount(
                marketPriceService,
                amountSpec,
                priceSpec,
                market,
                hasAmountRange,
                true,
                false
        );
        String formattedPrice = PriceUtil.findQuote(marketPriceService, bisqEasyOffer)
                .map(PriceFormatter::format)
                .orElse("");
        String formattedPriceSpec = PriceSpecFormatter.getFormattedPriceSpec(priceSpec, true);
        List<String> quoteSidePaymentMethods = PaymentMethodSpecUtil.getPaymentMethods(bisqEasyOffer.getQuoteSidePaymentMethodSpecs())
                .stream()
                .map(PaymentMethod::getPaymentRailName)
                .collect(Collectors.toList());
        List<String> baseSidePaymentMethods = PaymentMethodSpecUtil.getPaymentMethods(bisqEasyOffer.getBaseSidePaymentMethodSpecs())
                .stream()
                .map(PaymentMethod::getPaymentRailName)
                .collect(Collectors.toList());

        UserProfile userProfile = userProfileService.findUserProfile(authorUserProfileId).orElseThrow();
        UserProfileDto userProfileDto = DtoMappings.UserProfileMapping.fromBisq2Model(userProfile);
        ReputationScore reputationScore = reputationService.getReputationScore(authorUserProfileId);
        ReputationScoreDto reputationScoreDto = DtoMappings.ReputationScoreMapping.fromBisq2Model(reputationScore);
        return new OfferItemPresentationDto(bisqEasyOfferDto,
                isMyOffer,
                userProfileDto,
                formattedDate,
                formattedQuoteAmount,
                formattedBaseAmount,
                formattedPrice,
                formattedPriceSpec,
                quoteSidePaymentMethods,
                baseSidePaymentMethods,
                reputationScoreDto);
    }

    /**
     * Creates an OfferItemPresentationDto safely, returning Optional.empty() if
     * required data (user profile, market price) is not available.
     * <p>
     * This method provides graceful degradation when P2P network synchronization
     * is incomplete, allowing callers to receive partial results instead of
     * failing the entire request.
     * <p>
     * <b>Thread Safety:</b> This method is thread-safe as it only reads from
     * the provided services and creates immutable DTOs.
     * <p>
     * <b>Performance:</b> Pre-validates required data before delegating to
     * {@link #create} to minimize exception overhead on the hot path.
     *
     * @param userProfileService service for user profile lookup (must not be null)
     * @param userIdentityService service for user identity lookup (must not be null)
     * @param reputationService service for reputation score lookup (must not be null)
     * @param marketPriceService service for market price lookup (must not be null)
     * @param bisqEasyOfferbookMessage the offerbook message to process (must not be null)
     * @return Optional containing the DTO if successful, empty if data unavailable
     * @throws NullPointerException if bisqEasyOfferbookMessage is null
     */
    public static Optional<OfferItemPresentationDto> createSafe(
            UserProfileService userProfileService,
            UserIdentityService userIdentityService,
            ReputationService reputationService,
            MarketPriceService marketPriceService,
            BisqEasyOfferbookMessage bisqEasyOfferbookMessage) {

        // Null input validation (fail-fast for programming errors)
        Objects.requireNonNull(bisqEasyOfferbookMessage, "bisqEasyOfferbookMessage must not be null");

        // Pre-check 1: Verify offer exists
        if (bisqEasyOfferbookMessage.getBisqEasyOffer().isEmpty()) {
            return Optional.empty();
        }

        // Pre-check 2: Verify author profile ID exists
        String authorUserProfileId = bisqEasyOfferbookMessage.getAuthorUserProfileId();
        if (authorUserProfileId == null || authorUserProfileId.isBlank()) {
            return Optional.empty();
        }

        // Pre-check 3: Verify user profile is available in local store
        if (userProfileService.findUserProfile(authorUserProfileId).isEmpty()) {
            return Optional.empty();
        }

        // All pre-checks passed - delegate to existing create() method
        try {
            return Optional.of(create(
                    userProfileService,
                    userIdentityService,
                    reputationService,
                    marketPriceService,
                    bisqEasyOfferbookMessage));
        } catch (NoSuchElementException e) {
            // Defensive catch for any remaining edge cases (shouldn't occur after pre-checks)
            return Optional.empty();
        }
    }
}