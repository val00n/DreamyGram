package com.val00n.dmpg.model.message;

public class PendingMessage {
    public final Message message;
    public final String replyId;
    public final long createdAt;

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