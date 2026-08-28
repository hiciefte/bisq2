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

package bisq.desktop.main.content.mu_sig.trade.trade_limits.simulation;

import bisq.account.payment_method.fiat.FiatPaymentRail;
import bisq.desktop.common.view.Model;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Getter
public class MuSigTradeLimitsSimulationModel implements Model {
    private final double minAccountAge;
    private final double maxAccountAge;
    private final ObservableList<FiatPaymentRail> fiatPaymentRails = FXCollections.observableArrayList();
    private final ObjectProperty<FiatPaymentRail> selectedFiatPaymentRail = new SimpleObjectProperty<>();
    private final StringProperty fiatPaymentRailMaxLimit = new SimpleStringProperty();
    private final BooleanProperty hasBisq1AccountAgeWitness = new SimpleBooleanProperty();
    private final DoubleProperty accountAge = new SimpleDoubleProperty();
    private final StringProperty tradeLimit = new SimpleStringProperty();
    private final StringProperty rateLimit = new SimpleStringProperty();


    public MuSigTradeLimitsSimulationModel(double minAccountAge,
                                           double maxAccountAge,
                                           List<FiatPaymentRail> fiatPaymentRails) {
        this.minAccountAge = minAccountAge;
        this.maxAccountAge = maxAccountAge;
        this.fiatPaymentRails.addAll(fiatPaymentRails);
    }
}
