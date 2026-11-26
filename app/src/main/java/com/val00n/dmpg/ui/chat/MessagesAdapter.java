package com.val00n.dmpg.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.FileParams;
import com.val00n.dmpg.model.message.Act;
import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.model.message.MessageRow;
import com.val00n.dmpg.services.SettingsManager;
import com.val00n.dmpg.ui.AspectImageView;
import com.val00n.dmpg.ui.AvatarViewBinder;
import com.val00n.dmpg.ui.ImageViewBinder;
import com.val00n.dmpg.utils.DateUtils;
import java.util.ArrayList;
import java.util.Map;

import static android.content.ContentValues.TAG;

public class MessagesAdapter extends BaseAdapter {

    private final Map<String, User> users;
    private final Map<String, Chat> chats;
    private final ArrayList<Message> items;
    private final ArrayList<MessageRow> rows;
    private final AvatarViewBinder avatarViewBinder;
    private final ImageViewBinder imageViewBinder;

    private int rowMarginPx = 4;
    private int avatarWidthPx = 56;
    private int albumSpacingPx = 3;
    private int stickerSizePx = 128;

    private LayoutInflater inflater;
    private boolean isSavedMessages;
    private boolean isChat;
    private String topic;
    private AlbumView.Listener albumListener;
    private MediaClickListener mediaClickListener;

    private String chatStyle = SettingsManager.CHAT_STYLE_DEFAULT;

    private final FrameLayout.LayoutParams photoLayoutParams;

    private static final int VIEW_TYPE_IN = 0;
    private static final int VIEW_TYPE_OUT = 1;
    private static final int VIEW_TYPE_ACT = 2;
    private static final int VIEW_TYPE_DATE = 3;

    private static final int MAX_MEDIA_GROUP_SIZE = 10;
    private static final int GROUP_INTERVAL_SECONDS = 6 * 60;

    public interface MediaClickListener {
        void onImageClick(MessageRow item);
        void onVideoClick(MessageRow item);
    }

    public MessagesAdapter(ArrayList<Message> items, Map<String, User> users, Map<String, Chat> chats,
                           boolean isSavedMessages, boolean isChat,
                           AvatarViewBinder avatarViewBinder, ImageViewBinder imageViewBinder) {
        this.items = items;
        this.users = users;
        this.chats = chats;
        this.avatarViewBinder = avatarViewBinder;
        this.imageViewBinder = imageViewBinder;
        this.isSavedMessages = isSavedMessages;
        this.isChat = isChat;

        this.photoLayoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );

