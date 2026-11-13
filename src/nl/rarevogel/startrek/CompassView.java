package nl.rarevogel.startrek;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * The course rose from the instructions (4 3 2 / 5 -*- 1 / 6 7 8). Dragging sets a course;
 * the needle follows the game's own course vectors, so what you see is where you go.
 */
public class CompassView extends View {

    public interface Listener { void onCourse(double course); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private double course = Double.NaN;
    private Listener listener;

    public CompassView(Context c, Typeface tf) {
        super(c);
        dp = getResources().getDisplayMetrics().density;
        p.setTypeface(tf);
    }

    public void setListener(Listener l) { listener = l; }
    public void setCourse(double c) { course = c; invalidate(); }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 4 * dp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * dp);
        p.setColor(PanelView.FAINT);
        cv.drawCircle(cx, cy, r * .72f, p);
        // ticks at every .5
        for (int i = 0; i < 16; i++) {
            double c = 1 + i * .5;
            double[] v = unit(c);
            float in = (i % 2 == 0) ? .60f : .66f;
            p.setColor(i % 2 == 0 ? PanelView.DIM : PanelView.FAINT);
            cv.drawLine(cx + (float) v[1] * r * in, cy + (float) v[0] * r * in,
                    cx + (float) v[1] * r * .72f, cy + (float) v[0] * r * .72f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(r * .30f);
        for (int i = 1; i <= 8; i++) {
            double a = (i - 1) * Math.PI / 4;
            float x = cx + (float) Math.cos(a) * r * .87f, y = cy - (float) Math.sin(a) * r * .87f;
            p.setColor(PanelView.GREEN);
            cv.drawText(Integer.toString(i), x, y + p.getTextSize() * .35f, p);
        }
        if (!Double.isNaN(course)) {
            double[] v = unit(course);
            p.setColor(PanelView.AMBER);
            p.setStrokeWidth(3 * dp);
            cv.drawLine(cx, cy, cx + (float) v[1] * r * .66f, cy + (float) v[0] * r * .66f, p);
            cv.drawCircle(cx + (float) v[1] * r * .66f, cy + (float) v[0] * r * .66f, 4 * dp, p);
        }
        p.setColor(PanelView.WHITE);
        p.setTextSize(r * .22f);
        cv.drawText("<*>", cx, cy + p.getTextSize() * .33f, p);
    }

    /** Course vector scaled to unit length (row, column). */
    static double[] unit(double c) {
        double[] v = Engine.courseVector(c >= 9 ? c - 8 : c);
        if (v == null) return new double[]{0, 0};
        double l = Math.hypot(v[0], v[1]);
        return new double[]{v[0] / l, v[1] / l};
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
        if (e.getAction() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        if (Math.hypot(dx, dy) < 6 * dp) return true;
        double c = Engine.direction(0, 0, dy, dx);
        if (Double.isNaN(c)) return true;
        c = Math.round(c * 20) / 20.0;           // steps of 0.05
        if (c >= 9) c = 1;
        if (c != course) {
            course = c;
            if (e.getAction() != MotionEvent.ACTION_DOWN) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onCourse(c);
            invalidate();
        }
        return true;
    }
}
