package nl.rarevogel.startrek;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Random;

/**
 * Super Star Trek (Leedom/Ahl, 1978) — a line-by-line port of the Microsoft BASIC
 * listing published in "BASIC Computer Games". Line numbers from the listing are
 * kept in the comments so the code can be checked against the original.
 *
 * Deliberate changes, all of them known bugs in the listing:
 *  - line 440:  B9 starts at 0 instead of 2 (otherwise the starbase count is off by two)
 *  - line 4410: phaser accuracy depends on the library-computer (D(8)), as the message says
 *  - lines 8310-8450: no division by zero when start and target coincide
 * Plus float hygiene: positions are rounded to 1e-6 while moving, so a course computed
 * exactly lands exactly (the 1978 single-precision BASIC had the same drift problem).
 */
public class Engine implements Runnable {

    public interface IO {
        void print(String s);
        /** Blocks until the player answers. */
        String input(int kind, String[] chips, String prefill);
        void snapshot(Snapshot s);
        void saveState(String state);
        void clearState();
        void showInstructions();
        void gameEnded();
        int columns();
    }

    public static final int K_COMMAND = 0, K_COURSE_NAV = 1, K_COURSE_TOR = 2, K_WARP = 3,
            K_NUMBER = 4, K_COMPUTER = 5, K_YN = 6, K_COORDS = 7, K_AYE = 8, K_ENDED = 9;

    public static final String RESTART_TOKEN = "\u0000RESTART";

    static class Restart extends RuntimeException {}
    static class EndGame extends RuntimeException {}

    private final IO io;
    private final Random rng = new Random();
    private double lastRnd = 0;
    private String resume;

    // ---- game state (names follow the BASIC listing) ----
    int[][] G = new int[10][10], Z = new int[10][10];
    double[][] C = new double[11][3];
    double[][] K = new double[4][4];
    double[] D = new double[10];
    String[][] Q = new String[10][10];
    double T, T0, T9, D0, E, E0, P, P0, S9, S, D4;
    int B9, K9, K3, B3, S3, B4, B5, K7;
    double Q1, Q2, S1, S2;
    String cond = "GREEN";

    static final String[] DEVICES = {"", "WARP ENGINES", "SHORT RANGE SENSORS", "LONG RANGE SENSORS",
            "PHASER CONTROL", "PHOTON TUBES", "DAMAGE CONTROL", "SHIELD CONTROL", "LIBRARY-COMPUTER"};
    static final String[] REGION1 = {"", "ANTARES", "RIGEL", "PROCYON", "VEGA", "CANOPUS", "ALTAIR", "SAGITTARIUS", "POLLUX"};
    static final String[] REGION2 = {"", "SIRIUS", "DENEB", "CAPELLA", "BETELGEUSE", "ALDEBARAN", "REGULUS", "ARCTURUS", "SPICA"};
    static final String[] ROMAN = {"", " I", " II", " III", " IV"};

    public Engine(IO io, String resumeState) {
        this.io = io;
        this.resume = resumeState;
        // 530-600 course vectors
        C[3][1] = -1; C[2][1] = -1; C[4][1] = -1; C[4][2] = -1; C[5][2] = -1; C[6][2] = -1;
        C[1][2] = 1; C[2][2] = 1; C[6][1] = 1; C[7][1] = 1; C[8][1] = 1; C[8][2] = 1; C[9][2] = 1;
    }

    // ------------------------------------------------------------------ helpers
    double rnd() { lastRnd = rng.nextDouble(); return lastRnd; }
    int fnr() { return (int) Math.floor(rnd() * 7.98 + 1.01); }
    static int INT(double x) { return (int) Math.floor(x); }
    static double r6(double x) { return Math.rint(x * 1e6) / 1e6; }

    /** Microsoft BASIC number formatting: leading space (or minus), trailing space. */
    static String pn(double x) {
        return (x < 0 ? "-" : " ") + fmt(Math.abs(x)) + " ";
    }

    static String fmt(double a) {
        if (a == Math.rint(a) && a < 1e9) return Long.toString((long) a);
        String s = new BigDecimal(a).round(new MathContext(7)).stripTrailingZeros().toPlainString();
        if (s.startsWith("0.")) s = s.substring(1);
        return s;
    }

    void p(String s) { io.print(s); }
    void pl(String s) { io.print(s + "\n"); }
    void pl() { io.print("\n"); }

    String inStr(String prompt, int kind, String[] chips) {
        p(prompt + "? ");
        snap();
        String a = io.input(kind, chips, null);
        if (RESTART_TOKEN.equals(a)) throw new Restart();
        pl(a);
        return a.trim().toUpperCase();
    }

    double inNum(String prompt, int kind, String[] chips) {
        return inNums(prompt, kind, chips, 1)[0];
    }

    /** INPUT with numeric variables, including ?REDO FROM START and the ?? continuation. */
    double[] inNums(String prompt, int kind, String[] chips, int count) {
        p(prompt + "? ");
        while (true) {
            snap();
            double[] out = new double[count];
            int got = 0;
            boolean bad = false;
            while (got < count) {
                String a = io.input(kind, chips, null);
                if (RESTART_TOKEN.equals(a)) throw new Restart();
                pl(a);
                String[] parts = a.trim().isEmpty() ? new String[]{""} : a.split(",", -1);
                for (String part : parts) {
                    if (got >= count) break;
                    String t = part.trim();
                    if (t.isEmpty()) { out[got++] = 0; continue; }
                    try { out[got++] = Double.parseDouble(t); } catch (NumberFormatException e) { bad = true; break; }
                }
                if (bad) break;
                if (got < count) p("?? ");
            }
            if (!bad) return out;
            pl("?REDO FROM START");
            p(prompt + "? ");
        }
    }

