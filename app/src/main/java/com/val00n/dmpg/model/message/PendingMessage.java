package com.val00n.dmpg.model.message;

public class PendingMessage {
    public final Message message;
    public final String replyId;

    public PendingMessage(Message message, String replyId) {
        this.message = message;
        this.replyId = replyId;
    }
}