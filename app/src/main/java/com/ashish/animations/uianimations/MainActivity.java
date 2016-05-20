package com.ashish.animations.uianimations;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.ashish.ui.activity.CardDeckActivity;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    ListView view;

    String[] choices = {"Card Deck View"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        view = (ListView) findViewById(R.id.choices);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.choice_item, R.id.choice_item, choices);
        view.setAdapter(adapter);
        view.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (position) {
            case 0:
                // Card Deck Adapter View
                Intent intent = new Intent(this, CardDeckActivity.class);
                this.startActivity(intent);
        }
    }
}