    String cell(double z1, double z2) {           // 8830
        int r = INT(z1 + .5), c = INT(z2 + .5);
        if (r < 1 || r > 8 || c < 1 || c > 8) return "   ";
        return Q[r][c];
    }

    void put(double z1, double z2, String a) {    // 8670
        int r = INT(z1 - .5) + 1, c = INT(z2 - .5) + 1;
        if (r >= 1 && r <= 8 && c >= 1 && c <= 8) Q[r][c] = a;
    }

    int[] findEmpty() {                            // 8590
        while (true) {
            int r1 = fnr(), r2 = fnr();
            if (cell(r1, r2).equals("   ")) return new int[]{r1, r2};
        }
    }

    double fnd(int i) { return Math.sqrt(Math.pow(K[i][1] - S1, 2) + Math.pow(K[i][2] - S2, 2)); }

    static String quadName(int z4, int z5, boolean regionOnly) {   // 9030
        String g = z5 <= 4 ? REGION1[z4] : REGION2[z4];
        if (!regionOnly) g += ROMAN[(z5 - 1) % 4 + 1];
        return g;
    }

    static String right3(int n) { String s = Integer.toString(n + 1000); return s.substring(s.length() - 3); }

    static String spaces(int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(' '); return b.toString(); }

    boolean narrow(int need) { return io.columns() < need; }

    // ------------------------------------------------------------------ main
    @Override
    public void run() {
        while (true) {
            try {
                if (resume != null && load(resume)) {
                    resume = null;
                    pl();
                    pl("*** MISSION LOG RESTORED ***");
                    srs();
                } else {
                    resume = null;
                    title();
                    newGame();
                }
                mainLoop();
            } catch (Restart r) {
                io.clearState();
                pl();
                pl();
            } catch (EndGame e) {
                io.clearState();
                io.gameEnded();
                p("");
                snap();
                String a = io.input(K_ENDED, null, null);   // wait for "new game"
                pl();
                pl();
            }
        }
    }

    void title() {
        pl();
        int cols = io.columns();
        String[] ship = {
                "                                    ,------*------,",
                "                    ,-------------   '---  ------'",
                "                     '-------- --'      / /",
                "                         ,---' '-------/ /--,",
                "                          '----------------'"};
        int shift = cols >= 52 ? 0 : Math.min(20, 52 - cols + 1);
        for (String s : ship) pl(s.substring(Math.min(shift, 20)));
        pl();
        pl(spaces(Math.max(0, (cols - 30) / 2)) + "THE USS ENTERPRISE --- NCC-1701");
        pl();
        pl();
        String a = inStr("DO YOU NEED INSTRUCTIONS (Y/N)", K_YN, null);
        if (a.startsWith("Y")) io.showInstructions();
        pl();
    }

    void newGame() {
        // 370-440
        T = INT(rnd() * 20 + 20) * 100; T0 = T; T9 = 25 + INT(rnd() * 10); D0 = 0; E = 3000; E0 = E;
        P = 10; P0 = P; S9 = 200; S = 0; B9 = 0; K9 = 0;
        String xs = "", x0s = " IS ";
        // 490
        Q1 = fnr(); Q2 = fnr(); S1 = fnr(); S2 = fnr();
        for (int i = 1; i <= 8; i++) D[i] = 0;
        // 820-1040
        for (int i = 1; i <= 8; i++) for (int j = 1; j <= 8; j++) {
            int k3 = 0; Z[i][j] = 0; double r1 = rnd();
            if (r1 > .98) { k3 = 3; K9 += 3; }
            else if (r1 > .95) { k3 = 2; K9 += 2; }
            else if (r1 > .80) { k3 = 1; K9 += 1; }
            int b3 = 0; if (rnd() > .96) { b3 = 1; B9++; }
            G[i][j] = k3 * 100 + b3 * 10 + fnr();
        }
        if (K9 > T9) T9 = K9 + 1;
        // 1100-1160
        if (B9 == 0) {
            int q1 = (int) Q1, q2 = (int) Q2;
            if (G[q1][q2] < 200) { G[q1][q2] += 120; K9++; }
            B9 = 1; G[q1][q2] += 10; Q1 = fnr(); Q2 = fnr();
        }
        K7 = K9; if (B9 != 1) { xs = "S"; x0s = " ARE "; }
        pl("YOUR ORDERS ARE AS FOLLOWS:");
        pl("     DESTROY THE" + pn(K9) + "KLINGON WARSHIPS WHICH HAVE INVADED");
        pl("   THE GALAXY BEFORE THEY CAN ATTACK FEDERATION HEADQUARTERS");
        pl("   ON STARDATE" + pn(T0 + T9) + "  THIS GIVES YOU" + pn(T9) + "DAYS.  THERE" + x0s);
        pl("  " + pn(B9) + "STARBASE" + xs + " IN THE GALAXY FOR RESUPPLYING YOUR SHIP");
        pl();
        enterQuadrant();
    }

