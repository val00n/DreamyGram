package com.val00n.dmpg.ui.main;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Dialog;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.services.MPGramService;
import java.util.ArrayList;

public class DialogsAdapter extends BaseAdapter {

    private final Context context;
    private final MPGramService mpgService;
    private final ArrayList<Dialog> items;
    private final String userToken;

    public DialogsAdapter(Context context, ArrayList<Dialog> items, String userToken) {
        this.context = context;
        this.userToken = userToken;
        this.mpgService = App.getMPGramService();
        this.items = items;
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.dialog_item, parent, false);

            holder = new ViewHolder();
            holder.avatar = (ImageView) convertView.findViewById(R.id.dialogAvatar);
            holder.title = (TextView) convertView.findViewById(R.id.dialogTitle);
            holder.lastTime = (TextView) convertView.findViewById(R.id.dialogLastTime);
            holder.lastMessage = (TextView) convertView.findViewById(R.id.dialogBottomText);
            holder.unreadStatus = (TextView) convertView.findViewById(R.id.dialogUnreadText);
            holder.readIcon = (ImageView) convertView.findViewById(R.id.dialogReadStatus);
            holder.pinIcon = convertView.findViewById(R.id.dialogPinIcon);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Dialog item = items.get(position);
        final int currentPosition = position;

        holder.title.setText(item.getDisplayName(App.selfId));
        holder.lastTime.setText(item.getDateTimeString());

        if (item.isTyping) {
            holder.lastMessage.setText("Typing...");
        } else {
            holder.lastMessage.setText(item.getSubtitle());
        }

        StringBuilder badge = new StringBuilder();
        if (item.unread > 0) {
            badge.append("(").append(item.unread).append(")");
        }
        if (item.mentions > 0) {
            badge.append(" @").append(item.mentions);
        }
        holder.unreadStatus.setText(badge.toString());

        if (item.pin) {
            holder.pinIcon.setVisibility(View.VISIBLE);
        } else {
            holder.pinIcon.setVisibility(View.GONE);
        }

        if (item.msg == null || !item.msg.out) {
            holder.readIcon.setVisibility(View.GONE);
        } else {
            holder.readIcon.setVisibility(View.VISIBLE);
            Message.MessageState state = item.msg.messageState != null
                    ? item.msg.messageState
                    : Message.MessageState.MESSAGE_SENT;
            switch (state) {
                case MESSAGE_SENDING:
                    holder.readIcon.setImageResource(R.drawable.ic_wait);
                    break;
                case MESSAGE_SENT:
                    holder.readIcon.setImageResource(R.drawable.ic_checkmark);
                    break;
                case MESSAGE_WAS_READ:
                    holder.readIcon.setImageResource(R.drawable.ic_double_checkmark);
                    break;
                case MESSAGE_ERROR:
                    holder.readIcon.setImageResource(R.drawable.ic_cross);
                    break;
            }
        }

        if (item.hasAvatar()) {
            if (item.cachedAvatar != null) {
                holder.avatar.setImageBitmap(item.cachedAvatar);
            } else {
                mpgService.getAvatar(item.id, userToken, new MPGramService.ApiCallback<byte[]>() {
                    @Override
                    public void onSuccess(final byte[] imageBytes) {

                        if (currentPosition < items.size() && items.get(currentPosition) == item) {
                            final Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                            if (bitmap != null) {
                                item.cachedAvatar = bitmap;

                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (currentPosition < items.size() && items.get(currentPosition) == item) {
                                            holder.avatar.setImageBitmap(bitmap);
                                        }
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.d("DialogsAdapter", "Failed to load avatar: " + error.getMessage());
                    }
                });
            }
        }

        return convertView;
    }

    static class ViewHolder {
        ImageView avatar;
        View pinIcon;
        ImageView readIcon;
        TextView title;
        TextView lastTime;
        TextView lastMessage;
        TextView unreadStatus;
    }
}
