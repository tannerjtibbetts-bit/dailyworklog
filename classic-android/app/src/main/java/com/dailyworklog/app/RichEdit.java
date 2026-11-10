package com.dailyworklog.app;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shows formatted notes (bold, underline, colour, and the automatic red "Delay" and highlighted
 * "on hold") and keeps a text box's formatting runs in step with the typing.
 */
final class RichEdit {
    static final int HIGHLIGHT = 0xFF000000 | Rich.HIGHLIGHT;
    private static final int ACTIVE = 0xFF174A8B, IDLE = 0xFFDAE6F4, INK = 0xFF000000 | Rich.BLACK;
    private static final String[] COLOUR_NAMES = {"Black", "Yellow", "Orange", "Red"};

    /** The text with its formatting as spans, for a TextView or a PDF layout. */
    static CharSequence spanned(String text, List<Rich.Run> runs, boolean keywords) {
        SpannableString s = new SpannableString(text);
        for (Rich.Segment seg : Rich.segments(text, runs == null ? Collections.emptyList() : runs, keywords)) {
            addSpans(s, seg);
        }
        return s;
    }

    /** Keywords only (titles and other text without formatting). */
    static CharSequence spanned(String text) {
        return spanned(text, Collections.emptyList(), true);
    }

    /** Field notes as shown: trimmed, keeping the formatting when the saved text is already trimmed. */
    static CharSequence notes(Models.Task t) {
        String n = t.notes.trim();
        return spanned(n, n.equals(t.notes) ? t.notesStyle : Collections.emptyList(), true);
    }

    private static void addSpans(Spanned s, Rich.Segment seg) {
        android.text.Spannable sp = (android.text.Spannable) s;
        Rich.Style st = seg.style;
        int a = seg.start, b = seg.end(), f = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        if (st.bold) sp.setSpan(new StyleSpan(Typeface.BOLD), a, b, f);
        if (st.underline) sp.setSpan(new UnderlineSpan(), a, b, f);
        int rgb = Rich.rgb(st.colour);
        if (rgb >= 0) sp.setSpan(new ForegroundColorSpan(0xFF000000 | rgb), a, b, f);
        if (st.highlight) sp.setSpan(new BackgroundColorSpan(HIGHLIGHT), a, b, f);
    }

    // ----- a text box with formatting -----

    final EditText box;
    final boolean formatting;
    /** User formatting of the box's current text. */
    List<Rich.Run> runs;
    /** Style for what's typed next, set by the toolbar when nothing is selected. */
    Rich.Style typing;

    private String before = "";
    private boolean applying;
    /** Where the cursor lands after the last edit, so that move doesn't count as the person moving it. */
    private int afterEdit = -1, lastStart = -1, lastEnd = -1;
    private final List<Runnable> listeners = new ArrayList<>();

    private RichEdit(EditText box, List<Rich.Run> runs, boolean formatting) {
        this.box = box;
        this.runs = new ArrayList<>(runs);
        this.formatting = formatting;
    }

