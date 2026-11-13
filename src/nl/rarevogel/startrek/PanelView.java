package nl.rarevogel.startrek;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** The bridge display: short range scan with status beside it, or the cumulative galaxy record. */
public class PanelView extends View {

    public interface Listener {
        void onSectorTap(int r, int c);
        void onQuadrantTap(int r, int c);
    }

    static final int GREEN = 0xFF33FF66, DIM = 0xFF1C7A3A, FAINT = 0xFF0F3D1E, AMBER = 0xFFFFB000,
            RED = 0xFFFF4D4D, CYAN = 0xFF55D6FF, STAR = 0xFFE0E070, WHITE = 0xFFFFFFFF, YELLOW = 0xFFFFE04D;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private Snapshot snap;
    private Listener listener;
    private boolean galaxyTab = false;
    private boolean aimable = false;
    private double preview = Double.NaN;
    private int markR = -1, markC = -1;

    // layout results
    private final RectF grid = new RectF(), tabSrs = new RectF(), tabMap = new RectF();
    private float cell;

    public PanelView(Context c, Typeface tf) {
        super(c);
        dp = getResources().getDisplayMetrics().density;
        text.setTypeface(tf);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1 * dp);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(2 * dp);
        dash.setColor(AMBER);
        dash.setPathEffect(new DashPathEffect(new float[]{6 * dp, 4 * dp}, 0));
    }

    public void setListener(Listener l) { listener = l; }
    public void setSnapshot(Snapshot s) { snap = s; invalidate(); }
    public void setAimable(boolean a) { aimable = a; if (!a) { preview = Double.NaN; markR = -1; } invalidate(); }
    public void setPreview(double course) { preview = course; invalidate(); }
    public void setMark(int r, int c) { markR = r; markC = c; invalidate(); }
    public boolean isGalaxyTab() { return galaxyTab; }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        cv.drawColor(0xFF000000);
        float tabH = 30 * dp;
        // tabs
        text.setTextSize(20 * dp);
        tabSrs.set(4 * dp, 2 * dp, w / 2 - 2 * dp, tabH);
        tabMap.set(w / 2 + 2 * dp, 2 * dp, w - 4 * dp, tabH);
        drawTab(cv, tabSrs, "SHORT RANGE SCAN", !galaxyTab);
        drawTab(cv, tabMap, "GALAXY RECORD", galaxyTab);
        float top = tabH + 6 * dp;
        float avail = h - top - 4 * dp;
        if (snap == null) return;
        if (!galaxyTab) drawSrs(cv, w, top, avail);
        else drawGalaxy(cv, w, top, avail);
        if (aimable) {
            text.setTextSize(15 * dp);
            text.setColor(AMBER);
            text.setTextAlign(Paint.Align.RIGHT);
            String hint = snap.computerOk ? "TIK OM TE RICHTEN" : "COMPUTER DEFECT";
            cv.drawText(hint, w - 6 * dp, h - 4 * dp, text);
            text.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawTab(Canvas cv, RectF r, String label, boolean on) {
        fill.setColor(on ? 0xFF0B2914 : 0xFF000000);
        cv.drawRoundRect(r, 4 * dp, 4 * dp, fill);
        line.setColor(on ? GREEN : FAINT);
        cv.drawRoundRect(r, 4 * dp, 4 * dp, line);
        text.setColor(on ? GREEN : DIM);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.min(18 * dp, r.width() / label.length() * 1.75f));
        cv.drawText(label, r.centerX(), r.centerY() + text.getTextSize() * .32f, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSrs(Canvas cv, float w, float top, float avail) {
        float labelW = 14 * dp;
        float size = Math.min(avail - labelW, (w - labelW) * 0.60f);
        cell = size / 8f;
        float gx = 4 * dp + labelW, gy = top + labelW;
        grid.set(gx, gy, gx + size, gy + size);

        // coordinates
        text.setTextSize(Math.min(14 * dp, cell * .55f));
        text.setColor(DIM);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 8; i++) {
            cv.drawText(Integer.toString(i + 1), gx + cell * (i + .5f), gy - 3 * dp, text);
            cv.drawText(Integer.toString(i + 1), gx - labelW / 2, gy + cell * (i + .5f) + text.getTextSize() * .35f, text);
        }
        line.setColor(FAINT);
        cv.drawRect(grid, line);
        for (int i = 1; i < 8; i++) {
            for (int j = 1; j < 8; j++) {
                fill.setColor(FAINT);
                cv.drawCircle(gx + cell * j, gy + cell * i, 1.2f * dp, fill);
            }
        }
        if (!snap.srsOk) {
            text.setColor(RED);
            text.setTextSize(cell * .5f);
            cv.drawText("*** SHORT RANGE", grid.centerX(), grid.centerY() - cell * .3f, text);
            cv.drawText("SENSORS ARE OUT ***", grid.centerX(), grid.centerY() + cell * .5f, text);
        } else {
            if (markR >= 1) {
                fill.setColor(0x55FFB000);
                cv.drawRect(gx + cell * (markC - 1), gy + cell * (markR - 1), gx + cell * markC, gy + cell * markR, fill);
            }
            if (!Double.isNaN(preview)) drawPreview(cv);
            text.setTextSize(cell * .62f);
            for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) {
                String s = snap.cells[i][j];
                if (s == null || s.equals("   ")) continue;
                int col = GREEN;
                if (s.equals("+K+")) col = RED;
                else if (s.equals(">!<")) col = CYAN;
                else if (s.equals(" * ")) col = STAR;
                else if (s.equals("<*>")) col = WHITE;
                text.setColor(col);
                cv.drawText(s.trim().isEmpty() ? s : (s.equals(" * ") ? "*" : s),
                        gx + cell * (j + .5f), gy + cell * (i + .5f) + text.getTextSize() * .33f, text);
            }
        }
        text.setTextAlign(Paint.Align.LEFT);

        // status column
        float sx = grid.right + 10 * dp, sw = w - sx - 4 * dp;
        String[] labels = {"STARDATE", "CONDITION", "QUADRANT", "SECTOR", "PHOTON TORPEDOES", "TOTAL ENERGY", "SHIELDS", "KLINGONS REMAINING"};
        String[] vals = {
                Engine.fmt(snap.stardate), snap.cond, snap.q1 + " , " + snap.q2,
                Engine.fmt(snap.s1) + " , " + Engine.fmt(snap.s2), Integer.toString(snap.torps),
                Integer.toString(snap.energy), Integer.toString(snap.shields), Integer.toString(snap.klingons)};
        float rowH = (grid.bottom - top) / 8f;
        float ls = Math.min(rowH * .38f, sw / 17f * 1.9f), vs = Math.min(rowH * .56f, 24 * dp);
        for (int i = 0; i < 8; i++) {
            float y = top + rowH * i;
            text.setTextSize(ls);
            text.setColor(DIM);
            cv.drawText(labels[i], sx, y + ls, text);
            text.setTextSize(vs);
            int col = GREEN;
            if (i == 1) {
                if (snap.cond.contains("RED")) col = RED;
                else if (snap.cond.equals("YELLOW")) col = YELLOW;
                else if (snap.cond.equals("DOCKED")) col = CYAN;
            }
            if (i == 6 && snap.shields < 200 && snap.cond.contains("RED")) col = RED;
            text.setColor(col);
            cv.drawText(vals[i], sx, y + ls + vs * .95f, text);
        }
    }

    private void drawPreview(Canvas cv) {
        double[] v = Engine.courseVector(preview);
        if (v == null) return;
        double r = snap.s1, c = snap.s2;
        float x0 = grid.left + (float) ((c - .5) * cell), y0 = grid.top + (float) ((r - .5) * cell);
        // extend until it leaves the quadrant
        double k = 0;
        while (k < 20) {
            double rr = r + v[0] * (k + .1), cc = c + v[1] * (k + .1);
            if (rr < .5 || rr > 8.5 || cc < .5 || cc > 8.5) break;
            k += .1;
        }
        float x1 = grid.left + (float) ((c + v[1] * k - .5) * cell), y1 = grid.top + (float) ((r + v[0] * k - .5) * cell);
        cv.drawLine(x0, y0, x1, y1, dash);
    }

    private void drawGalaxy(Canvas cv, float w, float top, float avail) {
        float labelW = 16 * dp;
        float cw = (w - labelW - 8 * dp) / 8f, ch = (avail - labelW) / 8f;
        float gx = 4 * dp + labelW, gy = top + labelW;
        grid.set(gx, gy, gx + cw * 8, gy + ch * 8);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.min(15 * dp, ch * .6f));
        text.setColor(DIM);
        for (int i = 0; i < 8; i++) {
            cv.drawText(Integer.toString(i + 1), gx + cw * (i + .5f), gy - 3 * dp, text);
            cv.drawText(Integer.toString(i + 1), gx - labelW / 2, gy + ch * (i + .5f) + text.getTextSize() * .35f, text);
        }
        line.setColor(FAINT);
        for (int i = 0; i <= 8; i++) {
            cv.drawLine(gx, gy + ch * i, gx + cw * 8, gy + ch * i, line);
            cv.drawLine(gx + cw * i, gy, gx + cw * i, gy + ch * 8, line);
        }
        // region boundaries (columns 1-4 / 5-8)
        line.setColor(DIM);
        cv.drawLine(gx + cw * 4, gy, gx + cw * 4, gy + ch * 8, line);
        if (markR >= 1) {
            fill.setColor(0x55FFB000);
            cv.drawRect(gx + cw * (markC - 1), gy + ch * (markR - 1), gx + cw * markC, gy + ch * markR, fill);
        }
        float ts = Math.min(ch * .62f, cw / 3f * 1.85f);
        for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) {
            int z = snap.z[i][j];
            boolean here = (i + 1 == snap.q1 && j + 1 == snap.q2);
            float cx = gx + cw * (j + .5f), cy = gy + ch * (i + .5f);
            if (here) {
                fill.setColor(0xFF0B2914);
                cv.drawRect(gx + cw * j + 1, gy + ch * i + 1, gx + cw * (j + 1) - 1, gy + ch * (i + 1) - 1, fill);
                line.setColor(WHITE);
                cv.drawRect(gx + cw * j + 1, gy + ch * i + 1, gx + cw * (j + 1) - 1, gy + ch * (i + 1) - 1, line);
            }
            text.setTextSize(ts);
            float base = cy + ts * .33f;
            if (z == 0) {
                text.setColor(FAINT);
                cv.drawText("***", cx, base, text);
                continue;
            }
            String s = Engine.right3(z);
            // colour each digit: Klingons, starbases, stars
            float cwid = text.measureText("0");
            float x = cx - cwid * 1.5f;
            int[] cols = {s.charAt(0) != '0' ? RED : DIM, s.charAt(1) != '0' ? CYAN : DIM, here ? WHITE : GREEN};
            text.setTextAlign(Paint.Align.LEFT);
            for (int k = 0; k < 3; k++) {
                text.setColor(cols[k]);
                cv.drawText(String.valueOf(s.charAt(k)), x + cwid * k, base, text);
            }
            text.setTextAlign(Paint.Align.CENTER);
        }
        text.setTextAlign(Paint.Align.LEFT);
    }

    public void setGalaxyTab(boolean g) { galaxyTab = g; invalidate(); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float x = e.getX(), y = e.getY();
        if (tabSrs.contains(x, y)) { setGalaxyTab(false); playSoundEffect(0); return true; }
        if (tabMap.contains(x, y)) { setGalaxyTab(true); playSoundEffect(0); return true; }
        if (snap == null || listener == null || !grid.contains(x, y)) return true;
        int c = (int) ((x - grid.left) / (grid.width() / 8f)) + 1;
        int r = (int) ((y - grid.top) / (grid.height() / 8f)) + 1;
        if (r < 1 || r > 8 || c < 1 || c > 8) return true;
        if (galaxyTab) listener.onQuadrantTap(r, c);
        else listener.onSectorTap(r, c);
        return true;
    }
}
