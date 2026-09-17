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

package bisq.desktop.main.content.mu_sig.trade.trade_limits;

import bisq.desktop.ServiceProvider;
import bisq.desktop.common.view.Controller;
import bisq.desktop.common.view.TabController;
import bisq.desktop.main.content.mu_sig.trade.trade_limits.overview.TradeLimitsOverviewController;
import bisq.desktop.main.content.mu_sig.trade.trade_limits.simulation.MuSigTradeLimitsSimulationController;
import bisq.desktop.main.content.mu_sig.trade.trade_limits.algorithm.MuSigTradeLimitsAlgorithmController;
import bisq.desktop.navigation.NavigationTarget;
import bisq.desktop.overlay.OverlayController;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class TradeLimitsController extends TabController<TradeLimitsModel> {
    @Getter
    private final TradeLimitsView view;
    private final ServiceProvider serviceProvider;

    public TradeLimitsController(ServiceProvider serviceProvider) {
        super(new TradeLimitsModel(), NavigationTarget.MU_SIG_TRADE_LIMITS);

        this.serviceProvider = serviceProvider;
        view = new TradeLimitsView(model, this);
    }

    @Override
    public void onActivate() {
    }

    @Override
    public void onDeactivate() {
    }

    @Override
    protected Optional<? extends Controller> createController(NavigationTarget navigationTarget) {
        return switch (navigationTarget) {
            case MU_SIG_TRADE_LIMITS_OVERVIEW -> Optional.of(new TradeLimitsOverviewController(serviceProvider));
            case MU_SIG_TRADE_LIMITS_ALGORITHM -> Optional.of(new MuSigTradeLimitsAlgorithmController(serviceProvider));
            case MU_SIG_TRADE_LIMITS_SIMULATION -> Optional.of(new MuSigTradeLimitsSimulationController(serviceProvider));
            default -> Optional.empty();
        };
    }

    void onClose() {
        OverlayController.hide();
    }
}
