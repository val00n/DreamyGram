package com.val00n.dmpg.ui.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Act;
import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.Message;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.utils.DateUtils;

import java.util.ArrayList;
import java.util.Map;

public class MessagesAdapter extends BaseAdapter {
    private final Context context;
    private final Map<String, User> users;
    private final Map<String, Chat> chats;
    private final ArrayList<Message> items;
    private final LayoutInflater inflater;
    private final int rowMarginPx;
    private final int avatarWidthPx;
    private long nowMilis;

    private static final int VIEW_TYPE_IN = 0;
    private static final int VIEW_TYPE_OUT = 1;
    private static final int VIEW_TYPE_ACT = 2; // a.k.a. date separator

    private static final int AVATAR_WIDTH = 48;
    private static final int AVATAR_PADDING_WIDTH = 8;
    private static final int GROUP_INTERVAL_SECONDS = 6 * 60;

    public MessagesAdapter(Context context, ArrayList<Message> items, Map<String, User> users, Map<String, Chat> chats) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);

        this.items = items;
        this.users = users;
        this.chats = chats;

        float density = context.getResources().getDisplayMetrics().density;
        rowMarginPx = Math.round(4 * density);
        avatarWidthPx = Math.round((AVATAR_PADDING_WIDTH + AVATAR_WIDTH) * density);
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
        final int viewType = getItemViewType(position);

        if (position == 0) {
            nowMilis = System.currentTimeMillis();
        }

        if (viewType == VIEW_TYPE_ACT) return bindActView(items.get(position), convertView, parent);

        if (convertView == null) {
            int layout = viewType == VIEW_TYPE_OUT
                    ? R.layout.message_out
                    : R.layout.message;
            convertView = inflater.inflate(layout, parent, false);
            holder = new ViewHolder();

            holder.avatar = (ImageView) convertView.findViewById(R.id.messageAvatar);
            holder.title = (TextView) convertView.findViewById(R.id.messageTitle);
            holder.date = (TextView) convertView.findViewById(R.id.messageDate);
            holder.text = (TextView) convertView.findViewById(R.id.messageText);
            holder.textLayout = (LinearLayout) convertView.findViewById(R.id.messageTextLayout);
            holder.titleRow = (LinearLayout) convertView.findViewById(R.id.messageTitleRow);
            holder.divider = convertView.findViewById(R.id.messageDivider);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Message item = items.get(position);
        final Message prev = position > 0 ? items.get(position - 1) : null;

        final boolean isGrouped = prev != null
                && !prev.dateSeparator
                && prev.act == null
                && item.date - prev.date < GROUP_INTERVAL_SECONDS
                && isSameSender(item, prev);

        if (holder.isGrouped != isGrouped) {
            holder.isGrouped = isGrouped;

            holder.avatar.setVisibility(isGrouped ? View.GONE : View.VISIBLE);
            holder.titleRow.setVisibility(isGrouped ? View.GONE : View.VISIBLE);
            holder.divider.setVisibility(isGrouped ? View.GONE : View.VISIBLE);

            if (isGrouped) {
                if (viewType == VIEW_TYPE_OUT) {
                    holder.text.setPadding(0, 0, avatarWidthPx, 0);
                } else {
                    holder.text.setPadding(avatarWidthPx, 0, 0, 0);
                }
            } else {
                holder.text.setPadding(0, 0, 0, 0);
            }

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.textLayout.getLayoutParams();
            lp.setMargins(rowMarginPx, isGrouped ? 0 : rowMarginPx, rowMarginPx, isGrouped ? 0 : rowMarginPx);
            holder.textLayout.setLayoutParams(lp);
        }

        if (item.text != null && item.text.length() > 0) {
            holder.text.setText(item.text);
        } else if (item.media != null && item.media.type != null) {
            holder.text.setText("MEDIA: " + item.media.type + "MIME:" + item.media.mime);
        } else {
            holder.text.setText("NO MESSAGE");
        }

        if (!item.out) {
            if (item.from_id != null && item.from_id.length() > 0) {
                if (item.from_id.startsWith("-")) {
                    holder.title.setText(getChatTitle(chats, item.from_id));
                } else {
                    holder.title.setText(getUserTitle(users, item.from_id));
                }
            } else if (item.peer_id != null && item.peer_id.length() > 0) {
                if (item.peer_id.startsWith("-")) {
                    holder.title.setText(getChatTitle(chats, item.peer_id));
                } else {
                    holder.title.setText(getUserTitle(users, item.peer_id));
                }
            } else {
                holder.title.setText("UNKNOWN USER");
            }
        }

        holder.date.setText(DateUtils.formatChatTime(item.date, false, nowMilis));

        return convertView;
    }

    private View bindActView(Message item, View convertView, ViewGroup parent) {
        TextView actText;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.act_message, parent, false);
            actText = (TextView) convertView.findViewById(R.id.actMessageText);
            convertView.setTag(actText);
        } else {
            actText = (TextView) convertView.getTag();
        }

        actText.setText(item.dateSeparator ? DateUtils.formatDateSeparator(item.date) : getActText(item));
        return convertView;
    }

    private String getActText(Message item) {
        Act act = item.act;
        String type = act.action;
        String name = getUserTitle(users, item.from_id);

        switch (type) {
            case "ChatCreate":
                return "Group created";
            case "ChannelCreate":
                return "Channel created";
            case "ChatEditPhoto":
                return "Photo was updated";
            case "HistoryClear":
                return "History was cleared";
            case "ChatEditTitle":
                return "Title changed: " + act.t;
            case "PinMessage":
                return name + " pinned message";
            case "ChatJoinedByLink":
                return name + " joined by link";
            case "ChatJoinedByRequest":
                return name + " joined by request";
            case "ChatAddUser":
                if (item.from_id != null && item.from_id.equals(act.user))
                    return name + " joined";
                return name + " added " + getUserTitle(users, act.user);
            case "ChatDeleteUser":
                if (item.from_id != null && item.from_id.equals(act.user))
                    return name + " left";
                return name + " removed " + getUserTitle(users, act.user);
            default:
                return "Action: " + type;
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message item = items.get(position);
        if (item.dateSeparator) return VIEW_TYPE_ACT;
        if (item.act != null) return VIEW_TYPE_ACT;
        return item.out ? VIEW_TYPE_OUT : VIEW_TYPE_IN;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    private String getUserTitle(Map<String, User> users, String id) {
        if (users != null) {
            User user = users.get(id);
            if (user != null) {
                return user.getDisplayName();
            }
        }
        return id;
    }

    private String getChatTitle(Map<String, Chat> chats, String id) {
        if (chats != null) {
            Chat chat = chats.get(id);
            if (chat != null) {
                return chat.t;
            }
        }
        return id;
    }

    private static boolean isSameSender(Message item, Message prev) {
        if (item == null || prev == null) return false;
        String id = item.from_id != null && item.from_id.length() > 0 ? item.from_id : item.peer_id;
        String prevId = prev.from_id != null && prev.from_id.length() > 0 ? prev.from_id : prev.peer_id;
        return id != null && id.equals(prevId);
    }

    static class ViewHolder {
        ImageView avatar;
        TextView title;
        TextView date;
        TextView text;
        LinearLayout titleRow;
        LinearLayout textLayout;
        View divider;

        boolean isGrouped;
    }
}
