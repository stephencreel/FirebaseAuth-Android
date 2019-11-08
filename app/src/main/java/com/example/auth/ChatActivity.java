package com.example.auth;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;

public class ChatActivity extends BaseActivity implements View.OnClickListener, RecyclerViewAdapter.ItemClickListener {
    private FirebaseAuth mAuth;
    private TextInputEditText mMessageInput;
    RecyclerViewAdapter adapter;
    DatabaseReference mDB;
    FirebaseStorage storage;
    String userEmail = null;
    String channelID = null;
    String pass;
    SecretKeySpec localKey;
    SQLiteDatabase localDB;
    SecretKeySpec channelKey;
    ArrayList<String> messages;
    ArrayList<FileData> files;
    Boolean messExists = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get Firebase Authentication Instance
        mAuth = FirebaseAuth.getInstance();

        // Get Firebase Database Reference
        mDB = FirebaseDatabase.getInstance().getReference();

        // Get Firebase Storage Reference
        storage = FirebaseStorage.getInstance();

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

        // Set Button Clcikc Listeners
        findViewById(R.id.import_file_button).setOnClickListener(this);
        findViewById(R.id.message_back_button).setOnClickListener(this);
        findViewById(R.id.message_submit_button).setOnClickListener(this);

        // Set Variable for Message Data
        mMessageInput = findViewById(R.id.messageData);

        // Sync Messages with Firebase Database
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
    public void onItemClick(View view, int position) {
        // Check to See if Clicked Message is File
        for(final FileData file : files) {
            if(file.fileString.equals(adapter.getItem(position))) {
                Toast.makeText(
                        ChatActivity.this, "Downloading file to root Android internal storage folder.", Toast.LENGTH_LONG
                ).show();

                if (Build.VERSION.SDK_INT >= 23) {
                    int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    }
                }

                StorageReference storageRef = storage.getReference().child(file.fileID);
                final long ONE_MEGABYTE = 1024 * 1024;
                storageRef.child(file.fileName).getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                    @Override
                    public void onSuccess(byte[] bytes) {
                        Log.d("TESTLOG", "File downloaded.");
                        byte[] decBytes = null;
                        try {
                            decBytes = Crypto.decodeFile(bytes, channelKey);
                            File newFile = new File(Environment.getExternalStorageDirectory(), file.fileName);
                            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(newFile));
                            bos.write(decBytes);
                            bos.flush();
                            bos.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.d("TESTLOG", "Could not download file.");
                    }
                });

            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            case 7:

                if (resultCode == RESULT_OK) {

                    String PathHolder = data.getData().getPath();

                    Toast.makeText(this, PathHolder, Toast.LENGTH_LONG).show();

                    String sdPath = PathHolder.substring(PathHolder.indexOf(":") + 1, PathHolder.length());

                    String fileName = PathHolder.substring(PathHolder.lastIndexOf("/") + 1, PathHolder.length());

                    if (Build.VERSION.SDK_INT >= 23) {
                        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
                        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                        }
                    }

                    FileInputStream fis = null;
                    byte[] bytes = null;
                    try {
                        fis = new FileInputStream(sdPath);
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] b = new byte[1024];

                        for (int readNum; (readNum = fis.read(b)) != -1;) {
                            bos.write(b, 0, readNum);
                        }
                        bytes = bos.toByteArray();
                        bytes = Crypto.encodeFile(bytes, channelKey);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    ChatActivity.Message message = createFileMessage(fileName);
                    establishFileMessage(message);

