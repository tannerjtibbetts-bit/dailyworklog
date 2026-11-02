package com.dailyworklog.app;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Opening screen: logo, app name and an Enter button. */
public class IntroActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_intro);
        View root = findViewById(R.id.root);
        Ui.applyInsets(root);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), root);
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        View logo = findViewById(R.id.logo);
        View name = findViewById(R.id.name);
        View bar = findViewById(R.id.bar);
        View tagline = findViewById(R.id.tagline);
        View mission = findViewById(R.id.mission);
        View enter = findViewById(R.id.enter);
        View support = findViewById(R.id.support);

        if (savedInstanceState == null) {
            // Logo pops in, then the name, underline, tagline and button follow.
            logo.setAlpha(0f);
            logo.setScaleX(0.6f);
            logo.setScaleY(0.6f);
            logo.setRotation(-20f);
            logo.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
                    .setDuration(700).setInterpolator(new OvershootInterpolator(1.4f)).start();

            rise(name, 280);
            bar.setScaleX(0f);
            bar.animate().scaleX(1f).setStartDelay(520).setDuration(400)
                    .setInterpolator(new DecelerateInterpolator()).start();
            rise(tagline, 600);
            rise(mission, 740);
            rise(enter, 900);
            rise(support, 1000);
        }

        support.setOnClickListener(v -> ManualActivity.open(this));
        enter.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            SignInActivity.enter(this);  // straight in, or sign in / ask for access first
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    private void rise(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(Ui.dp(this, 28));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(450)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}
