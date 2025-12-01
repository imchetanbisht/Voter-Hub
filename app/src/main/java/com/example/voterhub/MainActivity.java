package com.example.voterhub;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

public class MainActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2500; // 2.5 seconds
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        // 🔹 Start logo animation
        ImageView logo = findViewById(R.id.logo);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_scale);
        logo.startAnimation(fadeIn);

        // 🔹 Wait for splash duration, then check user authentication
        new Handler().postDelayed(() -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();

            if (currentUser != null) {
                // 🔍 Verify user still exists on Firebase (not deleted)
                currentUser.getIdToken(true)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult() != null) {
                                // ✅ Valid token → go to Dashboard
                                startActivity(new Intent(MainActivity.this, RoleSelectionActivity.class));
                            } else {
                                // ❌ Invalid or deleted account → sign out and go to Login
                                mAuth.signOut();
                                startActivity(new Intent(MainActivity.this, LoginActivity2.class));
                            }
                            finish();
                        });
            } else {
                // ❌ No user logged in → go to Login screen
                startActivity(new Intent(MainActivity.this, LoginActivity2.class));
                finish();
            }

        }, SPLASH_DURATION);
    }
}
