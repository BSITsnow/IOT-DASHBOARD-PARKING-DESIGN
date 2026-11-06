package parking;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Parking availability dashboard with a lot map and route to the nearest vacant slot.
 *
 * Run examples:
 *   mvn javafx:run                                                   (simulated data)
 *   mvn javafx:run -Djavafx.args="--layout=C:\path\my-lot.txt"       (your own lot design)
 *   mvn javafx:run -Djavafx.args="--port=COM3 --baud=9600"           (real hardware)
 *
 * The number and position of slots come from lot-layout.txt.
 */
public class ParkingDashboard extends Application {

    private static final long STALE_AFTER_SECONDS = 10;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private LotLayout layout;
    private int slotCount;
    private DataSource source;
    private ParkingMap map;

    private final Label totalValue = new Label("0");
    private final Label availableValue = new Label("0");
    private final Label occupiedValue = new Label("0");
    private final Label percentValue = new Label("0%");
    private final ProgressBar occupancyBar = new ProgressBar(0);
    private final Label alertBanner = new Label("FULL CAPACITY: all monitored slots are occupied");
    private final Label routeLabel = new Label();
    private final Label lastUpdateLabel = new Label("Waiting for data...");
    private final Label statusDot = new Label("●");

    private Instant lastValidUpdate;
    private LocalTime lastValidClock;

    @Override
    public void start(Stage stage) throws Exception {
        Map<String, String> args = getParameters().getNamed();
        String layoutFile = args.get("layout");
        layout = LotLayout.load(layoutFile == null ? null : Path.of(layoutFile));
        slotCount = layout.slots().size();

        String port = args.get("port");
        int baud = Integer.parseInt(args.getOrDefault("baud", "9600"));
        source = (port == null)
                ? new SimulatedDataSource(slotCount)
                : new SerialDataSource(port, baud, slotCount);

        map = new ParkingMap(layout);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setPadding(new Insets(24));
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setBottom(buildFooter());

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");

        Scene scene = new Scene(scroll, 960, 900);
        scene.getStylesheets().add(getClass().getResource("/dashboard.css").toExternalForm());
        stage.setTitle("Smart Parking Dashboard");
        stage.setScene(scene);
        stage.show();

        Timeline tick = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshFreshness()));
        tick.setCycleCount(Timeline.INDEFINITE);
        tick.play();

        render(new boolean[slotCount]);      // all vacant until real data arrives
        source.start(frame -> Platform.runLater(() -> onFrame(frame)));
    }

    @Override
    public void stop() {
        if (source != null) source.stop();
    }

    // ---------------------------------------------------------------- layout

    private Node buildHeader() {
        Label title = new Label("Smart Parking Monitor");
        title.getStyleClass().add("title");
        Label subtitle = new Label("IoT prototype · live slot status");
        subtitle.getStyleClass().add("subtitle");
        VBox box = new VBox(2, title, subtitle);
        box.setPadding(new Insets(0, 0, 16, 0));
        return box;
    }

    private Node buildCenter() {
        HBox cards = new HBox(16,
                card("Total Slots", totalValue, "card-neutral"),
                card("Available", availableValue, "card-available"),
                card("Occupied", occupiedValue, "card-occupied"),
                occupancyCard());
        cards.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        alertBanner.getStyleClass().add("alert");
        alertBanner.setMaxWidth(Double.MAX_VALUE);
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);

        Label mapTitle = new Label("Parking Map");
        mapTitle.getStyleClass().add("section-title");
        routeLabel.getStyleClass().add("route-label");

        HBox mapRow = new HBox(map);
        mapRow.setAlignment(Pos.CENTER_LEFT);

        Label legendGreen = new Label("■ Vacant");
        legendGreen.getStyleClass().add("legend-green");
        Label legendRed = new Label("■ Occupied");
        legendRed.getStyleClass().add("legend-red");
        Label legendRoute = new Label("●●● Route to nearest vacant slot");
        legendRoute.getStyleClass().add("legend-route");
        HBox legend = new HBox(16, legendGreen, legendRed, legendRoute);

        VBox center = new VBox(14, cards, alertBanner, mapTitle, routeLabel, mapRow, legend);
        center.setPadding(new Insets(8, 0, 8, 0));
        return center;
    }

    private Node buildFooter() {
        statusDot.getStyleClass().add("dot-wait");
        lastUpdateLabel.getStyleClass().add("footer-text");
        HBox footer = new HBox(8, statusDot, lastUpdateLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 0, 0, 0));
        return footer;
    }

    private VBox card(String caption, Label value, String styleClass) {
        value.getStyleClass().add("card-value");
        Label cap = new Label(caption);
        cap.getStyleClass().add("card-caption");
        VBox box = new VBox(4, cap, value);
        box.getStyleClass().addAll("card", styleClass);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox occupancyCard() {
        VBox box = card("Occupancy", percentValue, "card-neutral");
        occupancyBar.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(occupancyBar);
        return box;
    }

    // ---------------------------------------------------------------- updates

    private void onFrame(boolean[] occupied) {
        lastValidUpdate = Instant.now();
        lastValidClock = LocalTime.now();
        render(occupied);
        refreshFreshness();
    }

    private void render(boolean[] occupied) {
        int occ = 0;
        for (boolean o : occupied) if (o) occ++;
        int available = slotCount - occ;
        double ratio = slotCount == 0 ? 0 : (double) occ / slotCount;

        totalValue.setText(String.valueOf(slotCount));
        availableValue.setText(String.valueOf(available));
        occupiedValue.setText(String.valueOf(occ));
        percentValue.setText(Math.round(ratio * 100) + "%");
        occupancyBar.setProgress(ratio);

        String nearest = map.update(occupied);
        routeLabel.setText(nearest == null
                ? "No vacant slots right now."
                : "Nearest vacant slot: " + nearest);

        boolean full = slotCount > 0 && occ == slotCount;
        alertBanner.setVisible(full);
        alertBanner.setManaged(full);
    }

    private void refreshFreshness() {
        statusDot.getStyleClass().removeAll("dot-ok", "dot-stale", "dot-wait");
        if (lastValidUpdate == null) {
            lastUpdateLabel.setText("Waiting for data...");
            statusDot.getStyleClass().add("dot-wait");
            return;
        }
        long ago = java.time.Duration.between(lastValidUpdate, Instant.now()).getSeconds();
        String when = CLOCK.format(lastValidClock);
        if (ago > STALE_AFTER_SECONDS) {
            lastUpdateLabel.setText("Last valid update: " + when + " (" + ago + "s ago) · no recent data, check the device");
            statusDot.getStyleClass().add("dot-stale");
        } else {
            lastUpdateLabel.setText("Last valid update: " + when + " (" + ago + "s ago)");
            statusDot.getStyleClass().add("dot-ok");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
