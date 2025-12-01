package com.example.voterhub;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private EditText inputName, inputEmail, inputPassword;
    private Button btnSignup;
    private TextView tvLogin;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // 🔹 Firebase init
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 🔹 UI init
        inputName = findViewById(R.id.inputName);
        inputEmail = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        btnSignup = findViewById(R.id.btnSignup);
        tvLogin = findViewById(R.id.tvLogin);

        // 🔹 Go to Login page
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity2.class));
            finish();
        });

        // 🔹 Register button click
        btnSignup.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String name = inputName.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        // 🔸 Input validation
        if (TextUtils.isEmpty(name)) {
            inputName.setError("Enter your name");
            inputName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email)) {
            inputEmail.setError("Enter your email");
            inputEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            inputPassword.setError("Password must be at least 6 characters");
            inputPassword.requestFocus();
            return;
        }

        btnSignup.setEnabled(false);

        // 🔹 Firebase Signup
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    btnSignup.setEnabled(true);

                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();

                        // 🔹 Save user details in Firestore
                        Map<String, Object> user = new HashMap<>();
                        user.put("name", name);
                        user.put("email", email);
                        user.put("role", "voter");

                        db.collection("users").document(userId).set(user)
                                .addOnSuccessListener(aVoid -> {

                                    // ✅ Save locally using SharedPreferences
                                    SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                                    prefs.edit()
                                            .putString("userName", name)
                                            .putString("userEmail", email)
                                            .apply();

                                    // ✅ Move to Dashboard directly (no logout)
                                    Toast.makeText(SignupActivity.this,
                                            "Welcome, " + name + "! Account created successfully.",
                                            Toast.LENGTH_LONG).show();

                                    Intent intent = new Intent(SignupActivity.this, LoginActivity2.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(SignupActivity.this,
                                        "Error saving user: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
                    } else {
                        // 🔹 Handle duplicate account
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(SignupActivity.this,
                                    "This email is already registered! Please login instead.",
                                    Toast.LENGTH_LONG).show();

                            Intent intent = new Intent(SignupActivity.this, LoginActivity2.class);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(SignupActivity.this,
                                    "Signup failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