    void enterQuadrant() {                         // 1320
        int q1 = (int) Q1, q2 = (int) Q2;
        K3 = 0; B3 = 0; S3 = 0; D4 = .5 * rnd(); Z[q1][q2] = G[q1][q2];
        String name = quadName(q1, q2, false);
        pl();
        if (T0 == T) {
            pl("YOUR MISSION BEGINS WITH YOUR STARSHIP LOCATED");
            pl("IN THE GALACTIC QUADRANT, '" + name + "'.");
        } else {
            pl("NOW ENTERING " + name + " QUADRANT . . .");
        }
        pl();
        K3 = INT(G[q1][q2] * .01); B3 = INT(G[q1][q2] * .1) - 10 * K3;
        S3 = G[q1][q2] - 100 * K3 - 10 * B3;
        if (K3 != 0) {
            pl("COMBAT AREA      CONDITION RED");
            if (S <= 200) pl("   SHIELDS DANGEROUSLY LOW");
        }
        for (int i = 1; i <= 3; i++) { K[i][1] = 0; K[i][2] = 0; K[i][3] = 0; }
        for (int i = 1; i <= 8; i++) for (int j = 1; j <= 8; j++) Q[i][j] = "   ";
        put(S1, S2, "<*>");
        for (int i = 1; i <= K3; i++) {
            int[] r = findEmpty(); put(r[0], r[1], "+K+");
            K[i][1] = r[0]; K[i][2] = r[1]; K[i][3] = S9 * (0.5 + rnd());
        }
        if (B3 >= 1) { int[] r = findEmpty(); put(r[0], r[1], ">!<"); B4 = r[0]; B5 = r[1]; }
        for (int i = 1; i <= S3; i++) { int[] r = findEmpty(); put(r[0], r[1], " * "); }
        srs();                                     // 1980
    }

    void mainLoop() {
        while (true) {
            // 1990
            if (!(S + E > 10 && (E > 10 || D[7] == 0))) {
                pl();
                pl("** FATAL ERROR **   YOU'VE JUST STRANDED YOUR SHIP IN ");
                pl("SPACE");
                p("YOU HAVE INSUFFICIENT MANEUVERING ENERGY,");
                pl(" AND SHIELD CONTROL");
                p("IS PRESENTLY INCAPABLE OF CROSS");
                pl("-CIRCUITING TO ENGINE ROOM!!");
                end6220();
            }
            io.saveState(save());
            String a = inStr("COMMAND", K_COMMAND, null);
            String c = a.length() >= 3 ? a.substring(0, 3) : a;
            switch (c) {
                case "NAV": nav(); break;
                case "SRS": srs(); break;
                case "LRS": lrs(); break;
                case "PHA": phasers(); break;
                case "TOR": torpedo(); break;
                case "SHE": shields(); break;
                case "DAM": damage(); break;
                case "COM": computer(); break;
                case "XXX": end6270(); break;
                default:
                    pl("ENTER ONE OF THE FOLLOWING:");
                    pl("  NAV  (TO SET COURSE)");
                    pl("  SRS  (FOR SHORT RANGE SENSOR SCAN)");
                    pl("  LRS  (FOR LONG RANGE SENSOR SCAN)");
                    pl("  PHA  (TO FIRE PHASERS)");
                    pl("  TOR  (TO FIRE PHOTON TORPEDOES)");
                    pl("  SHE  (TO RAISE OR LOWER SHIELDS)");
                    pl("  DAM  (FOR DAMAGE CONTROL REPORTS)");
                    pl("  COM  (TO CALL ON LIBRARY-COMPUTER)");
                    pl("  XXX  (TO RESIGN YOUR COMMAND)");
                    pl();
            }
        }
    }

