package com.example.voterhub;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome, tvTimer;

    private TextView tvTotalClubs, tvHostedCount, tvTotalVisitors, tvTotalVotes;

    private Button btnSignOut, btnSetTime, btnStartTimer, btnStopTimer, btnReset;
    private RecyclerView rvRecentActivity;

    private TextView tvCreate, tvViewClubs, tvDeclared;

    private CountDownTimer countDownTimer;
    private boolean timerRunning = false;
    private long timeLeftInMillis = 0;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ArrayList<String> recentActivities;
    private RecentActivityAdapter adapter;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupClicks();
        showUserWelcomeMessage();
        setupRecentActivityList();
        loadDashboardStats();
    }

    private void initViews() {

        tvWelcome = findViewById(R.id.tvWelcome);
        tvTimer = findViewById(R.id.tvTimer);

        tvTotalClubs = findViewById(R.id.tvTotalClubs);
        tvHostedCount = findViewById(R.id.tvHostedCount);
        tvTotalVisitors = findViewById(R.id.tvTotalVisitors);
        tvTotalVotes = findViewById(R.id.tvTotalVotes);

        btnSignOut = findViewById(R.id.btnSignOut);
        btnSetTime = findViewById(R.id.btnSetTime);
        btnStartTimer = findViewById(R.id.btnStartTimer);
        btnStopTimer = findViewById(R.id.btnStopTimer);
        btnReset = findViewById(R.id.btnReset);

        rvRecentActivity = findViewById(R.id.rvRecentActivity);

        tvCreate = findViewById(R.id.cardCreate).findViewById(R.id.tvTitle);
        tvViewClubs = findViewById(R.id.cardViewClubs).findViewById(R.id.tvTitle);
        tvDeclared = findViewById(R.id.cardDeclared).findViewById(R.id.tvTitle);

        tvCreate.setText("Create Clubs");
        tvViewClubs.setText("View Clubs");
        tvDeclared.setText("Declared Result");
    }


    private void setupClicks() {

        findViewById(R.id.btnHeaderBack).setOnClickListener(v -> {
            Intent intent = new Intent(this, RoleSelectionActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        btnSignOut.setOnClickListener(v -> showSignOutPopup());
        btnSetTime.setOnClickListener(v -> openTimePicker());
        btnStartTimer.setOnClickListener(v -> startTimer());
        btnStopTimer.setOnClickListener(v -> stopTimer());

        btnReset.setOnClickListener(v -> showResetPopup());

        findViewById(R.id.cardCreate).setOnClickListener(v ->
                startActivity(new Intent(this, CreateClubActivity.class)));

        findViewById(R.id.cardViewClubs).setOnClickListener(v ->
                startActivity(new Intent(this, ViewClubActivity.class)));

        findViewById(R.id.cardDeclared).setOnClickListener(v ->
                startActivity(new Intent(this, DeclaredResultActivity.class)));
    }


    private void showUserWelcomeMessage() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String name = prefs.getString("userName", "Admin");
        tvWelcome.setText("Hi " + name + " 👋");
    }

    // ===============================
    // RESET POPUP
    // ===============================
    private void showResetPopup() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset System")
                .setMessage("Do you want to Reset?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> resetSystem())
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // ===============================
    // FULL SYSTEM RESET
    // ===============================
    private void resetSystem() {

        // RESET candidates votes & details BUT KEEP CLUBS
        db.collection("clubs").get().addOnSuccessListener(query -> {
            for (DocumentSnapshot doc : query) {

                List<Map<String, Object>> positions =
                        (List<Map<String, Object>>) doc.get("positions");

                if (positions == null) continue;

                for (Map<String, Object> pos : positions) {
                    List<Map<String, Object>> candList =
                            (List<Map<String, Object>>) pos.get("candidates");

                    if (candList != null) {
                        for (Map<String, Object> c : candList) {
                            c.put("votes", 0);
                            c.put("imageUri", "");
                            c.put("brief", "");
                        }
                    }
                }

                doc.getReference().update("positions", positions);
            }
        });

        // DELETE voters, votes, results, logs but NOT clubs
        db.collection("votes").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot doc : q) doc.getReference().delete();
        });

        db.collection("voters").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot doc : q) doc.getReference().delete();
        });

        db.collection("results").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot doc : q) doc.getReference().delete();
        });

        db.collection("activityLog").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot doc : q) doc.getReference().delete();
        });

        Toast.makeText(this, "System Reset Successfully!", Toast.LENGTH_LONG).show();

        startActivity(getIntent());
        finish();
    }

    // ===============================
    // RECENT ACTIVITY LIST
    // ===============================
    private void setupRecentActivityList() {

        rvRecentActivity.setLayoutManager(new LinearLayoutManager(this));
        recentActivities = new ArrayList<>();
        adapter = new RecentActivityAdapter(recentActivities);
        rvRecentActivity.setAdapter(adapter);

        db.collection("activityLog")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener((value, error) -> {

                    if (value == null) return;

                    recentActivities.clear();

                    for (DocumentSnapshot snap : value) {

                        String msg = snap.getString("message");
                        Long ts = snap.getTimestamp("timestamp") != null
                                ? snap.getTimestamp("timestamp").toDate().getTime()
                                : System.currentTimeMillis();

                        recentActivities.add(msg + " (" + getTimeAgo(ts) + ")");
                    }

                    adapter.notifyDataSetChanged();
                });
    }

    // Time Ago
    private String getTimeAgo(long time) {

        long now = System.currentTimeMillis();
        long diff = now - time;

        long minutes = diff / 60000;
        long hours = minutes / 60;

        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " mins ago";
        return hours + " hours ago";
    }

    // ===============================
    // DASHBOARD STATS
    // ===============================
    private void loadDashboardStats() {

        // Total Clubs
        db.collection("clubs").addSnapshotListener((snap, e) -> {
            if (snap != null) tvTotalClubs.setText(String.valueOf(snap.size()));
        });

        // ✔ REAL HOSTED EVENT COUNT (From meta/hosted_event)
        db.collection("meta").document("hosted_event")
                .addSnapshotListener((snap, e) -> {
                    if (snap != null) {
                        long count = snap.getLong("count") != null ? snap.getLong("count") : 0;
                        tvHostedCount.setText(String.valueOf(count));
                    }
                });

        // Total Visitors
        db.collection("voters").addSnapshotListener((snap, e) -> {
            if (snap != null) tvTotalVisitors.setText(String.valueOf(snap.size()));
        });

        // Total Votes
        db.collection("clubs").addSnapshotListener((snap, e) -> {

            int totalVotes = 0;

            if (snap == null) return;

            for (DocumentSnapshot doc : snap) {

                List<Map<String, Object>> positions = (List<Map<String, Object>>) doc.get("positions");
                if (positions == null) continue;

                for (Map<String, Object> pos : positions) {

                    List<Map<String, Object>> candidates =
                            (List<Map<String, Object>>) pos.get("candidates");

                    if (candidates == null) continue;

                    for (Map<String, Object> cand : candidates) {
                        Object v = cand.get("votes");
                        totalVotes += v == null ? 0 : Integer.parseInt(v.toString());
                    }
                }
            }

            tvTotalVotes.setText(String.valueOf(totalVotes));
        });
    }


    private void showSignOutPopup() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sign Out")
                .setMessage("Are you sure you want to Sign Out?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> {

                    mAuth.signOut();

                    SharedPreferences.Editor editor =
                            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit();
                    editor.clear();
                    editor.apply();

                    Intent intent = new Intent(this, LoginActivity2.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();

                })
                .setNegativeButton("No", null)
                .show();
    }


    private void openTimePicker() {
        TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (v, h, m) -> {
                    timeLeftInMillis = (h * 60L + m) * 60 * 1000;
                    updateTimerText();
                },
                0, 0, true
        );
        timePicker.show();
    }


    private void startTimer() {

        if (timeLeftInMillis <= 0) {
            Toast.makeText(this, "Please set a valid time!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (timerRunning) return;

        long endTime = System.currentTimeMillis() + timeLeftInMillis;

        // ⭐ Increase hosted event count ONLY when timer starts
        db.collection("meta").document("hosted_event")
                .get()
                .addOnSuccessListener(snap -> {
                    long old = snap.getLong("count") != null ? snap.getLong("count") : 0;

                    db.collection("meta").document("hosted_event")
                            .set(new HashMap<String, Object>() {{
                                put("count", old + 1);
                            }});
                });

        // Start countdown
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long ms) {
                timeLeftInMillis = ms;
                updateTimerText();
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                tvTimer.setText("00:00:00");
            }
        }.start();

        timerRunning = true;
    }


    private void stopTimer() {
        if (countDownTimer != null) countDownTimer.cancel();

        timerRunning = false;

        db.collection("meta").document("timer")
                .update("isActive", false,
                        "endTimeMillis", 0);   // ❗ IMPORTANT FIX
    }



    private void updateTimerText() {
        int h = (int) (timeLeftInMillis / 1000) / 3600;
        int m = (int) ((timeLeftInMillis / 1000) % 3600) / 60;
        int s = (int) (timeLeftInMillis / 1000) % 60;

        tvTimer.setText(String.format(Locale.getDefault(),
                "%02d:%02d:%02d", h, m, s));
    }


    @Override
    protected void onResume() {
        super.onResume();

        db.collection("meta").document("timer")
                .get().addOnSuccessListener(snap -> {

                    Boolean active = snap.getBoolean("isActive");
                    Long end = snap.getLong("endTimeMillis");

                    if (active != null && active && end != null) {

                        long remaining = end - System.currentTimeMillis();

                        if (remaining > 0) {

                            timeLeftInMillis = remaining;

                            countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
                                @Override
                                public void onTick(long ms) {
                                    timeLeftInMillis = ms;
                                    updateTimerText();
                                }

                                @Override
                                public void onFinish() {
                                    timerRunning = false;
                                    tvTimer.setText("00:00:00");
                                }
                            }.start();

                            timerRunning = true;

                        } else {
                            timerRunning = false;
                            timeLeftInMillis = 0;
                        }
                    }

                    updateTimerText();
                });
    }
}
