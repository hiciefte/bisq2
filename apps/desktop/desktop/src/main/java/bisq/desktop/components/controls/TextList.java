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

package bisq.desktop.components.controls;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;

@Slf4j
public abstract class TextList extends VBox {
    public TextList(String text,
                    @Nullable String style,
                    double gap,
                    double vSpacing,
                    String regex,
                    @Nullable String mark) {
        setFillWidth(true);
        setSpacing(vSpacing);
        List<String> list = List.of(text.split(regex));
        if (list.size() == 1 && list.get(0).equals(text)) {
            Text contentText = new Text(text);
            if (style != null) {
                contentText.getStyleClass().add(style);
            }
            TextFlow content = new TextFlow(contentText);
            getChildren().add(content);
            return;
        }

        int listStartIndex = 1;
        if (!list.get(0).isBlank()) {
            addParagraph(list.get(0), style);
        }

        int lastNonBlankIndex = -1;
        for (int i = list.size() - 1; i >= listStartIndex; i--) {
            if (!list.get(i).isBlank()) {
                lastNonBlankIndex = i;
                break;
            }
        }

        int listIndex = 0;
        for (int i = listStartIndex; i <= lastNonBlankIndex; i++) {
            String item = list.get(i);
            if (item.isBlank()) {
                continue;
            }
            if (i == lastNonBlankIndex) {
                String[] itemAndTail = item.split("\\R\\R+", 2);
                if (addListItem(itemAndTail[0], style, gap, mark, listIndex + 1)) {
                    listIndex++;
                }
                if (itemAndTail.length > 1 && !itemAndTail[1].isBlank()) {
                    addParagraph(itemAndTail[1], style, true);
                }
            } else {
                if (addListItem(item, style, gap, mark, listIndex + 1)) {
                    listIndex++;
                }
            }
        }
    }

    protected String getMark(int index) {
        return index + ". ";
    }

    private boolean addListItem(String item, @Nullable String style, double gap, @Nullable String mark, int index) {
        String textContent = normalizeText(item, false);
        if (textContent.isEmpty()) {
            return false;
        }
        Text contentText = new Text(textContent);
        String markString = mark == null ? getMark(index) : mark;
        Text markText = new Text(markString);
        if (style != null) {
            markText.getStyleClass().add(style);
            contentText.getStyleClass().add(style);
        }
        TextFlow content = new TextFlow(contentText);
        HBox.setHgrow(content, Priority.ALWAYS);
        getChildren().add(new HBox(gap, markText, content));
        return true;
    }

    private void addParagraph(String text, @Nullable String style) {
        addParagraph(text, style, false);
    }

    private void addParagraph(String text, @Nullable String style, boolean addLeadingBlankLine) {
        String content = normalizeText(text, addLeadingBlankLine);
        if (content.isEmpty()) {
            return;
        }
        Text contentText = new Text(content);
        if (style != null) {
            contentText.getStyleClass().add(style);
        }
        TextFlow contentFlow = new TextFlow(contentText);
        getChildren().add(contentFlow);
    }

    private String normalizeText(String text, boolean addLeadingBlankLine) {
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].strip();
        }
        String content = String.join("\n", lines).strip();
        if (addLeadingBlankLine && !content.isEmpty()) {
            return "\n" + content;
        }
        return content;
    }
}