    // ------------------------------------------------------------------ NAV
    void nav() {
        double c1 = inNum("COURSE (0-9)", K_COURSE_NAV, null);   // 2300
        if (c1 == 9) c1 = 1;
        if (!(c1 >= 1 && c1 < 9)) { pl("   LT. SULU REPORTS, 'INCORRECT COURSE DATA, SIR!'"); return; }
        String xs = "8"; if (D[1] < 0) xs = "0.2";
        double w1 = inNum("WARP FACTOR (0-" + xs + ")", K_WARP,
                D[1] < 0 ? new String[]{".1", ".2"} : new String[]{".125", ".5", "1", "2", "4", "8"});
        if (D[1] < 0 && w1 > .2) { pl("WARP ENGINES ARE DAMAGED.  MAXIUM SPEED = WARP 0.2"); return; }
        if (!(w1 > 0 && w1 <= 8)) {
            if (w1 == 0) return;
            p("   CHIEF ENGINEER SCOTT REPORTS 'THE ENGINES WON'T TAKE");
            pl(" WARP " + pn(w1) + "!'");
            return;
        }
        int n = INT(w1 * 8 + .5);                                 // 2490
        if (E - n < 0) {
            pl("ENGINEERING REPORTS   'INSUFFICIENT ENERGY AVAILABLE");
            pl("                       FOR MANEUVERING AT WARP" + pn(w1) + "!'");
            if (S < n - E || D[7] < 0) return;
            pl("DEFLECTOR CONTROL ROOM ACKNOWLEDGES" + pn(S) + "UNITS OF ENERGY");
            pl("                         PRESENTLY DEPLOYED TO SHIELDS.");
            return;
        }
        // 2590 Klingons move / fire on moving starship
        for (int i = 1; i <= K3; i++) {
            if (K[i][3] == 0) continue;
            put(K[i][1], K[i][2], "   ");
            int[] r = findEmpty();
            K[i][1] = r[0]; K[i][2] = r[1]; put(r[0], r[1], "+K+");
        }
        klingonsFire();
        // 2700 repairs while under way
        boolean d1 = false; double d6 = w1; if (w1 >= 1) d6 = 1;
        for (int i = 1; i <= 8; i++) {
            if (D[i] >= 0) continue;
            D[i] += d6;
            if (D[i] > -.1 && D[i] < 0) { D[i] = -.1; continue; }
            if (D[i] < 0) continue;
            if (!d1) { d1 = true; p("DAMAGE CONTROL REPORT:  "); }
            else p(spaces(8));
            pl(DEVICES[i] + " REPAIR COMPLETED.");
        }
        if (rnd() <= .2) {                                        // 2880
            int r1 = fnr();
            if (rnd() < .6) {
                D[r1] -= (rnd() * 5 + 1);
                pl("DAMAGE CONTROL REPORT:  " + DEVICES[r1] + " DAMAGED");
                pl();
            } else {
                D[r1] += rnd() * 3 + 1;
                pl("DAMAGE CONTROL REPORT:  " + DEVICES[r1] + " STATE OF REPAIR IMPROVED");
                pl();
            }
        }
        // 3070 begin moving starship
        put(INT(S1), INT(S2), "   ");
        int ci = INT(c1);
        double x1 = C[ci][1] + (C[ci + 1][1] - C[ci][1]) * (c1 - ci);
        double x2 = C[ci][2] + (C[ci + 1][2] - C[ci][2]) * (c1 - ci);
        double x = S1, y = S2;
        double q4 = Q1, q5 = Q2;
        for (int i = 1; i <= n; i++) {
            S1 = r6(x + i * x1); S2 = r6(y + i * x2);
            if (S1 < 1 || S1 >= 9 || S2 < 1 || S2 >= 9) {         // 3500 exceeded quadrant limits
                x = r6(8 * Q1 + x + n * x1); y = r6(8 * Q2 + y + n * x2);
                Q1 = INT(x / 8); Q2 = INT(y / 8); S1 = INT(x - Q1 * 8); S2 = INT(y - Q2 * 8);
                if (S1 == 0) { Q1--; S1 = 8; }
                if (S2 == 0) { Q2--; S2 = 8; }
                boolean x5 = false;
                if (Q1 < 1) { x5 = true; Q1 = 1; S1 = 1; }
                if (Q1 > 8) { x5 = true; Q1 = 8; S1 = 8; }
                if (Q2 < 1) { x5 = true; Q2 = 1; S2 = 1; }
                if (Q2 > 8) { x5 = true; Q2 = 8; S2 = 8; }
                if (x5) {
                    pl("LT. UHURA REPORTS MESSAGE FROM STARFLEET COMMAND:");
                    pl("  'PERMISSION TO ATTEMPT CROSSING OF GALACTIC PERIMETER");
                    pl("  IS HEREBY *DENIED*.  SHUT DOWN YOUR ENGINES.'");
                    pl("CHIEF ENGINEER SCOTT REPORTS  'WARP ENGINES SHUT DOWN");
                    pl("  AT SECTOR" + pn(S1) + "," + pn(S2) + "OF QUADRANT" + pn(Q1) + "," + pn(Q2) + ".'");
                    if (T > T0 + T9) end6220();
                }
                if (8 * Q1 + Q2 == 8 * q4 + q5) { arrive(w1, n); return; }
                T = T + 1;                                         // 3870
                maneuverEnergy(n);
                enterQuadrant();
                return;
            }
            int r = INT(S1), c = INT(S2);
            if (!Q[r][c].startsWith("  ")) {                       // 3240
                S1 = INT(r6(S1 - x1)); S2 = INT(r6(S2 - x2));
                p("WARP ENGINES SHUT DOWN AT ");
                pl("SECTOR" + pn(S1) + "," + pn(S2) + "DUE TO BAD NAVAGATION");
                arrive(w1, n);
                return;
            }
        }
        S1 = INT(S1); S2 = INT(S2);
        arrive(w1, n);
    }

    void arrive(double w1, int n) {                // 3370
        put(INT(S1), INT(S2), "<*>");
        maneuverEnergy(n);
        double t8 = 1;
        if (w1 < 1) t8 = .1 * INT(10 * w1);
        T = T + t8;
        if (T > T0 + T9) end6220();
        srs();                                     // 1980
    }

    void maneuverEnergy(int n) {                   // 3910
        E = E - n - 10;
        if (E >= 0) return;
        pl("SHIELD CONTROL SUPPLIES ENERGY TO COMPLETE THE MANEUVER.");
        S = S + E; E = 0;
        if (S <= 0) S = 0;
    }

    // ------------------------------------------------------------------ LRS
    void lrs() {                                   // 4000
        if (D[3] < 0) { pl("LONG RANGE SENSORS ARE INOPERABLE"); return; }
        int q1 = (int) Q1, q2 = (int) Q2;
        pl("LONG RANGE SCAN FOR QUADRANT" + pn(q1) + "," + pn(q2));
        String o1 = "-------------------";
        pl(o1);
        for (int i = q1 - 1; i <= q1 + 1; i++) {
            int[] nn = {0, -1, -2, -3};
            for (int j = q2 - 1; j <= q2 + 1; j++) {
                if (i > 0 && i < 9 && j > 0 && j < 9) { nn[j - q2 + 2] = G[i][j]; Z[i][j] = G[i][j]; }
            }
            StringBuilder b = new StringBuilder();
            for (int l = 1; l <= 3; l++) {
                b.append(": ");
                if (nn[l] < 0) b.append("*** ");
                else b.append(right3(nn[l])).append(' ');
            }
            b.append(':');
            pl(b.toString());
            pl(o1);
        }
    }

