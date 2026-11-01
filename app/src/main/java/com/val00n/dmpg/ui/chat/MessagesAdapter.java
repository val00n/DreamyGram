package com.val00n.dmpg.ui.chat;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.message.Act;
import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.model.message.MessageRow;
import com.val00n.dmpg.ui.AvatarViewBinder;
import com.val00n.dmpg.utils.DateUtils;
import java.util.ArrayList;
import java.util.Map;

public class MessagesAdapter extends BaseAdapter {

    private final Map<String, User> users;
    private final Map<String, Chat> chats;
    private final ArrayList<Message> items;
    private final ArrayList<MessageRow> rows;
    private final AvatarViewBinder avatarViewBinder;

    private int rowMarginPx = -1;
    private int avatarWidthPx = -1;
    private LayoutInflater inflater;
    private boolean isSavedMessages;

    private static final int VIEW_TYPE_IN = 0;
    private static final int VIEW_TYPE_OUT = 1;
    private static final int VIEW_TYPE_ACT = 2;
    private static final int VIEW_TYPE_DATE = 3;

    private static final int GROUP_INTERVAL_SECONDS = 6 * 60;

    public MessagesAdapter(ArrayList<Message> items, Map<String, User> users, Map<String, Chat> chats, boolean isSavedMessages, AvatarViewBinder avatarViewBinder) {
        this.items = items;
        this.users = users;
        this.chats = chats;
        this.avatarViewBinder = avatarViewBinder;
        this.isSavedMessages = isSavedMessages;

        rows = new ArrayList<MessageRow>();
        buildRows();
    }

    private void ensureViews(ViewGroup parent) {
        if (inflater != null) return;

        Context ctx = parent.getContext();
        inflater = LayoutInflater.from(ctx);

        Resources res = ctx.getResources();
        float density = res.getDisplayMetrics().density;
        rowMarginPx = Math.round(4 * density);
        avatarWidthPx = Math.round(56 * density);
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        MessageRow row = rows.get(position);
        if (row.viewType == VIEW_TYPE_DATE || row.viewType == VIEW_TYPE_ACT) {
            return null;
        }
        return row.message;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).viewType;
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

    public void setSavedMessages(boolean v) {
        if (isSavedMessages == v) return;
        isSavedMessages = v;
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ensureViews(parent);

        final int viewType = getItemViewType(position);
        MessageRow row = rows.get(position);

        if (viewType == VIEW_TYPE_ACT || viewType == VIEW_TYPE_DATE) {
            return bindActView(row, convertView, parent);
        }

        final ViewHolder holder;

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

        holder.text.setText(row.content);

        if (row.grouped) {
            holder.avatar.setVisibility(View.GONE);
            holder.titleRow.setVisibility(View.GONE);
            holder.divider.setVisibility(View.GONE);

            if (viewType == VIEW_TYPE_OUT) {
                holder.textLayout.setPadding(0, 0, avatarWidthPx, 0);
            } else {
                holder.textLayout.setPadding(avatarWidthPx, 0, 0, 0);
            }
        } else {
            if (holder.title != null) holder.title.setText(row.title);
            holder.date.setText(row.dateText);

            holder.avatar.setVisibility(View.VISIBLE);
            holder.titleRow.setVisibility(View.VISIBLE);
            holder.divider.setVisibility(View.VISIBLE);

            holder.textLayout.setPadding(0, 0, 0, 0);
        }

        LinearLayout.LayoutParams lp =
                (LinearLayout.LayoutParams) holder.textLayout.getLayoutParams();
        lp.setMargins(
                rowMarginPx,
                row.grouped ? 0 : rowMarginPx,
                rowMarginPx,
                row.grouped ? 0 : rowMarginPx
        );
        holder.textLayout.setLayoutParams(lp);

        if (row.quoteText != null) {
            holder.quoteLayout.setVisibility(View.VISIBLE);
            holder.quoteText.setText(row.quoteText);
        } else {
            holder.quoteLayout.setVisibility(View.GONE);
        }

        if (row.forwardCaption != null) {
            holder.captionLayout.setVisibility(View.VISIBLE);
            holder.captionText.setText(row.forwardCaption);
        } else {
            holder.captionLayout.setVisibility(View.GONE);
        }

        if (viewType == VIEW_TYPE_OUT) {
            if (row.statusRes != 0) {
                holder.status.setVisibility(View.VISIBLE);
                holder.status.setImageResource(row.statusRes);
            } else {
                holder.status.setVisibility(View.GONE);
            }
        }

        avatarViewBinder.bind(holder.avatar, row.avatarPeer, row.hasAvatar);

        return convertView;
    }

