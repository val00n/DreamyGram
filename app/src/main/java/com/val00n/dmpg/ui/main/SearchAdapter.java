package com.val00n.dmpg.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.search.SearchResult;
import com.val00n.dmpg.model.search.SearchRow;
import com.val00n.dmpg.ui.AvatarViewBinder;
import java.util.ArrayList;

public class SearchAdapter extends BaseAdapter {

    private final ArrayList<SearchRow> rows;
    private final AvatarViewBinder avatarViewBinder;

    private ArrayList<SearchResult> globalResults;
    private ArrayList<SearchResult> localResults;
    private boolean showEmpty;

    public SearchAdapter(AvatarViewBinder avatarViewBinder) {
        this.avatarViewBinder = avatarViewBinder;

        rows = new ArrayList<SearchRow>();
    }

    public void setResults(ArrayList<SearchResult> globalResults, ArrayList<SearchResult> localResults, boolean showEmpty) {
        this.globalResults = globalResults;
        this.localResults = localResults;
        this.showEmpty = showEmpty;

        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public void notifyDataSetChanged() {
        buildRows();
        super.notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final SearchRow row = rows.get(position);

        if (row.type == SearchRow.TYPE_SEPARATOR) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.search_separator, parent, false);
            }
            return view;
        }

        if (row.type == SearchRow.TYPE_EMPTY) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.search_empty, parent, false);
            }
            return view;
        }

        final ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.dialog_item, parent, false);

            holder = new ViewHolder();
            holder.avatar = (ImageView) convertView.findViewById(R.id.dialogAvatar);
            holder.title = (TextView) convertView.findViewById(R.id.dialogTitle);
            holder.date = (TextView) convertView.findViewById(R.id.dialogLastTime);
            holder.subtitle = (TextView) convertView.findViewById(R.id.dialogBottomText);
            holder.unreadBadge = (TextView) convertView.findViewById(R.id.dialogUnreadText);
            holder.statusIcon = (ImageView) convertView.findViewById(R.id.dialogReadStatus);
            holder.pinIcon = convertView.findViewById(R.id.dialogPinIcon);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        SearchResult result = row.result;

        holder.title.setText(result.title);
        holder.date.setText("");
        holder.subtitle.setText(result.getSubtitle());
        holder.unreadBadge.setText(row.unreadBadge);

        holder.pinIcon.setVisibility(View.GONE);
        holder.statusIcon.setVisibility(View.GONE);

        avatarViewBinder.bind(holder.avatar, result.peerId, result.hasAvatar);

        return convertView;
    }

    private void buildRows() {
        rows.clear();

        ArrayList<SearchRow> globalRows = new ArrayList<SearchRow>();
        ArrayList<SearchRow> localRows = new ArrayList<SearchRow>();

        if (globalResults != null) {
            for (int i = 0; i < globalResults.size(); i++) {
                SearchResult result = globalResults.get(i);
                if (result == null) continue;
                globalRows.add(buildRow(SearchRow.TYPE_GLOBAL, result));
            }
        }

        if (localResults != null) {
            for (int i = 0; i < localResults.size(); i++) {
                SearchResult result = localResults.get(i);
                if (result == null) continue;
                localRows.add(buildRow(SearchRow.TYPE_LOCAL, result));
            }
        }

        rows.addAll(globalRows);

        if (!globalRows.isEmpty() && !localRows.isEmpty()) {
            SearchRow separator = new SearchRow();
            separator.type = SearchRow.TYPE_SEPARATOR;
            rows.add(separator);
        }

        rows.addAll(localRows);

        if (showEmpty && rows.size() == 0) {
            SearchRow empty = new SearchRow();
            empty.type = SearchRow.TYPE_EMPTY;
            rows.add(empty);
        }
    }

    private SearchRow buildRow(int type, SearchResult result) {
        SearchRow row = new SearchRow();
        row.type = type;
        row.result = result;

        StringBuilder badge = new StringBuilder();
        if (result.dialog != null) {
            if (result.dialog.unread > 0) {
                badge.append("(").append(result.dialog.unread).append(")");
            }
            if (result.dialog.mentions > 0) {
                badge.append(" @").append(result.dialog.mentions);
            }
        }
        row.unreadBadge = badge.toString();

        return row;
    }

    static class ViewHolder {
        ImageView avatar;
        View pinIcon;
        ImageView statusIcon;
        TextView title;
        TextView date;
        TextView subtitle;
        TextView unreadBadge;
    }
}