    // ------------------------------------------------------------------ PHA
    void phasers() {                               // 4260
        if (D[4] < 0) { pl("PHASERS INOPERATIVE"); return; }
        if (K3 <= 0) { spockNoEnemy(); return; }
        if (D[8] < 0) pl("COMPUTER FAILURE HAMPERS ACCURACY");
        p("PHASERS LOCKED ON TARGET;  ");
        double x;
        while (true) {
            pl("ENERGY AVAILABLE =" + pn(E) + "UNITS");
            x = inNum("NUMBER OF UNITS TO FIRE", K_NUMBER, phaserChips());
            if (x <= 0) return;
            if (E - x >= 0) break;
        }
        E = E - x;
        if (D[8] < 0) x = x * rnd();              // 4410 (listing says D(7))
        int h1 = INT(x / K3);
        for (int i = 1; i <= 3; i++) {
            if (K[i][3] <= 0) continue;
            int h = INT((h1 / fnd(i)) * (rnd() + 2));
            if (h <= .15 * K[i][3]) {
                pl("SENSORS SHOW NO DAMAGE TO ENEMY AT " + pn(K[i][1]) + "," + pn(K[i][2]));
                continue;
            }
            K[i][3] = K[i][3] - h;
            p(pn(h) + "UNIT HIT ON KLINGON AT SECTOR" + pn(K[i][1]) + ",");
            pl(pn(K[i][2]));
            if (K[i][3] <= 0) {
                pl("*** KLINGON DESTROYED ***");
                K3--; K9--; put(K[i][1], K[i][2], "   ");
                K[i][3] = 0; G[(int) Q1][(int) Q2] -= 100; Z[(int) Q1][(int) Q2] = G[(int) Q1][(int) Q2];
                if (K9 <= 0) win();
            } else {
                pl("   (SENSORS SHOW" + pn(K[i][3]) + "UNITS REMAINING)");
            }
        }
        klingonsFire();
    }

    String[] phaserChips() {
        int e = (int) E;
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        for (int v : new int[]{100, 200, 300, 500, 1000}) if (v <= e) l.add(Integer.toString(v));
        if (e > 0) l.add(Integer.toString(e));
        return l.toArray(new String[0]);
    }

    void spockNoEnemy() {                          // 4270
        pl("SCIENCE OFFICER SPOCK REPORTS  'SENSORS SHOW NO ENEMY SHIPS");
        pl("                                IN THIS QUADRANT'");
    }

    // ------------------------------------------------------------------ TOR
    void torpedo() {                               // 4700
        if (P <= 0) { pl("ALL PHOTON TORPEDOES EXPENDED"); return; }
        if (D[5] < 0) { pl("PHOTON TUBES ARE NOT OPERATIONAL"); return; }
        while (true) {
            double c1 = inNum("PHOTON TORPEDO COURSE (1-9)", K_COURSE_TOR, null);   // 4760
            if (c1 == 9) c1 = 1;
            if (!(c1 >= 1 && c1 < 9)) { pl("ENSIGN CHEKOV REPORTS,  'INCORRECT COURSE DATA, SIR!'"); return; }
            int ci = INT(c1);
            double x1 = C[ci][1] + (C[ci + 1][1] - C[ci][1]) * (c1 - ci); E = E - 2; P = P - 1;
            double x2 = C[ci][2] + (C[ci + 1][2] - C[ci][2]) * (c1 - ci);
            double x = S1, y = S2, tx = S1, ty = S2;
            int step = 0;
            pl("TORPEDO TRACK:");
            while (true) {
                step++;
                x = r6(tx + step * x1); y = r6(ty + step * x2);
                int x3 = INT(x + .5), y3 = INT(y + .5);
                if (x3 < 1 || x3 > 8 || y3 < 1 || y3 > 8) { pl("TORPEDO MISSED"); klingonsFire(); return; }
                pl("               " + pn(x3) + "," + pn(y3));
                String here = cell(x, y);
                if (here.equals("   ")) continue;
                if (here.equals("+K+")) {
                    pl("*** KLINGON DESTROYED ***"); K3--; K9--;
                    if (K9 <= 0) win();
                    int i;
                    for (i = 1; i <= 3; i++) if (x3 == K[i][1] && y3 == K[i][2]) break;
                    if (i > 3) i = 3;
                    K[i][3] = 0;
                    torpedoDone(x, y);
                    return;
                }
                if (here.equals(" * ")) {
                    pl("STAR AT" + pn(x3) + "," + pn(y3) + "ABSORBED TORPEDO ENERGY.");
                    klingonsFire();
                    return;
                }
                if (here.equals(">!<")) {
                    pl("*** STARBASE DESTROYED ***"); B3--; B9--;
                    if (B9 > 0 || K9 > T - T0 - T9) {
                        pl("STARFLEET COMMAND REVIEWING YOUR RECORD TO CONSIDER");
                        pl("COURT MARTIAL!"); D0 = 0;
                        torpedoDone(x, y);
                        return;
                    }
                    pl("THAT DOES IT, CAPTAIN!!  YOU ARE HEREBY RELIEVED OF COMMAND");
                    pl("AND SENTENCED TO 99 STARDATES AT HARD LABOR ON CYGNUS 12!!");
                    end6270();
                }
                break;   // 5280: anything else sends you back to the course prompt
            }
        }
    }

    void torpedoDone(double x, double y) {         // 5430
        put(x, y, "   ");
        G[(int) Q1][(int) Q2] = K3 * 100 + B3 * 10 + S3; Z[(int) Q1][(int) Q2] = G[(int) Q1][(int) Q2];
        klingonsFire();
    }

    // ------------------------------------------------------------------ SHE
    void shields() {                               // 5530
        if (D[7] < 0) { pl("SHIELD CONTROL INOPERABLE"); return; }
        p("ENERGY AVAILABLE =" + pn(E + S));
        double tot = E + S;
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        l.add("0");
        for (int v : new int[]{200, 500, 1000, 1500}) if (v <= tot) l.add(Integer.toString(v));
        double x = inNum("NUMBER OF UNITS TO SHIELDS", K_NUMBER, l.toArray(new String[0]));
        if (x < 0 || S == x) { pl("<SHIELDS UNCHANGED>"); return; }
        if (x > E + S) {
            pl("SHIELD CONTROL REPORTS  'THIS IS NOT THE FEDERATION TREASURY.'");
            pl("<SHIELDS UNCHANGED>");
            return;
        }
        E = E + S - x; S = x;
        pl("DEFLECTOR CONTROL ROOM REPORT:");
        pl("  'SHIELDS NOW AT" + pn(INT(S)) + "UNITS PER YOUR COMMAND.'");
    }

