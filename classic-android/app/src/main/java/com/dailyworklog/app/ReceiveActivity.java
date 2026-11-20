package com.dailyworklog.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Receives a text shared from Messages ("Share" → Daily Work Log) and opens the right screen with
 * it filled in: an approval code goes to sign-in, a handover or access request to Approvals.
 */
public class ReceiveActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String t = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Class<?> to;
        if (AccessCodes.looksLikeHandover(t)) {
            to = Access.isOwner(this) && Access.need(this) != Access.Need.NONE ? SignInActivity.class : ApprovalsActivity.class;
        } else if (AccessCodes.parseApproval(t) != null) {
            to = SignInActivity.class;
        } else if (AccessCodes.parseRequest(t) != null) {
            to = Access.need(this) == Access.Need.NONE ? ApprovalsActivity.class : SignInActivity.class;
        } else {
            Toast.makeText(this, "That text isn't a Daily Work Log code", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startActivity(new Intent(this, to).putExtra("text", t));
        finish();
    }
}
