package com.example.voterhub;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateClubActivity extends AppCompatActivity {

    private LinearLayout layoutPositions;
    private EditText etClubName, etClubDescription;

    private FirebaseFirestore db;

    private ImageView targetImageView;   // stores what imageView was clicked
    private Uri selectedImageUri;        // stores selected candidate image

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_club);

        db = FirebaseFirestore.getInstance();

        etClubName = findViewById(R.id.etClubName);
        etClubDescription = findViewById(R.id.etClubDescription);
        layoutPositions = findViewById(R.id.layoutPositions);

        Button btnAddPosition = findViewById(R.id.btnAddPosition);
        Button btnSaveClub = findViewById(R.id.btnSaveClub);
        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        btnAddPosition.setOnClickListener(v -> addPosition());
        btnSaveClub.setOnClickListener(v -> saveClub());
    }

    // ------------------ ADD POSITION ------------------
    private void addPosition() {
        View positionView = LayoutInflater.from(this)
                .inflate(R.layout.item_position, layoutPositions, false);

        Button btnAddCandidate = positionView.findViewById(R.id.btnAddCandidate);
        LinearLayout layoutCandidates = positionView.findViewById(R.id.layoutCandidates);

        btnAddCandidate.setOnClickListener(v -> addCandidate(layoutCandidates));

        layoutPositions.addView(positionView);
    }

    // ------------------ ADD CANDIDATE ------------------
    private void addCandidate(LinearLayout layoutCandidates) {
        View candidateView = LayoutInflater.from(this)
                .inflate(R.layout.item_candidate, layoutCandidates, false);

        ImageView imgCandidate = candidateView.findViewById(R.id.imgCandidate);
        Button btnDeleteCandidate = candidateView.findViewById(R.id.btnDeleteCandidate);

        imgCandidate.setOnClickListener(v -> pickImage(imgCandidate));
        btnDeleteCandidate.setOnClickListener(v -> layoutCandidates.removeView(candidateView));

        layoutCandidates.addView(candidateView);
    }

    // ------------------ IMAGE PICKER ------------------
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {

                    selectedImageUri = result.getData().getData();

                    if (selectedImageUri != null && targetImageView != null) {
                        targetImageView.setImageURI(selectedImageUri);
                        targetImageView.setTag(selectedImageUri.toString());
                    }
                }
            });

    private void pickImage(ImageView imgView) {
        targetImageView = imgView;

        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        imagePickerLauncher.launch(intent);
    }

    // ------------------ SAVE CLUB ------------------
    private void saveClub() {

        String clubName = etClubName.getText().toString().trim();
        String clubDescription = etClubDescription.getText().toString().trim();

        if (clubName.isEmpty() || clubDescription.isEmpty()) {
            Toast.makeText(this, "Please fill all details!", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Map<String, Object>> positionsList = new ArrayList<>();

        // Loop positions
        for (int i = 0; i < layoutPositions.getChildCount(); i++) {

            View positionView = layoutPositions.getChildAt(i);

            EditText etPositionName = positionView.findViewById(R.id.etPositionName);
            LinearLayout layoutCandidates = positionView.findViewById(R.id.layoutCandidates);

            String positionName = etPositionName.getText().toString().trim();
            if (positionName.isEmpty()) continue;

            ArrayList<Map<String, Object>> candidateList = new ArrayList<>();

            // Loop candidates
            for (int j = 0; j < layoutCandidates.getChildCount(); j++) {

                View candidateView = layoutCandidates.getChildAt(j);

                EditText etName = candidateView.findViewById(R.id.etCandidateName);
                EditText etBrief = candidateView.findViewById(R.id.etCandidateBrief);
                ImageView imgCandidate = candidateView.findViewById(R.id.imgCandidate);

                String name = etName.getText().toString().trim();
                String brief = etBrief.getText().toString().trim();
                String imageUri = imgCandidate.getTag() != null ? imgCandidate.getTag().toString() : "";

                if (!name.isEmpty()) {
                    Map<String, Object> candidateMap = new HashMap<>();
                    candidateMap.put("name", name);
                    candidateMap.put("brief", brief);
                    candidateMap.put("votes", 0);

                    // ADD THIS ❗ imageUri empty → save empty string
                    candidateMap.put("imageUri", imageUri);

                    candidateList.add(candidateMap);
                }
            }

            Map<String, Object> posData = new HashMap<>();
            posData.put("positionName", positionName);
            posData.put("candidates", candidateList);

            positionsList.add(posData);
        }

        // FINAL CLUB DATA
        Map<String, Object> clubData = new HashMap<>();
        clubData.put("clubName", clubName);
        clubData.put("clubDescription", clubDescription);
        clubData.put("positions", positionsList);

        db.collection("clubs").add(clubData)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Club Created Successfully!", Toast.LENGTH_SHORT).show();
                    resetForm();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ------------------ RESET FORM ------------------
    private void resetForm() {
        etClubName.setText("");
        etClubDescription.setText("");
        layoutPositions.removeAllViews();
    }
}
