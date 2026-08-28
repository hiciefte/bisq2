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

import bisq.desktop.components.controls.UnorderedList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class TradeLimitsViewUtils {
    public static Label getHeadline(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("bisq-text-headline-2");
        return label;
    }

    public static Label getSubHeadline(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("bisq-sub-title-label");
        VBox.setMargin(label, new Insets(10, 0, 0, 0));
        return label;
    }

    public static Label getInfo(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("bisq-text-13");
        return label;
    }

    public static UnorderedList getUnorderedList(String text) {
        return new UnorderedList(text, "bisq-text-13", 7, 5);
    }

}
