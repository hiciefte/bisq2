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

package bisq.desktop.main.content.bisq_easy.trade_wizard.direction;

import bisq.desktop.common.view.Model;
import bisq.offer.Direction;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
public class TradeWizardDirectionModel implements Model {
    private final ObjectProperty<Direction> direction = new SimpleObjectProperty<>(Direction.BUY);
    private final BooleanProperty showReputationInfo = new SimpleBooleanProperty();
    private final BooleanProperty buyButtonDisabled = new SimpleBooleanProperty();
    @Setter
    private String formattedAmountWithoutReputationNeeded;

    void reset() {
        direction.set(Direction.BUY);
        showReputationInfo.set(false);
        buyButtonDisabled.set(false);
    }
}