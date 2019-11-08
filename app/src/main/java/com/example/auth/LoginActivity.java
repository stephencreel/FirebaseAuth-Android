package com.example.auth;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.textfield.TextInputLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import javax.crypto.spec.SecretKeySpec;

public class LoginActivity extends BaseActivity implements View.OnClickListener {
	private static final String TAG = "EmailPasswordActivity";
	private EditText mEdtEmail, mEdtPassword;
	private FirebaseAuth mAuth;
	private TextView mTextViewProfile;
	private TextInputLayout mLayoutEmail, mLayoutPassword;
	String pass;
	SecretKeySpec localKey;
	DatabaseReference mDB;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		mTextViewProfile = findViewById(R.id.profile);
		mEdtEmail = findViewById(R.id.edt_email);
		mEdtPassword = findViewById(R.id.edt_password);
		mLayoutEmail = findViewById(R.id.layout_email);
		mLayoutPassword = findViewById(R.id.layout_password);

		// Set Click Listeners for Buttons
		findViewById(R.id.login_create_account_button).setOnClickListener(this);
		findViewById(R.id.login_sign_in_button).setOnClickListener(this);

		mAuth = FirebaseAuth.getInstance();

		// Get Database Reference
		mDB = FirebaseDatabase.getInstance().getReference();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.login_create_account_button:
				hideKeyboard(this);
				createAccount(mEdtEmail.getText().toString(), mEdtPassword.getText().toString());
				break;
			case R.id.login_sign_in_button:
				hideKeyboard(this);
				signIn(mEdtEmail.getText().toString(), mEdtPassword.getText().toString());
				break;
		}
	}

	// Allows User to Create Account with Email and Password
	private void createAccount(String email, String password) {
		// If Input Forms are Invalid, Notify User of Error
		if (!validateForm()) {
			return;
		}
		pass = password;
		final String userEmail = email;
		showProgressDialog();
		mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
			@Override
			public void onComplete(@NonNull Task<AuthResult> task) {
				// If Account Creation Unsuccessful, Notify User of Error
				if (!task.isSuccessful()) {
					mTextViewProfile.setTextColor(Color.RED);
					mTextViewProfile.setText(task.getException().getMessage());
				}
				// If Account Creation Successful, Set Up Database and Request Email Verification
				else {
					mTextViewProfile.setText("");

					// Generate User's Local Symmetric Key from Password
					localKey = Crypto.keyFromString(pass);

					// Create Local SQLite Database With User ID As Name and Generate Tables
					SQLiteDatabase localDB = openOrCreateDatabase(mAuth.getUid(),MODE_PRIVATE,null);

					// TODO Drop Tables for Testing
					localDB.execSQL("DROP TABLE IF EXISTS Channels");
					localDB.execSQL("DROP TABLE IF EXISTS Messages");

					localDB.execSQL("CREATE TABLE IF NOT EXISTS AsymKeys(PublicKey TEXT,PrivateKey TEXT);");
					localDB.execSQL("CREATE TABLE IF NOT EXISTS Channels(ChannelID TEXT PRIMARY KEY, ChannelName TEXT, SymmetricKey TEXT, LastUpdate INTEGER);");
					localDB.execSQL("CREATE TABLE IF NOT EXISTS Messages(MessageID TEXT PRIMARY KEY, ChannelID TEXT, Message TEXT, UserData TEXT, TimeSTAMP INTEGER, IsFile BOOLEAN);");
					// Generate RSA Public/Private Key Pair, Encrypt with AES, and Store in Local Database and Firebase Database
					String prvEncrypt = null;
					String pubEncrypt = null;
					KeyPair asymKeys = Crypto.generateKeyPair();
					try {
						String prvString = Crypto.savePrivateKey(asymKeys.getPrivate());
						String pubString = Crypto.savePublicKey(asymKeys.getPublic());
						prvEncrypt = Crypto.symmetricEncrypt(prvString, localKey);
						pubEncrypt = Crypto.symmetricEncrypt(pubString, localKey);
					} catch (GeneralSecurityException e) {
						e.printStackTrace();
					}
					localDB.execSQL("INSERT INTO AsymKeys VALUES('" + pubEncrypt + "','" + prvEncrypt + "');");
					try {
						mDB.child("PrivateData").child(mAuth.getUid()).child("public_key").setValue(Crypto.symmetricEncrypt(Crypto.savePublicKey(asymKeys.getPublic()), localKey));
						mDB.child("PrivateData").child(mAuth.getUid()).child("private_key").setValue(Crypto.symmetricEncrypt(Crypto.savePrivateKey(asymKeys.getPrivate()), localKey));
					} catch (GeneralSecurityException e) {
						e.printStackTrace();
					}

					// Store User Data and Public Key in the Firebase Database
					try {
						mDB.child("Users").child(mAuth.getUid()).child("public_key").setValue(Crypto.savePublicKey(asymKeys.getPublic()));
						mDB.child("Users").child(mAuth.getUid()).child("email").setValue(userEmail);
						mDB.child("Channels").child(mAuth.getUid()).setValue("True");
					} catch (GeneralSecurityException e) {
						e.printStackTrace();
					}

					// Generation Verification Email and Sign Out of Account
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
		pass = password;
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

					// Generate User's Local Symmetric Key from Password
					localKey = Crypto.keyFromString(pass);

					// Ensure Local Database and Tables are Created (In Case User Logs in on New Device)
					SQLiteDatabase localDB = openOrCreateDatabase(mAuth.getUid(),MODE_PRIVATE,null);

					// TODO Drop Tables for Testing
					localDB.execSQL("DROP TABLE IF EXISTS Channels");
					localDB.execSQL("DROP TABLE IF EXISTS Messages");

					localDB.execSQL("CREATE TABLE IF NOT EXISTS AsymKeys(PublicKey TEXT,PrivateKey TEXT);");
					localDB.execSQL("CREATE TABLE IF NOT EXISTS Channels(ChannelID TEXT PRIMARY KEY, ChannelName TEXT, SymmetricKey TEXT, LastUpdate INTEGER);");
					localDB.execSQL("CREATE TABLE IF NOT EXISTS Messages(MessageID TEXT PRIMARY KEY, ChannelID TEXT, Message TEXT, UserData TEXT, TimeSTAMP INTEGER, IsFile BOOLEAN);");

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

	// Initiates Main Chat App Activity
	private void gotoMain() {
		startActivity(new Intent(this, MainActivity.class).putExtra("pass", pass));
	}

	// Sends Verification Email to User upon Account Creation
	private void sendVerify() {
		final FirebaseUser firebaseUser = mAuth.getCurrentUser();
		firebaseUser.sendEmailVerification().addOnCompleteListener(this, new OnCompleteListener<Void>() {
			@Override
			public void onComplete(@NonNull Task<Void> task) {
				if (task.isSuccessful()) {
					Toast.makeText(
							LoginActivity.this, "Verification email sent to " + firebaseUser.getEmail(), Toast.LENGTH_LONG
					).show();
				} else {
					Toast.makeText(LoginActivity.this, task.getException().getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		});
	}

}