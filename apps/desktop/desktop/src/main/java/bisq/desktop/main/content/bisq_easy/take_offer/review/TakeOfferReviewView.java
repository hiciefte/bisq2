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

package bisq.desktop.main.content.bisq_easy.take_offer.review;

import bisq.common.application.DevMode;
import bisq.desktop.common.Transitions;
import bisq.desktop.common.threading.UIScheduler;
import bisq.desktop.common.view.View;
import bisq.desktop.components.containers.Spacer;
import bisq.desktop.components.controls.TextFlowUtils;
import bisq.desktop.components.controls.WrappingText;
import bisq.desktop.main.content.bisq_easy.components.WaitingAnimation;
import bisq.desktop.main.content.bisq_easy.components.WaitingState;
import bisq.desktop.main.content.bisq_easy.take_offer.TakeOfferView;
import bisq.i18n.Res;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

@Slf4j
class TakeOfferReviewView extends View<StackPane, TakeOfferReviewModel, TakeOfferReviewController> {
    public static final String DESCRIPTION_STYLE = "trade-wizard-review-description";
    public static final String VALUE_STYLE = "trade-wizard-review-value";
    public static final String DETAILS_STYLE = "trade-wizard-review-details";
    private final static int FEEDBACK_WIDTH = 700;

    private final VBox takeOfferStatus, sendTakeOfferMessageFeedback, takeOfferSuccess;
    private final Button takeOfferSuccessButton;
    private final Label priceDetails, bitcoinPaymentMethod, fiatPaymentMethod, fee, feeDetails;
    private final GridPane content;
    private final TextFlow price;
    private final WaitingAnimation takeOfferSendMessageWaitingAnimation;
    private Subscription takeOfferStatusPin;
    private boolean minWaitingTimePassed = false;