    private boolean isGrouped(Message item, Message prev) {
        return prev != null
                && item.fwd == null
                && prev.act == null
                && item.date - prev.date < GROUP_INTERVAL_SECONDS
                && isSameSender(item, prev);
    }

    private void buildRows() {
        rows.clear();
        Message prev = null;
        String lastDayKey = null;

        for (Message item : items) {
            if (item.act != null) {
                MessageRow row = new MessageRow();
                row.viewType = VIEW_TYPE_ACT;
                row.message = item;
                row.content = getActText(item);
                rows.add(row);
                prev = item;
                continue;
            }

            String currentDayKey = DateUtils.dayKey(item.date);
            boolean isNewDay = !currentDayKey.equals(lastDayKey);

            if (isNewDay) {
                lastDayKey = currentDayKey;
                MessageRow dateRow = new MessageRow();
                dateRow.viewType = VIEW_TYPE_DATE;
                dateRow.message = null;
                dateRow.content = DateUtils.formatDateSeparator(item.date);
                rows.add(dateRow);
            }

            MessageRow row = new MessageRow();

            final boolean outgoing = item.out || isSavedMessages;
            row.viewType = outgoing ? VIEW_TYPE_OUT : VIEW_TYPE_IN;

            row.message = item;
            row.grouped = !isNewDay && isGrouped(item, prev);

            row.content = getContentString(item);
            row.title = getNameString(item);
            row.dateText = DateUtils.formatChatTime(item.date, false, 0);

            row.avatarPeer = getAvatarPeer(item);
            row.hasAvatar = hasAvatar(row.avatarPeer);

            if (outgoing) {
                switch (item.messageState) {
                    case MESSAGE_SENDING:
                        row.statusRes = R.drawable.ic_wait;
                        break;
                    case MESSAGE_SENT:
                        row.statusRes = R.drawable.ic_checkmark;
                        break;
                    case MESSAGE_WAS_READ:
                        row.statusRes = R.drawable.ic_double_checkmark;
                        break;
                    case MESSAGE_ERROR:
                        row.statusRes = R.drawable.ic_cross;
                        break;
                }
            }

            if (item.reply != null && item.reply.peer != null) {
                if (item.reply.quote != null) {
                    row.quoteText = getNameString(item.reply.peer) + ":\n" + item.reply.quote;
                } else if (item.reply.msg != null) {
                    row.quoteText = getNameString(item.reply.peer) + ":\n" + getContentString(item.reply.msg);
                } else {
                    row.quoteText = getNameString(item.reply.peer);
                }
            } else {
                row.quoteText = null;
            }

            if (item.fwd != null && (item.fwd.fromId != null || item.fwd.fromName != null)) {
                String senderName;
                if (item.fwd.fromName != null) {
                    senderName = item.fwd.fromName;
                } else if (item.fwd.fromId != null && item.fwd.fromId.length() > 0) {
                    senderName = getNameString(item.fwd.fromId);
                } else {
                    senderName = "unknown";
                }

                row.forwardCaption = "Forwarded from: " + senderName;
            } else {
                row.forwardCaption = null;
            }

            rows.add(row);
            prev = item;
        }
    }

    public boolean isMessage(int position) {
        if (position < 0 || position >= rows.size()) return false;
        int type = rows.get(position).viewType;
        return type == VIEW_TYPE_IN || type == VIEW_TYPE_OUT;
    }

    private boolean hasAvatar(String peer) {
        if (peer == null || peer.length() == 0) return false;
        if (peer.startsWith("-")) {
            Chat chat = chats != null ? chats.get(peer) : null;
            return chat != null && chat.p;
        } else {
            User user = users != null ? users.get(peer) : null;
            return user != null && user.p;
        }
    }

    private String getAvatarPeer(Message item) {
        return item.from_id != null && item.from_id.length() > 0
                ? item.from_id
                : item.peer_id;
    }

    private String getNameString(String id) {
        if (id.startsWith("-")) {
            return getChatTitle(chats, id);
        } else {
            return getUserTitle(users, id);
        }
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

    private View bindActView(MessageRow row, View convertView, ViewGroup parent) {
        TextView actText;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.act_message, parent, false);
            actText = (TextView) convertView.findViewById(R.id.actMessageText);
            convertView.setTag(actText);
        } else {
            actText = (TextView) convertView.getTag();
        }

        actText.setText(row.content);
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
    public int getViewTypeCount() {
        return 4;
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
    }
}
