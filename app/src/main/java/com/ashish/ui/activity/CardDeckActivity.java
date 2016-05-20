package com.ashish.ui.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.ashish.animations.uianimations.R;
import com.ashish.ui.adapter.CardDeckAdapter;
import com.ashish.ui.view.CardDeckAdapterView;

public class CardDeckActivity extends AppCompatActivity {

    private CardDeckAdapterView mCardAdapterView;
    private CardDeckAdapter mAdapter;

    private String[] dataSet = {
            "Item 1",
            "Item 2",
            "Item 3",
            "Item 4",
            "Item 5",
            "Item 6",
            "Item 7",
            "Item 8",
            "Item 9",
            "Item 10",
            "Item 11",
            "Item 12",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_deck);

        mAdapter = new CardDeckAdapter(this, dataSet);
        mCardAdapterView = (CardDeckAdapterView) findViewById(R.id.detail_card_view);
        mCardAdapterView.setAdapter(mAdapter);
        mCardAdapterView.setSelection(0);
    }
}
