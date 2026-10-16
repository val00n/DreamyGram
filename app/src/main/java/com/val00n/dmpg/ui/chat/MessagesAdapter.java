package com.val00n.dmpg.ui.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.message.Act;
import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.message.Message;
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

    private static final int VIEW_TYPE_IN = 0;
    private static final int VIEW_TYPE_OUT = 1;
    private static final int VIEW_TYPE_ACT = 2; // a.k.a. date separator

    private static final int AVATAR_WIDTH = 48;
    private static final int AVATAR_PADDING_WIDTH = 8;
    private static final int GROUP_INTERVAL_SECONDS = 6 * 60;

    private String peerId;

    public MessagesAdapter(Context context, ArrayList<Message> items, Map<String, User> users, Map<String, Chat> chats) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);

        this.items = items;
        this.users = users;
        this.chats = chats;

        float density = this.context.getResources().getDisplayMetrics().density;
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

    public void setPeerId(String v) { peerId = v; }
    private boolean isSelf() { return peerId != null && peerId.equals(App.selfId); }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        final int viewType = getItemViewType(position);

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
            holder.captionLayout = (LinearLayout) convertView.findViewById(R.id.messageCaptionLayout);
            holder.quoteLayout = (LinearLayout) convertView.findViewById(R.id.messageQuoteLayout);
            holder.quoteText = (TextView) convertView.findViewById(R.id.messageQuoteText);
            holder.captionText = (TextView) convertView.findViewById(R.id.messageCaptionText);
            holder.divider = convertView.findViewById(R.id.messageDivider);

            if (viewType == VIEW_TYPE_OUT) {
                holder.status = (ImageView) convertView.findViewById(R.id.messageStatus);
            }

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Message item = items.get(position);
        final Message prev = position > 0 ? items.get(position - 1) : null;

        final boolean isGrouped = prev != null
                && !prev.dateSeparator
                && item.fwd == null
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
                    holder.textLayout.setPadding(0, 0, avatarWidthPx, 0);
                } else {
                    holder.textLayout.setPadding(avatarWidthPx, 0, 0, 0);
                }
            } else {
                holder.textLayout.setPadding(0, 0, 0, 0);
            }

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.textLayout.getLayoutParams();
            lp.setMargins(rowMarginPx, isGrouped ? 0 : rowMarginPx, rowMarginPx, isGrouped ? 0 : rowMarginPx);
            holder.textLayout.setLayoutParams(lp);
        }

        holder.text.setText(getContentString(item));

        if (!item.out && !isSelf()) {
            holder.title.setText(getNameString(item));
        } else if (isSelf()) {
            holder.status.setVisibility(View.GONE);
        } else {
            holder.status.setVisibility(View.VISIBLE);
            switch (item.messageState) {
                case MESSAGE_SENDING:
                    holder.status.setImageResource(R.drawable.ic_wait);
                    break;
                case MESSAGE_SENT:
                    holder.status.setImageResource(R.drawable.ic_checkmark);
                    break;
                case MESSAGE_WAS_READ:
                    holder.status.setImageResource(R.drawable.ic_double_checkmark);
                    break;
                case MESSAGE_ERROR:
                    holder.status.setImageResource(R.drawable.ic_cross);
                    break;
            }
        }

        if (item.reply != null) {
            holder.quoteLayout.setVisibility(View.VISIBLE);

            if (item.reply.msg != null) {
                holder.quoteText.setText(getNameString(item.reply.msg) + ":\n" + getContentString(item.reply.msg));
            } else {
                holder.quoteText.setText(item.reply.quote);
            }
        } else {
            holder.quoteLayout.setVisibility(View.GONE);
        }

        if (item.fwd != null && (item.fwd.fromId != null || item.fwd.fromName != null)) {
            holder.captionLayout.setVisibility(View.VISIBLE);

            String senderName;
            if (item.fwd.fromName != null) {
                senderName = item.fwd.fromName;
            } else if (item.fwd.fromId != null && item.fwd.fromId.length() > 0) {
                if (item.fwd.fromId.startsWith("-")) {
                    senderName = getChatTitle(chats, item.fwd.fromId);
                } else {
                    senderName = getUserTitle(users, item.fwd.fromId);
                }
            } else {
                senderName = "unknown";
            }

            holder.captionText.setText("Forwarded from: " + senderName);
        } else {
            holder.captionLayout.setVisibility(View.GONE);
        }

        holder.date.setText(DateUtils.formatChatTime(item.date, false, 0));

        return convertView;
    }

    private String getNameString(Message item) {
        if (item.from_id != null && item.from_id.length() > 0) {
            if (item.from_id.startsWith("-")) {
                return getChatTitle(chats, item.from_id);
            } else {
                return getUserTitle(users, item.from_id);
            }
        } else if (item.peer_id != null && item.peer_id.length() > 0) {
            if (item.peer_id.startsWith("-")) {
                return getChatTitle(chats, item.peer_id);
            } else {
                return getUserTitle(users, item.peer_id);
            }
        } else {
            return "unknown";
        }
    }

    private String getContentString(Message item) {
        if (item.text != null && item.text.length() > 0) {
            return item.text;
        } else if (item.media != null && item.media.type != null) {
            return "MEDIA: " + item.media.type + "\nMIME:" + item.media.mime;
        } else {
            return "NO MESSAGE";
        }
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
        return (item.out || isSelf()) ? VIEW_TYPE_OUT : VIEW_TYPE_IN;
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
        if (prev == null) return false;
        String id = item.from_id != null && item.from_id.length() > 0 ? item.from_id : item.peer_id;
        String prevId = prev.from_id != null && prev.from_id.length() > 0 ? prev.from_id : prev.peer_id;
        return id != null && id.equals(prevId);
    }

    static class ViewHolder {
        ImageView avatar;
        TextView title;
        TextView date;
        TextView text;
        TextView captionText;
        TextView quoteText;
        LinearLayout titleRow;
        LinearLayout textLayout;
        LinearLayout captionLayout;
        LinearLayout quoteLayout;
        ImageView status;
        View divider;

        boolean isGrouped;
    }
}
