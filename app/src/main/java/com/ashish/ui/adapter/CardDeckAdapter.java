package com.ashish.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ashish.animations.uianimations.R;

/**
 * Created by ashishgupta on 11/7/15.
 */
public class CardDeckAdapter extends BaseAdapter {

    private static class ViewHolder {
        TextView text;
    }

    private String[] mDataSet;
    private Context mContext;
    private LayoutInflater inflater;

    public CardDeckAdapter(Context context, String[] dataSet) {
        this.mContext = context;
        mDataSet = dataSet;
        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        if (mDataSet != null) {
            return mDataSet.length;
        }
        return 0;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Object getItem(int position) {
        return mDataSet[position];
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        String data = mDataSet[position];

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.card_deck_item, parent, false);
            holder = new ViewHolder();
            holder.text = (TextView) convertView.findViewById(R.id.text);
            convertView.setTag(holder);
        }

        holder = (ViewHolder) convertView.getTag();

        holder.text.setText(data);
        return convertView;
    }
}
