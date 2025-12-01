package com.example.voterhub;

import android.animation.LayoutTransition;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.List;
import java.util.Map;

public class ViewClubActivity extends AppCompatActivity {

    private LinearLayout layoutClubs;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_clubs);

        layoutClubs = findViewById(R.id.layoutClubs);
        db = FirebaseFirestore.getInstance();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(ViewClubActivity.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });


        loadClubs();
    }

    private void loadClubs() {
        layoutClubs.removeAllViews();

        db.collection("clubs").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No clubs found!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        addClubCard(doc);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void addClubCard(DocumentSnapshot doc) {

        View clubCard = LayoutInflater.from(this)
                .inflate(R.layout.item_club_card, layoutClubs, false);

        TextView tvClubName = clubCard.findViewById(R.id.tvClubName);
        TextView tvClubDesc = clubCard.findViewById(R.id.tvClubDesc);
        LinearLayout layoutPositions = clubCard.findViewById(R.id.layoutPositions);
        Button btnDelete = clubCard.findViewById(R.id.btnDeleteClub);

        // FIXED: correct clickable area
        LinearLayout cardInner = clubCard.findViewById(R.id.cardInner);

        tvClubName.setText(doc.getString("clubName"));
        tvClubDesc.setText(doc.getString("clubDescription"));

        LayoutTransition lt = layoutPositions.getLayoutTransition();
        if (lt != null) {
            lt.enableTransitionType(LayoutTransition.CHANGING);
        }

        layoutPositions.setVisibility(View.GONE);

        // Toggle dropdown
        cardInner.setOnClickListener(v -> {
            if (layoutPositions.getVisibility() == View.VISIBLE) {
                layoutPositions.setVisibility(View.GONE);
            } else {
                layoutPositions.setVisibility(View.VISIBLE);
            }
        });

        // Load positions
        List<Map<String, Object>> positions = (List<Map<String, Object>>) doc.get("positions");
        if (positions != null) {
            for (Map<String, Object> position : positions) {
                addPosition(layoutPositions, position);
            }
        }

        // Delete
        btnDelete.setOnClickListener(v -> {
            db.collection("clubs").document(doc.getId())
                    .delete()
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Club deleted!", Toast.LENGTH_SHORT).show();
                        layoutClubs.removeView(clubCard);
                    });
        });

        layoutClubs.addView(clubCard);
    }


    private void addPosition(LinearLayout layoutPositions, Map<String, Object> position) {
        View positionView = LayoutInflater.from(this)
                .inflate(R.layout.item_position_card, layoutPositions, false);

        TextView tvPositionName = positionView.findViewById(R.id.tvPositionName);
        LinearLayout layoutCandidates = positionView.findViewById(R.id.layoutCandidates);

        String posName = position.get("positionName") != null ? position.get("positionName").toString() : "Unknown Position";
        tvPositionName.setText(posName);

        layoutCandidates.setVisibility(View.GONE);
        positionView.setOnClickListener(v -> {
            if (layoutCandidates.getVisibility() == View.VISIBLE) {
                layoutCandidates.setVisibility(View.GONE);
            } else {
                layoutCandidates.setVisibility(View.VISIBLE);
            }
        });

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) position.get("candidates");
        if (candidates != null && !candidates.isEmpty()) {
            layoutCandidates.removeAllViews();
            for (Map<String, Object> candidate : candidates) {
                addCandidate(layoutCandidates, candidate);
            }
        }

        layoutPositions.addView(positionView);
    }

    private void addCandidate(LinearLayout layoutCandidates, Map<String, Object> candidate) {
        View candidateView = LayoutInflater.from(this)
                .inflate(R.layout.item_candidate_card, layoutCandidates, false);

        ImageView imgCandidate = candidateView.findViewById(R.id.imgCandidate);
        TextView tvName = candidateView.findViewById(R.id.tvCandidateName);
        TextView tvBrief = candidateView.findViewById(R.id.tvCandidateBrief);
        TextView tvVotes = candidateView.findViewById(R.id.tvVotes);

        // ✅ Safely handle missing values
        tvName.setText(candidate.get("name") != null ? candidate.get("name").toString() : "Unknown");
        tvBrief.setText(candidate.get("brief") != null ? candidate.get("brief").toString() : "No details");

        String imageUrl = candidate.get("imageUri") != null ? candidate.get("imageUri").toString() : "";

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.manager)   // temporary image
                    .error(R.drawable.manager)         // if image not found
                    .into(imgCandidate);
        } else {
            imgCandidate.setImageResource(R.drawable.manager);
        }


        Object votes = candidate.get("votes");
        int voteCount = 0;
        try {
            voteCount = votes != null ? Integer.parseInt(votes.toString()) : 0;
        } catch (NumberFormatException ignored) {}

        tvVotes.setText("Votes: " + voteCount);

        layoutCandidates.addView(candidateView);
    }
}