    /**
     * Shows formatting in the box from now on. savedText is the text the runs belong to (the box may
     * hold slightly different text, e.g. untrimmed after the screen was rebuilt). With formatting
     * false only Delay / on hold are shown (titles).
     */
    static RichEdit attach(EditText box, String savedText, List<Rich.Run> runs, boolean formatting) {
        String now = box.getText().toString();
        List<Rich.Run> r = formatting ? runs : Collections.emptyList();
        if (formatting && !now.equals(savedText)) r = Rich.afterEdit(savedText, now, r, null);
        RichEdit re = new RichEdit(box, r, formatting);
        re.before = now;
        re.lastStart = box.getSelectionStart();
        re.lastEnd = box.getSelectionEnd();
        box.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!re.applying) re.before = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int removed, int added) {
                re.afterEdit = start + added;
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (re.applying) return;
                String text = s.toString();
                if (re.formatting && !text.equals(re.before)) re.runs = Rich.afterEdit(re.before, text, re.runs, re.typing);
                re.before = text;
                re.apply();
                re.changed();
            }
        });
        if (box instanceof RichEditText) ((RichEditText) box).setSelectionListener(re::onSelection);
        re.apply();
        return re;
    }

    private void onSelection(int start, int end) {
        if (start == lastStart && end == lastEnd) return;
        boolean fromEdit = start == end && start == afterEdit;
        lastStart = start;
        lastEnd = end;
        afterEdit = -1;
        if (!fromEdit) typing = null;  // moved the cursor: type in the style of the text there
        changed();
    }

    /** Text the runs belong to. */
    String text() {
        return box.getText().toString();
    }

    /** The runs for the text trimmed, as it's saved. */
    List<Rich.Run> trimmedRuns() {
        String t = text();
        return Rich.afterEdit(t, t.trim(), runs, null);
    }

    void addListener(Runnable r) {
        listeners.add(r);
    }

    private void changed() {
        for (Runnable r : listeners) r.run();
    }

    /** Redraws the formatting: our spans out, fresh ones in (IME composing spans are left alone). */
    void apply() {
        if (applying) return;
        applying = true;
        try {
            Editable e = box.getText();
            for (Class<?> k : new Class<?>[]{StyleSpan.class, UnderlineSpan.class, ForegroundColorSpan.class, BackgroundColorSpan.class}) {
                for (Object o : e.getSpans(0, e.length(), k)) {
                    if ((e.getSpanFlags(o) & Spanned.SPAN_COMPOSING) == 0) e.removeSpan(o);
                }
            }
            for (Rich.Segment seg : Rich.segments(e.toString(), formatting ? runs : Collections.emptyList(), true)) {
                addSpans(e, seg);
            }
        } finally {
            applying = false;
        }
    }

    private int selStart() {
        return Math.max(0, Math.min(box.getSelectionStart(), box.getSelectionEnd()));
    }

    private int selEnd() {
        return Math.max(box.getSelectionStart(), box.getSelectionEnd());
    }

    /** The style that B, U and the colour dots show as on: the selection's, or the typing style. */
    Rich.Style current() {
        String t = text();
        int a = selStart(), b = selEnd();
        if (box.getSelectionStart() < 0) return Rich.Style.PLAIN;
        if (a == b) {
            if (typing != null) return typing;
            return Rich.styleAt(t, runs, a > 0 ? a - 1 : 0);
        }
        return new Rich.Style(Rich.allHave(t, runs, a, b, false), Rich.allHave(t, runs, a, b, true),
                Rich.styleAt(t, runs, a).colour, false);
    }

    void toggle(boolean underline) {
        int a = selStart(), b = selEnd();
        if (box.getSelectionStart() >= 0 && a < b) {
            runs = Rich.applyToggle(text(), runs, a, b, underline);
        } else {
            Rich.Style c = current();
            typing = underline ? c.withUnderline(!c.underline) : c.withBold(!c.bold);
        }
        apply();
        changed();
    }

    void colour(String colour) {
        int a = selStart(), b = selEnd();
        if (box.getSelectionStart() >= 0 && a < b) {
            runs = Rich.applyColour(text(), runs, a, b, colour);
        } else {
            typing = current().withColour(colour);
        }
        apply();
        changed();
    }

    // ----- toolbar -----

    /**
     * B, U and the four colour dots, put directly above row (the line holding the text box).
     * onChange saves after a tap.
     */
    void addToolbarAbove(View row, Runnable onChange) {
        ViewGroup parent = (ViewGroup) row.getParent();
        View bar = toolbar(onChange);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(box.getContext(), 10);
        parent.addView(bar, parent.indexOfChild(row), lp);
    }

    View toolbar(Runnable onChange) {
        Context c = box.getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView bold = toggleButton(c, "B", "Bold");
        bold.setTypeface(Typeface.DEFAULT_BOLD);
        TextView under = toggleButton(c, "U", "Underline");
        under.setPaintFlags(under.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        bold.setOnClickListener(v -> {
            toggle(false);
            if (onChange != null) onChange.run();
        });
        under.setOnClickListener(v -> {
            toggle(true);
            if (onChange != null) onChange.run();
        });
        row.addView(bold);
        row.addView(under);

        List<GradientDrawable> rings = new ArrayList<>();
        int size = Ui.dp(c, 40), dot = Ui.dp(c, 28);
        for (int i = 0; i < Rich.COLOURS.size(); i++) {
            String colour = Rich.COLOURS.get(i);
            FrameLayout f = new FrameLayout(c);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(0x00000000);
            f.setBackground(ring);
            rings.add(ring);
            View d = new View(c);
            GradientDrawable fill = new GradientDrawable();
            fill.setShape(GradientDrawable.OVAL);
            int rgb = Rich.rgb(colour);
            fill.setColor(rgb < 0 ? INK : 0xFF000000 | rgb);
            d.setBackground(fill);
            f.addView(d, new FrameLayout.LayoutParams(dot, dot, Gravity.CENTER));
            f.setContentDescription(COLOUR_NAMES[i]);
            if (android.os.Build.VERSION.SDK_INT >= 26) f.setTooltipText(COLOUR_NAMES[i]);
            f.setFocusable(false);
            f.setOnClickListener(v -> {
                colour(colour);
                if (onChange != null) onChange.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(i == 0 ? Ui.dp(c, 6) : Ui.dp(c, 2));
            row.addView(f, lp);
        }

        Runnable refresh = () -> {
            Rich.Style cur = current();
            showToggle(bold, cur.bold);
            showToggle(under, cur.underline);
            for (int i = 0; i < rings.size(); i++) {
                rings.get(i).setStroke(Ui.dp(c, 2.5f), Rich.COLOURS.get(i).equals(cur.colour) ? ACTIVE : 0x00000000);
            }
        };
        addListener(refresh);
        refresh.run();

        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.addView(row);
        return scroll;
    }

    private static TextView toggleButton(Context c, String label, String description) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextSize(19);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(description);
        if (android.os.Build.VERSION.SDK_INT >= 26) t.setTooltipText(description);
        t.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 40));
        lp.setMarginEnd(Ui.dp(c, 8));
        t.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(c, 10));
        t.setBackground(bg);
        return t;
    }

    private static void showToggle(TextView t, boolean on) {
        ((GradientDrawable) t.getBackground()).setColor(on ? ACTIVE : IDLE);
        t.setTextColor(on ? 0xFFFFFFFF : INK);
        t.setSelected(on);
    }
}