                    StorageReference storageRef = storage.getReference().child(message.message_id);
                    UploadTask uploadTask = storageRef.child(fileName).putBytes(bytes);
                    uploadTask.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            Log.d("LOGTEST", "Failed to upload.");
                        }
                    }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Log.d("LOGTEST", "Uploaded file.");
                        }
                    });
                }
                break;

        }
    }

    public void establishMessage() throws GeneralSecurityException {
        DatabaseReference messageDB = FirebaseDatabase.getInstance().getReference();
        ChatActivity.Message newMessage = createMessage();
        messageDB.child("Messages").child(channelID).child(newMessage.message_id).setValue(newMessage);
    }

    public void establishFileMessage(Message message) {
        DatabaseReference messageDB = FirebaseDatabase.getInstance().getReference();
        messageDB.child("Messages").child(channelID).child(message.message_id).setValue(message);
    }

    public ChatActivity.Message createFileMessage(String fileName) {

        // Get Timestamp
        long unixTime = System.currentTimeMillis() / 1000;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM d, yyyy hh:mm a");
        String format = simpleDateFormat.format(new Date());

        // Get Channel Name and Encrypt
        String message = Crypto.symmetricEncrypt(fileName, channelKey);

        // Get User Data and Encrypt
        String userData = Crypto.symmetricEncrypt("\n\n - " + userEmail + "\n @ " + format, channelKey);

        // Generate a Message ID and Check to See if ID is in Use
        String messageID = Crypto.generateRanString(20);
        while(messageExists(messageID)) {
            messageID = Crypto.generateRanString(20);
        }

        return new ChatActivity.Message(messageID, message, userData, unixTime, true);
    }

    // Generates a New Channel Object from User Input and Encrypts with Public Key
    public ChatActivity.Message createMessage() throws GeneralSecurityException {

        // Get Timestamp
        long unixTime = System.currentTimeMillis() / 1000;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM d, yyyy hh:mm a");
        String format = simpleDateFormat.format(new Date());

        // Get Channel Name and Encrypt
        String message = Crypto.symmetricEncrypt(mMessageInput.getText().toString(), channelKey);

        // Get User Data and Encrypt
        String userData = Crypto.symmetricEncrypt("\n\n - " + userEmail + "\n @ " + format, channelKey);

        // Generate a Message ID and Check to See if ID is in Use
        String messageID = Crypto.generateRanString(20);
        while(messageExists(messageID)) {
            messageID = Crypto.generateRanString(20);
        }

        // Create Channel Object
        return new ChatActivity.Message(messageID, message, userData, unixTime, false);
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
                String userData = dataSnapshot.child("user_data").getValue().toString();
                Integer timeStamp = Integer.parseInt(dataSnapshot.child("time_stamp").getValue().toString());
                Boolean isFile = Boolean.parseBoolean(dataSnapshot.child("is_file").getValue().toString());

                // Decrypt Channel Data
                messageData = Crypto.symmetricDecrypt(messageData, channelKey);
                userData = Crypto.symmetricDecrypt(userData, channelKey);

                // Add Message Data to Local Database if Not Present
                Cursor currentMessages = localDB.rawQuery("Select * from Messages WHERE MessageID = '" + messageID + "'", null);
                if (currentMessages.getCount() <= 0) {
                    addMessage(messageID, messageData, userData, timeStamp, isFile);
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
    public void addMessage(String messageID, String message, String userData, Integer timestamp, Boolean isFile) {
        localDB.execSQL("INSERT INTO Messages (MessageID, ChannelID, Message, UserData, TimeStamp, IsFile) " +
                "VALUES('" + messageID + "','" + channelID + "','" + Crypto.symmetricEncrypt(message, localKey) + "','" +
                Crypto.symmetricEncrypt(userData, localKey) + "','" + timestamp + "','" + isFile + "');");
    }

    // Class to Contain Message Data
    public class Message {
        public String message_id;
        public String message_data;
        public String user_data;
        public Long time_stamp;
        public Boolean is_file;

        public Message(String messageID, String messageData, String userData, Long timeStamp, Boolean isFile) {
            this.message_id = messageID;
            this.message_data = messageData;
            this.user_data = userData;
            this.time_stamp = timeStamp;
            this.is_file = isFile;
        }
    }

    public class FileData {
        public String fileString;
        public String fileName;
        public String fileID;

        public FileData(String fileString, String fileName, String fileID) {
            this.fileString = fileString;
            this.fileName = fileName;
            this.fileID = fileID;
        }
    }

    // Updates the Visible List of Channels
    public void updateUI() {

        // Data to Populate the RecyclerView List
        messages = new ArrayList<>();
        files = new ArrayList<>();


        // Unencrypt and Populate Channel Data from Database
        // Sort Descending By Most Recently Updated Channels
        Cursor messagesFromDB = localDB.rawQuery("Select * from Messages WHERE ChannelID = '" + channelID +"' ORDER BY TimeStamp ASC",null);
        while(messagesFromDB.moveToNext()) {
            String messageID = messagesFromDB.getString(0);
            String message = Crypto.symmetricDecrypt(messagesFromDB.getString(2), localKey);
            String userData = Crypto.symmetricDecrypt(messagesFromDB.getString(3), localKey);
            Boolean isFile = Boolean.parseBoolean(messagesFromDB.getString(5));
            if(!(message == null) && !message.equals("")) {
                if (isFile) {
                    messages.add("File: " + message + "\nClick to Download" + userData);
                    files.add(new FileData("File: " + message + "\nClick to Download" + userData, message, messageID));
                }
                else {
                    messages.add(message + userData);
                }
            }
        }
        messagesFromDB.close();

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
