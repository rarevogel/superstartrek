import nl.rarevogel.startrek.*;
import java.util.*;

/** Plays random games against the engine to shake out crashes and hangs. */
public class Harness {
    static class Stop extends RuntimeException {}

    public static void main(String[] a) throws Exception {
        int games = Integer.parseInt(a[0]);
        boolean show = a.length > 1;
        int[] cols = {40, 70};
        long inputs = 0; int wins = 0, ends = 0;
        for (int g = 0; g < games; g++) {
            final Random r = new Random(g);
            final StringBuilder out = new StringBuilder();
            final int[] count = {0};
            final int c = cols[g % 2];
            final String[] state = {null};
            final boolean[] ended = {false};
            Engine.IO io = new Engine.IO() {
                public void print(String s) { out.append(s); }
                public String input(int kind, String[] chips, String prefill) {
                    if (++count[0] > 400 || ended[0]) throw new Stop();
                    switch (kind) {
                        case Engine.K_COMMAND: {
                            String[] cmds = {"NAV", "SRS", "LRS", "PHA", "TOR", "SHE", "DAM", "COM", "NAV", "TOR", "PHA", "FOO", ""};
                            if (r.nextInt(300) == 0) return "XXX";
                            return cmds[r.nextInt(cmds.length)];
                        }
                        case Engine.K_COURSE_NAV: case Engine.K_COURSE_TOR:
                            return r.nextInt(20) == 0 ? "abc" : String.format(Locale.US, "%.2f", r.nextDouble() * 9.5);
                        case Engine.K_WARP: return String.format(Locale.US, "%.2f", r.nextDouble() * 3);
                        case Engine.K_NUMBER: return Integer.toString(r.nextInt(1200) - 50);
                        case Engine.K_COMPUTER: return Integer.toString(r.nextInt(8) - 1);
                        case Engine.K_YN: return r.nextBoolean() ? "Y" : "N";
                        case Engine.K_COORDS: return r.nextBoolean() ? (r.nextInt(9) + "," + r.nextInt(9)) : Integer.toString(r.nextInt(9));
                        case Engine.K_AYE: ended[0] = true; return "NO";
                        case Engine.K_ENDED: ended[0] = true; throw new Stop();
                    }
                    return "";
                }
                public void snapshot(Snapshot s) {}
                public void saveState(String st) { state[0] = st; }
                public void clearState() {}
                public void showInstructions() {}
                public void gameEnded() {}
                public int columns() { return c; }
            };
            Engine e = new Engine(io, null);
            try { e.run(); } catch (Stop s) { }
            inputs += count[0];
            if (out.indexOf("CONGRULATION") >= 0) wins++;
            if (out.indexOf("END OF YOUR MISSION") >= 0) ends++;
            // save/restore round trip
            if (state[0] != null) {
                final StringBuilder out2 = new StringBuilder();
                final int[] n2 = {0};
                Engine.IO io2 = new Engine.IO() {
                    public void print(String s) { out2.append(s); }
                    public String input(int k, String[] ch, String p) { if (++n2[0] > 1) throw new Stop(); return "SRS"; }
                    public void snapshot(Snapshot s) {}
                    public void saveState(String st) {}
                    public void clearState() {}
                    public void showInstructions() {}
                    public void gameEnded() {}
                    public int columns() { return 40; }
                };
                try { new Engine(io2, state[0]).run(); } catch (Stop s) {}
                if (out2.indexOf("RESTORED") < 0) { System.out.println("RESTORE FAILED game " + g); System.out.println(state[0]); return; }
            }
            if (show && g == 0) System.out.println(out);
        }
        System.out.println("games=" + games + " inputs=" + inputs + " ended=" + ends + " wins=" + wins);
    }
}
