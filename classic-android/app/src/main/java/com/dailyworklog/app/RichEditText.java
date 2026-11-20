package com.dailyworklog.app;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

/** A text box that reports cursor moves, so formatted notes know when to drop the typing style. */
public class RichEditText extends EditText {
    interface SelectionListener {
        void onSelection(int start, int end);
    }

    private SelectionListener selectionListener;

    public RichEditText(Context context) {
        super(context);
    }

    public RichEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RichEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void setSelectionListener(SelectionListener l) {
        selectionListener = l;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        // Called from the constructor too, before the listener is set.
        if (selectionListener != null) selectionListener.onSelection(selStart, selEnd);
    }
}
