package nl.rarevogel.startrek;

import java.util.*;

/** Plays sensibly, using the same course maths the touch screen uses, and checks it lands. */
public class Bot {
    static class Stop extends RuntimeException {}

    public static void main(String[] args) {
        int games = Integer.parseInt(args[0]);
        int wins = 0, navChecks = 0, navMiss = 0, torChecks = 0, torMiss = 0, quadChecks = 0, quadMiss = 0;
        for (int g = 0; g < games; g++) {
            final StringBuilder out = new StringBuilder();
            final Engine[] eng = new Engine[1];
            final int[] n = {0};
            final ArrayDeque<String> queue = new ArrayDeque<>();
            final double[] expect = new double[4]; // target r,c and kind (1 sector nav, 2 quadrant nav)
            final int[] stats = new int[6];
            final int[] mark = {0};
            Engine.IO io = new Engine.IO() {
                public void print(String s) { out.append(s); }
                public String input(int kind, String[] chips, String prefill) {
                    if (++n[0] > 3000) throw new Stop();
                    Engine e = eng[0];
                    if (kind == Engine.K_ENDED || kind == Engine.K_AYE) throw new Stop();
                    if (kind == Engine.K_YN) return "Y";
                    if (kind != Engine.K_COMMAND) return queue.isEmpty() ? "0" : queue.poll();
                    queue.clear();
                    // verify last nav
                    String since = out.substring(mark[0]);
                    if (since.contains("DAMAGED.") || since.contains("INSUFFICIENT") || since.contains("BAD NAVAGATION") || since.contains("PERIMETER") || since.contains("STARDATE ") && since.contains("END OF")) expect[2] = 0;
                    if (expect[2] == 1) {
                        stats[0]++;
                        if (!(e.S1 == expect[0] && e.S2 == expect[1])) { stats[1]++; if (stats[1] < 4) System.out.println("SECTOR MISS want "+expect[0]+","+expect[1]+" got "+e.S1+","+e.S2+"\n"+since); }
                    } else if (expect[2] == 2) {
                        stats[4]++;
                        if (!(e.Q1 == expect[0] && e.Q2 == expect[1])) { stats[5]++; if (stats[5] < 4) System.out.println("QUAD MISS want "+expect[0]+","+expect[1]+" got "+e.Q1+","+e.Q2+"\n"+since); }
                    }
                    expect[2] = 0;
                    mark[0] = out.length();
                    if (e.D[1] < 0) { queue.add("1"); queue.add(".2"); return "NAV"; }
                    if (e.K3 > 0) {
                        if (e.S < 400 && e.E > 600) { queue.add("400"); return "SHE"; }
                        for (int i = 1; i <= 3; i++) if (e.K[i][3] > 0) {
                            if (e.P > 0 && e.D[5] >= 0 && clearPath(e, e.K[i][1], e.K[i][2])) {
                                double c = Engine.direction(e.S1, e.S2, e.K[i][1], e.K[i][2]);
                                queue.add(String.valueOf(c));
                                stats[2]++;
                                int before = e.K9;
                                // check after: K9 drops
                                return "TOR";
                            }
                        }
                        if (e.D[4] >= 0 && e.E > 300) { queue.add(String.valueOf((int) Math.min(e.E - 200, 600))); return "PHA"; }
                    }
                    if (e.E < 800 || e.P == 0) {
                        if (e.B3 > 0) {   // fly next to the base
                            for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                                int r = e.B4 + dr, c = e.B5 + dc;
                                if (r < 1 || r > 8 || c < 1 || c > 8 || !e.Q[r][c].equals("   ")) continue;
                                if (r == e.S1 && c == e.S2) continue;
                                double crs = Engine.direction(e.S1, e.S2, r, c);
                                int steps = (int) Math.max(Math.abs(r - e.S1), Math.abs(c - e.S2));
                                queue.add(String.valueOf(crs)); queue.add(String.valueOf(steps / 8.0));
                                expect[0] = r; expect[1] = c; expect[2] = 1;
                                return "NAV";
                            }
                        }
                    }
                    // go to the nearest quadrant with Klingons (or a base when low)
                    int bq1 = -1, bq2 = -1; double best = 1e9;
                    boolean wantBase = e.E < 800 || e.P == 0;
                    for (int i = 1; i <= 8; i++) for (int j = 1; j <= 8; j++) {
                        boolean ok = wantBase ? (e.G[i][j] / 10) % 10 > 0 : e.G[i][j] >= 100;
                        if (!ok || (i == e.Q1 && j == e.Q2)) continue;
                        double d = Math.max(Math.abs(i - e.Q1), Math.abs(j - e.Q2));
                        if (d < best) { best = d; bq1 = i; bq2 = j; }
                    }
                    if (bq1 < 0) return "SRS";
                    double crs = Engine.direction(e.Q1, e.Q2, bq1, bq2);
                    queue.add(String.valueOf(crs)); queue.add(String.valueOf(best));
                    expect[0] = bq1; expect[1] = bq2; expect[2] = 2; out.append("[start q"+e.Q1+","+e.Q2+" s"+e.S1+","+e.S2+" K3="+e.K3+"]");
                    return "NAV";
                }
                public void snapshot(Snapshot s) {}
                public void saveState(String st) {}
                public void clearState() {}
                public void showInstructions() {}
                public void gameEnded() {}
                public int columns() { return 40; }
            };
            eng[0] = new Engine(io, null);
            try { eng[0].run(); } catch (Stop s) {}
            if (out.indexOf("CONGRULATION") >= 0) wins++;
            navChecks += stats[0]; navMiss += stats[1]; quadChecks += stats[4]; quadMiss += stats[5];
            torChecks += stats[2];
            if (g == 0 && args.length > 1) System.out.println(out.substring(Math.max(0, out.length() - 6000)));
        }
        System.out.println("games=" + games + " wins=" + wins + " sectorNav=" + navChecks + " miss=" + navMiss
                + " quadNav=" + quadChecks + " miss=" + quadMiss + " torpedoes=" + torChecks);
    }

    static boolean clearPath(Engine e, double tr, double tc) {
        double c = Engine.direction(e.S1, e.S2, tr, tc);
        double[] v = Engine.courseVector(c);
        double x = e.S1, y = e.S2;
        for (int i = 0; i < 12; i++) {
            x = Engine.r6(x + v[0]); y = Engine.r6(y + v[1]);
            int x3 = (int) Math.floor(x + .5), y3 = (int) Math.floor(y + .5);
            if (x3 < 1 || x3 > 8 || y3 < 1 || y3 > 8) return false;
            if (x3 == tr && y3 == tc) return true;
            if (!e.Q[x3][y3].equals("   ")) return false;
        }
        return false;
    }
}
