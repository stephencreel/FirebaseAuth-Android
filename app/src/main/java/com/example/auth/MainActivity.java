package com.example.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,RecyclerViewAdapter.ItemClickListener {
    private FirebaseAuth mAuth;
    RecyclerViewAdapter adapter;
    DatabaseReference mDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get Firebase Authentication Instance
        mAuth = FirebaseAuth.getInstance();

        // Get Database Reference
        mDB = FirebaseDatabase.getInstance().getReference();

        findViewById(R.id.main_sign_out_button).setOnClickListener(this);
        findViewById(R.id.main_new_chat_button).setOnClickListener(this);

        // Data to Populate the RecyclerView List
        // TODO Find a way to populate ArrayList with Channel Names
        ArrayList<String> messages = new ArrayList<>();

        // Test Channel Names (One User and Multi Users)
        // TODO Remove when Data Collection Sorted
        messages.add("Stephen");
        messages.add("Sam, Seth, Henry");

        // Set Up RecyclerView
        RecyclerView recyclerView = findViewById(R.id.message_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerViewAdapter(this, messages);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL));

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_sign_out_button:
                mAuth.signOut();
                finish();
                break;
            case R.id.main_new_chat_button:


                Message message = new Message("test");

                mDB.child("Channels").setValue(message);

                break;
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        Toast.makeText(this, "You clicked " + adapter.getItem(position) + " on row number " + position, Toast.LENGTH_SHORT).show();
    }

}
