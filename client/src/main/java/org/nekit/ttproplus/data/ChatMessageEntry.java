package org.nekit.ttproplus.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatMessageEntry {

    public static final int TYPE_PRIVATE = 1;
    public static final int TYPE_CHANNEL = 2;
    public static final int TYPE_BROADCAST = 3;
    public static final int TYPE_SYSTEM = 4;

    private long id;
    private String serverKey = "";
    private int msgType = TYPE_CHANNEL;
    private int channelId;
    private String channelName = "";
    private int fromUserId;
    private String fromUsername = "";
    private String fromNickname = "";
    private int toUserId;
    private String toUsername = "";
    private String toNickname = "";
    private String messageText = "";
    private long timestamp = System.currentTimeMillis();
    private boolean outgoing;

    public ChatMessageEntry() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getServerKey() {
        return serverKey != null ? serverKey : "";
    }

    public void setServerKey(String serverKey) {
        this.serverKey = serverKey;
    }

    public int getMsgType() {
        return msgType;
    }

    public void setMsgType(int msgType) {
        this.msgType = msgType;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName != null ? channelName : "";
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public int getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(int fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getFromUsername() {
        return fromUsername != null ? fromUsername : "";
    }

    public void setFromUsername(String fromUsername) {
        this.fromUsername = fromUsername;
    }

    public String getFromNickname() {
        return fromNickname != null ? fromNickname : "";
    }

    public void setFromNickname(String fromNickname) {
        this.fromNickname = fromNickname;
    }

    public int getToUserId() {
        return toUserId;
    }

    public void setToUserId(int toUserId) {
        this.toUserId = toUserId;
    }

    public String getToUsername() {
        return toUsername != null ? toUsername : "";
    }

    public void setToUsername(String toUsername) {
        this.toUsername = toUsername;
    }

    public String getToNickname() {
        return toNickname != null ? toNickname : "";
    }

    public void setToNickname(String toNickname) {
        this.toNickname = toNickname;
    }

    public String getMessageText() {
        return messageText != null ? messageText : "";
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }

    public String getSenderDisplayName() {
        if (fromNickname != null && !fromNickname.trim().isEmpty()) {
            return fromNickname;
        }
        if (fromUsername != null && !fromUsername.trim().isEmpty()) {
            return fromUsername;
        }
        return "ID " + fromUserId;
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getFormattedShortTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
