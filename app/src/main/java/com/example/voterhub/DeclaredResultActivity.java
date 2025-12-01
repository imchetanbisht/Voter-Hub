package com.example.voterhub;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import android.animation.LayoutTransition;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.*;

import java.util.*;

public class DeclaredResultActivity extends AppCompatActivity {

    private Spinner spinnerClubs;
    private LinearLayout layoutResults;
    private FirebaseFirestore db;

    private ArrayList<String> clubNames = new ArrayList<>();
    private ArrayList<String> clubIds = new ArrayList<>();

    private ListenerRegistration resultListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_declared_results);

        spinnerClubs = findViewById(R.id.spinnerClubs);
        layoutResults = findViewById(R.id.layoutResults);
        db = FirebaseFirestore.getInstance();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(DeclaredResultActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        });

        loadClubs();
    }

    // -----------------------------------------------------------
    // Load clubs in spinner
    // -----------------------------------------------------------
    private void loadClubs() {
        db.collection("clubs").get().addOnSuccessListener(docs -> {

            clubNames.clear();
            clubIds.clear();

            for (DocumentSnapshot doc : docs) {
                clubNames.add(doc.getString("clubName"));
                clubIds.add(doc.getId());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    clubNames
            );

            spinnerClubs.setAdapter(adapter);

            spinnerClubs.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    startListeningToClub(clubIds.get(pos));
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        });
    }

    // -----------------------------------------------------------
    // Real-time listener
    // -----------------------------------------------------------
    private void startListeningToClub(String clubId) {

        if (resultListener != null)
            resultListener.remove();

        resultListener = db.collection("clubs")
                .document(clubId)
                .addSnapshotListener((snapshot, error) -> {

                    if (snapshot == null || !snapshot.exists()) return;

                    layoutResults.removeAllViews();
                    loadClubAndPositions(snapshot);
                });
    }

    // -----------------------------------------------------------
    // Load Club → Position → Candidates
    // -----------------------------------------------------------
    private void loadClubAndPositions(DocumentSnapshot doc) {

        // Club Title
        TextView tvClubTitle = new TextView(this);
        tvClubTitle.setText(doc.getString("clubName"));
        tvClubTitle.setTextSize(22f);
        tvClubTitle.setPadding(20, 20, 20, 20);
        tvClubTitle.setBackgroundColor(0xFFE8EDFF);
        tvClubTitle.setTextColor(0xFF000000);

        LinearLayout clubContainer = new LinearLayout(this);
        clubContainer.setOrientation(LinearLayout.VERTICAL);
        clubContainer.setPadding(10, 10, 10, 10);
        clubContainer.setBackgroundColor(0xFFFFFFFF);
        clubContainer.setElevation(4f);

        // Position List Container
        LinearLayout layoutPositionList = new LinearLayout(this);
        layoutPositionList.setOrientation(LinearLayout.VERTICAL);
        layoutPositionList.setVisibility(View.GONE);
        layoutPositionList.setPadding(10, 10, 10, 10);

        layoutPositionList.setLayoutTransition(new LayoutTransition());

        // Club click → toggle positions
        tvClubTitle.setOnClickListener(v -> {
            layoutPositionList.setVisibility(
                    layoutPositionList.getVisibility() == View.VISIBLE ?
                            View.GONE : View.VISIBLE
            );
        });

        clubContainer.addView(tvClubTitle);
        clubContainer.addView(layoutPositionList);

        layoutResults.addView(clubContainer);

        // Load all positions
        List<Map<String, Object>> positions =
                (List<Map<String, Object>>) doc.get("positions");

        if (positions != null) {
            for (Map<String, Object> position : positions) {
                addPositionDropdown(layoutPositionList, position);
            }
        }
    }

    // -----------------------------------------------------------
    // Position dropdown
    // -----------------------------------------------------------
    private void addPositionDropdown(LinearLayout parent, Map<String, Object> position) {

        View posView = LayoutInflater.from(this)
                .inflate(R.layout.item_result_position, parent, false);

        TextView tvPosition = posView.findViewById(R.id.tvPositionTitle);
        LinearLayout layoutCandidateList = posView.findViewById(R.id.layoutCandidateResults);

        String positionName = position.get("positionName") != null
                ? position.get("positionName").toString() : "Position";

        tvPosition.setText(positionName);

        layoutCandidateList.setVisibility(View.GONE);
        layoutCandidateList.setLayoutTransition(new LayoutTransition());

        // Click → toggle candidates
        tvPosition.setOnClickListener(v -> {
            layoutCandidateList.setVisibility(
                    layoutCandidateList.getVisibility() == View.VISIBLE ?
                            View.GONE : View.VISIBLE
            );
        });

        // Load candidates
        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) position.get("candidates");

        if (candidates != null && !candidates.isEmpty()) {

            candidates.sort((a, b) -> Long.compare(safeVotes(b.get("votes")), safeVotes(a.get("votes"))));

            long maxVotes = safeVotes(candidates.get(0).get("votes"));
            boolean winnerDone = false;

            for (Map<String, Object> cand : candidates) {

                View row = LayoutInflater.from(this)
                        .inflate(R.layout.item_candidate_result, layoutCandidateList, false);

                ImageView img = row.findViewById(R.id.imgCandidate);
                ImageView trophy = row.findViewById(R.id.imgTrophy);
                TextView tvName = row.findViewById(R.id.tvCandidateName);
                TextView tvVotes = row.findViewById(R.id.tvVotes);

                View voteBar = row.findViewById(R.id.voteBar);
                View voteBg = row.findViewById(R.id.voteBarBg);

                String name = cand.get("name") != null ? cand.get("name").toString() : "Unknown";
                long votes = safeVotes(cand.get("votes"));

                tvName.setText(name);
                tvVotes.setText("Votes: " + votes);

                if (cand.get("imageUri") != null)
                    Glide.with(this).load(cand.get("imageUri")).into(img);

                if (!winnerDone) {
                    row.setBackgroundColor(0xFFFFF3CC);
                    trophy.setVisibility(View.VISIBLE);
                    winnerDone = true;
                }

                int percent = (int) (maxVotes == 0 ? 0 : (votes * 100f / maxVotes));

                voteBar.post(() -> {
                    int full = voteBg.getWidth();
                    if (full <= 0) full = row.getWidth();

                    int barWidth = (int) (full * (percent / 100f));
                    voteBar.getLayoutParams().width = barWidth;
                    voteBar.requestLayout();
                });

                layoutCandidateList.addView(row);
            }
        }

        parent.addView(posView);
    }

    // -----------------------------------------------------------
    private long safeVotes(Object o) {
        if (o == null) return 0;
        try {
            if (o instanceof Number) return ((Number) o).longValue();
            return Long.parseLong(o.toString());
        } catch (Exception e) { return 0; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resultListener != null) resultListener.remove();
    }
}
