package nl.rarevogel.startrek;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity implements Game.Listener, PanelView.Listener {

    private static final int BG = 0xFF000000;
    private static final int GREEN = PanelView.GREEN, DIM = PanelView.DIM, FAINT = PanelView.FAINT;

    private Game game;
    private Typeface vt;
    private float dp;
    private SharedPreferences prefs;

    private PanelView panel;
    private ScrollView logScroll;
    private TextView logView;
    private EditText entry;
    private FrameLayout controls;
    private FrameLayout overlay;
    private CompassView compass;

    private float logSizePx;
    private boolean atLineStart = true;
    private int shownKind = -2;
    private String pendingWarp;
    private String lastAim;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("ui", MODE_PRIVATE);
        try { vt = Typeface.createFromAsset(getAssets(), "vt323.ttf"); }
        catch (Exception e) { vt = Typeface.MONOSPACE; }
        try {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        } catch (Throwable ignored) {}

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        col.addView(topBar(), new LinearLayout.LayoutParams(-1, (int) (40 * dp)));

        panel = new PanelView(this, vt);
        panel.setListener(this);
        int sw = getResources().getDisplayMetrics().widthPixels;
        col.addView(panel, new LinearLayout.LayoutParams(-1, (int) Math.min(sw * 0.66f, 330 * dp)));

        View rule = new View(this);
        rule.setBackgroundColor(FAINT);
        col.addView(rule, new LinearLayout.LayoutParams(-1, (int) dp));

        logScroll = new PinchScrollView(this);
        logScroll.setFillViewport(true);
        logView = new TextView(this);
        logView.setTypeface(vt);
        logView.setTextColor(GREEN);
        logView.setPadding((int) (8 * dp), (int) (4 * dp), (int) (8 * dp), (int) (4 * dp));
        logView.setLineSpacing(0, 1f);
        logView.setTextIsSelectable(false);
        logScroll.addView(logView, new ViewGroup.LayoutParams(-1, -2));
        col.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        col.addView(inputBar(), new LinearLayout.LayoutParams(-1, (int) (46 * dp)));

        controls = new FrameLayout(this);
        controls.setPadding((int) (6 * dp), (int) (2 * dp), (int) (6 * dp), (int) (6 * dp));
        col.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        float def = 0;
        logSizePx = prefs.getFloat("logSize", def);
        game = Game.get(this);
        logView.post(new Runnable() { public void run() { fitFont(false); } });
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.setListener(this);
        onLogReset();
        if (game.snapshot != null) panel.setSnapshot(game.snapshot);
        shownKind = -2;
        if (game.promptKind >= 0) onPrompt(game.promptKind, game.promptChips);
    }

    @Override
    protected void onPause() {
        super.onPause();
        game.setListener(null);
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) { overlay.setVisibility(View.GONE); return; }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ building blocks
    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (10 * dp), 0, (int) (4 * dp), 0);
        TextView t = new TextView(this);
        t.setText("SUPER STAR TREK");
        t.setTypeface(vt);
        t.setTextColor(GREEN);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        t.setSingleLine(true);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(smallButton("?", new View.OnClickListener() {
            public void onClick(View v) { showInstructions(); }
        }));
        bar.addView(smallButton("A-", new View.OnClickListener() {
            public void onClick(View v) { zoom(1 / 1.12f); }
        }));
        bar.addView(smallButton("A+", new View.OnClickListener() {
            public void onClick(View v) { zoom(1.12f); }
        }));
        bar.addView(smallButton("NIEUW", new View.OnClickListener() {
            public void onClick(View v) {
                confirm("Nieuw spel beginnen? De huidige missie gaat verloren.", new Runnable() {
                    public void run() { atLineStart = true; game.newGame(); }
                });
            }
        }));
        return bar;
    }

    private View smallButton(String label, View.OnClickListener l) {
        TextView b = button(label, null, 18);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, (int) (32 * dp));
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        b.setMinWidth((int) (36 * dp));
        b.setPadding((int) (6 * dp), 0, (int) (6 * dp), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private View inputBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (8 * dp), (int) (4 * dp), (int) (6 * dp), (int) (4 * dp));
        TextView gt = new TextView(this);
        gt.setText(">");
        gt.setTypeface(vt);
        gt.setTextColor(GREEN);
        gt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        bar.addView(gt);
        entry = new EditText(this);
        entry.setTypeface(vt);
        entry.setTextColor(0xFFFFFFFF);
        entry.setHintTextColor(DIM);
        entry.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        entry.setSingleLine(true);
        entry.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
        entry.setBackground(box(0xFF050F08, FAINT));
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        try { entry.setShowSoftInputOnFocus(false); } catch (Throwable ignored) {}
        entry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (id == EditorInfo.IME_ACTION_DONE || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && e.getAction() == KeyEvent.ACTION_DOWN)) { submit(); return true; }
                return false;
            }
        });
        entry.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { updatePreview(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
        bar.addView(entry, lp);
        TextView kb = button("ABC", null, 20);
        kb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                entry.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(entry, InputMethodManager.SHOW_FORCED);
            }
        });
        bar.addView(kb, new LinearLayout.LayoutParams((int) (56 * dp), -1));
        return bar;
    }

    private GradientDrawable box(int fillC, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillC);
        g.setCornerRadius(5 * dp);
        g.setStroke((int) Math.max(1, dp), stroke);
        return g;
    }

    /** A terminal-style button: green outline, label in VT323, optional small caption. */
    private TextView button(String label, String caption, int size) {
        TextView b = new TextView(this);
        b.setTypeface(vt);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(GREEN);
        if (caption != null) {
            android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder(label + "\n" + caption);
            s.setSpan(new android.text.style.RelativeSizeSpan(.55f), label.length() + 1, s.length(), 0);
            s.setSpan(new android.text.style.ForegroundColorSpan(DIM), label.length() + 1, s.length(), 0);
            b.setText(s);
            b.setLineSpacing(0, .85f);
        } else b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        StateListDrawable st = new StateListDrawable();
        st.addState(new int[]{android.R.attr.state_pressed}, box(0xFF1A5C2E, GREEN));
        st.addState(new int[]{}, box(0xFF061409, DIM));
        b.setBackground(st);
        b.setClickable(true);
        b.setSoundEffectsEnabled(true);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void addCell(LinearLayout row, View v, float weight, int heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (heightDp * dp), weight);
        lp.setMargins((int) (3 * dp), (int) (3 * dp), (int) (3 * dp), (int) (3 * dp));
        row.addView(v, lp);
    }

    // ------------------------------------------------------------------ controls per prompt
    @Override
    public void onPrompt(int kind, String[] chips) {
        if (kind != shownKind || kind == Engine.K_NUMBER || kind == Engine.K_WARP) buildControls(kind, chips);
        shownKind = kind;
        entry.setText("");
        lastAim = null;
        boolean aim = kind == Engine.K_COURSE_NAV || kind == Engine.K_COURSE_TOR;
        panel.setAimable(aim);
        if (kind == Engine.K_WARP && pendingWarp != null) {
            entry.setText(pendingWarp);
            entry.setSelection(entry.length());
        }
        pendingWarp = null;
        if (kind != Engine.K_COURSE_NAV) panel.setMark(-1, -1);
        entry.setHint(hintFor(kind));
        if (!aim && panel.isGalaxyTab() && kind == Engine.K_COMMAND) { /* leave the tab as the player chose */ }
        scrollToEnd();
    }

    private String hintFor(int k) {
        switch (k) {
            case Engine.K_COMMAND: return "COMMAND";
            case Engine.K_COURSE_NAV: return "KOERS 1-9";
            case Engine.K_COURSE_TOR: return "TORPEDOKOERS 1-9";
            case Engine.K_WARP: return "WARP";
            case Engine.K_NUMBER: return "AANTAL";
            case Engine.K_COMPUTER: return "0-5";
            case Engine.K_YN: return "Y/N";
            case Engine.K_COORDS: return "X,Y";
            case Engine.K_AYE: return "AYE";
            default: return "";
        }
    }

    private void buildControls(int kind, String[] chips) {
        controls.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        controls.addView(box, new FrameLayout.LayoutParams(-1, -2));
        compass = null;
        switch (kind) {
            case Engine.K_COMMAND: {
                String[][] cmds = {{"NAV", "koers zetten"}, {"SRS", "korte scan"}, {"LRS", "lange scan"},
                        {"PHA", "phasers"}, {"TOR", "torpedo"}, {"SHE", "schilden"},
                        {"DAM", "schade"}, {"COM", "computer"}, {"XXX", "opgeven"}};
                for (int r = 0; r < 3; r++) {
                    LinearLayout row = row();
                    for (int c = 0; c < 3; c++) {
                        final String cmd = cmds[r * 3 + c][0];
                        TextView b = button(cmd, cmds[r * 3 + c][1], 30);
                        b.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                tick(v);
                                if (cmd.equals("XXX")) {
                                    confirm("Je commando neerleggen? (XXX beëindigt de missie)", new Runnable() {
                                        public void run() { send("XXX"); }
                                    });
                                } else send(cmd);
                            }
                        });
                        addCell(row, b, 1, 64);
                    }
                    box.addView(row);
                }
                break;
            }
            case Engine.K_COURSE_NAV:
            case Engine.K_COURSE_TOR: {
                LinearLayout row = row();
                compass = new CompassView(this, vt);
                compass.setListener(new CompassView.Listener() {
                    public void onCourse(double c) {
                        pendingWarp = null;
                        lastAim = null;
                        panel.setMark(-1, -1);
                        entry.setText(fmtCourse(c));
                        entry.setSelection(entry.length());
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (232 * dp), 1f);
                row.addView(compass, lp);
                View pad = keypad(false, kind == Engine.K_COURSE_TOR ? "VUUR" : "ENTER", "0");
                row.addView(pad, new LinearLayout.LayoutParams(0, -2, 1.25f));
                box.addView(row);
                break;
            }
            case Engine.K_WARP:
            case Engine.K_NUMBER: {
                if (chips != null && chips.length > 0) box.addView(chipRow(chips));
                box.addView(keypadWide(kind == Engine.K_WARP ? "0" : "-1"));
                break;
            }
            case Engine.K_COORDS: {
                box.addView(keypadWide("0,0", true));
                break;
            }
            case Engine.K_COMPUTER: {
                String[][] f = {{"0", "GALACTIC RECORD"}, {"1", "STATUS REPORT"}, {"2", "TORPEDO DATA"},
                        {"3", "STARBASE NAV DATA"}, {"4", "DIR/DIST CALC"}, {"5", "REGION NAME MAP"}};
                for (int r = 0; r < 3; r++) {
                    LinearLayout row = row();
                    for (int c = 0; c < 2; c++) {
                        final String[] fn = f[r * 2 + c];
                        TextView b = button(fn[0] + "  " + fn[1], null, 19);
                        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                        b.setPadding((int) (12 * dp), 0, 0, 0);
                        b.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) { tick(v); send(fn[0]); }
                        });
                        addCell(row, b, 1, 54);
                    }
                    box.addView(row);
                }
                LinearLayout row = row();
                TextView back = button("TERUG", null, 22);
                back.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { tick(v); send("-1"); }
                });
                addCell(row, back, 1, 48);
                box.addView(row);
                break;
            }
            case Engine.K_YN: {
                LinearLayout row = row();
                for (final String s : new String[]{"Y", "N"}) {
                    TextView b = button(s, s.equals("Y") ? "ja" : "nee", 38);
                    b.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) { tick(v); send(s); }
                    });
                    addCell(row, b, 1, 90);
                }
                box.addView(row);
                break;
            }
            case Engine.K_AYE: {
                LinearLayout row = row();
                TextView aye = button("AYE", "nieuwe missie", 38);
                aye.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { tick(v); send("AYE"); }
                });
                TextView no = button("NEE", "stoppen", 38);
                no.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { tick(v); send("NO"); }
                });
                addCell(row, aye, 1, 90);
                addCell(row, no, 1, 90);
                box.addView(row);
                break;
            }
            case Engine.K_ENDED: {
                LinearLayout row = row();
                TextView b = button("NIEUW SPEL", "einde missie", 34);
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { tick(v); atLineStart = true; game.newGame(); }
                });
                addCell(row, b, 1, 90);
                box.addView(row);
                break;
            }
        }
    }

    private View chipRow(String[] chips) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = row();
        for (final String c : chips) {
            TextView b = button(c, null, 24);
            b.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { tick(v); send(c); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, (int) (44 * dp));
            lp.setMargins((int) (3 * dp), (int) (3 * dp), (int) (3 * dp), (int) (3 * dp));
            r.addView(b, lp);
        }
        hs.addView(r);
        return hs;
    }

    /** 3-column keypad beside the compass. */
    private View keypad(boolean comma, String enterLabel, final String cancel) {
        String[][] keys = {{"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}, {".", "0", "DEL"}};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (String[] k : keys) {
            LinearLayout row = row();
            for (String s : k) addCell(row, key(s), 1, 44);
            box.addView(row);
        }
        LinearLayout row = row();
        addCell(row, cancelKey(cancel), 1, 46);
        addCell(row, enterKey(enterLabel), 1.4f, 46);
        box.addView(row);
        return box;
    }

    private View keypadWide(String cancel) { return keypadWide(cancel, false); }

    /** Full-width 4-column keypad for numbers. */
    private View keypadWide(String cancel, boolean comma) {
        String[][] keys = {{"7", "8", "9", "DEL"}, {"4", "5", "6", comma ? "," : "-"}, {"1", "2", "3", "."}};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (String[] k : keys) {
            LinearLayout row = row();
            for (String s : k) addCell(row, key(s), 1, 50);
            box.addView(row);
        }
        LinearLayout row = row();
        addCell(row, cancelKey(cancel), 1, 50);
        addCell(row, key("0"), 1, 50);
        addCell(row, enterKey("ENTER"), 2, 50);
        box.addView(row);
        return box;
    }

    private View key(final String s) {
        TextView b = button(s, null, 30);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tick(v);
                Editable e = entry.getText();
                int pos = entry.getSelectionStart() < 0 ? e.length() : entry.getSelectionStart();
                if (s.equals("DEL")) {
                    if (e.length() > 0) {
                        int p = Math.max(0, Math.min(pos, e.length()));
                        if (p > 0) e.delete(p - 1, p); else e.delete(e.length() - 1, e.length());
                    }
                } else {
                    if (lastAim != null) { e.clear(); lastAim = null; panel.setMark(-1, -1); }
                    e.insert(Math.max(0, Math.min(pos, e.length())), s);
                }
            }
        });
        if (s.equals("DEL")) {
            b.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { entry.setText(""); return true; }
            });
        }
        return b;
    }

    private View enterKey(String label) {
        TextView b = button(label, null, 28);
        b.setTextColor(0xFF000000);
        StateListDrawable st = new StateListDrawable();
        st.addState(new int[]{android.R.attr.state_pressed}, box(0xFF7DFF9E, GREEN));
        st.addState(new int[]{}, box(GREEN, GREEN));
        b.setBackground(st);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { tick(v); submit(); }
        });
        return b;
    }

    private View cancelKey(final String value) {
        TextView b = button("ANNULEER", null, 20);
        b.setTextColor(DIM);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { tick(v); pendingWarp = null; send(value); }
        });
        return b;
    }

    private void tick(View v) { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }

    // ------------------------------------------------------------------ input
    private void submit() {
        String s = entry.getText().toString().trim();
        if (game.promptKind == Engine.K_COURSE_NAV && lastAim == null) pendingWarp = null;
        send(s);
    }

    private void send(String s) {
        if (game.promptKind < 0) return;
        entry.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(entry.getWindowToken(), 0);
        panel.setAimable(false);
        game.answer(s);
    }

    private void updatePreview() {
        int k = game.promptKind;
        if (k != Engine.K_COURSE_NAV && k != Engine.K_COURSE_TOR) return;
        double c = Double.NaN;
        try { c = Double.parseDouble(entry.getText().toString().trim()); } catch (Exception ignored) {}
        if (c == 9) c = 1;
        if (!(c >= 1 && c < 9)) c = Double.NaN;
        panel.setPreview(c);
        if (compass != null) compass.setCourse(c);
    }

    static String fmtCourse(double c) {
        String s = String.format(Locale.US, "%.4f", c);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    @Override
    public void onSectorTap(int r, int c) {
        int k = game.promptKind;
        Snapshot s = game.snapshot;
        if ((k != Engine.K_COURSE_NAV && k != Engine.K_COURSE_TOR) || s == null) return;
        if (!s.computerOk) { toast("LIBRARY-COMPUTER beschadigd: voer de koers zelf in"); return; }
        if (r == s.s1 && c == s.s2) return;
        String key = "s" + r + "," + c;
        if (key.equals(lastAim)) { submit(); return; }      // second tap on the same target
        double crs = Engine.direction(s.s1, s.s2, r, c);
        if (crs >= 9) crs -= 8;
        entry.setText(fmtCourse(crs));
        entry.setSelection(entry.length());
        lastAim = key;
        panel.setMark(r, c);
        if (k == Engine.K_COURSE_NAV) {
            double steps = Math.max(Math.abs(r - s.s1), Math.abs(c - s.s2));
            pendingWarp = Engine.fmt(steps / 8.0).replaceFirst("^\\.", "0.");
        }
        panel.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    @Override
    public void onQuadrantTap(int r, int c) {
        int k = game.promptKind;
        Snapshot s = game.snapshot;
        if (k != Engine.K_COURSE_NAV || s == null) {
            if (k == Engine.K_COURSE_TOR) toast("Torpedo's blijven in dit kwadrant");
            return;
        }
        if (!s.computerOk) { toast("LIBRARY-COMPUTER beschadigd: voer de koers zelf in"); return; }
        if (r == s.q1 && c == s.q2) return;
        String key = "q" + r + "," + c;
        if (key.equals(lastAim)) { submit(); return; }
        double crs = Engine.direction(s.q1, s.q2, r, c);
        if (crs >= 9) crs -= 8;
        entry.setText(fmtCourse(crs));
        entry.setSelection(entry.length());
        lastAim = key;
        panel.setMark(r, c);
        pendingWarp = Engine.fmt(Math.max(Math.abs(r - s.q1), Math.abs(c - s.q2)));
        panel.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ------------------------------------------------------------------ terminal
    @Override
    public void onText(String s) {
        logView.append(reflow(s));
        scrollToEnd();
    }

    @Override
    public void onLogReset() {
        atLineStart = true;
        logView.setText(reflow(game.getLog()));
        scrollToEnd();
    }

    @Override
    public void onSnapshot(Snapshot s) { panel.setSnapshot(s); }

    @Override
    public void onInstructions() { showInstructions(); }

    /** Lines that are wider than the screen lose their deep indentation so they wrap cleanly. */
    private String reflow(String s) {
        int cols = game.columns();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int nl = s.indexOf('\n', i);
            String line = nl < 0 ? s.substring(i) : s.substring(i, nl + 1);
            if (atLineStart && nl >= 0 && line.length() - 1 > cols) {
                int lead = 0;
                while (lead < line.length() && line.charAt(lead) == ' ') lead++;
                if (lead > 4) line = "    " + line.substring(lead);
            }
            out.append(line);
            atLineStart = nl >= 0;
            if (nl < 0) break;
            i = nl + 1;
        }
        return out.toString();
    }

    private void scrollToEnd() {
        logScroll.post(new Runnable() {
            public void run() { logScroll.fullScroll(View.FOCUS_DOWN); entry.requestFocus(); }
        });
    }

    /** Pick a font size so that 40 columns fit, or apply the stored size. */
    private void fitFont(boolean keep) {
        int w = logView.getWidth() - logView.getPaddingLeft() - logView.getPaddingRight();
        if (w <= 0) return;
        Paint p = new Paint();
        p.setTypeface(vt);
        if (logSizePx <= 0) {
            p.setTextSize(100);
            float cw = p.measureText("0000000000") / 10f;
            logSizePx = Math.max(12 * dp, Math.min(30 * dp, 100f * (w / 40f) / cw));
        }
        logView.setTextSize(TypedValue.COMPLEX_UNIT_PX, logSizePx);
        p.setTextSize(logSizePx);
        float cw = p.measureText("0000000000") / 10f;
        game.setColumns((int) (w / cw));
    }

    private void zoom(float f) {
        logSizePx = Math.max(9 * dp, Math.min(40 * dp, logSizePx * f));
        prefs.edit().putFloat("logSize", logSizePx).apply();
        fitFont(true);
        onLogReset();
    }

    private class PinchScrollView extends ScrollView {
        private final ScaleGestureDetector det;
        private float acc = 1;
        PinchScrollView(Context c) {
            super(c);
            det = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) {
                    acc *= d.getScaleFactor();
                    if (acc > 1.08f || acc < 0.92f) {
                        logSizePx = Math.max(9 * dp, Math.min(40 * dp, logSizePx * acc));
                        logView.setTextSize(TypedValue.COMPLEX_UNIT_PX, logSizePx);
                        acc = 1;
                    }
                    return true;
                }
                public void onScaleEnd(ScaleGestureDetector d) {
                    prefs.edit().putFloat("logSize", logSizePx).apply();
                    fitFont(true);
                    onLogReset();
                }
            });
        }
        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            det.onTouchEvent(e);
            if (det.isInProgress() || e.getPointerCount() > 1) return true;
            return super.dispatchTouchEvent(e);
        }
    }

    // ------------------------------------------------------------------ overlay & dialogs
    private void showInstructions() {
        overlay.removeAllViews();
        overlay.setBackgroundColor(0xF2000000);
        overlay.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        TextView t = new TextView(this);
        t.setTypeface(vt);
        t.setTextColor(GREEN);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(11 * dp, logSizePx * .85f));
        t.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (12 * dp));
        t.setText(Instructions.TEXT);
        t.setHorizontallyScrolling(true);
        hs.addView(t);
        sv.addView(hs);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout row = row();
        TextView close = button("SLUITEN", null, 26);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { overlay.setVisibility(View.GONE); }
        });
        addCell(row, close, 1, 52);
        row.setPadding((int) (6 * dp), 0, (int) (6 * dp), (int) (8 * dp));
        col.addView(row);
        overlay.addView(col, new FrameLayout.LayoutParams(-1, -1));
        overlay.setVisibility(View.VISIBLE);
    }

    private void confirm(String msg, final Runnable yes) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage(msg)
                .setPositiveButton("Ja", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { yes.run(); }
                })
                .setNegativeButton("Nee", null)
                .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
