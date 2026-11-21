package com.val00n.dmpg.model.message;

import java.io.File;

public class PendingMessage {
    public final Message message;
    public final String replyId;
    public final long createdAt;

    public File attachmentFile;
    public String attachmentName;
    public String attachmentMime;
    public boolean attachmentAsFile;
    public boolean attachmentAsSpoiler;

    public int minServerId;
    public long lastAttemptAt;
    public int confirmAttempts;
    public boolean accepted;
    public boolean uncertain;

    public PendingMessage(Message message, String replyId, int minServerId) {
        this.message = message;
        this.replyId = replyId;
        this.createdAt = message.date;
        this.lastAttemptAt = message.date;
        this.minServerId = minServerId;
    }
}