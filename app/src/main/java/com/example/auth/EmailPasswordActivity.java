package com.example.auth;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AlertDialog;

import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.InputStream;
import java.net.URL;

public class EmailPasswordActivity extends BaseActivity implements View.OnClickListener {
	private static final String TAG = "EmailPasswordActivity";
	private EditText mEdtEmail, mEdtPassword;
	private FirebaseAuth mAuth;
	private TextView mTextViewProfile;
	private TextInputLayout mLayoutEmail, mLayoutPassword;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_emailpassword);

		mTextViewProfile = findViewById(R.id.profile);
		mEdtEmail = findViewById(R.id.edt_email);
		mEdtPassword = findViewById(R.id.edt_password);
		mLayoutEmail = findViewById(R.id.layout_email);
		mLayoutPassword = findViewById(R.id.layout_password);

		findViewById(R.id.email_sign_in_button).setOnClickListener(this);
		findViewById(R.id.email_create_account_button).setOnClickListener(this);

		mAuth = FirebaseAuth.getInstance();

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.email_create_account_button:
				hideKeyboard(this);
				createAccount(mEdtEmail.getText().toString(), mEdtPassword.getText().toString());
				break;
			case R.id.email_sign_in_button:
				hideKeyboard(this);
				signIn(mEdtEmail.getText().toString(), mEdtPassword.getText().toString());
				break;
		}
	}

	private void createAccount(String email, String password) {
		if (!validateForm()) {
			return;
		}
		showProgressDialog();
		mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
			@Override
			public void onComplete(@NonNull Task<AuthResult> task) {
				if (!task.isSuccessful()) {
					mTextViewProfile.setTextColor(Color.RED);
					mTextViewProfile.setText(task.getException().getMessage());
				} else {
					mTextViewProfile.setText("");
					sendVerify();
					mAuth.signOut();
				}
				hideProgressDialog();
			}
		});
	}

	private void signIn(String email, String password) {
		if (!validateForm()) {
			return;
		}
		showProgressDialog();
		mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
			@Override
			public void onComplete(@NonNull Task<AuthResult> task) {
				Log.d(TAG, "signInWithEmail:onComplete:" + task.isSuccessful());
				if (!task.isSuccessful()) {
					mTextViewProfile.setTextColor(Color.RED);
					mTextViewProfile.setText(task.getException().getMessage());
				} else if (!mAuth.getCurrentUser().isEmailVerified()) {
					mTextViewProfile.setText("Please verify account via email.");
					mAuth.signOut();
				} else {
					mTextViewProfile.setText("");
					gotoMain();

				}
				hideProgressDialog();
			}
		});
	}

	// Used To Validate Input Entries
	private boolean validateForm() {
		if (TextUtils.isEmpty(mEdtEmail.getText().toString())) {
			mLayoutEmail.setError("Required.");
			return false;
		} else if (TextUtils.isEmpty(mEdtPassword.getText().toString())) {
			mLayoutPassword.setError("Required.");
			return false;
		} else {
			mLayoutEmail.setError(null);
			mLayoutPassword.setError(null);
			return true;
		}
	}

	// Hides the Keyboard on OnClick Event to Better Display Error Messages
	public static void hideKeyboard(Activity activity) {
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
		View view = activity.getCurrentFocus();
		if (view == null) {
			view = new View(activity);
		}
		imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
	}

	// Initiates Main Chat App Activity
	private void gotoMain() {
		startActivity(new Intent(this, ChatActivity.class));
	}

	private void sendVerify() {
		final FirebaseUser firebaseUser = mAuth.getCurrentUser();
		firebaseUser.sendEmailVerification().addOnCompleteListener(this, new OnCompleteListener<Void>() {
			@Override
			public void onComplete(@NonNull Task<Void> task) {
				if (task.isSuccessful()) {
					Toast.makeText(
							EmailPasswordActivity.this, "Verification email sent to " + firebaseUser.getEmail(), Toast.LENGTH_LONG
					).show();
				} else {
					Toast.makeText(EmailPasswordActivity.this, task.getException().getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		});
	}

}