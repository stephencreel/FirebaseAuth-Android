package com.example.auth;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.textfield.TextInputLayout;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.spec.SecretKeySpec;

import static com.example.auth.Crypto.generateKeyPair;

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

		findViewById(R.id.login_create_account_button).setOnClickListener(this);
		findViewById(R.id.login_sign_in_button).setOnClickListener(this);

		mAuth = FirebaseAuth.getInstance();

		// Get Database Reference
		mDB = FirebaseDatabase.getInstance().getReference();

		startActivity(new Intent(this, NewChatActivity.class));

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
					localDB.execSQL("CREATE TABLE IF NOT EXISTS AsymKeys(PublicKey TEXT,PrivateKey TEXT);");
					localDB.execSQL("CREATE TABLE IF NOT EXISTS Channels(ChannelID INTEGER PRIMARY KEY AUTOINCREMENT,ChannelName TEXT, SymmetricKey TEXT);");

					// Generate RSA Public/Private Key Pair, Encrypt with AES, and Store in Local Database
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

					// TODO Begin Test Block
						// Test if Key Storage Successful
						// Tested and Working as of 10/29/19 (Stephen)
						Cursor keysFromDB = localDB.rawQuery("Select * from AsymKeys",null);
						keysFromDB.moveToFirst();
						try {
							String encryptTest = Crypto.RSAEncrypt("Test message", asymKeys.getPublic());
							PrivateKey prvFromDB = Crypto.loadPrivateKey(Crypto.symmetricDecrypt((keysFromDB.getString(1)), localKey));
							String decryptTest = Crypto.RSADecrypt(encryptTest, prvFromDB);
							if (decryptTest.equals("Test message")) {
								Log.d("LOG", "Key storage successful.");
							}
						} catch (GeneralSecurityException e) {
							e.printStackTrace();
						}
					// TODO End Test Block

					// Store Public Key in the Firebase Database
					try {
						mDB.child("PublicKeys").child(mAuth.getUid()).setValue(Crypto.savePublicKey(asymKeys.getPublic()));
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

					// TODO We need to find some way of backing up all user data
					// TODO in the Firebase database encrypted with local symmetric key

					mTextViewProfile.setText("");
					gotoMain(localKey);

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
	private void gotoMain(SecretKeySpec localKey) {
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