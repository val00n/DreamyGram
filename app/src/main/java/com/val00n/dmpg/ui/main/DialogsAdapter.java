package com.val00n.dmpg.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.dialog.Dialog;
import com.val00n.dmpg.model.dialog.DialogRow;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.ui.AvatarViewBinder;
import java.util.ArrayList;

public class DialogsAdapter extends BaseAdapter {

    private final ArrayList<Dialog> items;
    private final ArrayList<DialogRow> rows;
    private final AvatarViewBinder avatarViewBinder;
    private final Context context;

    private String selfId;

    public DialogsAdapter(Context context, ArrayList<Dialog> items, String selfId, AvatarViewBinder avatarViewBinder) {
        this.context = context;
        this.selfId = selfId;
        this.items = items;
        this.avatarViewBinder = avatarViewBinder;

        rows = new ArrayList<DialogRow>();
        buildRows();
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
    public void notifyDataSetChanged() {
        buildRows();
        super.notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        final DialogRow row = rows.get(position);

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

        holder.title.setText(row.title);
        holder.date.setText(row.dialog.getDateTimeString());
        holder.subtitle.setText(row.subTitle);
        holder.unreadBadge.setText(row.unreadBadge);

        if (row.pinned) {
            holder.pinIcon.setVisibility(View.VISIBLE);
        } else {
            holder.pinIcon.setVisibility(View.GONE);
        }

        holder.statusIcon.setVisibility(row.out ? View.VISIBLE : View.GONE);
        if (row.out) {
            holder.statusIcon.setImageResource(row.statusRes);
        }

        avatarViewBinder.bind(holder.avatar, row.avatarPeer, row.hasAvatar);

        return convertView;
    }

    private void buildRows() {
        rows.clear();
        for (Dialog item : items) {
            DialogRow newRow = new DialogRow();

            newRow.dialog = item;
            newRow.title = item.getDisplayName(selfId);

            if (item.isTyping) {
                newRow.subTitle = context.getString(R.string.chat_typing);
            } else {
                newRow.subTitle = item.getSubtitle();
            }

            StringBuilder badge = new StringBuilder();
            if (item.unread > 0) {
                badge.append("(").append(item.unread).append(")");
            }
            if (item.mentions > 0) {
                badge.append(" @").append(item.mentions);
            }

            newRow.unreadBadge = badge.toString();
            newRow.pinned = item.pin;

            if (item.msg == null || !item.msg.out) {
                newRow.out = false;
            } else {
                newRow.out = true;
                Message.MessageState state = item.msg.messageState != null
                        ? item.msg.messageState
                        : Message.MessageState.MESSAGE_SENT;
                switch (state) {
                    case MESSAGE_SENDING:
                        newRow.statusRes = R.drawable.ic_wait;
                        break;
                    case MESSAGE_SENT:
                        newRow.statusRes = R.drawable.ic_checkmark;
                        break;
                    case MESSAGE_WAS_READ:
                        newRow.statusRes = R.drawable.ic_double_checkmark;
                        break;
                    case MESSAGE_ERROR:
                        newRow.statusRes = R.drawable.ic_cross;
                        break;
                }
            }

            newRow.avatarPeer = item.peerId;
            newRow.hasAvatar = item.hasAvatar();

            rows.add(newRow);
        }
    }

    public void setSelfId(String selfId) {
        this.selfId = selfId;
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
