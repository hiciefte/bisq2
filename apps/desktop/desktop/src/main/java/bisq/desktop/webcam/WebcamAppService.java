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

package bisq.desktop.webcam;

import bisq.application.ApplicationService;
import bisq.common.logging.LogSetup;
import bisq.common.observable.Observable;
import bisq.common.observable.Pin;
import bisq.common.timer.Scheduler;
import bisq.common.util.NetworkUtils;
import ch.qos.logback.classic.Level;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static bisq.desktop.webcam.WebcamAppService.State.*;
import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class WebcamAppService {
    private static final int SOCKET_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(10);
    private static final long STARTUP_TIME_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    private static final long CHECK_HEART_BEAT_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private static final long HEART_BEAT_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    @Getter
    private final Observable<State> state = new Observable<>();
    private Pin qrCodePin, isShutdownSignalReceivedPin;

    public enum State {
        NEW,
        STARTING,
        RUNNING,
        STOPPING,
        TERMINATED
    }

    @Getter
    private final WebcamAppModel model;
    private final QrCodeListeningServer qrCodeListeningServer;
    private final WebcamProcessLauncher webcamProcessLauncher;
    private Optional<Scheduler> checkHeartBeatUpdateScheduler = Optional.empty();
    private Optional<Scheduler> maxStartupTimescheduler = Optional.empty();

    public WebcamAppService(ApplicationService.Config config) {
        model = new WebcamAppModel(config);
        InputHandler inputHandler = new InputHandler(model);
        qrCodeListeningServer = new QrCodeListeningServer(SOCKET_TIMEOUT, inputHandler, this::handleException);
        webcamProcessLauncher = new WebcamProcessLauncher(model.getBaseDir());

        state.set(NEW);
        LogSetup.setLevel(Level.ERROR);
    }

    public void start() {
        checkArgument(isIdle(), "Start call when service is not in idle state");

        model.getLastHeartBeatTimestamp().set(0L);
        model.reset();

        setupTimeoutSchedulers();

        int port = NetworkUtils.selectRandomPort();
        model.setPort(port);

        // Start local tcp server listening for input from qr code scan
        qrCodeListeningServer.start(port);

        state.set(STARTING);
        webcamProcessLauncher.start(port)
                .whenComplete((process, throwable) -> {
                    if (throwable != null) {
                        handleException(throwable);
                    } else {
                        state.set(RUNNING);
                    }
                });
        log.info("We start the webcam application as new Java process and listen for a QR code result. TCP listening port={}", port);

        qrCodePin = model.getQrCode().addObserver(qrCode -> {
            if (qrCode != null) {
                shutdown();
            }
        });
        isShutdownSignalReceivedPin = model.getIsShutdownSignalReceived().addObserver(isShutdownSignalReceived -> {
            if (isShutdownSignalReceived != null) {
                shutdown();
            }
        });
    }

    public CompletableFuture<Boolean> shutdown() {
        log.info("shutdown");
        state.set(STOPPING);
        stopSchedulers();
        unbind();
        qrCodeListeningServer.stopServer();
        return webcamProcessLauncher.shutdown()
                .thenApply(terminatedGraceFully -> {
                    state.set(TERMINATED);
                    model.reset();
                    return terminatedGraceFully;
                });
    }

    private void unbind() {
        if (qrCodePin != null) {
            qrCodePin.unbind();
        }
        if (isShutdownSignalReceivedPin != null) {
            isShutdownSignalReceivedPin.unbind();
        }
    }

    public boolean isIdle() {
        return state.get() == NEW || state.get() == TERMINATED;
    }

    private void stopSchedulers() {
        maxStartupTimescheduler.ifPresent(Scheduler::stop);
        maxStartupTimescheduler = Optional.empty();
        checkHeartBeatUpdateScheduler.ifPresent(Scheduler::stop);
        checkHeartBeatUpdateScheduler = Optional.empty();
    }

    private void handleException(Throwable throwable) {
        //todo map common expections and provide user friendly strings
           /* model.getWebcamAppErrorMessage().set(throwable.getMessage());
            new Popup().error(throwable).show();*/
        shutdown();
    }

    private void setupTimeoutSchedulers() {
        stopSchedulers();

        maxStartupTimescheduler = Optional.of(Scheduler.run(() -> {
            if (model.getLastHeartBeatTimestamp().get() == 0) {
                String errorMessage = "Have not received a heartbeat signal from the webcam app after " + STARTUP_TIME_TIMEOUT / 1000 + " seconds.";
                log.warn(errorMessage);
                model.getLocalException().set(new TimeoutException(errorMessage));
            } else {
                checkHeartBeatUpdateScheduler = Optional.of(Scheduler.run(() -> {
                            long now = System.currentTimeMillis();
                            if (now - model.getLastHeartBeatTimestamp().get() > HEART_BEAT_TIMEOUT) {
                                String errorMessage = "The last reeceived heartbeat signal from the webcam app is older than " + HEART_BEAT_TIMEOUT / 1000 + " seconds.";
                                log.warn(errorMessage);
                                model.getLocalException().set(new TimeoutException(errorMessage));
                            }
                        })
                        .periodically(CHECK_HEART_BEAT_INTERVAL));
            }
        }).after(STARTUP_TIME_TIMEOUT));
    }
}
