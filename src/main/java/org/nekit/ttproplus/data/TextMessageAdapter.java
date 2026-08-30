package org.nekit.ttproplus.data;

import java.util.Vector;

import dk.bearware.ServerProperties;
import dk.bearware.TextMsgType;
import org.nekit.ttproplus.gui.AccessibilityAssistant;
import org.nekit.ttproplus.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Patterns;
import android.view.WindowManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import android.app.Activity;
import java.util.regex.Matcher;


public class TextMessageAdapter extends BaseAdapter {

    private Vector<MyTextMessage> messages;
    private Vector<Integer> filteredIndices;

    private final LayoutInflater inflater;
    private final AccessibilityAssistant accessibilityAssistant;

    private ListView listView;
    private boolean autoScroll = true;

    private int myuserid;
    private boolean show_logs = true;

    int def_bg_color, def_text_color;

    int user_bg_color = 0xff4c9fff, user_text_color = Color.WHITE;
    int self_bg_color = 0xff659f5d, self_text_color = Color.WHITE;
    int loginfo_bg_color, loginfo_text_color;
    int logerr_bg_color = 0xffcd0028, logerr_text_color = Color.WHITE;
    int srvinfo_bg_color = Color.DKGRAY, srvinfo_text_color = Color.WHITE;

    private static final long THROTTLE_MS = 120;
    private long lastNotifyTime = 0;
    private boolean flushPending = false;
    private final Handler throttleHandler = new Handler(Looper.getMainLooper());
    private int lastKnownSize = 0;

    public TextMessageAdapter(Context context, AccessibilityAssistant accessibilityAssistant,
                              Vector<MyTextMessage> msgs, int myuserid) {
        this(context, accessibilityAssistant);
        setMyUserID(myuserid);
        setTextMessages(msgs);
    }

    @SuppressWarnings("ResourceType")
    public TextMessageAdapter(Context context, AccessibilityAssistant accessibilityAssistant) {
        inflater = LayoutInflater.from(context);
        this.accessibilityAssistant = accessibilityAssistant;
        messages = new Vector<>();
        filteredIndices = new Vector<>();

        TypedArray array = context.getTheme().obtainStyledAttributes(new int[] {
            android.R.attr.colorBackground,
            android.R.attr.textColorPrimary,
        });
        def_bg_color = array.getColor(0, 0xFF00FF);
        def_text_color = array.getColor(1, 0xFF00FF);

        array.recycle();

        loginfo_bg_color = def_bg_color;
        loginfo_text_color = def_text_color;
    }

    public void setListView(ListView lv) {
        listView = lv;
    }

    public void setAutoScroll(boolean scroll) {
        autoScroll = scroll;
    }

    public void setTextMessages(Vector<MyTextMessage> msgs) {
        this.messages = msgs;
        lastKnownSize = 0;
        rebuildFilter();
        super.notifyDataSetChanged();
    }

    private void rebuildFilter() {
        filteredIndices.clear();
        if(show_logs) {
            for(int i = 0; i < messages.size(); i++)
                filteredIndices.add(i);
        }
        else {
            for(int i = 0; i < messages.size(); i++) {
                MyTextMessage m = messages.get(i);
                switch(m.nMsgType) {
                    case MyTextMessage.MSGTYPE_LOG_ERROR :
                    case MyTextMessage.MSGTYPE_LOG_INFO :
                        break;
                    default :
                        filteredIndices.add(i);
                        break;
                }
            }
        }
        lastKnownSize = messages.size();
    }

    Vector<MyTextMessage> getRawMessages() {
        return messages;
    }

    public void setMyUserID(int userid) {
        myuserid = userid;
    }

    public void showLogMessages(boolean enable) {
        show_logs = enable;
        rebuildFilter();
        flushNow();
    }

    @Override
    public int getCount() {
        return filteredIndices.size();
    }

