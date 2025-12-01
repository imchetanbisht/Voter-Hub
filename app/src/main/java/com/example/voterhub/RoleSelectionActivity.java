package com.example.voterhub;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class RoleSelectionActivity extends AppCompatActivity {
    private static final String TAG = "RoleSelection";

    private CardView cardAdmin, cardVoter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        // initialize
        cardAdmin = findViewById(R.id.cardAdmin);
        cardVoter = findViewById(R.id.cardVoter);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Log.d(TAG, "onCreate: initialized");

        cardAdmin.setOnClickListener(v -> {
            Toast.makeText(this, "Admin selected", Toast.LENGTH_SHORT).show();
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.click_scale));
            // Navigate immediately (good UX) and save role in background
            navigateAndSaveRole("admin");
        });

        cardVoter.setOnClickListener(v -> {
            Toast.makeText(this, "Voter selected", Toast.LENGTH_SHORT).show();
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.click_scale));
            navigateAndSaveRole("voter");
        });
    }

    /**
     * Navigate immediately to target dashboard and try to save role in background.
     * This ensures UI never blocks. Firestore result is logged.
     */
    private void navigateAndSaveRole(String role) {
        // navigate immediately
        Intent intent = role.equals("admin")
                ? new Intent(this, DashboardActivity.class)
                : new Intent(this, VoterDashboardActivity.class);
        startActivity(intent);
        // do NOT call finish() yet — wait a tiny moment to attempt save and logs (optional)
        // but we will finish() to remove this activity from stack
        finish();

        // now save role in background (fire-and-forget)
        try {
            if (mAuth.getCurrentUser() == null) {
                Log.w(TAG, "navigateAndSaveRole: no logged in user. Role not saved.");
                return;
            }
            String userId = mAuth.getCurrentUser().getUid();
            Map<String, Object> data = new HashMap<>();
            data.put("role", role);

            db.collection("users").document(userId)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Role saved to Firestore: " + role + " for uid=" + userId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to save role to Firestore: " + e.getMessage(), e);
                    });
        } catch (Exception ex) {
            Log.e(TAG, "Exception while saving role", ex);
        }
    }
}
