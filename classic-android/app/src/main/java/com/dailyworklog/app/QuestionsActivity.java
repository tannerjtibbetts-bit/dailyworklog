package com.dailyworklog.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** "Check the questions": the questions and signature lines read off a form, to fix up and save. */
public class QuestionsActivity extends FormScreen {
    private Models.ReportForm form;
    /** A working copy; the form only changes on Save. */
    private Models.ReportForm draft;

    private static final String[] KIND_IDS = {Models.FormQuestion.TEXT, Models.FormQuestion.LONG, Models.FormQuestion.RATING,
            Models.FormQuestion.AVERAGE};
    private static final String[] KIND_NAMES = {"Short answer", "Long answer", "Rating", "Average of ratings"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        form = store.form(getIntent().getStringExtra("form"));
        if (form == null) {
            finish();
            return;
        }
        try {
            draft = Models.ReportForm.fromJson(new JSONObject(
                    savedInstanceState != null && savedInstanceState.getString("draft") != null
                            ? savedInstanceState.getString("draft") : form.toJson().toString()));
        } catch (Exception e) {
            draft = form;
        }
        setTitles("Check the questions", form.name);
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        try {
            if (draft != null) out.putString("draft", draft.toJson().toString());
        } catch (Exception ignored) {
            // nothing to keep
        }
    }

    /** "Short answer", "Rating (Excellent 4.0 … Poor 0.0)", ... */
    static String describe(Models.FormQuestion q) {
        switch (q.kind) {
            case Models.FormQuestion.LONG: return "Long answer";
            case Models.FormQuestion.AVERAGE: return "Worked out: average of the ratings";
            case Models.FormQuestion.RATING:
                if (q.choices.isEmpty()) return "Rating";
                Models.Choice a = q.choices.get(0), b = q.choices.get(q.choices.size() - 1);
                String first = (a.label + " " + a.value).trim(), last = (b.label + " " + b.value).trim();
                return q.choices.size() == 1 ? "Rating (" + first + ")" : "Rating (" + first + " … " + last + ")";
            default: return "Short answer";
        }
    }

    private void render() {
        body.removeAllViews();
        if (draft.questions.isEmpty()) para("No questions were found on this form. Add them with + Add a question.");
        for (Models.FormQuestion q : draft.questions) {
            row(q.label, describe(q), v -> editQuestion(q), v -> {
                draft.questions.remove(q);
                render();
            });
        }
        action("+  Add a question", v -> editQuestion(null));

        heading("Signature lines");
        for (int i = 0; i < draft.signatures.size(); i++) {
            final int at = i;
            row(draft.signatures.get(i), null, v -> Ui.prompt(this, "Signature line", "e.g. Supervisor signature",
                    draft.signatures.get(at), "Save", n -> {
                        draft.signatures.set(at, n);
                        render();
                    }), v -> {
                draft.signatures.remove(at);
                render();
            });
        }
        action("+  Add a signature line", v -> Ui.prompt(this, "Signature line", "e.g. Supervisor signature", "", "Add", n -> {
            draft.signatures.add(n);
            render();
        }));

        button("View the form", false, v -> Docs.showForm(this, store, form));
        button("Save", true, v -> {
            form.questions = draft.questions;
            form.signatures = draft.signatures;
            store.save();
            finish();
        });
    }

    /** A card with the label, an optional grey line, and a red × on the right. */
    private void row(String label, String sub, View.OnClickListener tap, View.OnClickListener remove) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackgroundResource(R.drawable.bg_card);
        c.setForeground(getDrawable(android.R.drawable.list_selector_background));
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 17, R.color.navy, true));
        if (sub != null) texts.addView(text(sub, 14, R.color.grey, false));
        c.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView x = new ImageView(this);
        x.setImageResource(R.drawable.ic_close);
        x.setColorFilter(0xFFD93B3F);
        x.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        x.setContentDescription("Delete " + label);
        x.setBackgroundResource(android.R.drawable.list_selector_background);
        x.setOnClickListener(remove);
        c.addView(x, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        c.setOnClickListener(tap);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        body.addView(c, lp);
    }

    /** The rating scale used by the form's other rating questions (or a plain one). */
    private List<Models.Choice> scale() {
        for (Models.FormQuestion q : draft.questions) {
            if (Models.FormQuestion.RATING.equals(q.kind) && !q.choices.isEmpty()) return q.choices;
        }
        List<Models.Choice> plain = new ArrayList<>();
        for (String s : new String[]{"Excellent", "Good", "Fair", "Poor"}) plain.add(new Models.Choice(s, ""));
        return plain;
    }

    /** Label and kind of a question (null = a new one). */
    private void editQuestion(Models.FormQuestion existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        EditText label = new EditText(this);
        label.setHint("Question");
        label.setText(existing == null ? "" : existing.label);
        label.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(label);
        RadioGroup kinds = new RadioGroup(this);
        kinds.setPadding(0, Ui.dp(this, 8), 0, 0);
        String current = existing == null ? Models.FormQuestion.TEXT : existing.kind;
        for (int i = 0; i < KIND_IDS.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(KIND_NAMES[i]);
            rb.setTag(KIND_IDS[i]);
            kinds.addView(rb);
            if (KIND_IDS[i].equals(current)) kinds.check(rb.getId());
        }
        box.addView(kinds);
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add a question" : "Edit question")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String l = label.getText().toString().trim();
            if (l.isEmpty()) {
                label.setError("Enter the question");
                return;
            }
            View picked = kinds.findViewById(kinds.getCheckedRadioButtonId());
            String kind = picked == null ? Models.FormQuestion.TEXT : (String) picked.getTag();
            Models.FormQuestion q = existing;
            if (q == null) {
                q = new Models.FormQuestion(draft.newQuestionId(), l, kind);
                draft.questions.add(q);
            }
            q.label = l;
            if (Models.FormQuestion.RATING.equals(kind) && q.choices.isEmpty()) {
                for (Models.Choice c : scale()) q.choices.add(new Models.Choice(c.label, c.value));
            }
            if (!Models.FormQuestion.RATING.equals(kind)) q.choices.clear();
            q.kind = kind;
            d.dismiss();
            render();
        }));
        d.show();
    }
}
