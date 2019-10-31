package com.example.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Random;

import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,RecyclerViewAdapter.ItemClickListener {
    private FirebaseAuth mAuth;
    RecyclerViewAdapter adapter;
    DatabaseReference mDB;
    SQLiteDatabase localDB;
    String pass;
    SecretKeySpec localKey;
    ArrayList<String> channels;
    ArrayList<String> channelIDs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get Firebase Authentication Instance
        mAuth = FirebaseAuth.getInstance();

        // Get Firebase Database Reference
        mDB = FirebaseDatabase.getInstance().getReference();



        // Get Local Database Reference
        localDB = openOrCreateDatabase(mAuth.getUid(),MODE_PRIVATE,null);

        // Convert User Password to 128-bit Local Symmetric Key
        pass = getIntent().getExtras().getString("pass");
        localKey = Crypto.keyFromString(pass);

        // Set OnClick Listeners for Buttons
        findViewById(R.id.main_sign_out_button).setOnClickListener(this);
        findViewById(R.id.main_new_chat_button).setOnClickListener(this);

        // Ensure Database Tables Exist
        localDB.execSQL("DROP TABLE IF EXISTS Channels");
        localDB.execSQL("CREATE TABLE IF NOT EXISTS AsymKeys(PublicKey TEXT,PrivateKey TEXT);");
        localDB.execSQL("CREATE TABLE IF NOT EXISTS Channels(ChannelID TEXT PRIMARY KEY, ChannelName TEXT, SymmetricKey TEXT, LastUpdate INTEGER);");



        // localDB.execSQL("DELETE FROM Channels");

        // Sync Channels to Local Database and Begin Listening for Changes
        syncChannels();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_sign_out_button:
                mAuth.signOut();
                finish();
                break;
            case R.id.main_new_chat_button:

                startActivity(new Intent(this, NewChatActivity.class));

                break;
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        startActivity(new Intent(this, ChatActivity.class).putExtra("channelName", adapter.getItem(position)));
    }

    // Adds a New Channel to the Local Database
    public void addChannel(String ID, String name, String key) {
        long unixTime = System.currentTimeMillis() / 1000;
        localDB.execSQL("INSERT INTO Channels (ChannelID, ChannelName, SymmetricKey, LastUpdate) " +
                "VALUES('" + ID + "','" + Crypto.symmetricEncrypt(name, localKey) + "','" + Crypto.symmetricEncrypt(key, localKey) + "','" + unixTime + "');");
        Log.d("LOG", name + ": " + unixTime);
    }

    // Generate Random 20-Character String
    public String generateID () {
        final String data = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz|!£$%&/=@#";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 10; i++) {
            sb.append(data.charAt(random.nextInt(data.length())));
        }
        return sb.toString();

    }

    public void syncChannels() {

        // Set Up Listener to Monitor For Change in Channels
        final DatabaseReference channelDB = FirebaseDatabase.getInstance().getReference().child("Channels").child(mAuth.getUid());
        channelDB.orderByKey().addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                // Get Channel Data for Each Channel
                String channelID = dataSnapshot.getKey();
                String channelName = dataSnapshot.child("channel_name").getValue().toString();
                String channelKey = dataSnapshot.child("symmetric_key").getValue().toString();

                // Add Channel Data to Local Database if Not Present
                Cursor currentChannels = localDB.rawQuery("Select * from Channels WHERE ChannelID = '" + channelID + "'",null);
                if(currentChannels.getCount() <= 0){
                    addChannel(channelID, channelName, channelKey);
                }
                currentChannels.close();

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

    // Updates the Visible List of Channels
    public void updateUI() {

        // Data to Populate the RecyclerView List
        channels = new ArrayList<>();
        channelIDs = new ArrayList<>();

        // Unencrypt and Populate Channel Data from Database
        // Sort Descending By Most Recently Updated Channels
        Cursor channelsFromDB = localDB.rawQuery("Select ChannelID, ChannelName from Channels ORDER BY LastUpdate DESC",null);
        while(channelsFromDB.moveToNext()) {
            channelIDs.add(channelsFromDB.getString(0));
            String channelName = Crypto.symmetricDecrypt(channelsFromDB.getString(1), localKey);
            channels.add(channelName);
        }
        channelsFromDB.close();

        // Generate a New RecyclerView with Updated Data
        RecyclerView recyclerView = findViewById(R.id.message_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerViewAdapter(this, channels);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL));
    }

}
