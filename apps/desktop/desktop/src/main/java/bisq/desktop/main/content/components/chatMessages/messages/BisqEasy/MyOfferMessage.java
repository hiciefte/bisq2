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

package bisq.desktop.main.content.components.chatMessages.messages.BisqEasy;

import bisq.chat.ChatChannel;
import bisq.chat.ChatMessage;
import bisq.desktop.components.containers.Spacer;
import bisq.desktop.components.controls.DropdownMenu;
import bisq.desktop.components.controls.DropdownMenuItem;
import bisq.desktop.main.content.components.chatMessages.ChatMessageListItem;
import bisq.desktop.main.content.components.chatMessages.ChatMessagesListView;
import bisq.desktop.main.content.components.chatMessages.messages.BubbleMessage;
import bisq.i18n.Res;
import de.jensd.fx.fontawesome.AwesomeIcon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class MyOfferMessage extends BubbleMessage {
    private DropdownMenuItem removeOffer;
    private Label copyIcon;

    public MyOfferMessage(ChatMessageListItem<? extends ChatMessage, ? extends ChatChannel<? extends ChatMessage>> item,
                          ListView<ChatMessageListItem<? extends ChatMessage, ? extends ChatChannel<? extends ChatMessage>>> list,
                          ChatMessagesListView.Controller controller, ChatMessagesListView.Model model) {
        super(item, list, controller, model);

        // Message
        message.setAlignment(Pos.CENTER_RIGHT);
        message.maxWidthProperty().bind(list.widthProperty().subtract(160));
        VBox messageVBox = new VBox(message);
        HBox.setMargin(messageVBox, new Insets(0, -10, 0, 0));

        // User profile icon
        userProfileIcon.setSize(60);
        userProfileIconVbox.setAlignment(Pos.CENTER_LEFT);
        HBox.setMargin(userProfileIconVbox, new Insets(0, 0, 10, 0));

        // Dropdown menu
        DropdownMenu dropdownMenu = createAndGetDropdownMenu();

        // Message background
        HBox hBox = new HBox(15, messageVBox, userProfileIconVbox);
        HBox dropdownMenuHBox = new HBox(Spacer.fillHBox(), dropdownMenu);
        VBox vBox = new VBox(hBox, dropdownMenuHBox);
        messageBgHBox.getChildren().setAll(vBox);
        messageBgHBox.getStyleClass().add("chat-message-bg-my-message");
        messageHBox.getChildren().setAll(Spacer.fillHBox(), messageBgHBox);

        // Reactions
        reactionsHBox.getChildren().setAll(Spacer.fillHBox(), supportedLanguages, copyIcon);
        reactionsHBox.setAlignment(Pos.CENTER_RIGHT);

        getChildren().setAll(userNameAndDateHBox, messageHBox, reactionsHBox);
    }

    @Override
    protected void setUpUserNameAndDateTime() {
        super.setUpUserNameAndDateTime();

        userNameAndDateHBox = new HBox(10, dateTime, userName);
        userNameAndDateHBox.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(userNameAndDateHBox, new Insets(-5, 10, -5, 0));
    }

    @Override
    protected void setUpReactions() {
        copyIcon = getIconWithToolTip(AwesomeIcon.COPY, Res.get("action.copyToClipboard"));
        reactionsHBox.setVisible(false);
    }

    @Override
    protected void addReactionsHandlers() {
        copyIcon.setOnMouseClicked(e -> onCopyMessage(item.getChatMessage()));
    }

    private DropdownMenu createAndGetDropdownMenu() {
        removeOffer = new DropdownMenuItem("delete-bin-red-lit-10", "delete-bin-red",
                Res.get("offer.deleteOffer"));
        removeOffer.setOnAction(e -> controller.onDeleteMessage(item.getChatMessage()));
        removeOffer.getStyleClass().add("red-menu-item");

        DropdownMenu dropdownMenu = new DropdownMenu("ellipsis-h-grey", "ellipsis-h-white", true);
        dropdownMenu.setVisible(item.isPublicChannel());
        dropdownMenu.setManaged(item.isPublicChannel());
        dropdownMenu.setTooltip(Res.get("chat.dropdownMenu.tooltip"));
        dropdownMenu.addMenuItems(removeOffer);
        return dropdownMenu;
    }

    @Override
    public void cleanup() {
        removeOffer.setOnAction(null);
    }
}