    @Override
    public Object getItem(int position) {
        return messages.get(filteredIndices.get(position));
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_SERVER = 1;
    private static final int VIEW_TYPE_LOG = 2;
    private static final int VIEW_TYPE_COUNT = 3;

    private final View.OnLongClickListener itemLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            Object tag = v.getTag();
            if (tag instanceof BaseMsgViewHolder) {
                MyTextMessage msg = ((BaseMsgViewHolder) tag).msg;
                if (msg != null) {
                    showMessageInteractionDialog(v.getContext(), msg);
                    return true;
                }
            }
            return false;
        }
    };

    private static class BaseMsgViewHolder {
        MyTextMessage msg;
    }

    private static class UserMsgViewHolder extends BaseMsgViewHolder {
        TextView name;
        TextView msgtext;
        TextView msgdate;
    }

    private static class ServerMsgViewHolder extends BaseMsgViewHolder {
        TextView logmsg;
        TextView logmotd;
        TextView logtm;
    }

    private static class LogMsgViewHolder extends BaseMsgViewHolder {
        TextView logmsg;
        TextView logtm;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        MyTextMessage txtmsg = (MyTextMessage) getItem(position);
        if (txtmsg == null) return VIEW_TYPE_LOG;
        switch (txtmsg.nMsgType) {
            case TextMsgType.MSGTYPE_CHANNEL:
            case TextMsgType.MSGTYPE_BROADCAST:
            case TextMsgType.MSGTYPE_USER:
                return VIEW_TYPE_USER;
            case MyTextMessage.MSGTYPE_SERVERPROP:
                return VIEW_TYPE_SERVER;
            default:
                return VIEW_TYPE_LOG;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MyTextMessage txtmsg = (MyTextMessage) getItem(position);
        int viewType = getItemViewType(position);
        int bg_color = Color.BLACK, text_color = Color.WHITE;

        switch (viewType) {
            case VIEW_TYPE_USER: {
                UserMsgViewHolder holder;
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.item_textmsg, parent, false);
                    holder = new UserMsgViewHolder();
                    holder.name = convertView.findViewById(R.id.name_text);
                    holder.msgtext = convertView.findViewById(R.id.msg_text);
                    holder.msgdate = convertView.findViewById(R.id.time_text);
                    convertView.setTag(holder);
                    convertView.setOnLongClickListener(itemLongClickListener);
                } else {
                    holder = (UserMsgViewHolder) convertView.getTag();
                }

                holder.msg = txtmsg;
                if (txtmsg.nFromUserID == myuserid) {
                    bg_color = self_bg_color;
                    text_color = self_text_color;
                } else {
                    bg_color = user_bg_color;
                    text_color = user_text_color;
                }

                holder.name.setText(txtmsg.szNickName);
                holder.msgdate.setText(txtmsg.time != null ? txtmsg.time.toString() : "");
                holder.msgtext.setText(txtmsg.szMessage);

                holder.name.setTextColor(text_color);
                holder.msgdate.setTextColor(text_color);
                holder.msgtext.setTextColor(text_color);
                break;
            }
            case VIEW_TYPE_SERVER: {
                ServerMsgViewHolder holder;
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.item_textmsg_srvinfo, parent, false);
                    holder = new ServerMsgViewHolder();
                    holder.logmsg = convertView.findViewById(R.id.srvname_text);
                    holder.logmotd = convertView.findViewById(R.id.srvmotd_text);
                    holder.logtm = convertView.findViewById(R.id.logtime_text);
                    convertView.setTag(holder);
                    convertView.setOnLongClickListener(itemLongClickListener);
                } else {
                    holder = (ServerMsgViewHolder) convertView.getTag();
                }

                holder.msg = txtmsg;
                bg_color = srvinfo_bg_color;
                text_color = srvinfo_text_color;

                if (txtmsg.userData instanceof ServerProperties) {
                    ServerProperties p = (ServerProperties) txtmsg.userData;
                    holder.logmsg.setText(p.szServerName);
                    holder.logmotd.setText(p.szMOTD);
                } else {
                    holder.logmsg.setText("");
                    holder.logmotd.setText("");
                }
                holder.logtm.setText(txtmsg.time != null ? txtmsg.time.toString() : "");

                holder.logmsg.setTextColor(text_color);
                holder.logtm.setTextColor(text_color);
                break;
            }
            case VIEW_TYPE_LOG:
            default: {
                LogMsgViewHolder holder;
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.item_textmsg_logmsg, parent, false);
                    holder = new LogMsgViewHolder();
                    holder.logmsg = convertView.findViewById(R.id.logmsg_text);
                    holder.logtm = convertView.findViewById(R.id.logtime_text);
                    convertView.setTag(holder);
                    convertView.setOnLongClickListener(itemLongClickListener);
                } else {
                    holder = (LogMsgViewHolder) convertView.getTag();
                }

                holder.msg = txtmsg;
                switch (txtmsg.nMsgType) {
                    case MyTextMessage.MSGTYPE_LOG_ERROR:
                        bg_color = logerr_bg_color;
                        text_color = logerr_text_color;
                        break;
                    case MyTextMessage.MSGTYPE_LOG_INFO:
                        bg_color = loginfo_bg_color;
                        text_color = loginfo_text_color;
                        break;
                    default:
                        bg_color = loginfo_bg_color;
                        text_color = loginfo_text_color;
                        break;
                }

                holder.logmsg.setText(txtmsg.szMessage);
                holder.logtm.setText(txtmsg.time != null ? txtmsg.time.toString() : "");

                holder.logmsg.setTextColor(text_color);
                holder.logtm.setTextColor(text_color);
                break;
            }
        }

        convertView.setBackgroundColor(bg_color);
        if (accessibilityAssistant != null) {
            convertView.setAccessibilityDelegate(accessibilityAssistant);
        }

        return convertView;
    }

    private boolean isLogMsg(MyTextMessage m) {
        return m.nMsgType == MyTextMessage.MSGTYPE_LOG_ERROR ||
               m.nMsgType == MyTextMessage.MSGTYPE_LOG_INFO;
    }

    @Override
    public void notifyDataSetChanged() {
        if(messages.size() < lastKnownSize) {
            rebuildFilter();
        }
        else {
            for(int i = lastKnownSize; i < messages.size(); i++) {
                if(show_logs || !isLogMsg(messages.get(i)))
                    filteredIndices.add(i);
            }
            lastKnownSize = messages.size();
        }
        throttledNotify();
    }

    private void throttledNotify() {
        long now = SystemClock.uptimeMillis();
        if(now - lastNotifyTime >= THROTTLE_MS) {
            lastNotifyTime = now;
            flushPending = false;
            if (accessibilityAssistant != null) {
                accessibilityAssistant.lockEvents();
            }
            super.notifyDataSetChanged();
            if(autoScroll && listView != null && filteredIndices.size() > 0)
                listView.setSelection(filteredIndices.size() - 1);
            if (accessibilityAssistant != null) {
                accessibilityAssistant.unlockEvents();
            }
        }
        else if(!flushPending) {
            flushPending = true;
            throttleHandler.postAtTime(this::flushNow, lastNotifyTime + THROTTLE_MS);
        }
    }

    private void flushNow() {
        flushPending = false;
        lastNotifyTime = SystemClock.uptimeMillis();
        if (accessibilityAssistant != null) {
            accessibilityAssistant.lockEvents();
        }
        super.notifyDataSetChanged();
        if(autoScroll && listView != null && filteredIndices.size() > 0)
            listView.setSelection(filteredIndices.size() - 1);
        if (accessibilityAssistant != null) {
            accessibilityAssistant.unlockEvents();
        }
    }
    public void showMessageInteractionDialog(final Context context, MyTextMessage txtmsg) {
        final String messageText = txtmsg.szMessage;
        if (messageText == null) {
            return;
        }
        final List<String> urls = new ArrayList<>();
        Matcher m = Patterns.WEB_URL.matcher(messageText);
        while (m.find()) {
            String url = m.group();
            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("ftp://")) {
                url = "http://" + url;
            }
            urls.add(url);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.message_options_title);
        List<String> options = new ArrayList<>();
        options.add(context.getString(R.string.message_option_copy));
        if (!urls.isEmpty()) {
            options.add(context.getString(R.string.message_option_open_link));
        }
        builder.setItems(options.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("message", messageText);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                    }
                    Toast.makeText(context, R.string.message_copied, Toast.LENGTH_SHORT).show();
                } else if (which == 1) {
                    if (urls.size() == 1) {
                        openUrl(context, urls.get(0));
                    } else {
                        showLinkSelectionDialog(context, urls);
                    }
                }
            }
        });
        AlertDialog dialog = builder.create();
        if (!(context instanceof Activity)) {
            if (dialog.getWindow() != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                } else {
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
                }
            }
        }
        dialog.show();
    }

    public void openUrl(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.err_cannot_open_link, Toast.LENGTH_SHORT).show();
        }
    }

    public void showLinkSelectionDialog(final Context context, final List<String> urls) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.message_select_link);
        builder.setItems(urls.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openUrl(context, urls.get(which));
            }
        });
        AlertDialog dialog = builder.create();
        if (!(context instanceof Activity)) {
            if (dialog.getWindow() != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                } else {
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
                }
            }
        }
        dialog.show();
    }
}
