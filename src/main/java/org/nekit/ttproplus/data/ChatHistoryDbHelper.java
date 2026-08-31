package org.nekit.ttproplus.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dk.bearware.TextMessage;

public class ChatHistoryDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "ChatHistoryDb";
    private static final String DATABASE_NAME = "teamtalk_chat_history.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "chat_history";
    public static final String COL_ID = "_id";
    public static final String COL_SERVER_KEY = "server_key";
    public static final String COL_MSG_TYPE = "msg_type";
    public static final String COL_CHANNEL_ID = "channel_id";
    public static final String COL_CHANNEL_NAME = "channel_name";
    public static final String COL_FROM_USER_ID = "from_user_id";
    public static final String COL_FROM_USERNAME = "from_username";
    public static final String COL_FROM_NICKNAME = "from_nickname";
    public static final String COL_TO_USER_ID = "to_user_id";
    public static final String COL_TO_USERNAME = "to_username";
    public static final String COL_TO_NICKNAME = "to_nickname";
    public static final String COL_MESSAGE_TEXT = "message_text";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_IS_OUTGOING = "is_outgoing";

    private static ChatHistoryDbHelper instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static synchronized ChatHistoryDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ChatHistoryDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private ChatHistoryDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SERVER_KEY + " TEXT NOT NULL, "
                + COL_MSG_TYPE + " INTEGER NOT NULL, "
                + COL_CHANNEL_ID + " INTEGER DEFAULT 0, "
                + COL_CHANNEL_NAME + " TEXT, "
                + COL_FROM_USER_ID + " INTEGER DEFAULT 0, "
                + COL_FROM_USERNAME + " TEXT, "
                + COL_FROM_NICKNAME + " TEXT, "
                + COL_TO_USER_ID + " INTEGER DEFAULT 0, "
                + COL_TO_USERNAME + " TEXT, "
                + COL_TO_NICKNAME + " TEXT, "
                + COL_MESSAGE_TEXT + " TEXT NOT NULL, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_IS_OUTGOING + " INTEGER DEFAULT 0"
                + ");";

        db.execSQL(createTableQuery);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_srv_chan ON " + TABLE_HISTORY + " (" + COL_SERVER_KEY + ", " + COL_CHANNEL_ID + ", " + COL_TIMESTAMP + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_srv_user ON " + TABLE_HISTORY + " (" + COL_SERVER_KEY + ", " + COL_FROM_USER_ID + ", " + COL_TO_USER_ID + ", " + COL_TIMESTAMP + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_srv_ts ON " + TABLE_HISTORY + " (" + COL_SERVER_KEY + ", " + COL_TIMESTAMP + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }

    public void saveMessageAsync(final ChatMessageEntry entry) {
        if (entry == null || entry.getMessageText() == null || entry.getMessageText().trim().isEmpty()) {
            return;
        }
        executor.execute(() -> saveMessageSync(entry));
    }

    public long saveMessageSync(ChatMessageEntry entry) {
        if (entry == null || entry.getMessageText() == null || entry.getMessageText().trim().isEmpty()) {
            return -1;
        }
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_SERVER_KEY, entry.getServerKey());
            values.put(COL_MSG_TYPE, entry.getMsgType());
            values.put(COL_CHANNEL_ID, entry.getChannelId());
            values.put(COL_CHANNEL_NAME, entry.getChannelName());
            values.put(COL_FROM_USER_ID, entry.getFromUserId());
            values.put(COL_FROM_USERNAME, entry.getFromUsername());
            values.put(COL_FROM_NICKNAME, entry.getFromNickname());
            values.put(COL_TO_USER_ID, entry.getToUserId());
            values.put(COL_TO_USERNAME, entry.getToUsername());
            values.put(COL_TO_NICKNAME, entry.getToNickname());
            values.put(COL_MESSAGE_TEXT, entry.getMessageText());
            values.put(COL_TIMESTAMP, entry.getTimestamp() > 0 ? entry.getTimestamp() : System.currentTimeMillis());
            values.put(COL_IS_OUTGOING, entry.isOutgoing() ? 1 : 0);

            long rowId = db.insert(TABLE_HISTORY, null, values);
            entry.setId(rowId);
            return rowId;
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert chat message: " + e.getMessage());
            return -1;
        }
    }

    public void saveIncomingMessage(String serverKey, TextMessage msg, String fromNick, String channelName) {
        if (msg == null || msg.szMessage == null || msg.szMessage.trim().isEmpty()) {
            return;
        }
        ChatMessageEntry entry = new ChatMessageEntry();
        entry.setServerKey(serverKey != null ? serverKey : "");
        entry.setMsgType(msg.nMsgType);
        entry.setChannelId(msg.nChannelID);
        entry.setChannelName(channelName != null ? channelName : "");
        entry.setFromUserId(msg.nFromUserID);
        entry.setFromUsername(msg.szFromUsername != null ? msg.szFromUsername : "");
        entry.setFromNickname(fromNick != null ? fromNick : "");
        entry.setToUserId(msg.nToUserID);
        entry.setMessageText(msg.szMessage);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setOutgoing(false);
        saveMessageAsync(entry);
    }

    public void saveOutgoingMessage(String serverKey, MyTextMessage msg, String myNick, String toNick, String channelName) {
        if (msg == null || msg.szMessage == null || msg.szMessage.trim().isEmpty()) {
            return;
        }
        ChatMessageEntry entry = new ChatMessageEntry();
        entry.setServerKey(serverKey != null ? serverKey : "");
        entry.setMsgType(msg.nMsgType);
        entry.setChannelId(msg.nChannelID);
        entry.setChannelName(channelName != null ? channelName : "");
        entry.setFromUserId(msg.nFromUserID);
        entry.setFromUsername(msg.szFromUsername != null ? msg.szFromUsername : "");
        entry.setFromNickname(myNick != null ? myNick : msg.szNickName);
        entry.setToUserId(msg.nToUserID);
        entry.setToNickname(toNick != null ? toNick : "");
        entry.setMessageText(msg.szMessage);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setOutgoing(true);
        saveMessageAsync(entry);
    }

    public void saveLogMessage(String serverKey, String logText, int channelId, String channelName) {
        if (logText == null || logText.trim().isEmpty()) {
            return;
        }
        ChatMessageEntry entry = new ChatMessageEntry();
        entry.setServerKey(serverKey != null ? serverKey : "");
        entry.setMsgType(ChatMessageEntry.TYPE_SYSTEM);
        entry.setChannelId(channelId);
        entry.setChannelName(channelName != null ? channelName : "");
        entry.setFromNickname("Система");
        entry.setMessageText(logText);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setOutgoing(false);
        saveMessageAsync(entry);
    }

    public List<ChatMessageEntry> getRecentMessages(String serverKey, int filterType, int channelId, int peerUserId, int limit) {
        List<ChatMessageEntry> list = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            StringBuilder where = new StringBuilder();
            List<String> args = new ArrayList<>();

            if (serverKey != null && !serverKey.isEmpty()) {
                where.append(COL_SERVER_KEY).append(" = ?");
                args.add(serverKey);
            }

            if (filterType == ChatMessageEntry.TYPE_CHANNEL) {
                if (where.length() > 0) where.append(" AND ");
                where.append(COL_MSG_TYPE).append(" IN (?, ?, ?)");
                args.add(String.valueOf(ChatMessageEntry.TYPE_CHANNEL));
                args.add(String.valueOf(ChatMessageEntry.TYPE_BROADCAST));
                args.add(String.valueOf(ChatMessageEntry.TYPE_SYSTEM));
                if (channelId > 0) {
                    where.append(" AND ").append(COL_CHANNEL_ID).append(" = ?");
                    args.add(String.valueOf(channelId));
                }
            } else if (filterType == ChatMessageEntry.TYPE_PRIVATE) {
                if (where.length() > 0) where.append(" AND ");
                where.append(COL_MSG_TYPE).append(" = ?");
                args.add(String.valueOf(ChatMessageEntry.TYPE_PRIVATE));
                if (peerUserId > 0) {
                    where.append(" AND (").append(COL_FROM_USER_ID).append(" = ? OR ").append(COL_TO_USER_ID).append(" = ?)");
                    args.add(String.valueOf(peerUserId));
                    args.add(String.valueOf(peerUserId));
                }
            }

            String limitStr = limit > 0 ? String.valueOf(limit) : "100";
            try (Cursor cursor = db.query(TABLE_HISTORY, null,
                    where.length() > 0 ? where.toString() : null,
                    args.toArray(new String[0]),
                    null, null, COL_TIMESTAMP + " DESC", limitStr)) {

                if (cursor != null && cursor.moveToFirst()) {
                    ColumnIndices indices = new ColumnIndices(cursor);
                    do {
                        list.add(cursorToEntry(cursor, indices));
                    } while (cursor.moveToNext());
                }
            }
            Collections.reverse(list);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching recent messages: " + e.getMessage());
        }
        return list;
    }

    public List<ChatMessageEntry> queryHistory(String serverKey, int filterType, String query, int limit, int offset) {
        List<ChatMessageEntry> list = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            StringBuilder where = new StringBuilder();
            List<String> args = new ArrayList<>();

            if (serverKey != null && !serverKey.isEmpty()) {
                where.append(COL_SERVER_KEY).append(" = ?");
                args.add(serverKey);
            }

            if (filterType == ChatMessageEntry.TYPE_CHANNEL) {
                if (where.length() > 0) where.append(" AND ");
                where.append(COL_MSG_TYPE).append(" IN (?, ?, ?)");
                args.add(String.valueOf(ChatMessageEntry.TYPE_CHANNEL));
                args.add(String.valueOf(ChatMessageEntry.TYPE_BROADCAST));
                args.add(String.valueOf(ChatMessageEntry.TYPE_SYSTEM));
            } else if (filterType == ChatMessageEntry.TYPE_PRIVATE) {
                if (where.length() > 0) where.append(" AND ");
                where.append(COL_MSG_TYPE).append(" = ?");
                args.add(String.valueOf(ChatMessageEntry.TYPE_PRIVATE));
            }

            if (query != null && !query.trim().isEmpty()) {
                if (where.length() > 0) where.append(" AND ");
                where.append("(")
                        .append(COL_MESSAGE_TEXT).append(" LIKE ? OR ")
                        .append(COL_FROM_NICKNAME).append(" LIKE ? OR ")
                        .append(COL_FROM_USERNAME).append(" LIKE ? OR ")
                        .append(COL_CHANNEL_NAME).append(" LIKE ?)");
                String pattern = "%" + query.trim() + "%";
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }

            String limitStr = (limit > 0 ? limit : 200) + (offset > 0 ? " OFFSET " + offset : "");
            try (Cursor cursor = db.query(TABLE_HISTORY, null,
                    where.length() > 0 ? where.toString() : null,
                    args.toArray(new String[0]),
                    null, null, COL_TIMESTAMP + " DESC", limitStr)) {

                if (cursor != null && cursor.moveToFirst()) {
                    ColumnIndices indices = new ColumnIndices(cursor);
                    do {
                        list.add(cursorToEntry(cursor, indices));
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying history: " + e.getMessage());
        }
        return list;
    }

    public void clearHistory(String serverKey, int filterType) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            StringBuilder where = new StringBuilder();
            List<String> args = new ArrayList<>();

            if (serverKey != null && !serverKey.isEmpty()) {
                where.append(COL_SERVER_KEY).append(" = ?");
                args.add(serverKey);
            }

            if (filterType == ChatMessageEntry.TYPE_CHANNEL) {
                if (where.length() > 0) where.append(" AND ");
                where.append(COL_MSG_TYPE).append(" IN (?, ?, ?)");
                args.add(String.valueOf(ChatMessageEntry.TYPE_CHANNEL));
                args.add(String.valueOf(ChatMessageEntry.TYPE_BROADCAST));
                args.add(String.valueOf(ChatMessageEntry.TYPE_SYSTEM));
            } else if (filterType == ChatMessageEntry.TYPE_PRIVATE) {
                if (where.length() > 0) where.append(" AND ");
                where.append(COL_MSG_TYPE).append(" = ?");
                args.add(String.valueOf(ChatMessageEntry.TYPE_PRIVATE));
            }

            db.delete(TABLE_HISTORY, where.length() > 0 ? where.toString() : null, args.toArray(new String[0]));
        } catch (Exception e) {
            Log.e(TAG, "Error clearing history: " + e.getMessage());
        }
    }

    public String exportHistoryToText(String serverKey, int filterType) {
        List<ChatMessageEntry> messages = queryHistory(serverKey, filterType, null, 10000, 0);
        Collections.reverse(messages);

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        sb.append("История сообщений TeamTalk").append("\n");
        sb.append("Сервер: ").append(serverKey != null && !serverKey.isEmpty() ? serverKey : "Все серверы").append("\n");
        sb.append("Дата экспорта: ").append(sdf.format(new Date())).append("\n");
        sb.append("Всего сообщений: ").append(messages.size()).append("\n");
        sb.append("--------------------------------------------------\n\n");

        for (ChatMessageEntry msg : messages) {
            String timeStr = sdf.format(new Date(msg.getTimestamp()));
            String typeStr;
            switch (msg.getMsgType()) {
                case ChatMessageEntry.TYPE_PRIVATE:
                    typeStr = msg.isOutgoing() ? "[Личное -> " + msg.getToNickname() + "]" : "[Личное от " + msg.getSenderDisplayName() + "]";
                    break;
                case ChatMessageEntry.TYPE_BROADCAST:
                    typeStr = "[Оповещение]";
                    break;
                case ChatMessageEntry.TYPE_SYSTEM:
                    typeStr = "[Система]";
                    break;
                case ChatMessageEntry.TYPE_CHANNEL:
                default:
                    typeStr = msg.getChannelName().isEmpty() ? "[Канал]" : "[Канал: " + msg.getChannelName() + "]";
                    break;
            }

            sb.append("[").append(timeStr).append("] ")
                    .append(typeStr).append(" ")
                    .append(msg.getSenderDisplayName()).append(": ")
                    .append(msg.getMessageText())
                    .append("\n");
        }

        return sb.toString();
    }

    private static class ColumnIndices {
        final int id;
        final int serverKey;
        final int msgType;
        final int channelId;
        final int channelName;
        final int fromUserId;
        final int fromUsername;
        final int fromNickname;
        final int toUserId;
        final int toUsername;
        final int toNickname;
        final int messageText;
        final int timestamp;
        final int isOutgoing;

        ColumnIndices(Cursor cursor) {
            id = cursor.getColumnIndexOrThrow(COL_ID);
            serverKey = cursor.getColumnIndexOrThrow(COL_SERVER_KEY);
            msgType = cursor.getColumnIndexOrThrow(COL_MSG_TYPE);
            channelId = cursor.getColumnIndexOrThrow(COL_CHANNEL_ID);
            channelName = cursor.getColumnIndexOrThrow(COL_CHANNEL_NAME);
            fromUserId = cursor.getColumnIndexOrThrow(COL_FROM_USER_ID);
            fromUsername = cursor.getColumnIndexOrThrow(COL_FROM_USERNAME);
            fromNickname = cursor.getColumnIndexOrThrow(COL_FROM_NICKNAME);
            toUserId = cursor.getColumnIndexOrThrow(COL_TO_USER_ID);
            toUsername = cursor.getColumnIndexOrThrow(COL_TO_USERNAME);
            toNickname = cursor.getColumnIndexOrThrow(COL_TO_NICKNAME);
            messageText = cursor.getColumnIndexOrThrow(COL_MESSAGE_TEXT);
            timestamp = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
            isOutgoing = cursor.getColumnIndexOrThrow(COL_IS_OUTGOING);
        }
    }

    private ChatMessageEntry cursorToEntry(Cursor cursor, ColumnIndices idx) {
        ChatMessageEntry entry = new ChatMessageEntry();
        entry.setId(cursor.getLong(idx.id));
        entry.setServerKey(cursor.getString(idx.serverKey));
        entry.setMsgType(cursor.getInt(idx.msgType));
        entry.setChannelId(cursor.getInt(idx.channelId));
        entry.setChannelName(cursor.getString(idx.channelName));
        entry.setFromUserId(cursor.getInt(idx.fromUserId));
        entry.setFromUsername(cursor.getString(idx.fromUsername));
        entry.setFromNickname(cursor.getString(idx.fromNickname));
        entry.setToUserId(cursor.getInt(idx.toUserId));
        entry.setToUsername(cursor.getString(idx.toUsername));
        entry.setToNickname(cursor.getString(idx.toNickname));
        entry.setMessageText(cursor.getString(idx.messageText));
        entry.setTimestamp(cursor.getLong(idx.timestamp));
        entry.setOutgoing(cursor.getInt(idx.isOutgoing) == 1);
        return entry;
    }
}
