package bisq.desktop.main.content.bisq_easy.offerbook;

import bisq.common.currency.Market;
import bisq.common.currency.MarketRepository;
import bisq.desktop.common.utils.ImageUtil;
import bisq.desktop.components.controls.BisqTooltip;
import bisq.i18n.Res;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringExpression;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Comparator;
import java.util.List;

public class BisqEasyOfferbookUtil {
    private static final List<Market> majorMarkets = MarketRepository.getMajorMarkets();

    public static Comparator<MarketChannelItem> sortByNumOffers() {
        return (lhs, rhs) -> Integer.compare(rhs.getNumOffers().get(), lhs.getNumOffers().get());
    }

    public static Comparator<MarketChannelItem> sortByMajorMarkets() {
        return (lhs, rhs) -> {
            int index1 = majorMarkets.indexOf(lhs.getMarket());
            int index2 = majorMarkets.indexOf(rhs.getMarket());
            return Integer.compare(index1, index2);
        };
    }

    public static Comparator<MarketChannelItem> sortByMarketNameAsc() {
        return Comparator.comparing(MarketChannelItem::toString);
    }

    public static Comparator<MarketChannelItem> sortByMarketNameDesc() {
        return Comparator.comparing(MarketChannelItem::toString).reversed();
    }

    public static Comparator<MarketChannelItem> sortByMarketActivity() {
        return (lhs, rhs) -> BisqEasyOfferbookUtil.sortByNumOffers()
                .thenComparing(BisqEasyOfferbookUtil.sortByMajorMarkets())
                .thenComparing(BisqEasyOfferbookUtil.sortByMarketNameAsc())
                .compare(lhs, rhs);
    }

    public static Callback<TableColumn<MarketChannelItem, MarketChannelItem>,
            TableCell<MarketChannelItem, MarketChannelItem>> getMarketLabelCellFactory() {
        return column -> new TableCell<>() {
            private final Label marketName = new Label();
            private final Label marketCode = new Label();
            private final Label numOffers = new Label();
            private final ImageView star = ImageUtil.getImageViewById("star-white");
            private final HBox hBox = new HBox(10, marketCode, numOffers);
            private final VBox vBox = new VBox(0, marketName, hBox);
            private final HBox container = new HBox(10, vBox, star);
            private final Tooltip tooltip = new BisqTooltip();
            private Subscription selectedPin;

            {
                setCursor(Cursor.HAND);
                marketName.getStyleClass().add("market-name");
                hBox.setAlignment(Pos.CENTER_LEFT);
                vBox.setAlignment(Pos.CENTER_LEFT);
                Tooltip.install(vBox, tooltip);

                star.prefWidth(Region.USE_COMPUTED_SIZE);
            }

            @Override
            protected void updateItem(MarketChannelItem item, boolean empty) {
                super.updateItem(item, empty);

                // Clean up previous row
                if (getTableRow() != null && selectedPin != null) {
                    selectedPin.unsubscribe();
                }

                if (item != null && !empty) {
                    marketName.setText(item.getMarket().getQuoteCurrencyName());
                    marketCode.setText(item.getMarket().getQuoteCurrencyCode());
                    StringExpression formattedNumOffers = Bindings.createStringBinding(() ->
                            BisqEasyOfferbookUtil.getFormattedOfferNumber(item.getNumOffers().get()), item.getNumOffers());
                    numOffers.textProperty().bind(formattedNumOffers);
                    StringExpression formattedTooltip = Bindings.createStringBinding(() ->
                            BisqEasyOfferbookUtil.getFormattedTooltip(item.getNumOffers().get(), item.getMarket().getQuoteCurrencyName()), item.getNumOffers());
                    tooltip.textProperty().bind(formattedTooltip);
                    tooltip.setStyle("-fx-text-fill: -fx-dark-text-color;");

                    // Set up new row
                    TableRow<MarketChannelItem> newRow = getTableRow();
                    if (newRow != null) {
                        selectedPin = EasyBind.subscribe(newRow.selectedProperty(), item::updateMarketLogoEffect);
                    }

                    star.setOnMouseClicked(e -> item.addAsFavourite());

                    setGraphic(vBox);
                } else {
                    numOffers.textProperty().unbind();
                    tooltip.textProperty().unbind();
                    star.setOnMouseClicked(null);

                    setGraphic(null);
                }
            }
        };
    }

    public static Callback<TableColumn<MarketChannelItem, MarketChannelItem>,
            TableCell<MarketChannelItem, MarketChannelItem>> getMarketLogoCellFactory() {
        return column -> new TableCell<>() {
            {
                setCursor(Cursor.HAND);
            }

            @Override
            protected void updateItem(MarketChannelItem item, boolean empty) {
                super.updateItem(item, empty);

                setGraphic(item != null && !empty ? item.getMarketLogo() : null);
            }
        };
    }

    private static String getFormattedOfferNumber(int numOffers) {
        if (numOffers == 0) {
            return "";
        }
        return String.format("(%s)",
                numOffers > 1
                        ? Res.get("bisqEasy.offerbook.marketListCell.numOffers.many", numOffers)
                        : Res.get("bisqEasy.offerbook.marketListCell.numOffers.one", numOffers)
        );
    }

    private static String getFormattedTooltip(int numOffers, String quoteCurrencyName) {
        if (numOffers == 0) {
            return Res.get("bisqEasy.offerbook.marketListCell.numOffers.tooltip.none", quoteCurrencyName);
        }
        return numOffers > 1
                ? Res.get("bisqEasy.offerbook.marketListCell.numOffers.tooltip.many", numOffers, quoteCurrencyName)
                : Res.get("bisqEasy.offerbook.marketListCell.numOffers.tooltip.one", numOffers, quoteCurrencyName);
    }
}
