package parking;

/**
 * Turns one line from the microcontroller into slot states.
 *
 * Accepted formats (1 = occupied, 0 = vacant):
 *   S:1,0,1,0
 *   1010
 *
 * Anything else (wrong length, bad characters, noise) returns null,
 * so the dashboard never treats it as valid data.
 */
public final class FrameParser {

    private FrameParser() {}

    public static boolean[] parse(String line, int expectedSlots) {
        if (line == null) return null;
        String s = line.trim();
        if (s.startsWith("S:") || s.startsWith("s:")) s = s.substring(2);
        s = s.replace(",", "").replace(" ", "");
        if (s.length() != expectedSlots) return null;

        boolean[] result = new boolean[expectedSlots];
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '1') result[i] = true;
            else if (c == '0') result[i] = false;
            else return null;
        }
        return result;
    }
}
