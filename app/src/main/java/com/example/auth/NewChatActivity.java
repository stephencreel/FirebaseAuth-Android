package com.example.auth;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Bundle;
import android.service.autofill.UserData;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.GeneralSecurityException;
import java.util.ArrayList;

public class NewChatActivity extends BaseActivity implements View.OnClickListener,RecyclerViewAdapter.ItemClickListener {
    private FirebaseAuth mAuth;
    ArrayList<String> emails = new ArrayList<>();
    ArrayList<userData> savedUsers = new ArrayList<>();
    ArrayList<userData> userList = new ArrayList<>();
    RecyclerViewAdapter adapter;
    DatabaseReference mDB;
    String userEmail = null;
    Boolean chanExists = false;
    private TextInputEditText mChatName, mUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_chat);

        // Get Firebase Authentication Instance
        mAuth = FirebaseAuth.getInstance();

        // Get Firebase Database Reference
        mDB = FirebaseDatabase.getInstance().getReference();

        mChatName = findViewById(R.id.chatNameInput);
        mUserName = findViewById(R.id.userInput);

        findViewById(R.id.new_chat_back_button).setOnClickListener(this);
        findViewById(R.id.new_chat_add_button).setOnClickListener(this);
        findViewById(R.id.addUser).setOnClickListener(this);

        getEmail();

        getUsers();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.new_chat_back_button:
                finish();
                break;
            case R.id.new_chat_add_button:
                if(emails.isEmpty()){
                    Toast.makeText(
                            NewChatActivity.this, "Must add at least one user.", Toast.LENGTH_LONG
                    ).show();
                }
                else if (TextUtils.isEmpty(mChatName.getText().toString())) {
                    Toast.makeText(
                            NewChatActivity.this, "Chat name required.", Toast.LENGTH_LONG
                    ).show();
                }
                else {
                    // Create and Update Channels
                    try {
                        Log.d("LOG", "Trying to establish channels.");
                        establishChannels();
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                    }
                }
                finish();
                break;

            case R.id.addUser:
                if(TextUtils.isEmpty(mUserName.getText().toString())) {
                    Toast.makeText(
                            NewChatActivity.this, "Chat name empty.", Toast.LENGTH_LONG
                    ).show();
                }
                hideKeyboard(this);
                addUser(mUserName.getText().toString());
                mUserName.setText("");
                updateUI();
                break;
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        String email = adapter.getItem(position);
        emails.remove(email);
        for(userData user : savedUsers) {
            if(user.email.equals(email)) {
                savedUsers.remove(user);
            }
        }
        updateUI();
    }

    public void establishChannels() throws GeneralSecurityException {
        // Generate a Channel ID and Check to See if ID is in Use
        String channelID = Crypto.generateRanString(20);
        Log.d("LOG", "Trying channel ID: " + channelID);
        while(channelExists(channelID)) {
            channelID = Crypto.generateRanString(20);
        }

        // Generate a Symmetric Key for the Channel and Encrypt
        String symmKey = Crypto.generateSymmetricKey();

        DatabaseReference channelDB = FirebaseDatabase.getInstance().getReference();
        addUser(userEmail);
        for(userData user : savedUsers) {
            Channel newChannel = createChannel(channelID, symmKey, user.publicKey);
            channelDB.child("Channels").child(user.userID).child(newChannel.channel_id).setValue(newChannel);
        }
    }


    // Generates a New Channel Object from User Input and Encrypts with Public Key
    public Channel createChannel(String channelID, String symmKey, String publicKey) throws GeneralSecurityException {
        // Get Channel Name and Encrypt
        String channelName = Crypto.RSAEncrypt(mChatName.getText().toString(), Crypto.loadPublicKey(publicKey));

        // Encrypt Symmetric Key
        String channelKey = Crypto.RSAEncrypt(symmKey, Crypto.loadPublicKey(publicKey));

        // Create Channel Object
        return new Channel(channelID, channelName, channelKey);
    }

    // Checks to See if Generated Channel ID is Already in Use
    public Boolean channelExists(String channelID) {
        chanExists = false;
        final String chanIDTest = channelID;
        final DatabaseReference channelDB = FirebaseDatabase.getInstance().getReference().child("ChannelList");
        channelDB.orderByKey().equalTo(channelID)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Log.d("LOG", "Channel ID in use.");
                            chanExists = true;
                        } else {
                            Log.d("LOG", "Channel ID not in use.");
                            channelDB.child(chanIDTest).setValue("true");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) { }
                });
        return chanExists;
    }

    public void getEmail() {
        showProgressDialog();
        mDB.child("Users").child(mAuth.getUid()).orderByKey()
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        userEmail = dataSnapshot.child("email").getValue().toString();
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) { }
                });
        hideProgressDialog();
    }

    // Adds Valid User to List
    public void addUser(String email) {
        Boolean found = false;
        for(userData user : userList) {
            // If a User with Input Email Exists
            if (user.email.equals(email)) {
                // And the User List Doesn't Already Contain that Email
                if(emails.contains(email)) {
                    // Don't Toast if Email is User's Own Email
                    if(!email.equals(userEmail)) {
                        Toast.makeText(
                                NewChatActivity.this, "User already selected.", Toast.LENGTH_LONG
                        ).show();
                    }
                }
                else {
                    emails.add(email);
                    savedUsers.add(user);
                    updateUI();
                }
                found = true;
                break;
            }
        }
        if(!found) {
            Toast.makeText(
                    NewChatActivity.this, "User not found.", Toast.LENGTH_LONG
            ).show();
        }
    }

    // Retrieves All Users and User Data from Firebase Database
    public void getUsers() {
        userList = new ArrayList<>();
        // Search Firebase Database for Users
        final DatabaseReference userDB = FirebaseDatabase.getInstance().getReference().child("Users");
        ChildEventListener childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                // Add Each UserID/Email Pair to List
                String currentEmail = dataSnapshot.child("email").getValue().toString();
                String currentID = dataSnapshot.getKey().toString();
                String currentKey = dataSnapshot.child("public_key").getValue().toString();
                userData currentUser = new userData(currentEmail, currentID, currentKey);
                userList.add(currentUser);
            }
            @Override
            public void onCancelled(DatabaseError databaseError) { }
            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) { }
            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) { }
            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) { }
        };
        userDB.orderByValue().addChildEventListener(childEventListener);
    }

    // Class to Contain User Data
    public class userData {
        public String email;
        public String userID;
        public String publicKey;

        public userData(String email, String userID, String publicKey){
            this.email = email;
            this.userID = userID;
            this.publicKey = publicKey;
        }
    }

    // Class to Contain Channel Data
    public class Channel {
        public String channel_id;
        public String channel_name;
        public String symmetric_key;

        public Channel(String channelID, String channelName, String channelKey) {
            this.channel_id = channelID;
            this.symmetric_key = channelKey;
            this.channel_name = channelName;
        }
    }

    // Updates the Visible List of Channels
    public void updateUI() {

        // Generate a New RecyclerView with Updated Data
        RecyclerView recyclerView = findViewById(R.id.message_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerViewAdapter(this, emails);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL));
    }
}
