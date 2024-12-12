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

package bisq.desktop.main.content.bisq_easy.trade_wizard.amount_and_price;

import bisq.desktop.common.view.View;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeWizardAmountAndPriceView extends View<StackPane, TradeWizardAmountAndPriceModel, TradeWizardAmountAndPriceController> {


    public TradeWizardAmountAndPriceView(TradeWizardAmountAndPriceModel model,
                                         TradeWizardAmountAndPriceController controller,
                                         Pane amountSelection,
                                         Pane infoAndWarningsSection) {
        super(new StackPane(), model, controller);

        Label headline = new Label("Headline");
        Label amountAtPriceSymbol = new Label("@");
        HBox amountAndPriceHBox = new HBox(amountSelection);
        VBox contentVBox = new VBox(20, headline, amountAndPriceHBox, infoAndWarningsSection);
        contentVBox.setAlignment(Pos.TOP_CENTER);
        root.getChildren().add(contentVBox);
    }

    @Override
    protected void onViewAttached() {

    }

    @Override
    protected void onViewDetached() {

    }
}
