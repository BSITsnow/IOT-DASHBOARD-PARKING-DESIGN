package parking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Geometry of the parking lot, read from a plain text file so it can be redesigned without code. */
public record LotLayout(double width, double height,
                        double laneX, double laneY, double laneW, double laneH,
                        double entranceX, double entranceY,
                        double exitX, double exitY,
                        List<Slot> slots) {

    /** One parking slot. facingDown = the slot sits above the lane and opens toward it. */
    public record Slot(String id, double x, double y, double w, double h, boolean facingDown) {
        public double cx() { return x + w / 2; }
        public double cy() { return y + h / 2; }
    }

    public double laneMidY() {
        return laneY + laneH / 2;
    }

    /** Loads the bundled layout when file is null, otherwise the given file. */
    public static LotLayout load(Path file) throws IOException {
        if (file != null) {
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return parse(r);
            }
        }
        try (InputStream in = LotLayout.class.getResourceAsStream("/lot-layout.txt")) {
            if (in == null) throw new IOException("lot-layout.txt not found in resources");
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    static LotLayout parse(BufferedReader reader) throws IOException {
        double w = 680, h = 410;
        double[] lane = {20, 170, 640, 100};
        double[] entrance = {52, 220};
        double[] exit = {632, 220};
        List<Slot> slots = new ArrayList<>();

        String line;
        int n = 0;
        while ((line = reader.readLine()) != null) {
            n++;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            try {
                switch (p[0].toLowerCase()) {
                    case "canvas" -> { w = num(p, 1); h = num(p, 2); }
                    case "lane" -> lane = new double[]{num(p, 1), num(p, 2), num(p, 3), num(p, 4)};
                    case "entrance" -> entrance = new double[]{num(p, 1), num(p, 2)};
                    case "exit" -> exit = new double[]{num(p, 1), num(p, 2)};
                    case "slot" -> {
                        String dir = p[6].toLowerCase();
                        if (!dir.equals("up") && !dir.equals("down")) {
                            throw new IllegalArgumentException("direction must be up or down");
                        }
                        slots.add(new Slot(p[1], num(p, 2), num(p, 3), num(p, 4), num(p, 5), dir.equals("down")));
                    }
                    default -> throw new IllegalArgumentException("unknown keyword '" + p[0] + "'");
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("lot layout, line " + n + ": " + e.getMessage(), e);
            }
        }
        if (slots.isEmpty()) throw new IllegalArgumentException("lot layout has no slot lines");
        return new LotLayout(w, h, lane[0], lane[1], lane[2], lane[3],
                entrance[0], entrance[1], exit[0], exit[1], List.copyOf(slots));
    }

    private static double num(String[] p, int i) {
        return Double.parseDouble(p[i]);
    }
}
