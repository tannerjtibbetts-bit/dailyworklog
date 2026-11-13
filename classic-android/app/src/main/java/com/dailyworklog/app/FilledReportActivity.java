package com.dailyworklog.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** One filled-in report: every question with its answer box, asking the missing ones, and Export PDF. */
public class FilledReportActivity extends FormScreen {
    private static final int MISSING_BG = 0xFFFFF1E0;

    private Models.FilledReport report;
    private Models.ReportForm form;
    private Models.Project project;
    private ExportHelper exporter;
    private ActivityResultLauncher<Intent> speech;
    /** The box the mic is filling. */
    private EditText speakingInto;
    private final List<Runnable> refreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        report = store.filled(getIntent().getStringExtra("report"));
        form = report == null ? null : store.formFor(report);
        if (report == null || form == null) {
            finish();
            return;
        }
        project = project(getIntent().getStringExtra("project"), report.project);
        exporter = new ExportHelper(this);
        speech = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onSpeech);
        setTitles(form.name, report.person + " · " + report.project + " · " + ReportFill.day(report.createdAt));
        render();
        if (savedInstanceState == null && getIntent().getBooleanExtra("ask", false)) askMissing();
    }

    private void save(Models.FormQuestion q, String text) {
        ReportFill.setAnswer(project, q, report, text, System.currentTimeMillis());
        store.save();
        for (Runnable r : refreshers) r.run();
    }

    private void render() {
        body.removeAllViews();
        refreshers.clear();
        for (Models.FormQuestion q : form.questions) {
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setBackgroundResource(R.drawable.bg_card);
            block.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            block.addView(text(q.label, 16, R.color.navy, true));
            if (Models.FormQuestion.AVERAGE.equals(q.kind)) {
                TextView avg = text("", 18, R.color.text, true);
                block.addView(avg);
                refreshers.add(() -> {
                    String v = ReportFill.average(form, report);
                    avg.setText(v.isEmpty() ? "—" : v);
                });
            } else if (Models.FormQuestion.RATING.equals(q.kind)) {
                block.addView(ratingSpinner(q));
            } else if (Models.FormQuestion.DATE.equals(q.kind)) {
                Models.FormQuestion start = ReportFill.rangeStart(form, q);
                if (start != null) {
                    // The end of a period: picked with its start.
                    String v = report.answer(q.id).trim();
                    block.addView(text(v.isEmpty() ? "Picked with \u201C" + start.label + "\u201D" : v, 17,
                            v.isEmpty() ? R.color.grey : R.color.text, false));
                } else {
                    block.addView(dateButton(q, this::render));
                }
            } else {
                block.addView(textBox(q, report.answer(q.id), Models.FormQuestion.LONG.equals(q.kind), t -> save(q, t)));
            }
            if (!Models.FormQuestion.AVERAGE.equals(q.kind)) {
                // Light orange until answered.
                refreshers.add(() -> {
                    boolean empty = report.answer(q.id).trim().isEmpty();
                    if (empty) block.getBackground().mutate().setTint(MISSING_BG);
                    else block.getBackground().mutate().setTintList(null);
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(this, 10);
            body.addView(block, lp);
        }
        TextView ask = button("Ask the missing questions", false, v -> askMissing());
        refreshers.add(() -> ask.setVisibility(ReportFill.missing(form, report).isEmpty() ? View.GONE : View.VISIBLE));
        if (!form.pictures().isEmpty()) {
            button("Check where the answers go", false, v -> startActivity(new android.content.Intent(this, PlaceActivity.class)
                    .putExtra("report", report.id)));
        }
        button("Export PDF", true, v -> ReportsActivity.exportPdf(this, exporter, store, report));
        for (Runnable r : refreshers) r.run();
    }

    interface OnText {
        void onText(String text);
    }

    /** A text box (3-6 lines for a long answer) with a mic button; saves as you type. */
    private View textBox(Models.FormQuestion q, String value, boolean multi, OnText changed) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextSize(17);
        e.setHint("Answer");
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setSingleLine(false);
            e.setMinLines(3);
            e.setMaxLines(6);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                changed.onText(s.toString());
            }
        });
        row.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton mic = new ImageButton(this);
        mic.setImageResource(R.drawable.ic_mic);
        mic.setColorFilter(getColor(R.color.blue));
        mic.setBackgroundResource(android.R.drawable.list_selector_background);
        mic.setContentDescription("Speak your answer");
        if (android.os.Build.VERSION.SDK_INT >= 26) mic.setTooltipText("Speak your answer");
        mic.setOnClickListener(v -> speak(e, q.label));
        row.addView(mic, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        return row;
    }

    private Spinner ratingSpinner(Models.FormQuestion q) {
        List<String> items = new ArrayList<>();
        items.add("—");
        for (Models.Choice c : q.choices) items.add(c.display());
        String cur = report.answer(q.id);
        int at = 0;
        for (int i = 0; i < q.choices.size(); i++) if (q.choices.get(i).label.equalsIgnoreCase(cur)) at = i + 1;
        if (at == 0 && !cur.isEmpty()) {
            items.add(cur);
            at = items.size() - 1;
        }
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        sp.setSelection(at, false);
        final int start = at;
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int last = start;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == last) return;
                last = position;
                String v = position == 0 ? "" : position <= q.choices.size() ? q.choices.get(position - 1).label : items.get(position);
                save(q, v);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        return sp;
    }

    // ----- speaking -----

    private void speak(EditText into, String prompt) {
        speakingInto = into;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        try {
            speech.launch(i);
        } catch (ActivityNotFoundException e) {
            speakingInto = null;
            Toast.makeText(this, "Speech isn't available on this phone. Use the mic on your keyboard.", Toast.LENGTH_LONG).show();
        }
    }

    private void onSpeech(ActivityResult r) {
        EditText into = speakingInto;
        speakingInto = null;
        if (into == null || r.getResultCode() != RESULT_OK || r.getData() == null) return;
        ArrayList<String> res = r.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (res == null || res.isEmpty()) return;
        String cur = into.getText().toString().trim();
        String add = Fmt.capitalize(res.get(0));
        into.setText(cur.isEmpty() ? add : cur + " " + add);
        into.setSelection(into.getText().length());
    }

    // ----- asking the missing questions, one at a time -----

    private void askMissing() {
        // The "to" of a period is asked with its "from" (one pick), not on its own; the average is never asked.
        List<Models.FormQuestion> list = new ArrayList<>();
        for (Models.FormQuestion q : ReportFill.missing(form, report)) {
            Models.FormQuestion start = ReportFill.rangeStart(form, q);
            Models.FormQuestion ask = start != null ? start : q;
            if (!list.contains(ask)) list.add(ask);
        }
        if (list.isEmpty()) return;
        ask(list, 0);
    }

    /** "Pick a date" / the date, or for a period "Pick the start and end dates" / "Aug 1, 2026  to  Oct 31, 2026". */
    private TextView dateButton(Models.FormQuestion q, Runnable after) {
        Models.FormQuestion end = ReportFill.rangeEnd(form, q);
        TextView b = new TextView(this);
        b.setTextSize(17);
        b.setTextColor(getColor(R.color.deep_blue));
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setBackgroundResource(R.drawable.bg_chip_sky);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_calendar, 0, 0, 0);
        b.setCompoundDrawablePadding(Ui.dp(this, 10));
        if (android.os.Build.VERSION.SDK_INT >= 23) b.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.deep_blue)));
        Runnable show = () -> {
            String from = report.answer(q.id).trim();
            if (end == null) {
                b.setText(from.isEmpty() ? "Pick a date" : from);
            } else {
                String to = report.answer(end.id).trim();
                b.setText(from.isEmpty() && to.isEmpty() ? "Pick the start and end dates" : from + "  to  " + to);
            }
        };
        show.run();
        b.setOnClickListener(v -> {
            if (end == null) {
                DatePick.pick(this, q.label, report.answer(q.id), d -> {
                    save(q, d);
                    show.run();
                    after.run();
                });
            } else {
                DatePick.pickRange(this, DatePick.periodLabel(q.label), report.answer(q.id), report.answer(end.id), (f, t) -> {
                    save(q, f);
                    save(end, t);
                    show.run();
                    after.run();
                });
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        b.setLayoutParams(lp);
        return b;
    }

    private void ask(List<Models.FormQuestion> list, int i) {
        if (i >= list.size()) {
            render();
            return;
        }
        Models.FormQuestion q = list.get(i);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 4), Ui.dp(this, 22), 0);
        boolean period = ReportFill.rangeEnd(form, q) != null;
        TextView label = text(period ? DatePick.periodLabel(q.label) : q.label, 18, R.color.navy, true);
        label.setPadding(0, 0, 0, Ui.dp(this, 8));
        box.addView(label);
        if (Models.FormQuestion.RATING.equals(q.kind)) {
            RadioGroup g = new RadioGroup(this);
            for (Models.Choice c : q.choices) {
                RadioButton rb = new RadioButton(this);
                rb.setId(View.generateViewId());
                rb.setText(c.display());
                rb.setTextSize(17);
                rb.setMinHeight(Ui.dp(this, 48));
                g.addView(rb);
                if (c.label.equalsIgnoreCase(report.answer(q.id))) g.check(rb.getId());
                rb.setOnClickListener(v -> save(q, c.label));
            }
            box.addView(g);
        } else if (Models.FormQuestion.DATE.equals(q.kind)) {
            box.addView(dateButton(q, () -> {}));
        } else {
            box.addView(textBox(q, report.answer(q.id), Models.FormQuestion.LONG.equals(q.kind), t -> save(q, t)));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle("Question " + (i + 1) + " of " + list.size())
                .setView(sv)
                .setCancelable(false)
                .setNegativeButton("Stop", (x, w) -> render())
                .setNeutralButton("Skip", (x, w) -> ask(list, i + 1))
                .setPositiveButton(i == list.size() - 1 ? "Done" : "Next", (x, w) -> ask(list, i + 1))
                .create();
        if (!Models.FormQuestion.RATING.equals(q.kind) && d.getWindow() != null) {
            d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }
}
