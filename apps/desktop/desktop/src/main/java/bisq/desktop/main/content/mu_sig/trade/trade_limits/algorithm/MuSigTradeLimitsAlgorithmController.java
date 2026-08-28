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

package bisq.desktop.main.content.mu_sig.trade.trade_limits.algorithm;

import bisq.desktop.ServiceProvider;
import bisq.desktop.common.Browser;
import bisq.desktop.common.view.Controller;
import bisq.desktop.common.view.Navigation;
import bisq.desktop.navigation.NavigationTarget;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MuSigTradeLimitsAlgorithmController implements Controller {

    private final MuSigTradeLimitsAlgorithmModel model;
    @Getter
    private final MuSigTradeLimitsAlgorithmView view;

    public MuSigTradeLimitsAlgorithmController(ServiceProvider serviceProvider) {

        model = new MuSigTradeLimitsAlgorithmModel();
        view = new MuSigTradeLimitsAlgorithmView(model, this);
    }

    @Override
    public void onActivate() {
    }

    @Override
    public void onDeactivate() {
    }

    void onBack() {
        Navigation.navigateTo(NavigationTarget.MU_SIG_TRADE_LIMITS_OVERVIEW);
    }

    void onNext() {
        Navigation.navigateTo(NavigationTarget.MU_SIG_TRADE_LIMITS_SIMULATION);
    }

    void onLearnMore() {
        Browser.open("https://bisq.wiki/Reputation");
    }
}