        rows = new ArrayList<MessageRow>();
        buildRows();
    }

    private void ensureViews(ViewGroup parent) {
        if (inflater != null) return;

        Context ctx = parent.getContext();
        inflater = LayoutInflater.from(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;

        rowMarginPx = Math.round(rowMarginPx * density);
        avatarWidthPx = Math.round(avatarWidthPx * density);
        albumSpacingPx = Math.round(albumSpacingPx * density);
        stickerSizePx = Math.round(stickerSizePx * density);
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

    public void setTopic(String topic) {
        if ((this.topic == null && topic == null) || (this.topic != null && this.topic.equals(topic))) {
            return;
        }
        this.topic = topic;
        notifyDataSetChanged();
    }

    public void setAlbumListener(AlbumView.Listener listener) {
        this.albumListener = listener;
    }

    public void setMediaClickListener(MediaClickListener listener) {
        this.mediaClickListener = listener;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ensureViews(parent);

        final int viewType = getItemViewType(position);
        final MessageRow row = rows.get(position);

        if (viewType == VIEW_TYPE_ACT || viewType == VIEW_TYPE_DATE) {
            return bindActView(row, convertView, parent);
        }

        final ViewHolder holder;

        if (convertView != null) {
            ViewHolder existing = (ViewHolder) convertView.getTag();
            if (existing == null || !chatStyle.equals(existing.style)) {
                convertView = null;
            }
        }

        if (convertView == null) {
            int layout = getMessageLayout(viewType);
            convertView = inflater.inflate(layout, parent, false);
            holder = new ViewHolder();
            holder.style = chatStyle;

            holder.avatar = (ImageView) convertView.findViewById(R.id.messageAvatar);
            holder.title = (TextView) convertView.findViewById(R.id.messageTitle);
            holder.image = (AspectImageView) convertView.findViewById(R.id.messageImage);
            holder.imageContainer = convertView.findViewById(R.id.messageImageContainer);
            holder.album = (AlbumView) convertView.findViewById(R.id.messageAlbum);
            holder.file = (FileMessageView) convertView.findViewById(R.id.messageFile);
            holder.date = (TextView) convertView.findViewById(R.id.messageDate);
            holder.text = (TextView) convertView.findViewById(R.id.messageText);
            holder.textLayout = (LinearLayout) convertView.findViewById(R.id.messageTextLayout);
            holder.titleRow = (LinearLayout) convertView.findViewById(R.id.messageTitleRow);
            holder.captionLayout = (LinearLayout) convertView.findViewById(R.id.messageCaptionLayout);
            holder.quoteLayout = (LinearLayout) convertView.findViewById(R.id.messageQuoteLayout);
            holder.quoteText = (TextView) convertView.findViewById(R.id.messageQuoteText);
            holder.captionText = (TextView) convertView.findViewById(R.id.messageCaptionText);
            holder.divider = convertView.findViewById(R.id.messageDivider);
            holder.playOverlay = convertView.findViewById(R.id.messagePlayOverlay);

            if (holder.textLayout != null) {
                holder.defaultPaddingLeft = holder.textLayout.getPaddingLeft();
                holder.defaultPaddingTop = holder.textLayout.getPaddingTop();
                holder.defaultPaddingRight = holder.textLayout.getPaddingRight();
                holder.defaultPaddingBottom = holder.textLayout.getPaddingBottom();
            }

            if (holder.image != null) {
                holder.defaultImageScaleType = holder.image.getScaleType();
            }

            if (viewType == VIEW_TYPE_OUT) {
                holder.status = (ImageView) convertView.findViewById(R.id.messageStatus);
            }

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ViewGroup root = (ViewGroup) convertView;

        if (row.content != null && row.content.length() > 0) {
            holder.text.setVisibility(View.VISIBLE);
            holder.text.setText(row.content);
        } else {
            holder.text.setVisibility(View.GONE);
        }

        final boolean isModern = SettingsManager.CHAT_STYLE_MODERN.equals(chatStyle);
        final boolean hasNextInGroup =
                position + 1 < rows.size() && rows.get(position + 1).grouped;

        if (isModern) {
            if (hasNextInGroup) {
                holder.date.setVisibility(View.GONE);
            } else {
                holder.date.setText(row.dateText);
                holder.date.setVisibility(View.VISIBLE);
            }

            if (holder.title != null) {
                if (!isChat || row.grouped) {
                    holder.title.setVisibility(View.GONE);
                } else {
                    holder.title.setVisibility(View.VISIBLE);
                    holder.title.setText(row.title);
                }
            }
        } else {
            if (row.grouped) {
                if (holder.avatar != null) {
                    holder.avatar.setVisibility(View.GONE);
                }
                if (holder.titleRow != null) {
                    holder.titleRow.setVisibility(View.GONE);
                }
                if (holder.divider != null) {
                    holder.divider.setVisibility(View.GONE);
                }

                if (holder.avatar != null) {
                    if (viewType == VIEW_TYPE_OUT) {
                        holder.textLayout.setPadding(
                                holder.defaultPaddingLeft,
                                holder.defaultPaddingTop,
                                avatarWidthPx,
                                holder.defaultPaddingBottom);
                    } else {
                        holder.textLayout.setPadding(
                                avatarWidthPx,
                                holder.defaultPaddingTop,
                                holder.defaultPaddingRight,
                                holder.defaultPaddingBottom);
                    }
                } else {
                    holder.textLayout.setPadding(
                            holder.defaultPaddingLeft,
                            holder.defaultPaddingTop,
                            holder.defaultPaddingRight,
                            holder.defaultPaddingBottom);
                }
            } else {
                if (holder.title != null) {
                    holder.title.setVisibility(View.VISIBLE);
                    holder.title.setText(row.title);
                }
                holder.date.setText(row.dateText);

                if (holder.avatar != null) {
                    holder.avatar.setVisibility(View.VISIBLE);
                }
                if (holder.titleRow != null) {
                    holder.titleRow.setVisibility(View.VISIBLE);
                }
                if (holder.divider != null) {
                    holder.divider.setVisibility(View.VISIBLE);
                }

                holder.textLayout.setPadding(
                        holder.defaultPaddingLeft,
                        holder.defaultPaddingTop,
                        holder.defaultPaddingRight,
                        holder.defaultPaddingBottom);
            }
        }

        boolean nextGrouped = (position + 1 < rows.size()) && rows.get(position + 1).grouped;
        int topMargin = row.grouped ? 0 : rowMarginPx;
        int bottomMargin = nextGrouped ? 0 : rowMarginPx;

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.textLayout.getLayoutParams();
        if (lp.leftMargin != rowMarginPx || lp.rightMargin != rowMarginPx
                || lp.topMargin != topMargin || lp.bottomMargin != bottomMargin) {
            lp.setMargins(rowMarginPx, topMargin, rowMarginPx, bottomMargin);
            holder.textLayout.setLayoutParams(lp);
        }

        if (row.quoteText != null) {
            holder.quoteLayout.setVisibility(View.VISIBLE);
            holder.quoteText.setText(row.quoteText);
        } else {
            holder.quoteLayout.setVisibility(View.GONE);
        }

        if (holder.captionLayout != null) {
            if (row.forwardCaption != null) {
                holder.captionLayout.setVisibility(View.VISIBLE);
                holder.captionText.setText(row.forwardCaption);
            } else {
                holder.captionLayout.setVisibility(View.GONE);
            }
        } else if (holder.captionText != null) {
            if (row.forwardCaption != null) {
                holder.captionText.setVisibility(View.VISIBLE);
                holder.captionText.setText(row.forwardCaption);
            } else {
                holder.captionText.setVisibility(View.GONE);
            }
        }

        if (viewType == VIEW_TYPE_OUT && holder.status != null) {
            boolean showStatus = row.statusRes != 0 && !(isModern && hasNextInGroup);

            if (showStatus) {
                holder.status.setVisibility(View.VISIBLE);
                holder.status.setImageResource(row.statusRes);
            } else {
                holder.status.setVisibility(View.GONE);
            }
        }

        if (row.mediaGroup != null) {
            root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        } else {
            root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }

        if (row.hasMedia) {
            if (row.mediaGroup != null && holder.album != null) {
                holder.album.setVisibility(View.VISIBLE);
                holder.image.square(false);
                holder.album.setSpacingPx(albumSpacingPx);
                holder.album.bind(row.mediaGroup, imageViewBinder, albumListener);

                if (holder.imageContainer != null) {
                    holder.imageContainer.setVisibility(View.GONE);
                }
                holder.image.setVisibility(View.GONE);
                holder.image.setOnClickListener(null);
                holder.image.setTag(null);
                holder.image.setImageDrawable(null);
                holder.image.setSourceSize(0, 0);
                if (holder.playOverlay != null) {
                    holder.playOverlay.setVisibility(View.GONE);
                }
                holder.file.setVisibility(View.GONE);
            } else if (row.isPhoto || row.isSticker) {
                if (holder.album != null) {
                    holder.album.setVisibility(View.GONE);
                    holder.album.unbind();
                }
                if (holder.imageContainer != null) {
                    holder.imageContainer.setVisibility(View.VISIBLE);
                }
                holder.image.setVisibility(View.VISIBLE);
                holder.image.square(false);
                if (holder.playOverlay != null) {
                    holder.playOverlay.setVisibility(View.GONE);
                }
                holder.file.setVisibility(View.GONE);

                holder.image.setSourceSize(row.mediaWidth, row.mediaHeight);
                updateImageLayoutParams(holder.image, row.isSticker, viewType);

                if (row.message.id <= 0) {
                    holder.image.setScaleType(ImageView.ScaleType.CENTER);
                    holder.image.setOnClickListener(null);
                    holder.image.setTag(null);
                    holder.image.setImageResource(android.R.drawable.ic_menu_report_image);
                } else {
                    if (holder.defaultImageScaleType != null) {
                        holder.image.setScaleType(holder.defaultImageScaleType);
                    }
                    final String imagePeer = row.chatPeer != null ? row.chatPeer : row.peer;

                    holder.image.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mediaClickListener != null) {
                                mediaClickListener.onImageClick(row);
                            } else {
                                Intent intent = new Intent(v.getContext(), ImageViewerActivity.class);
                                intent.putExtra("PEER", imagePeer);
                                intent.putExtra("MESSAGE_ID", row.messageId);
                                intent.putExtra("MEDIA_MIME", row.mediaMime);
                                intent.putExtra("MEDIA_NAME", row.message.media.name);
                                intent.putExtra("MEDIA_SIZE", row.message.media.size);
                                v.getContext().startActivity(intent);
                            }
                        }
                    });

                    FileParams params = (row.isSticker
                            && ("application/x-tgsticker".equals(row.mediaMime)
                            || "video/webm".equals(row.mediaMime)))
                            ? FileParams.fileThumb()
                            : FileParams.preview();

                    imageViewBinder.bind(holder.image, imagePeer, row.messageId, row.thumb, params);
                }
            } else if (row.isVideo) {
                if (holder.album != null) {
                    holder.album.setVisibility(View.GONE);
                    holder.album.unbind();
                }
                if (holder.imageContainer != null) {
                    holder.imageContainer.setVisibility(View.VISIBLE);
                }
                holder.image.setVisibility(View.VISIBLE);
                holder.image.square(true);
                holder.image.setSourceSize(0, 0);
                updateImageLayoutParams(holder.image, false, viewType);
                if (holder.defaultImageScaleType != null) {
                    holder.image.setScaleType(holder.defaultImageScaleType);
                }
                holder.file.setVisibility(View.GONE);

                if (holder.playOverlay != null) {
                    holder.playOverlay.setVisibility(View.VISIBLE);
                }

                final String imagePeer = row.chatPeer != null ? row.chatPeer : row.peer;

                imageViewBinder.bind(holder.image, imagePeer, row.messageId, row.thumb, FileParams.fileThumb());

                holder.image.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mediaClickListener != null) {
                            mediaClickListener.onVideoClick(row);
                        } else {
                            Intent intent = new Intent(v.getContext(), VideoViewerActivity.class);
                            intent.putExtra("PEER", imagePeer);
                            intent.putExtra("MESSAGE_ID", row.messageId);
                            intent.putExtra("FILENAME", row.message.media.name);
                            v.getContext().startActivity(intent);
                        }
                    }
                });
            } else if (row.isFile) {
                if (holder.album != null) {
                    holder.album.setVisibility(View.GONE);
                    holder.album.unbind();
                }
                if (holder.imageContainer != null) {
                    holder.imageContainer.setVisibility(View.GONE);
                }
                holder.image.setVisibility(View.GONE);
                holder.image.setTag(null);
                holder.image.setImageDrawable(null);
                if (holder.playOverlay != null) {
                    holder.playOverlay.setVisibility(View.GONE);
                }
                holder.file.setVisibility(View.VISIBLE);
                holder.file.bind(row.message, row.chatPeer != null ? row.chatPeer : row.peer);
            } else {
                if (holder.album != null) {
                    holder.album.setVisibility(View.GONE);
                    holder.album.unbind();
                }
                if (holder.imageContainer != null) {
                    holder.imageContainer.setVisibility(View.GONE);
                }
                holder.image.setVisibility(View.GONE);
                holder.image.setTag(null);
                holder.image.setImageDrawable(null);
                if (holder.playOverlay != null) {
                    holder.playOverlay.setVisibility(View.GONE);
                }
                holder.file.setVisibility(View.GONE);
            }
        } else {
            if (holder.album != null) {
                holder.album.setVisibility(View.GONE);
                holder.album.unbind();
            }
            if (holder.imageContainer != null) {
                holder.imageContainer.setVisibility(View.GONE);
            }
            holder.image.setVisibility(View.GONE);
            holder.image.setTag(null);
            holder.image.setImageDrawable(null);
            if (holder.playOverlay != null) {
                holder.playOverlay.setVisibility(View.GONE);
            }
            holder.file.setVisibility(View.GONE);
        }

        if (holder.avatar != null) {
            avatarViewBinder.bind(holder.avatar, row.peer, row.hasAvatar);
        }

        return convertView;
    }

    private boolean isGrouped(Message item, Message prev) {
        if (prev == null || item.fwd != null || prev.act != null
                || item.date - prev.date >= GROUP_INTERVAL_SECONDS
                || !isSameSender(item, prev)) {
            return false;
        }
        return true;
    }

    private void buildRows() {
        rows.clear();
        Message prev = null;
        String lastDayKey = null;

        for (int i = 0; i < items.size(); i++) {
            Message item = items.get(i);

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

            if (canBeAlbumFirst(item)) {
                ArrayList<Message> album = collectMediaGroup(items, i);

                if (album.size() > 1) {
                    MessageRow row = buildMessageRow(item, isNewDay, prev);

                    row.mediaGroup = buildMediaGroup(album);

                    if (row.viewType == VIEW_TYPE_OUT) {
                        row.statusRes = getAlbumStatusRes(album);
                    }

                    rows.add(row);

                    prev = album.get(album.size() - 1);
                    i += album.size() - 1;
                    continue;
                }
            }

            MessageRow row = buildMessageRow(item, isNewDay, prev);
            rows.add(row);

            prev = item;
        }
    }

    private MessageRow buildMessageRow(Message item, boolean isNewDay, Message prev) {
        MessageRow row = new MessageRow();

        final boolean outgoing = item.out || isSavedMessages;

        row.viewType = outgoing ? VIEW_TYPE_OUT : VIEW_TYPE_IN;
        row.message = item;

        row.grouped = !isNewDay && isGrouped(item, prev);

        row.content = item.text;
        row.title = getNameString(item);
        row.dateText = DateUtils.formatChatTime(item.date, false, 0);
        row.peer = getAvatarPeer(item);
        row.chatPeer = item.peer_id;
        row.hasAvatar = hasAvatar(row.peer);

        String mediaType = item.media != null ? item.media.type : null;
        row.isVideo = "video".equals(mediaType);
        row.isPhoto = "photo".equals(mediaType);
        row.isSticker = "sticker".equals(mediaType);
        row.isFile = "document".equals(mediaType)
                || "audio".equals(mediaType)
                || "voice".equals(mediaType);

        boolean localPhoto = item.out && item.id <= 0 && row.isPhoto;
        row.hasMedia = item.media != null && !item.media.hide && (item.id > 0 || localPhoto);

        if (row.hasMedia) {
            row.messageId = Integer.toString(item.id);
            row.mediaWidth = item.media.width;
            row.mediaHeight = item.media.height;
            row.mediaType = item.media.type;
            row.mediaMime = item.media.mime;
            row.thumb = item.media.thumb;
        }

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

        boolean isTopicRootReply = item.reply != null
                && (item.reply.quote == null || item.reply.quote.length() == 0)
                && ((topic != null && topic.equals(item.reply.replyId))
                || (item.reply.msg != null && item.reply.msg.act != null
                && "TopicCreate".equals(item.reply.msg.act.action)));

        if (item.reply != null && !isTopicRootReply) {
            String replyAuthorId = null;

            if (item.reply.msg != null) {
                replyAuthorId = item.reply.msg.from_id;
            }
            if (replyAuthorId == null) {
                replyAuthorId = item.reply.peer;
            }

            String replyAuthor = replyAuthorId != null ? getNameString(replyAuthorId) : "Reply";

            String replyContent = null;
            if (item.reply.quote != null && item.reply.quote.length() > 0) {
                replyContent = item.reply.quote;
            } else if (item.reply.msg != null) {
                replyContent = getReplyPreview(item.reply.msg);
            }

            if (replyContent != null && replyContent.length() > 0) {
                row.quoteText = replyAuthor + ":\n" + replyContent;
            } else {
                row.quoteText = replyAuthor;
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
                senderName = App.get().getString(R.string.adapter_unknown);
            }

            row.forwardCaption = App.get().getString(R.string.adapter_forwarded_from, senderName);
        } else {
            row.forwardCaption = null;
        }

        return row;
    }

    private static boolean hasText(Message message) {
        return message != null && message.text != null && message.text.trim().length() > 0;
    }

    private boolean canBeAlbumFirst(Message message) {
        if (message == null || message.act != null || message.id <= 0 || message.group <= 0) return false;
        if (message.media == null || message.media.hide) return false;
        String t = message.media.type;
        return "photo".equals(t) || "video".equals(t);
    }

    private boolean canBeAlbumChild(Message message) {
        if (message == null || message.act != null || message.id <= 0 || message.group <= 0) return false;
        if (message.media == null || message.media.hide) return false;
        if (hasText(message)) return false;
        String t = message.media.type;
        return "photo".equals(t) || "video".equals(t);
    }

    private String getReplyPreview(Message msg) {
        if (msg.text != null && msg.text.length() > 0) {
            return msg.text;
        }
        if (msg.media != null && msg.media.type != null) {
            if ("photo".equals(msg.media.type)) {
                return "[photo]";
            }
            return "[" + msg.media.type + "]";
        }
        return "";
    }

    private boolean isSameAlbumSender(Message a, Message b) {
        if (a == null || b == null) return false;
        if (a.out != b.out) return false;

        return isSameSender(a, b);
    }

    private ArrayList<Message> collectMediaGroup(ArrayList<Message> items, int startIndex) {
        ArrayList<Message> album = new ArrayList<Message>();

        Message first = items.get(startIndex);

        if (!canBeAlbumFirst(first)) {
            return album;
        }

        album.add(first);

        Message prev = first;
        long groupId = first.group;
        String firstDayKey = DateUtils.dayKey(first.date);

        for (int i = startIndex + 1; i < items.size(); i++) {
            Message next = items.get(i);

            if (!canBeAlbumChild(next)) {
                break;
            }

            if (!firstDayKey.equals(DateUtils.dayKey(next.date))) {
                break;
            }

            if (!isSameAlbumSender(first, next)) {
                break;
            }

            if (groupId > 0) {
                if (next.group != groupId) {
                    break;
                }
            } else {
                if (next.group > 0) {
                    break;
                }

                if (next.date - prev.date > GROUP_INTERVAL_SECONDS) {
                    break;
                }
            }

            album.add(next);
            prev = next;

            if (album.size() >= MAX_MEDIA_GROUP_SIZE) {
                break;
            }
        }

        return album;
    }

    private MediaGroup buildMediaGroup(ArrayList<Message> album) {
        MediaGroup group = new MediaGroup();

        for (Message message : album) {
            MessageRow child = new MessageRow();

            child.message = message;
            child.peer = getAvatarPeer(message);
            child.chatPeer = message.peer_id;
            child.messageId = Integer.toString(message.id);

            child.hasMedia = true;

            if (message.media != null) {
                child.isVideo = "video".equals(message.media.type);
                child.isPhoto = "photo".equals(message.media.type);
                child.isSticker = "sticker".equals(message.media.type);
                child.isFile = !child.isPhoto && !child.isVideo && !child.isSticker;

                child.mediaWidth = message.media.width;
                child.mediaHeight = message.media.height;
                child.mediaType = message.media.type;
                child.mediaMime = message.media.mime;
                child.thumb = message.media.thumb;
            }

            group.items.add(child);
        }

        return group;
    }

    private int getAlbumStatusRes(ArrayList<Message> album) {
        if (album == null || album.isEmpty()) {
            return 0;
        }

        boolean hasError = false;
        boolean hasSending = false;
        boolean allRead = true;

        for (Message message : album) {
            if (message.messageState == Message.MessageState.MESSAGE_ERROR) {
                hasError = true;
                allRead = false;
            } else if (message.messageState == Message.MessageState.MESSAGE_SENDING) {
                hasSending = true;
                allRead = false;
            } else if (message.messageState == Message.MessageState.MESSAGE_SENT) {
                allRead = false;
            }
        }

        if (hasError) {
            return R.drawable.ic_cross;
        }

        if (hasSending) {
            return R.drawable.ic_wait;
        }

        if (allRead) {
            return R.drawable.ic_double_checkmark;
        }

        return R.drawable.ic_checkmark;
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
        if (item.from_id != null && item.from_id.length() > 0) {
            return item.from_id;
        }
        if (item.out && App.selfId != null && (item.id <= 0 || item.peer_id == null || !item.peer_id.startsWith("-"))) {
            return App.selfId;
        }
        return item.peer_id;
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
                return App.get().getString(R.string.act_group_created);
            case "ChannelCreate":
                return App.get().getString(R.string.act_channel_created);
            case "ChatEditPhoto":
                return App.get().getString(R.string.act_photo_updated);
            case "HistoryClear":
                return App.get().getString(R.string.act_history_cleared);
            case "ChatEditTitle":
                return App.get().getString(R.string.act_title_changed, act.t);
            case "PinMessage":
                return App.get().getString(R.string.act_pinned_message, name);
            case "ChatJoinedByLink":
                return App.get().getString(R.string.act_joined_by_link, name);
            case "ChatJoinedByRequest":
                return App.get().getString(R.string.act_joined_by_request, name);
            case "ChatAddUser":
                if (item.from_id != null && item.from_id.equals(act.user))
                    return App.get().getString(R.string.act_joined, name);
                return App.get().getString(R.string.act_added_user, name, getUserTitle(users, act.user));
            case "ChatDeleteUser":
                if (item.from_id != null && item.from_id.equals(act.user))
                    return App.get().getString(R.string.act_left, name);
                return App.get().getString(R.string.act_removed_user, name, getUserTitle(users, act.user));
            default:
                return App.get().getString(R.string.act_unknown, type);
        }
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
        if (prev == null || item.out != prev.out) return false;
        String id = item.from_id != null && item.from_id.length() > 0 ? item.from_id : item.peer_id;
        String prevId = prev.from_id != null && prev.from_id.length() > 0 ? prev.from_id : prev.peer_id;
        return id != null && id.equals(prevId);
    }

    public int findRowPositionByMessageId(int messageId) {
        for (int i = 0; i < rows.size(); i++) {
            MessageRow row = rows.get(i);
            if (row.message != null && row.message.id == messageId) {
                return i;
            }
            if (row.mediaGroup != null) {
                for (int j = 0; j < row.mediaGroup.items.size(); j++) {
                    MessageRow child = row.mediaGroup.items.get(j);
                    if (child.message != null && child.message.id == messageId) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public int findAnchorMessageId(int startPosition) {
        for (int i = Math.max(0, startPosition); i < rows.size(); i++) {
            MessageRow row = rows.get(i);
            if (row.message != null && row.message.id > 0) {
                return row.message.id;
            }
        }
        return -1;
    }

    private void updateImageLayoutParams(AspectImageView image, boolean isSticker, int viewType) {
        ViewGroup.LayoutParams layoutParams = image.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }

        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) layoutParams;
        int targetWidth = isSticker ? stickerSizePx : ViewGroup.LayoutParams.MATCH_PARENT;
        int targetHeight = isSticker ? stickerSizePx : ViewGroup.LayoutParams.WRAP_CONTENT;
        int targetGravity = (isSticker && viewType == VIEW_TYPE_OUT)
                ? android.view.Gravity.RIGHT
                : android.view.Gravity.LEFT;

        if (flp.width != targetWidth || flp.height != targetHeight || flp.gravity != targetGravity) {
            flp.width = targetWidth;
            flp.height = targetHeight;
            flp.gravity = targetGravity;
            image.setLayoutParams(flp);
        }
    }

    public void setChatStyle(String chatStyle) {
        if (chatStyle == null) {
            chatStyle = SettingsManager.CHAT_STYLE_DEFAULT;
        }
        if (this.chatStyle.equals(chatStyle)) {
            return;
        }
        this.chatStyle = chatStyle;
        super.notifyDataSetChanged();
    }

    private int getMessageLayout(int viewType) {
        if (SettingsManager.CHAT_STYLE_MODERN.equals(chatStyle)) {
            return viewType == VIEW_TYPE_OUT
                    ? R.layout.m_message_out
                    : R.layout.m_message;
        }
        if (SettingsManager.CHAT_STYLE_COMPACT.equals(chatStyle)) {
            return viewType == VIEW_TYPE_OUT
                    ? R.layout.c_message_out
                    : R.layout.c_message;
        }
        return viewType == VIEW_TYPE_OUT
                ? R.layout.message_out
                : R.layout.message;
    }

    static class ViewHolder {
        String style;
        ImageView avatar;
        TextView title;
        AspectImageView image;
        View imageContainer;
        ImageView.ScaleType defaultImageScaleType;
        AlbumView album;
        FileMessageView file;
        TextView date;
        TextView text;
        TextView captionText;
        TextView quoteText;
        LinearLayout titleRow;
        LinearLayout textLayout;
        LinearLayout captionLayout;
        LinearLayout quoteLayout;
        ImageView status;
        View playOverlay;
        View divider;
        int defaultPaddingLeft;
        int defaultPaddingTop;
        int defaultPaddingRight;
        int defaultPaddingBottom;
    }
}
