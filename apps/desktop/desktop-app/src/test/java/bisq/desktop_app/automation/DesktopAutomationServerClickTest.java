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

package bisq.desktop_app.automation;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopAutomationServerClickTest {
    @BeforeAll
    static void initJavaFxToolkit() {
        new JFXPanel();
    }

    @Test
    void dispatchClickFiresMousePressedReleasedAndClickedForGenericNodes() throws Exception {
        AtomicInteger pressedCounter = new AtomicInteger();
        AtomicInteger releasedCounter = new AtomicInteger();
        AtomicInteger clickedCounter = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            Pane pane = new Pane();
            pane.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> pressedCounter.incrementAndGet());
            pane.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> releasedCounter.incrementAndGet());
            pane.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> clickedCounter.incrementAndGet());

            Group root = new Group(pane);
            new Scene(root, 200, 120);

            assertThat(DesktopAutomationServer.dispatchClick(pane)).isTrue();
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pressedCounter.get()).isEqualTo(1);
        assertThat(releasedCounter.get()).isEqualTo(1);
        assertThat(clickedCounter.get()).isEqualTo(1);
    }
}
