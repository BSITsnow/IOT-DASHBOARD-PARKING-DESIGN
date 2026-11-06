package parking;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-down map of the lot: lane, slots (green/red), and a route from the
 * entrance to the nearest vacant slot. Geometry comes from LotLayout,
 * colors come from dashboard.css.
 */
public class ParkingMap extends Pane {

    private static final double ARROW_LENGTH = 12;
    private static final double ARROW_HALF_WIDTH = 7;

    private final LotLayout layout;
    private final List<SlotView> views = new ArrayList<>();
    private final Polyline route = new Polyline();
    private final Polygon arrow = new Polygon();

    private static final class SlotView {
        Rectangle rect;
        Label status;
        Group car;
    }

    public ParkingMap(LotLayout layout) {
        this.layout = layout;
        getStyleClass().add("parking-map");
        setMinSize(layout.width(), layout.height());
        setPrefSize(layout.width(), layout.height());
        setMaxSize(layout.width(), layout.height());

        Rectangle lane = new Rectangle(layout.laneX(), layout.laneY(), layout.laneW(), layout.laneH());
        lane.getStyleClass().add("lane");
        Line center = new Line(layout.laneX(), layout.laneMidY(),
                layout.laneX() + layout.laneW(), layout.laneMidY());
        center.getStyleClass().add("lane-line");
        getChildren().addAll(lane, center);

        for (LotLayout.Slot s : layout.slots()) {
            SlotView v = new SlotView();

            v.rect = new Rectangle(s.x(), s.y(), s.w(), s.h());
            v.rect.setArcWidth(12);
            v.rect.setArcHeight(12);
            v.rect.getStyleClass().add("slot-rect");

            double nameY = s.facingDown() ? s.y() + 6 : s.y() + s.h() - 30;
            double statusY = s.facingDown() ? s.y() + s.h() - 24 : s.y() + 6;

            Label name = label(s.id(), s, nameY, "slot-name");
            v.status = label("VACANT", s, statusY, "slot-status");

            Rectangle body = new Rectangle(s.cx() - 25, s.y() + 28, 50, 52);
            body.setArcWidth(16);
            body.setArcHeight(16);
            body.getStyleClass().add("car-body");
            double glassY = s.facingDown() ? s.y() + 36 : s.y() + 58;
            Rectangle glass = new Rectangle(s.cx() - 17, glassY, 34, 14);
            glass.setArcWidth(6);
            glass.setArcHeight(6);
            glass.getStyleClass().add("car-glass");
            v.car = new Group(body, glass);

            getChildren().addAll(v.rect, v.car, name, v.status);
            views.add(v);
        }

        route.getStyleClass().add("route");
        arrow.getStyleClass().add("route-arrow");
        route.setVisible(false);
        arrow.setVisible(false);
        getChildren().addAll(route, arrow);

        Circle you = new Circle(layout.entranceX(), layout.entranceY(), 10);
        you.getStyleClass().add("you-dot");
        getChildren().add(you);
        getChildren().add(textAt("You", layout.entranceX() - 30, layout.entranceY() + 14, 60));
        getChildren().add(textAt("Entrance", layout.entranceX() - 30, layout.laneY() - 20, 60));
        getChildren().add(textAt("Exit", layout.exitX() - 30, layout.exitY() + 14, 60));
    }

    /**
     * Repaints the slots and the route.
     *
     * @return id of the nearest vacant slot, or null when the lot is full
     */
    public String update(boolean[] occupied) {
        LotLayout.Slot best = null;
        double bestLen = Double.MAX_VALUE;

        for (int i = 0; i < views.size(); i++) {
            SlotView v = views.get(i);
            boolean occ = occupied[i];
            v.rect.getStyleClass().removeAll("slot-vacant", "slot-occupied");
            v.rect.getStyleClass().add(occ ? "slot-occupied" : "slot-vacant");
            v.car.setVisible(occ);
            v.status.setText(occ ? "OCCUPIED" : "VACANT");

            if (!occ) {
                LotLayout.Slot s = layout.slots().get(i);
                double len = Math.abs(s.cx() - layout.entranceX())
                        + Math.abs(layout.entranceY() - layout.laneMidY())
                        + Math.abs(s.cy() - layout.laneMidY());
                if (len < bestLen) {
                    bestLen = len;
                    best = s;
                }
            }
        }

        if (best == null) {
            route.setVisible(false);
            arrow.setVisible(false);
            return null;
        }
        drawRoute(best);
        return best.id();
    }

    private void drawRoute(LotLayout.Slot s) {
        double mid = layout.laneMidY();
        boolean slotAboveLane = s.cy() < mid;
        double tipY = s.cy();
        double baseY = slotAboveLane ? tipY + ARROW_LENGTH : tipY - ARROW_LENGTH;

        route.getPoints().clear();
        route.getPoints().addAll(layout.entranceX(), layout.entranceY());
        if (layout.entranceY() != mid) route.getPoints().addAll(layout.entranceX(), mid);
        route.getPoints().addAll(s.cx(), mid, s.cx(), baseY);

        arrow.getPoints().setAll(
                s.cx(), tipY,
                s.cx() - ARROW_HALF_WIDTH, baseY,
                s.cx() + ARROW_HALF_WIDTH, baseY);

        route.setVisible(true);
        arrow.setVisible(true);
        route.toFront();
        arrow.toFront();
    }

    private Label label(String text, LotLayout.Slot s, double y, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        l.setPrefWidth(s.w());
        l.setAlignment(Pos.CENTER);
        l.relocate(s.x(), y);
        return l;
    }

    private Label textAt(String text, double x, double y, double width) {
        Label l = new Label(text);
        l.getStyleClass().add("map-label");
        l.setPrefWidth(width);
        l.setAlignment(Pos.CENTER);
        l.relocate(x, y);
        return l;
    }
}