    TakeOfferReviewView(TakeOfferReviewModel model, TakeOfferReviewController controller, HBox reviewDataDisplay) {
        super(new StackPane(), model, controller);

        content = new GridPane();
        content.setHgap(10);
        content.setVgap(10);
        content.setMouseTransparent(true);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(25);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(25);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(25);
        ColumnConstraints col4 = new ColumnConstraints();
        col4.setPercentWidth(25);
        content.getColumnConstraints().addAll(col1, col2, col3, col4);

        int rowIndex = 0;
        Label headline = new Label(Res.get("bisqEasy.takeOffer.review.headline"));
        headline.getStyleClass().add("trade-wizard-review-headline");
        GridPane.setHalignment(headline, HPos.CENTER);
        GridPane.setMargin(headline, new Insets(10, 0, 30, 0));
        GridPane.setColumnSpan(headline, 4);
        content.add(headline, 0, rowIndex);

        rowIndex++;
        Region line1 = getLine();
        GridPane.setColumnSpan(line1, 4);
        content.add(line1, 0, rowIndex);

        rowIndex++;
        GridPane.setColumnSpan(reviewDataDisplay, 4);
        GridPane.setMargin(reviewDataDisplay, new Insets(0, 0, 10, 0));
        content.add(reviewDataDisplay, 0, rowIndex);

        rowIndex++;
        Label detailsHeadline = new Label(Res.get("bisqEasy.takeOffer.review.detailsHeadline").toUpperCase());
        detailsHeadline.getStyleClass().add("trade-wizard-review-details-headline");
        GridPane.setColumnSpan(detailsHeadline, 4);
        content.add(detailsHeadline, 0, rowIndex);

        rowIndex++;
        Region line2 = getLine();
        GridPane.setMargin(line2, new Insets(-10, 0, -5, 0));
        GridPane.setColumnSpan(line2, 4);
        content.add(line2, 0, rowIndex);

        rowIndex++;
        Label priceDescription = new Label(Res.get("bisqEasy.takeOffer.review.price.price"));
        priceDescription.getStyleClass().add(DESCRIPTION_STYLE);
        content.add(priceDescription, 0, rowIndex);

        price = new TextFlow();
        price.getStyleClass().add(VALUE_STYLE);
        content.add(price, 1, rowIndex);

        priceDetails = new Label();
        priceDetails.getStyleClass().add(DETAILS_STYLE);
        GridPane.setColumnSpan(priceDetails, 2);
        content.add(priceDetails, 2, rowIndex);

        rowIndex++;
        Label bitcoinPaymentMethodDescription = new Label(Res.get("bisqEasy.takeOffer.review.method.bitcoin"));
        bitcoinPaymentMethodDescription.getStyleClass().add(DESCRIPTION_STYLE);
        content.add(bitcoinPaymentMethodDescription, 0, rowIndex);

        bitcoinPaymentMethod = new Label();
        bitcoinPaymentMethod.getStyleClass().add(VALUE_STYLE);
        content.add(bitcoinPaymentMethod, 1, rowIndex);

        rowIndex++;
        Label fiatPaymentMethodDescription = new Label(Res.get("bisqEasy.takeOffer.review.method.fiat"));
        fiatPaymentMethodDescription.getStyleClass().add(DESCRIPTION_STYLE);
        content.add(fiatPaymentMethodDescription, 0, rowIndex);

        fiatPaymentMethod = new Label();
        fiatPaymentMethod.getStyleClass().add(VALUE_STYLE);
        content.add(fiatPaymentMethod, 1, rowIndex);

        rowIndex++;
        Label feeInfoDescription = new Label(Res.get("bisqEasy.tradeWizard.review.feeDescription"));
        feeInfoDescription.getStyleClass().add(DESCRIPTION_STYLE);
        content.add(feeInfoDescription, 0, rowIndex);

        fee = new Label();
        fee.getStyleClass().add(VALUE_STYLE);
        content.add(fee, 1, rowIndex);

        feeDetails = new Label();
        feeDetails.getStyleClass().add(DETAILS_STYLE);
        GridPane.setColumnSpan(feeDetails, 2);
        content.add(feeDetails, 2, rowIndex);

        rowIndex++;
        Region line3 = getLine();
        GridPane.setColumnSpan(line3, 4);
        content.add(line3, 0, rowIndex);

        // Feedback overlay
        takeOfferStatus = new VBox();
        takeOfferStatus.setVisible(false);

        sendTakeOfferMessageFeedback = new VBox(20);
        takeOfferSendMessageWaitingAnimation = new WaitingAnimation(WaitingState.TAKE_BISQ_EASY_OFFER);
        configSendTakeOfferMessageFeedback();

        takeOfferSuccessButton = new Button(Res.get("bisqEasy.takeOffer.review.takeOfferSuccessButton"));
        takeOfferSuccess = new VBox(20);
        configTakeOfferSuccess();

        StackPane.setMargin(content, new Insets(40));
        StackPane.setMargin(takeOfferStatus, new Insets(-TakeOfferView.TOP_PANE_HEIGHT, 0, 0, 0));
        root.getChildren().addAll(content, takeOfferStatus);
    }

    @Override
    protected void onViewAttached() {
        TextFlowUtils.updateTextFlow(price, model.getPrice());
        priceDetails.setText(model.getPriceDetails());

        fiatPaymentMethod.setText(model.getFiatPaymentMethod());
        bitcoinPaymentMethod.setText(model.getBitcoinPaymentMethod());

        feeDetails.setVisible(model.isFeeDetailsVisible());
        feeDetails.setManaged(model.isFeeDetailsVisible());

        fee.setText(model.getFee());
        feeDetails.setText(model.getFeeDetails());

        takeOfferSuccessButton.setOnAction(e -> controller.onShowOpenTrades());

        takeOfferStatusPin = EasyBind.subscribe(model.getTakeOfferStatus(), this::showTakeOfferStatusFeedback);
    }


    @Override
    protected void onViewDetached() {
        takeOfferSuccessButton.setOnAction(null);
        takeOfferStatusPin.unsubscribe();
        takeOfferSendMessageWaitingAnimation.stop();
    }

