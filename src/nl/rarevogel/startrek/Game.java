package nl.rarevogel.startrek;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Owns the game thread and everything the screen needs to redraw itself after the
 * activity is recreated: the terminal log, the open prompt and the latest snapshot.
 * Lives as long as the process; the log and state are also stored so a killed
 * process resumes at the last COMMAND prompt.
 */
public class Game implements Engine.IO {

    public interface Listener {
        void onText(String s);
        void onLogReset();
        void onPrompt(int kind, String[] chips);
        void onSnapshot(Snapshot s);
        void onInstructions();
    }

    private static Game instance;
    public static Game get(Context c) {
        if (instance == null) instance = new Game(c.getApplicationContext());
        return instance;
    }

    private static final int LOG_MAX = 60000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final LinkedBlockingQueue<String> answers = new LinkedBlockingQueue<>();
    private final StringBuilder log = new StringBuilder();
    private Listener listener;
    private volatile int columns = 40;

    public int promptKind = -1;
    public String[] promptChips;
    public Snapshot snapshot;

    private Game(Context c) {
        prefs = c.getSharedPreferences("game", Context.MODE_PRIVATE);
        String state = prefs.getString("state", null);
        if (state != null) log.append(prefs.getString("log", ""));
        Thread t = new Thread(new Engine(this, state), "engine");
        t.setDaemon(true);
        t.start();
    }

    public void setListener(Listener l) { listener = l; }
    public String getLog() { return log.toString(); }
    public void setColumns(int c) { columns = Math.max(20, c); }

    /** Called on the UI thread when the player answers the open prompt. */
    public void answer(String s) {
        if (promptKind < 0) return;
        promptKind = -1;
        answers.offer(s);
    }

    public void newGame() {
        promptKind = -1;
        log.setLength(0);
        if (listener != null) listener.onLogReset();
        prefs.edit().remove("state").remove("log").apply();
        answers.clear();
        answers.offer(Engine.RESTART_TOKEN);
    }

    // ---------------------------------------------------------------- Engine.IO (game thread)
    @Override public void print(final String s) {
        main.post(new Runnable() { public void run() {
            log.append(s);
            if (log.length() > LOG_MAX) {
                int cut = log.indexOf("\n", log.length() - LOG_MAX * 3 / 4);
                log.delete(0, cut < 0 ? log.length() - LOG_MAX * 3 / 4 : cut + 1);
                if (listener != null) listener.onLogReset();
            } else if (listener != null) listener.onText(s);
        }});
    }

    @Override public String input(final int kind, final String[] chips, String prefill) {
        main.post(new Runnable() { public void run() {
            promptKind = kind;
            promptChips = chips;
            if (listener != null) listener.onPrompt(kind, chips);
        }});
        try {
            return answers.take();
        } catch (InterruptedException e) {
            return Engine.RESTART_TOKEN;
        }
    }

    @Override public void snapshot(final Snapshot s) {
        main.post(new Runnable() { public void run() {
            snapshot = s;
            if (listener != null) listener.onSnapshot(s);
        }});
    }

    @Override public void saveState(final String state) {
        main.post(new Runnable() { public void run() {
            String l = log.toString();
            if (l.length() > 20000) {
                int cut = l.indexOf('\n', l.length() - 20000);
                l = l.substring(cut < 0 ? l.length() - 20000 : cut + 1);
            }
            prefs.edit().putString("state", state).putString("log", l).apply();
        }});
    }

    @Override public void clearState() {
        main.post(new Runnable() { public void run() { prefs.edit().remove("state").remove("log").apply(); }});
    }

    @Override public void showInstructions() {
        main.post(new Runnable() { public void run() { if (listener != null) listener.onInstructions(); }});
    }

    @Override public void gameEnded() {}

    @Override public int columns() { return columns; }
}
