package com.example.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.service.autofill.TextValueSanitizer;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

public class NewChatActivity extends AppCompatActivity implements View.OnClickListener,RecyclerViewAdapter.ItemClickListener {
    ArrayList<String> emails = new ArrayList<>();
    RecyclerViewAdapter adapter;
    private TextInputEditText mChatName, mUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_chat);

        mChatName = findViewById(R.id.chatNameInput);
        mUserName = findViewById(R.id.userInput);

        findViewById(R.id.new_chat_back_button).setOnClickListener(this);
        findViewById(R.id.addUser).setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.new_chat_back_button:
                finish();
                break;
            case R.id.addUser:
                emails.add(mUserName.getText().toString());
                mUserName.clearComposingText();
                updateUI();
                break;
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        emails.remove(adapter.getItem(position));
        updateUI();
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