    private void showTakeOfferStatusFeedback(TakeOfferReviewModel.TakeOfferStatus status) {
        if (status == TakeOfferReviewModel.TakeOfferStatus.SENT) {
            takeOfferStatus.getChildren().setAll(sendTakeOfferMessageFeedback, Spacer.fillVBox());
            takeOfferStatus.setVisible(true);

            Transitions.blurStrong(content, 0);
            Transitions.slideInTop(takeOfferStatus, 450);
            takeOfferSendMessageWaitingAnimation.playIndefinitely();

            UIScheduler.run(() -> {
                minWaitingTimePassed = true;
                if (model.getTakeOfferStatus().get() == TakeOfferReviewModel.TakeOfferStatus.SUCCESS) {
                    takeOfferStatus.getChildren().setAll(takeOfferSuccess, Spacer.fillVBox());
                    takeOfferSendMessageWaitingAnimation.stop();
                }
            }).after(DevMode.isDevMode() ? 500 : 4000);
        } else if (status == TakeOfferReviewModel.TakeOfferStatus.SUCCESS && minWaitingTimePassed) {
            takeOfferStatus.getChildren().setAll(takeOfferSuccess, Spacer.fillVBox());
            takeOfferSendMessageWaitingAnimation.stop();
        } else if (status == TakeOfferReviewModel.TakeOfferStatus.NOT_STARTED) {
            takeOfferStatus.getChildren().clear();
            takeOfferStatus.setVisible(false);
            Transitions.removeEffect(content);
        }
    }

    private void configSendTakeOfferMessageFeedback() {
        VBox contentBox = getFeedbackContentBox();

        sendTakeOfferMessageFeedback.setAlignment(Pos.TOP_CENTER);

        Label headlineLabel = new Label(Res.get("bisqEasy.takeOffer.review.sendTakeOfferMessageFeedback.headline"));
        headlineLabel.getStyleClass().add("trade-wizard-take-offer-send-message-headline");
        HBox title = new HBox(10, takeOfferSendMessageWaitingAnimation, headlineLabel);
        title.setAlignment(Pos.CENTER);

        WrappingText subtitleLabel = new WrappingText(Res.get("bisqEasy.takeOffer.review.sendTakeOfferMessageFeedback.subTitle"),
                "trade-wizard-take-offer-send-message-sub-headline");
        WrappingText info = new WrappingText(Res.get("bisqEasy.takeOffer.review.sendTakeOfferMessageFeedback.info"),
                "trade-wizard-take-offer-send-message-info");
        VBox subtitle = new VBox(10, subtitleLabel, info);
        subtitleLabel.setTextAlignment(TextAlignment.CENTER);
        info.setTextAlignment(TextAlignment.CENTER);
        subtitle.setAlignment(Pos.CENTER);

        contentBox.getChildren().addAll(title, subtitle);
        sendTakeOfferMessageFeedback.getChildren().addAll(contentBox, Spacer.fillVBox());
    }

    private void configTakeOfferSuccess() {
        VBox contentBox = getFeedbackContentBox();

        takeOfferSuccess.setAlignment(Pos.TOP_CENTER);

        Label headlineLabel = new Label(Res.get("bisqEasy.takeOffer.review.takeOfferSuccess.headline"));
        headlineLabel.getStyleClass().add("trade-wizard-take-offer-send-message-headline");

        Label subtitleLabel = new Label(Res.get("bisqEasy.takeOffer.review.takeOfferSuccess.subTitle"));
        configFeedbackSubtitleLabel(subtitleLabel);

        takeOfferSuccessButton.setDefaultButton(true);
        VBox.setMargin(takeOfferSuccessButton, new Insets(10, 0, 0, 0));
        contentBox.getChildren().addAll(headlineLabel, subtitleLabel, takeOfferSuccessButton);
        takeOfferSuccess.getChildren().addAll(contentBox, Spacer.fillVBox());
    }

    private VBox getFeedbackContentBox() {
        VBox contentBox = new VBox(20);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.getStyleClass().setAll("trade-wizard-feedback-bg");
        contentBox.setPadding(new Insets(30));
        contentBox.setMaxWidth(FEEDBACK_WIDTH);
        return contentBox;
    }

    private void configFeedbackSubtitleLabel(Label subtitleLabel) {
        subtitleLabel.setTextAlignment(TextAlignment.CENTER);
        subtitleLabel.setAlignment(Pos.CENTER);
        subtitleLabel.setMinWidth(FEEDBACK_WIDTH - 150);
        subtitleLabel.setMaxWidth(subtitleLabel.getMinWidth());
        subtitleLabel.setMinHeight(100);
        subtitleLabel.setWrapText(true);
        subtitleLabel.getStyleClass().add("trade-wizard-take-offer-send-message-sub-headline");
    }

    private Region getLine() {
        Region line = new Region();
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setStyle("-fx-background-color: -bisq-border-color-grey");
        line.setPadding(new Insets(9, 0, 8, 0));
        return line;
    }
}
