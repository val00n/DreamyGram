package com.val00n.dmpg.ui.settings.instances;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;

import java.util.ArrayList;

public class InstancesAdapter extends BaseAdapter {

    private final ArrayList<Instance> items;
    private final Context context;
    private long[] pingResults;
    private int[] sessionStatus;

    private final int NO_SESSION_STATUS = 2;
    private final int ACTIVE_STATUS = 1;
    private final int UNKNOWN_STATUS = 0;
    private final int INACTIVE_STATUS = -1;
    private final int ERROR_STATUS = -2;


    public InstancesAdapter(Context context, ArrayList<Instance> items) {
        this.context = context;
        this.items = items;

        sessionStatus = new int[items.size()];
        pingResults = new long[items.size()];
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void updateItem(int index, long ping, int status) { // FIXME: store baseURL instead of index
        if (index < 0 || index >= items.size()) return;
        if (index >= pingResults.length || index >= sessionStatus.length) return;
        pingResults[index] = ping;
        sessionStatus[index] = status;
        notifyDataSetChanged();
    }

    public void resetStatus() {
        if (sessionStatus.length != items.size()) {
            sessionStatus = new int[items.size()];
            pingResults = new long[items.size()];
        }

        for (int i = 0; i < items.size(); i++) {
            pingResults[i] = 0;
            sessionStatus[i] = 0;
        }
        notifyDataSetChanged();
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.instance_item, parent, false);

            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.instanceTitleTextView);
            holder.sessionAuthStatus = (TextView) convertView.findViewById(R.id.instanceAuthStatusTextView);
            holder.ping = (TextView) convertView.findViewById(R.id.instancePingTextView);
            holder.editButton = (ImageButton) convertView.findViewById(R.id.instanceEditButton);
            holder.root = (LinearLayout) convertView.findViewById(R.id.instanceRoot);

            holder.editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, InstanceEditActivity.class);
                    intent.putExtra("INSTANCE_INDEX", position);
                    context.startActivity(intent);
                }
            });

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        if ((position == App.getInstanceManager().getSelectedInstanceIndex())) {
            holder.root.setBackgroundColor(0x2200FF00); // green ö
        } else if (holder.root != null) {
            holder.root.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.title.setText(items.get(position).baseURL);
        holder.editButton.setTag(position);

        switch (sessionStatus[position]) {
            case UNKNOWN_STATUS:
                holder.sessionAuthStatus.setText("Session: unknown");
                break;
            case NO_SESSION_STATUS:
                holder.sessionAuthStatus.setText("Session: no session");
                break;
            case INACTIVE_STATUS:
                holder.sessionAuthStatus.setText("Session: inactive");
                break;
            case ACTIVE_STATUS:
                holder.sessionAuthStatus.setText("Session: active");
                break;
            case ERROR_STATUS:
                holder.sessionAuthStatus.setText("Session: error");
                break;
        }

        if (pingResults[position] == 0) {
            holder.ping.setText("Response time: --");
        } else if (pingResults[position] == -1) {
            holder.ping.setText("Response time: -1ms");
        } else {
            holder.ping.setText("Response time: " + pingResults[position] + "ms");
        }

        return convertView;
    }

    static class ViewHolder {
        LinearLayout root;
        TextView title;
        TextView sessionAuthStatus;
        TextView ping;
        ImageButton editButton;
    }

}