    // ------------------------------------------------------------------ DAM
    void damage() {                                // 5690
        if (D[6] >= 0) { damageReport(); return; }
        pl("DAMAGE CONTROL REPORT NOT AVAILABLE");
        if (D0 == 0) return;
        repairOffer();
    }

    void repairOffer() {                           // 5720
        double d3 = 0;
        for (int i = 1; i <= 8; i++) if (D[i] < 0) d3 += .1;
        if (d3 == 0) return;
        pl();
        d3 += D4; if (d3 >= 1) d3 = .9;
        pl("TECHNICIANS STANDING BY TO EFFECT REPAIRS TO YOUR SHIP;");
        pl("ESTIMATED TIME TO REPAIR:" + pn(.01 * INT(100 * d3)) + "STARDATES");
        String a = inStr("WILL YOU AUTHORIZE THE REPAIR ORDER (Y/N)", K_YN, null);
        if (!a.equals("Y")) return;
        for (int i = 1; i <= 8; i++) if (D[i] < 0) D[i] = 0;
        T = T + d3 + .1;
        damageReport();
    }

    void damageReport() {                          // 5910
        pl();
        pl("DEVICE             STATE OF REPAIR");
        for (int r1 = 1; r1 <= 8; r1++) {
            String g = DEVICES[r1];
            pl(g + spaces(25 - g.length()) + pn(INT(D[r1] * 100) * .01));
        }
        pl();
        if (D0 != 0) repairOffer();
    }

    // ------------------------------------------------------------------ Klingons shooting
    void klingonsFire() {                          // 6000
        if (K3 <= 0) return;
        if (D0 != 0) { pl("STARBASE SHIELDS PROTECT THE ENTERPRISE"); return; }
        for (int i = 1; i <= 3; i++) {
            if (K[i][3] <= 0) continue;
            int h = INT((K[i][3] / fnd(i)) * (2 + rnd()));
            S = S - h; K[i][3] = K[i][3] / (3 + lastRnd);
            pl(pn(h) + "UNIT HIT ON ENTERPRISE FROM SECTOR" + pn(K[i][1]) + "," + pn(K[i][2]));
            if (S <= 0) destroyed();
            pl("      <SHIELDS DOWN TO" + pn(S) + "UNITS>");
            if (h < 20) continue;
            if (rnd() > .6 || h / S <= .02) continue;
            int r1 = fnr();
            D[r1] = D[r1] - h / S - .5 * rnd();
            pl("DAMAGE CONTROL REPORTS " + DEVICES[r1] + " DAMAGED BY THE HIT'");
        }
    }

    // ------------------------------------------------------------------ end of game
    void end6220() { pl("IT IS STARDATE" + pn(T)); end6270(); }

    void destroyed() {                             // 6240
        S = 0;
        pl();
        p("THE ENTERPRISE HAS BEEN DESTROYED.  THEN FEDERATION ");
        pl("WILL BE CONQUERED");
        end6220();
    }

    void end6270() {
        pl("THERE WERE" + pn(K9) + "KLINGON BATTLE CRUISERS LEFT AT");
        pl("THE END OF YOUR MISSION.");
        end6290();
    }

    void end6290() {
        pl();
        pl();
        snap();
        if (B9 != 0) {
            pl("THE FEDERATION IS IN NEED OF A NEW STARSHIP COMMANDER");
            pl("FOR A SIMILAR MISSION -- IF THERE IS A VOLUNTEER,");
            String a = inStr("LET HIM STEP FORWARD AND ENTER 'AYE'", K_AYE, null);
            if (a.equals("AYE")) { pl(); pl(); throw new Restart(); }
        }
        throw new EndGame();
    }

    void win() {                                   // 6370
        pl("CONGRULATION, CAPTAIN!  THEN LAST KLINGON BATTLE CRUISER");
        pl("MENACING THE FDERATION HAS BEEN DESTROYED.");
        pl();
        pl("YOUR EFFICIENCY RATING IS" + pn(1000 * Math.pow(K7 / (T - T0), 2)));
        end6290();
    }

    // ------------------------------------------------------------------ SRS
    void srs() {                                   // 6430
        boolean docked = false;
        for (double i = S1 - 1; i <= S1 + 1 && !docked; i++)
            for (double j = S2 - 1; j <= S2 + 1; j++) {
                if (INT(i + .5) < 1 || INT(i + .5) > 8 || INT(j + .5) < 1 || INT(j + .5) > 8) continue;
                if (cell(i, j).equals(">!<")) { docked = true; break; }
            }
        if (docked) {
            D0 = 1; cond = "DOCKED"; E = E0; P = P0;
            pl("SHIELDS DROPPED FOR DOCKING PURPOSES"); S = 0;
        } else {
            D0 = 0;
            if (K3 > 0) cond = "*RED*";
            else { cond = "GREEN"; if (E < E0 * .1) cond = "YELLOW"; }
        }
        snap();
        if (D[2] < 0) {
            pl();
            pl("*** SHORT RANGE SENSORS ARE OUT ***");
            pl();
            return;
        }
        String[] st = statusLines();
        String o1 = "---------------------------------";
        boolean side = !narrow(66);
        pl(o1);
        for (int i = 1; i <= 8; i++) {
            StringBuilder b = new StringBuilder();
            for (int j = 1; j <= 8; j++) b.append(' ').append(Q[i][j]);
            if (side) b.append("        ").append(st[i - 1]);
            pl(b.toString());
        }
        pl(o1);
        if (!side) for (String s : st) pl(s);
    }

