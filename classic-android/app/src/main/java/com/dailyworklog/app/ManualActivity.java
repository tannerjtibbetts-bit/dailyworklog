package com.dailyworklog.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Support and the user manual: contents grouped by topic, a search box, and one page per topic.
 * The manual is assets/manual/manual.json, the same file the new iPhone/Android app shows.
 */
public class ManualActivity extends AppCompatActivity {
    private static final String ASSET = "manual/manual.json";
    private LinearLayout body;
    private ScrollView scroll;
    private static List<JSONObject> cached;

    static void open(Activity from) {
        from.startActivity(new Intent(from, ManualActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        body.setPadding(pad, Ui.dp(this, 24), pad, Ui.dp(this, 40));
        scroll.addView(body);
        setContentView(scroll);
        Ui.applyInsets(scroll);
        String id = getIntent().getStringExtra("section");
        JSONObject s = id == null ? null : byId(id);
        if (s != null) showSection(s, getIntent().getStringExtra("highlight"));
        else if (getIntent().getBooleanExtra("manual", false)) showContents();
        else showSupport();
    }

    // ----- the manual file -----

    /** Sections, leaving out ones only for the new app. */
    private List<JSONObject> sections() {
        if (cached != null) return cached;
        List<JSONObject> out = new ArrayList<>();
        try (InputStream in = getAssets().open(ASSET)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            JSONArray a = new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8)).optJSONArray("sections");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject s = a.optJSONObject(i);
                if (s != null && !s.optBoolean("newAppOnly", false)) out.add(s);
            }
        } catch (Exception e) {
            // An unreadable manual just shows as empty.
        }
        cached = out;
        return out;
    }

    private JSONObject byId(String id) {
        for (JSONObject s : sections()) if (id.equals(s.optString("id"))) return s;
        return null;
    }

    private static List<String> list(JSONObject o, String key) {
        List<String> out = new ArrayList<>();
        JSONArray a = o.optJSONArray(key);
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    /** Everything search looks at in a section's text. */
    private static String bodyText(JSONObject s) {
        StringBuilder b = new StringBuilder();
        JSONArray blocks = s.optJSONArray("blocks");
        for (int i = 0; blocks != null && i < blocks.length(); i++) {
            JSONObject x = blocks.optJSONObject(i);
            if (x == null) continue;
            b.append(x.optString("text")).append(' ').append(x.optString("caption")).append(' ');
            for (String it : list(x, "items")) b.append(it).append(' ');
        }
        return b.toString();
    }

    /**
     * Sections matching every word typed, best first: title words count most, then keywords,
     * then the summary and the text. Same scoring as the new app.
     */
    static List<Object[]> search(List<JSONObject> sections, String query) {
        List<Object[]> hits = new ArrayList<>();
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        if (query.trim().isEmpty()) return hits;
        for (JSONObject s : sections) {
            String title = s.optString("title").toLowerCase(Locale.ROOT);
            String keys = String.join(" ", list(s, "keywords")).toLowerCase(Locale.ROOT);
            String summary = s.optString("summary").toLowerCase(Locale.ROOT);
            String body = bodyText(s);
            String lowerBody = body.toLowerCase(Locale.ROOT);
            int score = 0;
            boolean all = true;
            for (String w : words) {
                int ws = 0;
                if (title.contains(w)) ws += 10;
                if (keys.contains(w)) ws += 6;
                if (summary.contains(w)) ws += 3;
                if (lowerBody.contains(w)) ws += 1;
                if (ws == 0) {
                    all = false;
                    break;
                }
                score += ws;
            }
            if (all) hits.add(new Object[]{s, score, snippet(body, lowerBody, words, s.optString("summary"))});
        }
        hits.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
        return hits;
    }

    private static String snippet(String body, String lowerBody, String[] words, String fallback) {
        for (String w : words) {
            int i = lowerBody.indexOf(w);
            if (i < 0) continue;
            int start = Math.max(0, i - 50), end = Math.min(body.length(), i + w.length() + 70);
            return (start > 0 ? "…" : "") + body.substring(start, end).trim() + (end < body.length() ? "…" : "");
        }
        return fallback;
    }

    // ----- Support page -----

    private void showSupport() {
        title("Support");
        LinearLayout tile = card();
        tile.setOrientation(LinearLayout.VERTICAL);
        TextView t = text("User manual", 20, R.color.navy);
        t.setTypeface(null, Typeface.BOLD);
        tile.addView(t);
        TextView sub = text("Every part of the app: what it does and how to use it, with pictures. Search for what you need.", 15, R.color.grey);
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        tile.addView(sub);
        tile.setOnClickListener(v -> startActivity(new Intent(this, ManualActivity.class).putExtra("manual", true)));
        body.addView(tile, cardLp(Ui.dp(this, 12)));
        TextView credit = text("App created by Tanner Tibbetts", 12, R.color.grey_light);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, Ui.dp(this, 24), 0, 0);
        body.addView(credit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ----- contents and search -----

    private void showContents() {
        title("User manual");
        EditText search = new EditText(this);
        search.setHint("Search the manual (e.g. photos, PIN, sea can)");
        search.setSingleLine(true);
        search.setTextSize(17);
        search.setBackgroundResource(R.drawable.bg_search);
        search.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        body.addView(search, cardLp(Ui.dp(this, 6)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        Runnable render = () -> fillList(list, search.getText().toString().trim());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                render.run();
            }
        });
        render.run();
    }

    private void fillList(LinearLayout list, String query) {
        list.removeAllViews();
        List<JSONObject> all = sections();
        if (all.isEmpty()) {
            list.addView(text("The manual couldn’t be opened.", 17, R.color.grey));
            return;
        }
        if (query.isEmpty()) {
            Map<String, List<JSONObject>> groups = new LinkedHashMap<>();
            for (JSONObject s : all) {
                List<JSONObject> g = groups.get(s.optString("group"));
                if (g == null) groups.put(s.optString("group"), g = new ArrayList<>());
                g.add(s);
            }
            for (Map.Entry<String, List<JSONObject>> g : groups.entrySet()) {
                TextView h = text(g.getKey().toUpperCase(Locale.ROOT), 13, R.color.grey);
                h.setTypeface(null, Typeface.BOLD);
                h.setLetterSpacing(0.06f);
                h.setPadding(Ui.dp(this, 8), Ui.dp(this, 18), 0, Ui.dp(this, 6));
                list.addView(h);
                LinearLayout card = card();
                card.setOrientation(LinearLayout.VERTICAL);
                for (JSONObject s : g.getValue()) entry(card, s, s.optString("summary"), "");
                list.addView(card);
            }
            return;
        }
        List<Object[]> hits = search(all, query);
        if (hits.isEmpty()) {
            TextView none = text("Nothing matches “" + query + "”.\nTry another word, like “photo”, “text” or “report”.", 17, R.color.grey);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, Ui.dp(this, 32), 0, 0);
            list.addView(none);
            return;
        }
        TextView count = text(hits.size() == 1 ? "1 topic found" : hits.size() + " topics found", 14, R.color.grey);
        count.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), 0, Ui.dp(this, 8));
        list.addView(count);
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        for (Object[] h : hits) entry(card, (JSONObject) h[0], (String) h[2], query);
        list.addView(card);
    }

    private void entry(LinearLayout card, JSONObject s, String sub, String query) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
        TextView t = new TextView(this);
        t.setText(marked(s.optString("title"), query));
        t.setTextSize(18);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(getColor(R.color.navy));
        row.addView(t);
        TextView st = new TextView(this);
        st.setText(marked(sub, query));
        st.setTextSize(15);
        st.setMaxLines(3);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        st.setTextColor(getColor(R.color.grey));
        row.addView(st);
        row.setOnClickListener(v -> startActivity(new Intent(this, ManualActivity.class)
                .putExtra("section", s.optString("id")).putExtra("highlight", query)));
        if (card.getChildCount() > 0) {
            View line = new View(this);
            line.setBackgroundColor(getColor(R.color.divider));
            card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        card.addView(row);
    }

    // ----- one topic -----

    private void showSection(JSONObject s, String highlight) {
        String q = highlight == null ? "" : highlight;
        TextView group = text(s.optString("group"), 14, R.color.grey);
        body.addView(group);
        TextView t = new TextView(this);
        t.setText(marked(s.optString("title"), q));
        t.setTextSize(28);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(getColor(R.color.navy));
        body.addView(t);
        if (!s.optString("summary").isEmpty()) {
            TextView sum = text("", 17, R.color.grey);
            sum.setText(marked(s.optString("summary"), q));
            sum.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
            body.addView(sum);
        }
        JSONArray blocks = s.optJSONArray("blocks");
        for (int i = 0; blocks != null && i < blocks.length(); i++) {
            JSONObject b = blocks.optJSONObject(i);
            if (b != null) block(b, q);
        }
        // Back / Next topic
        List<JSONObject> all = sections();
        int at = all.indexOf(s);
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(0, Ui.dp(this, 24), 0, 0);
        if (at > 0) nav.addView(navButton("Back", all.get(at - 1)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        else nav.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        View gap = new View(this);
        nav.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        if (at >= 0 && at < all.size() - 1) nav.addView(navButton("Next", all.get(at + 1)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        else nav.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        body.addView(nav);
    }

    private View navButton(String label, JSONObject to) {
        LinearLayout b = card();
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        boolean next = label.equals("Next");
        b.setGravity(next ? Gravity.END : Gravity.START);
        b.addView(text(label, 12, R.color.grey));
        TextView t = text(to.optString("title"), 15, R.color.deep_blue);
        t.setTypeface(null, Typeface.BOLD);
        t.setMaxLines(2);
        t.setGravity(next ? Gravity.END : Gravity.START);
        b.addView(t);
        b.setOnClickListener(v -> {
            startActivity(new Intent(this, ManualActivity.class).putExtra("section", to.optString("id")));
            finish();
        });
        return b;
    }

    private void block(JSONObject b, String q) {
        String type = b.optString("type", "text");
        switch (type) {
            case "steps": {
                List<String> items = list(b, "items");
                for (int i = 0; i < items.size(); i++) {
                    LinearLayout row = new LinearLayout(this);
                    row.setPadding(0, Ui.dp(this, i == 0 ? 12 : 4), 0, Ui.dp(this, 6));
                    TextView num = new TextView(this);
                    num.setText(String.valueOf(i + 1));
                    num.setTextColor(Color.WHITE);
                    num.setTypeface(null, Typeface.BOLD);
                    num.setGravity(Gravity.CENTER);
                    GradientDrawable circle = new GradientDrawable();
                    circle.setShape(GradientDrawable.OVAL);
                    circle.setColor(getColor(R.color.blue));
                    num.setBackground(circle);
                    row.addView(num, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
                    TextView t = bodyText(items.get(i), q);
                    t.setPadding(Ui.dp(this, 12), Ui.dp(this, 3), 0, 0);
                    row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    body.addView(row);
                }
                break;
            }
            case "bullets":
                for (String it : list(b, "items")) {
                    TextView t = bodyText("•  " + it, q);
                    t.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), 0, 0);
                    body.addView(t);
                }
                break;
            case "tip": {
                TextView t = bodyText("💡  " + b.optString("text"), q);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xFFFFF5D1);
                bg.setStroke(Ui.dp(this, 1), 0xFFEED98C);
                bg.setCornerRadius(Ui.dp(this, 16));
                t.setBackground(bg);
                int p = Ui.dp(this, 14);
                t.setPadding(p, p, p, p);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(this, 14);
                body.addView(t, lp);
                break;
            }
            case "image": {
                Bitmap bmp = image(b.optString("file"));
                if (bmp == null) break;
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bmp);
                iv.setAdjustViewBounds(true);
                iv.setMaxHeight(Ui.dp(this, 520));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(this, 16);
                body.addView(iv, lp);
                if (!b.optString("caption").isEmpty()) {
                    TextView c = text(b.optString("caption"), 14, R.color.grey);
                    c.setGravity(Gravity.CENTER);
                    c.setPadding(0, Ui.dp(this, 6), 0, 0);
                    body.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                break;
            }
            case "see": {
                JSONObject to = byId(b.optString("id"));
                if (to == null) break;
                String label = b.optString("text");
                TextView t = text("→  " + (label.isEmpty() ? to.optString("title") : label), 17, R.color.blue);
                t.setTypeface(null, Typeface.BOLD);
                t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
                t.setBackgroundResource(android.R.drawable.list_selector_background);
                t.setOnClickListener(v -> startActivity(new Intent(this, ManualActivity.class).putExtra("section", to.optString("id"))));
                body.addView(t);
                break;
            }
            default: {
                TextView t = bodyText(b.optString("text"), q);
                t.setPadding(0, Ui.dp(this, 12), 0, 0);
                body.addView(t);
            }
        }
    }

    /** Screenshots, shrunk to the screen width so they don't use much memory. */
    private Bitmap image(String file) {
        if (file.isEmpty() || file.contains("/")) return null;
        try (InputStream in = getAssets().open("manual/" + file)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = getResources().getDisplayMetrics().widthPixels < 900 ? 2 : 1;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    // ----- small helpers -----

    /** Text with the search words marked in yellow. */
    private static CharSequence marked(String text, String query) {
        if (query == null || query.trim().isEmpty()) return text;
        SpannableString s = new SpannableString(text);
        String lower = text.toLowerCase(Locale.ROOT);
        for (String w : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (w.length() < 2) continue;
            int i = lower.indexOf(w);
            while (i >= 0) {
                s.setSpan(new BackgroundColorSpan(0xFFFFE680), i, i + w.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = lower.indexOf(w, i + w.length());
            }
        }
        return s;
    }

    private TextView bodyText(String s, String q) {
        TextView t = new TextView(this);
        t.setText(marked(s, q));
        t.setTextSize(17);
        t.setLineSpacing(0, 1.2f);
        t.setTextColor(getColor(R.color.text));
        return t;
    }

    private void title(String s) {
        TextView t = text(s, 28, R.color.navy);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, 0, 0, Ui.dp(this, 8));
        body.addView(t);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setBackgroundResource(R.drawable.bg_card);
        int p = Ui.dp(this, 16);
        c.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 6));
        return c;
    }

    private LinearLayout.LayoutParams cardLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        return lp;
    }
}
