package com.example.auth;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class ChatActivity extends BaseActivity implements View.OnClickListener, RecyclerViewAdapter.ItemClickListener {
    private FirebaseAuth mAuth;
    private TextInputEditText mMessageInput;
    RecyclerViewAdapter adapter;
    DatabaseReference mDB;
    String userEmail = null;
    String channelID = null;
    String pass;
    SecretKeySpec localKey;
    SQLiteDatabase localDB;
    SecretKeySpec channelKey;
    ArrayList<String> messages;
    Boolean messExists = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get Firebase Authentication Instance
        mAuth = FirebaseAuth.getInstance();

        // Get Firebase Database Reference
        mDB = FirebaseDatabase.getInstance().getReference();

        // Get Local Database Reference
        localDB = openOrCreateDatabase(mAuth.getUid(),MODE_PRIVATE,null);

        // Display Channel Name
        String channelName = getIntent().getExtras().getString("channelName");
        TextView textView = findViewById(R.id.chatName);
        textView.setText(channelName);

        // Store Channel ID
        channelID = getIntent().getExtras().getString("channelID");

        // Get Local Symmetric Key
        pass = getIntent().getExtras().getString("pass");
        localKey = Crypto.keyFromString(pass);

        // Get Channel Symmetric Key
        Cursor keyCursor = localDB.rawQuery("Select SymmetricKey from Channels where ChannelID = '" + channelID + "'",null);
        keyCursor.moveToFirst();
        channelKey = Crypto.loadSymmetricKey(Crypto.symmetricDecrypt(keyCursor.getString(0), localKey));


        // Get User Email
        getEmail();

        findViewById(R.id.import_file_button).setOnClickListener(this);
        findViewById(R.id.message_back_button).setOnClickListener(this);
        findViewById(R.id.message_submit_button).setOnClickListener(this);

        mMessageInput = findViewById(R.id.messageData);

        syncMessages();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.message_back_button:
                finish();
                break;
            case R.id.message_submit_button:
                hideKeyboard(this);
                if (TextUtils.isEmpty(mMessageInput.getText().toString())) {
                    Toast.makeText(
                            ChatActivity.this, "Message cannot be empty.", Toast.LENGTH_LONG
                    ).show();
                } else {
                    // Create and Update Message
                    try {
                        establishMessage();
                        mMessageInput.setText("");
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.import_file_button:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, 7);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub

        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            case 7:

                if (resultCode == RESULT_OK) {

                    String PathHolder = data.getData().getPath();

                    Toast.makeText(this, PathHolder, Toast.LENGTH_LONG).show();

                }
                break;

        }
    }


    @Override
    public void onItemClick(View view, int position) {

    }

    public void establishMessage() throws GeneralSecurityException {
        DatabaseReference messageDB = FirebaseDatabase.getInstance().getReference();
        ChatActivity.Message newMessage = createMessage();
        messageDB.child("Messages").child(channelID).child(newMessage.message_id).setValue(newMessage);

    }

    // Generates a New Channel Object from User Input and Encrypts with Public Key
    public ChatActivity.Message createMessage() throws GeneralSecurityException {

        // Get Timestamp
        long unixTime = System.currentTimeMillis() / 1000;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM d, yyyy hh:mm a");
        String format = simpleDateFormat.format(new Date());

        // Get Channel Name and Encrypt
        String message = Crypto.symmetricEncrypt(mMessageInput.getText().toString() + "\n\n - " + userEmail + "\n @ " + format, channelKey);

        // Generate a Message ID and Check to See if ID is in Use
        String messageID = Crypto.generateRanString(20);
        while(messageExists(messageID)) {
            messageID = Crypto.generateRanString(20);
        }

        // Create Channel Object
        return new ChatActivity.Message(messageID, message, unixTime);
    }

    // Checks to See if Generated Channel ID is Already in Use
    public Boolean messageExists(String messageID) {
        messExists = false;
        final String messIDTest = messageID;
        final DatabaseReference channelDB = FirebaseDatabase.getInstance().getReference().child("MessageList");
        channelDB.orderByKey().equalTo(messageID)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Log.d("TESTLOG", "Message ID in use.");
                            messExists = true;
                        } else {
                            Log.d("TESTLOG", "Message ID not in use.");
                            channelDB.child(messIDTest).setValue("true");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) { }
                });
        return messExists;
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

    // Retrieve Differences in Local and Firebase Databases, and Update Local Database and Channel List
    public void syncMessages() {

        // Set Up Listener to Monitor For Change in Messages
        final DatabaseReference channelDB = FirebaseDatabase.getInstance().getReference().child("Messages").child(channelID);
        channelDB.orderByKey().addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                // Get Data for Each Message

                String messageID = dataSnapshot.child("message_id").getValue().toString();
                String messageData = dataSnapshot.child("message_data").getValue().toString();
                Integer timeStamp = Integer.parseInt(dataSnapshot.child("time_stamp").getValue().toString());

                // Decrypt Channel Data
                messageData = Crypto.symmetricDecrypt(messageData, channelKey);

                // Add Message Data to Local Database if Not Present
                Cursor currentMessages = localDB.rawQuery("Select * from Messages WHERE MessageID = '" + messageID + "'", null);
                if (currentMessages.getCount() <= 0) {
                    addMessage(messageID, messageData, timeStamp);
                }
                currentMessages.close();

                // Update the Visible List of Channels
                updateUI();

            }
            @Override
            public void onCancelled(DatabaseError databaseError) { }
            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) { }
            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) { }
            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) { }
        });
    }
    // Adds a New Message to the Local Database
    public void addMessage(String messageID, String message, Integer timestamp) {
        localDB.execSQL("INSERT INTO Messages (MessageID, ChannelID, Message, TimeStamp) " +
                "VALUES('" + messageID + "','" + channelID + "','" + Crypto.symmetricEncrypt(message, localKey) + "','" + timestamp + "');");
    }

    // Class to Contain Message Data
    public class Message {
        public String message_id;
        public String message_data;
        public Long time_stamp;

        public Message(String messageID, String messageData, Long timeStamp) {
            this.message_id = messageID;
            this.message_data = messageData;
            this.time_stamp = timeStamp;
        }
    }

    // Updates the Visible List of Channels
    public void updateUI() {

        // Data to Populate the RecyclerView List
        messages = new ArrayList<>();

        // Unencrypt and Populate Channel Data from Database
        // Sort Descending By Most Recently Updated Channels
        Cursor messagesFromDB = localDB.rawQuery("Select * from Messages WHERE ChannelID = '" + channelID +"' ORDER BY TimeStamp ASC",null);
        while(messagesFromDB.moveToNext()) {
            String message = Crypto.symmetricDecrypt(messagesFromDB.getString(2), localKey);
            if(!(message == null) && !message.equals("")) {
                messages.add(message);
                Log.e("TESTLOG", "Found: " + message);
            }
        }
        messagesFromDB.close();
        Log.e("TESTLOG", "Messages: " + messages.size());

        // Generate a New RecyclerView with Updated Data
        RecyclerView recyclerView = findViewById(R.id.message_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerViewAdapter(this, messages);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL));
    }


}
