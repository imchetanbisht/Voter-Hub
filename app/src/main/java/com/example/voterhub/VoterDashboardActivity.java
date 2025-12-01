package com.example.voterhub;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VoterDashboardActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private TextView tvWelcome, tvTimer;
    private LinearLayout layoutVoterClubs;

    private CountDownTimer countDownTimer;
    private long endTimeMillis = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voter_dashboard);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        tvWelcome = findViewById(R.id.tvWelcome);
        tvTimer = findViewById(R.id.tvTimerVoter);
        layoutVoterClubs = findViewById(R.id.layoutVoterClubs);

        Button btnSignOut = findViewById(R.id.btnSignOut);
        btnSignOut.setOnClickListener(v -> showSignOutPopup());

        // Back Button
        ImageView btnBack = findViewById(R.id.btnHeaderBack);
        btnBack.setOnClickListener(v -> onBackPressed());

        loadUserData();
        listenToTimer();
        loadClubs();
    }

    // ============================
    // LOAD USER NAME
    // ============================
    private void loadUserData() {
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snap -> {
                    String name = snap.getString("name");
                    tvWelcome.setText("Hi " + (name != null ? name : "User") + " 👋");
                });
    }

    // ============================
    // LOG ACTIVITY
    // ============================
    private void logActivity(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("message", message);
        map.put("timestamp", FieldValue.serverTimestamp());

        db.collection("activityLog").add(map);
    }

    // ============================
    // TIMER SYNC (Admin → Voter)
    // ============================
    private void listenToTimer() {
        db.collection("meta").document("timer")
                .addSnapshotListener((snapshot, error) -> {

                    if (snapshot == null || !snapshot.exists()) return;

                    Boolean active = snapshot.getBoolean("isActive");
                    Long end = snapshot.getLong("endTimeMillis");

                    // ❗ FIX — stop timer instantly when admin stops
                    if (active == null || !active || end == null || end == 0) {

                        if (countDownTimer != null) countDownTimer.cancel();

                        tvTimer.setText("Voting Closed");
                        disableVotingForAll();
                        return;
                    }

                    endTimeMillis = end;
                    startCountdownTimer();
                });
    }


    private void disableVotingForAll() {

        for (int i = 0; i < layoutVoterClubs.getChildCount(); i++) {

            View clubCard = layoutVoterClubs.getChildAt(i);
            LinearLayout layoutPositions = clubCard.findViewById(R.id.layoutPositions);

            for (int j = 0; j < layoutPositions.getChildCount(); j++) {

                View posCard = layoutPositions.getChildAt(j);
                LinearLayout layoutCandidates = posCard.findViewById(R.id.layoutCandidates);

                for (int k = 0; k < layoutCandidates.getChildCount(); k++) {

                    View candCard = layoutCandidates.getChildAt(k);
                    Button voteBtn = candCard.findViewById(R.id.btnVote);

                    if (voteBtn != null) {
                        voteBtn.setEnabled(false);
                        voteBtn.setText("Voting Closed");
                        voteBtn.setBackgroundTintList(getColorStateList(R.color.gray_disabled));
                    }
                }
            }
        }
    }

    private void startCountdownTimer() {
        long remain = endTimeMillis - System.currentTimeMillis();

        if (remain <= 0) {
            tvTimer.setText("00:00:00");
            return;
        }

        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(remain, 1000) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText(formatTime(ms));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("Voting Closed");
            }
        }.start();
    }

    private String formatTime(long ms) {
        int sec = (int) (ms / 1000) % 60;
        int min = (int) ((ms / 1000) / 60) % 60;
        int hr = (int) ((ms / 1000) / 3600);
        return String.format("%02d:%02d:%02d", hr, min, sec);
    }

    // ============================
    // LOAD ALL CLUBS
    // ============================
    private void loadClubs() {
        layoutVoterClubs.removeAllViews();

        db.collection("clubs").get().addOnSuccessListener(query -> {
            for (QueryDocumentSnapshot doc : query) {
                addClubCard(doc);
            }
        });
    }

    // ============================
    // ADD CLUB CARD
    // ============================
    @SuppressLint("InflateParams")
    private void addClubCard(DocumentSnapshot doc) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_voter_club_card, layoutVoterClubs, false);

        TextView tvName = card.findViewById(R.id.tvClubName);
        TextView tvDesc = card.findViewById(R.id.tvClubDesc);
        LinearLayout posLayout = card.findViewById(R.id.layoutPositions);
        LinearLayout inner = card.findViewById(R.id.cardInner);

        String clubName = doc.getString("clubName");

        tvName.setText(clubName);
        tvDesc.setText(doc.getString("clubDescription"));

        posLayout.setVisibility(View.GONE);

        inner.setOnClickListener(v ->
                posLayout.setVisibility(posLayout.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)
        );

        List<Map<String, Object>> positions = (List<Map<String, Object>>) doc.get("positions");
        if (positions != null) {
            for (Map<String, Object> pos : positions) {
                addPosition(doc.getId(), clubName, posLayout, pos);
            }
        }

        layoutVoterClubs.addView(card);
    }

    // ============================
    // ADD POSITION CARD
    // ============================
    private void addPosition(String clubId, String clubName, LinearLayout parent, Map<String, Object> posData) {

        View posView = LayoutInflater.from(this)
                .inflate(R.layout.item_voter_position_card, parent, false);

        TextView tvPosName = posView.findViewById(R.id.tvPositionName);
        LinearLayout candLayout = posView.findViewById(R.id.layoutCandidates);

        String posName = posData.get("positionName").toString();
        tvPosName.setText(posName);

        // FIXED TOGGLE ISSUE
        candLayout.setVisibility(View.GONE);
        posView.setOnClickListener(v ->
                candLayout.setVisibility(
                        candLayout.getVisibility() == View.VISIBLE
                                ? View.GONE
                                : View.VISIBLE
                )
        );

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) posData.get("candidates");

        if (candidates != null) {
            for (Map<String, Object> c : candidates) {
                addCandidate(clubId, clubName, posName, candLayout, c);
            }
        }

        parent.addView(posView);
    }


    // ============================
    // ADD CANDIDATE (With Voting)
    // ============================
    private void addCandidate(String clubId, String clubName, String positionName,
                              LinearLayout parent, Map<String, Object> candidate) {

        View item = LayoutInflater.from(this)
                .inflate(R.layout.item_voter_candidate_card, parent, false);

        ImageView img = item.findViewById(R.id.imgCandidate);
        TextView tvName = item.findViewById(R.id.tvCandidateName);
        TextView tvBrief = item.findViewById(R.id.tvCandidateBrief);
        Button btn = item.findViewById(R.id.btnVote);

        String name = candidate.get("name").toString();
        tvName.setText(name);

        String brief = candidate.get("brief") != null ? candidate.get("brief").toString() : "";
        tvBrief.setText(brief);

        // 🔥 FIX — IMAGE LOAD 100% WORKING
        String image = candidate.get("imageUri") != null ? candidate.get("imageUri").toString() : "";

        if (image != null && !image.trim().isEmpty()) {
            Glide.with(this)
                    .load(image)
                    .into(img);  // load candidate’s real image
        } else {
            img.setImageResource(R.drawable.manager);
        }


        String user = auth.getCurrentUser().getUid();
        String voteId = user + "_" + clubId + "_" + positionName;

        // 🔥 Check voting active
        db.collection("meta").document("timer")
                .get()
                .addOnSuccessListener(t -> {

                    Boolean active = t.getBoolean("isActive");

                    if (active == null || !active) {
                        btn.setEnabled(false);
                        btn.setText("Voting Closed");
                        btn.setBackgroundTintList(getColorStateList(R.color.gray_disabled));
                    }
                });

        // 🔥 Check already voted
        db.collection("votes").document(voteId).get().addOnSuccessListener(d -> {
            if (d.exists()) disableAllCandidates(parent, d.getString("candidate"));
        });

        // 🔥 On vote click
        btn.setOnClickListener(v ->
                castVote(voteId, user, clubId, clubName, positionName, name, parent)
        );

        parent.addView(item);
    }



    // ============================
    // DISABLE OTHER CANDIDATES
    // ============================
    private void disableAllCandidates(LinearLayout parent, String votedName) {

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            Button btn = child.findViewById(R.id.btnVote);
            TextView cName = child.findViewById(R.id.tvCandidateName);

            if (btn != null) {
                btn.setEnabled(false);

                if (cName.getText().toString().equals(votedName)) {
                    btn.setText("Voted");
                    btn.setBackgroundTintList(getColorStateList(R.color.blue));
                } else {
                    btn.setText("Voted");
                    btn.setBackgroundTintList(getColorStateList(R.color.gray_disabled));
                }
            }
        }
    }

    private void showSignOutPopup() {

        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to Sign Out?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> {

                    // Signout
                    FirebaseAuth.getInstance().signOut();

                    // Remove local user data (optional)
                    getSharedPreferences("UserPrefs", MODE_PRIVATE)
                            .edit().clear().apply();

                    // Move to Login Screen
                    Intent i = new Intent(this, LoginActivity2.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();

                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }


    // ============================
    // CAST VOTE + LOG ACTIVITY
    // ============================
    private void castVote(String voteId, String user, String clubId,
                          String clubName, String posName, String candidateName,
                          LinearLayout parent) {

        DocumentReference ref = db.collection("votes").document(voteId);

        ref.get().addOnSuccessListener(doc -> {

            if (doc.exists()) {
                disableAllCandidates(parent, doc.getString("candidate"));
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("voterId", user);
            data.put("clubId", clubId);
            data.put("position", posName);
            data.put("candidate", candidateName);
            data.put("createdAt", FieldValue.serverTimestamp());

            // -----------------------------------
            // 🔥 SAVE NORMAL VOTE (OLD)
            // -----------------------------------
            ref.set(data);

            // -----------------------------------
            // 🔥 SAVE DATE-WISE VOTES (NEW)
            // -----------------------------------
            String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());

            db.collection("votes")
                    .document(today)
                    .collection("day_votes")
                    .document(voteId)
                    .set(data);

            // -----------------------------------
            // UPDATE CLUB VOTE COUNT
            // -----------------------------------
            db.collection("clubs").document(clubId).get().addOnSuccessListener(snap -> {

                List<Map<String, Object>> posList =
                        (List<Map<String, Object>>) snap.get("positions");

                for (Map<String, Object> pos : posList) {

                    if (pos.get("positionName").equals(posName)) {

                        List<Map<String, Object>> candList =
                                (List<Map<String, Object>>) pos.get("candidates");

                        for (Map<String, Object> c : candList) {
                            if (c.get("name").equals(candidateName)) {
                                long old = c.get("votes") == null ? 0 : (long) c.get("votes");
                                c.put("votes", old + 1);
                            }
                        }
                    }
                }

                snap.getReference().update("positions", posList);

                // -----------------------------------
                // LOG ACTIVITY DATE WISE ALSO
                // -----------------------------------
                db.collection("users").document(user).get().addOnSuccessListener(userSnap -> {
                    String voterName = userSnap.getString("name");

                    if (voterName == null) voterName = "A voter";

                    logActivity(voterName + " vote done ✔️");
                });


                disableAllCandidates(parent, candidateName);

            });
        });
    }


}