    String[] statusLines() {
        return new String[]{
                "STARDATE          " + pn(INT(T * 10) * .1),
                "CONDITION          " + cond,
                "QUADRANT          " + pn(Q1) + "," + pn(Q2),
                "SECTOR            " + pn(S1) + "," + pn(S2),
                "PHOTON TORPEDOES  " + pn(INT(P)),
                "TOTAL ENERGY      " + pn(INT(E + S)),
                "SHIELDS           " + pn(INT(S)),
                "KLINGONS REMAINING" + pn(K9)};
    }

    // ------------------------------------------------------------------ COM
    void computer() {                              // 7290
        if (D[8] < 0) { pl("COMPUTER DISABLED"); return; }
        while (true) {
            double a = inNum("COMPUTER ACTIVE AND AWAITING COMMAND", K_COMPUTER, null);
            if (a < 0) return;
            pl();
            int f = INT(a);
            switch (f) {
                case 0: galaxyMap(false); return;
                case 1: statusReport(); return;
                case 2: torpedoData(); return;
                case 3: baseData(); return;
                case 4: ddCalculator(); return;
                case 5: galaxyMap(true); return;
                default:
                    pl("FUNCTIONS AVAILABLE FROM LIBRARY-COMPUTER:");
                    pl("   0 = CUMULATIVE GALACTIC RECORD");
                    pl("   1 = STATUS REPORT");
                    pl("   2 = PHOTON TORPEDO DATA");
                    pl("   3 = STARBASE NAV DATA");
                    pl("   4 = DIRECTION/DISTANCE CALCULATOR");
                    pl("   5 = GALAXY 'REGION NAME' MAP");
                    pl();
            }
        }
    }

    void galaxyMap(boolean names) {                // 7400 / 7540
        boolean compact = narrow(52);
        if (names) {
            pl(spaces(compact ? 11 : 24) + "THE GALAXY");
        } else {
            pl();
            pl((compact ? "" : "        ") + "COMPUTER RECORD OF GALAXY FOR QUADRANT" + pn(Q1) + "," + pn(Q2));
            pl();
        }
        String hdr = compact ? "     1   2   3   4   5   6   7   8"
                : "       1     2     3     4     5     6     7     8";
        String o1 = compact ? "    --- --- --- --- --- --- --- ---"
                : "     ----- ----- ----- ----- ----- ----- ----- -----";
        pl(hdr);
        pl(o1);
        for (int i = 1; i <= 8; i++) {
            StringBuilder b = new StringBuilder(pn(i));
            if (!names) {
                for (int j = 1; j <= 8; j++) {
                    b.append(compact ? " " : "   ");
                    b.append(Z[i][j] == 0 ? "***" : right3(Z[i][j]));
                }
            } else {
                String a = quadName(i, 1, true), c = quadName(i, 5, true);
                if (compact) {
                    int j0 = 3 + Math.max(0, 8 - a.length() / 2) - b.length() + 1;
                    b.append(spaces(Math.max(1, 11 - a.length() / 2 - b.length()))).append(a);
                    b.append(spaces(Math.max(1, 27 - c.length() / 2 - b.length()))).append(c);
                } else {
                    b.append(spaces(Math.max(1, INT(15 - .5 * a.length()) - b.length()))).append(a);
                    b.append(spaces(Math.max(1, INT(39 - .5 * c.length()) - b.length()))).append(c);
                }
            }
            pl(b.toString());
            pl(o1);
        }
        pl();
    }

    void statusReport() {                          // 7900
        pl("   STATUS REPORT:");
        String xs = K9 > 1 ? "S" : "";
        pl("KLINGON" + xs + " LEFT: " + pn(K9));
        pl("MISSION MUST BE COMPLETED IN" + pn(.1 * INT((T0 + T9 - T) * 10)) + "STARDATES");
        if (B9 >= 1) {
            pl("THE FEDERATION IS MAINTAINING" + pn(B9) + "STARBASE" + (B9 < 2 ? "" : "S") + " IN THE GALAXY");
        } else {
            pl("YOUR STUPIDITY HAS LEFT YOU ON YOUR ON IN");
            pl("  THE GALAXY -- YOU HAVE NO STARBASES LEFT!");
        }
        damage();                                  // 7990 GOTO 5690
    }

    void torpedoData() {                           // 8070
        if (K3 <= 0) { spockNoEnemy(); return; }
        pl("FROM ENTERPRISE TO KLINGON BATTLE CRUSER" + (K3 > 1 ? "S" : ""));
        for (int i = 1; i <= 3; i++) {
            if (K[i][3] <= 0) continue;
            ddPrint(S1, S2, K[i][1], K[i][2]);
        }
    }

    void baseData() {                              // 8500
        if (B3 != 0) {
            pl("FROM ENTERPRISE TO STARBASE:");
            ddPrint(S1, S2, B4, B5);
            return;
        }
        p("MR. SPOCK REPORTS,  'SENSORS SHOW NO STARBASES IN THIS");
        pl(" QUADRANT.'");
    }

    void ddCalculator() {                          // 8150
        pl("DIRECTION/DISTANCE CALCULATOR:");
        pl("YOU ARE AT QUADRANT " + pn(Q1) + "," + pn(Q2) + " SECTOR " + pn(S1) + "," + pn(S2));
        pl("PLEASE ENTER");
        double[] a = inNums("  INITIAL COORDINATES (X,Y)", K_COORDS, null, 2);
        double[] b = inNums("  FINAL COORDINATES (X,Y)", K_COORDS, null, 2);
        ddPrint(a[0], a[1], b[0], b[1]);
    }

    void ddPrint(double c1, double a, double w1, double x) {
        double dir = direction(c1, a, w1, x);
        if (!Double.isNaN(dir)) pl("DIRECTION =" + pn(dir));
        pl("DISTANCE =" + pn(Math.sqrt(Math.pow(x - a, 2) + Math.pow(c1 - w1, 2))));
    }

