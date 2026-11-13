package nl.rarevogel.startrek;

/** Read-only copy of the ship's situation, handed from the game thread to the screen. */
public class Snapshot {
    public String[][] cells = new String[8][8];
    public int[][] z = new int[8][8];
    public double s1, s2, stardate, daysLeft;
    public int q1, q2, torps, energy, shields, klingons;
    public String cond = "GREEN";
    public boolean srsOk = true, computerOk = true;
    public double[] damage = new double[8];
}