    /** 8220-8450: course from (c1,a) to (w1,x). NaN when both points coincide. */
    public static double direction(double c1, double a, double w1, double x) {
        x = x - a; a = c1 - w1;
        if (x == 0 && a == 0) return Double.NaN;
        double base;
        if (x < 0) {
            if (a > 0) { base = 3; return l8420(base, a, x); }
            base = 5; return l8290(base, a, x);
        }
        if (a < 0) { base = 7; return l8420(base, a, x); }
        return l8290(1, a, x);
    }

    static double l8290(double c1, double a, double x) {
        if (Math.abs(a) <= Math.abs(x)) return c1 + Math.abs(a) / Math.abs(x);
        return c1 + ((Math.abs(a) - Math.abs(x)) + Math.abs(a)) / Math.abs(a);
    }

    static double l8420(double c1, double a, double x) {
        if (Math.abs(a) >= Math.abs(x)) return c1 + Math.abs(x) / Math.abs(a);
        return c1 + ((Math.abs(x) - Math.abs(a)) + Math.abs(x)) / Math.abs(x);
    }

    /** Course vector used by NAV/TOR (row delta, column delta). */
    public static double[] courseVector(double c1) {
        if (c1 == 9) c1 = 1;
        double[][] c = new double[11][3];
        c[3][1] = -1; c[2][1] = -1; c[4][1] = -1; c[4][2] = -1; c[5][2] = -1; c[6][2] = -1;
        c[1][2] = 1; c[2][2] = 1; c[6][1] = 1; c[7][1] = 1; c[8][1] = 1; c[8][2] = 1; c[9][2] = 1;
        int ci = INT(c1);
        if (ci < 1 || ci > 8) return null;
        return new double[]{c[ci][1] + (c[ci + 1][1] - c[ci][1]) * (c1 - ci),
                c[ci][2] + (c[ci + 1][2] - c[ci][2]) * (c1 - ci)};
    }

    // ------------------------------------------------------------------ snapshot / save
    void snap() {
        Snapshot s = new Snapshot();
        for (int i = 1; i <= 8; i++) for (int j = 1; j <= 8; j++) {
            s.cells[i - 1][j - 1] = Q[i][j] == null ? "   " : Q[i][j];
            s.z[i - 1][j - 1] = Z[i][j];
        }
        s.s1 = S1; s.s2 = S2; s.q1 = (int) Q1; s.q2 = (int) Q2;
        s.stardate = INT(T * 10) * .1; s.cond = cond; s.torps = INT(P); s.energy = INT(E + S);
        s.shields = INT(S); s.klingons = K9; s.srsOk = D[2] >= 0; s.computerOk = D[8] >= 0;
        s.daysLeft = .1 * INT((T0 + T9 - T) * 10);
        for (int i = 1; i <= 8; i++) s.damage[i - 1] = D[i];
        io.snapshot(s);
    }

    String save() {
        StringBuilder b = new StringBuilder("v1\n");
        StringBuilder g = new StringBuilder(), z = new StringBuilder(), q = new StringBuilder();
        for (int i = 1; i <= 8; i++) for (int j = 1; j <= 8; j++) {
            g.append(G[i][j]).append(','); z.append(Z[i][j]).append(',');
            q.append(Q[i][j] == null ? "   " : Q[i][j]);
        }
        b.append(g).append('\n').append(z).append('\n').append(q).append('\n');
        StringBuilder k = new StringBuilder();
        for (int i = 1; i <= 3; i++) for (int j = 1; j <= 3; j++) k.append(K[i][j]).append(',');
        b.append(k).append('\n');
        StringBuilder d = new StringBuilder();
        for (int i = 1; i <= 8; i++) d.append(D[i]).append(',');
        b.append(d).append('\n');
        double[] v = {T, T0, T9, D0, E, E0, P, P0, S9, S, D4, B9, K9, K3, B3, S3, B4, B5, K7, Q1, Q2, S1, S2};
        for (double x : v) b.append(x).append(',');
        b.append('\n').append(cond).append('\n');
        return b.toString();
    }

    boolean load(String st) {
        try {
            String[] l = st.split("\n");
            if (!l[0].equals("v1")) return false;
            String[] g = l[1].split(","), z = l[2].split(",");
            String q = l[3];
            int n = 0;
            for (int i = 1; i <= 8; i++) for (int j = 1; j <= 8; j++) {
                G[i][j] = Integer.parseInt(g[n]); Z[i][j] = Integer.parseInt(z[n]);
                Q[i][j] = q.substring(n * 3, n * 3 + 3); n++;
            }
            String[] k = l[4].split(","); n = 0;
            for (int i = 1; i <= 3; i++) for (int j = 1; j <= 3; j++) K[i][j] = Double.parseDouble(k[n++]);
            String[] d = l[5].split(",");
            for (int i = 1; i <= 8; i++) D[i] = Double.parseDouble(d[i - 1]);
            String[] v = l[6].split(",");
            double[] x = new double[v.length];
            for (int i = 0; i < v.length; i++) x[i] = Double.parseDouble(v[i]);
            T = x[0]; T0 = x[1]; T9 = x[2]; D0 = x[3]; E = x[4]; E0 = x[5]; P = x[6]; P0 = x[7]; S9 = x[8]; S = x[9];
            D4 = x[10]; B9 = (int) x[11]; K9 = (int) x[12]; K3 = (int) x[13]; B3 = (int) x[14]; S3 = (int) x[15];
            B4 = (int) x[16]; B5 = (int) x[17]; K7 = (int) x[18]; Q1 = x[19]; Q2 = x[20]; S1 = x[21]; S2 = x[22];
            cond = l.length > 7 ? l[7] : "GREEN";
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
