package org.nekit.ttproplus.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import de.sciss.jump3r.mp3.Encoder;
import dk.bearware.Channel;
import dk.bearware.ClientError;
import dk.bearware.ClientStatistics;
import dk.bearware.TeamTalkBase;
import dk.bearware.TextMessage;
import dk.bearware.User;
import dk.bearware.UserAccount;
import dk.bearware.events.ClientEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.MyTextMessage;
import org.nekit.ttproplus.data.Preferences;
import org.nekit.ttproplus.data.ServerEntry;
import org.nekit.ttproplus.data.TextMessageAdapter;
import org.nekit.ttproplus.gui.FloatingWindowManager;

public class FloatingWindowManager {
    private ImageButton btnChannels;
    private ImageButton btnChat;
    private ImageButton btnMute;
    private ImageButton btnServers;
    private ImageButton btnVoice;
    private final Context context;
    private ImageView dragHandle;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private final SharedPreferences prefs;
    private final TeamTalkService service;
    private TextView txtPing;
    private final WindowManager windowManager;
    private boolean isShowing = false;
    private int lastKnownPing = -1;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() { 
        @Override
        public void run() {
            if (FloatingWindowManager.this.isShowing) {
                FloatingWindowManager.this.updateUIInternal();
                FloatingWindowManager.this.updateHandler.postDelayed(this, 1000L);
            }
        }
    };

    public FloatingWindowManager(TeamTalkService service) {
        this.service = service;
        this.context = service.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService("window");
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        initView();
    }

    private void initView() {
        this.floatingView = LayoutInflater.from(this.context).inflate(R.layout.layout_floating_window, (ViewGroup) null);
        this.dragHandle = (ImageView) this.floatingView.findViewById(R.id.drag_handle);
        this.btnVoice = (ImageButton) this.floatingView.findViewById(R.id.btn_voice);
        this.btnMute = (ImageButton) this.floatingView.findViewById(R.id.btn_mute);
        this.btnChat = (ImageButton) this.floatingView.findViewById(R.id.btn_chat);
        this.btnChannels = (ImageButton) this.floatingView.findViewById(R.id.btn_channels);
        this.btnServers = (ImageButton) this.floatingView.findViewById(R.id.btn_servers);
        this.txtPing = (TextView) this.floatingView.findViewById(R.id.txt_ping);
        this.params = new WindowManager.LayoutParams(-2, -2, Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE, 552, -3);
        if (Build.VERSION.SDK_INT >= 28) {
            this.params.setTitle(this.context.getString(R.string.desc_floating_window));
            this.floatingView.setAccessibilityPaneTitle(this.context.getString(R.string.desc_floating_window));
        }
        this.params.gravity = 8388659;
        this.params.x = 100;
        this.params.y = 200;
        this.dragHandle.setOnTouchListener(new View.OnTouchListener() { 
            private float initialTouchX;
            private float initialTouchY;
            private int initialX;
            private int initialY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case 0:
                        this.initialX = FloatingWindowManager.this.params.x;
                        this.initialY = FloatingWindowManager.this.params.y;
                        this.initialTouchX = event.getRawX();
                        this.initialTouchY = event.getRawY();
                        return true;
                    case 1:
                    default:
                        return false;
                    case 2:
                        FloatingWindowManager.this.params.x = this.initialX + ((int) (event.getRawX() - this.initialTouchX));
                        FloatingWindowManager.this.params.y = this.initialY + ((int) (event.getRawY() - this.initialTouchY));
                        if (FloatingWindowManager.this.isShowing) {
                            FloatingWindowManager.this.windowManager.updateViewLayout(FloatingWindowManager.this.floatingView, FloatingWindowManager.this.params);
                        }
                        return true;
                }
            }
        });
        this.btnVoice.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                FloatingWindowManager.this.lambda$initView$0(view);
            }
        });
        this.btnMute.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                FloatingWindowManager.this.lambda$initView$1(view);
            }
        });
        this.btnChat.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                FloatingWindowManager.this.lambda$initView$2(view);
            }
        });
        this.btnChannels.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                FloatingWindowManager.this.lambda$initView$3(view);
            }
        });
        this.btnServers.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                FloatingWindowManager.this.lambda$initView$4(view);
            }
        });
    }

        public void lambda$initView$0(View v) {
        boolean tx = !this.service.isVoiceTransmissionEnabled();
        if (this.service.isVoiceActivationEnabled()) {
            this.service.enableVoiceActivation(false);
        }
        this.service.enableVoiceTransmission(tx);
        updateUI();
    }

        public void lambda$initView$1(View v) {
        this.service.setMute(!this.service.getCurrentMuteState());
        updateUI();
    }

        public void lambda$initView$2(View v) {
        showChatTypeChoiceDialog();
    }

        public void lambda$initView$3(View v) {
        showChannelSelectionDialog();
    }

        public void lambda$initView$4(View v) {
        showServerSelectionDialog();
    }

    public void checkAndShow() {
        boolean enabled = this.prefs.getBoolean(Preferences.PREF_BG_MGMT_ENABLED, false);
        String displayType = this.prefs.getString(Preferences.PREF_BG_MGMT_DISPLAY_TYPE, "window");
        if (!enabled || !"window".equals(displayType)) {
            hide();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this.context)) {
            hide();
        } else if (!this.isShowing) {
            new Handler(Looper.getMainLooper()).post(new Runnable() { 
                @Override
                public final void run() {
                    FloatingWindowManager.this.lambda$checkAndShow$5();
                }
            });
        } else {
            updateUI();
        }
    }

        public void lambda$checkAndShow$5() {
        try {
            if (!this.isShowing) {
                this.windowManager.addView(this.floatingView, this.params);
                this.isShowing = true;
                this.updateHandler.post(this.updateRunnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hide() {
        if (this.isShowing) {
            new Handler(Looper.getMainLooper()).post(new Runnable() { 
                @Override
                public final void run() {
                    FloatingWindowManager.this.lambda$hide$6();
                }
            });
        }
    }

        public void lambda$hide$6() {
        try {
            if (this.isShowing) {
                this.windowManager.removeView(this.floatingView);
                this.isShowing = false;
                this.updateHandler.removeCallbacks(this.updateRunnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hideIfAppOpened() {
        if (this.prefs.getBoolean(Preferences.PREF_BG_MGMT_CLOSE_ON_APP_OPEN, false)) {
            hide();
        }
    }

    public void updateUI() {
        new Handler(Looper.getMainLooper()).post(new Runnable() { 
            @Override
            public final void run() {
                FloatingWindowManager.this.lambda$updateUI$7();
            }
        });
    }

        public void lambda$updateUI$7() {
        if (this.isShowing) {
            updateUIInternal();
        }
    }

        public void updateUIInternal() {
        String stateStr;
        int stateColor;
        this.btnVoice.setVisibility(this.prefs.getBoolean(Preferences.PREF_BG_MGMT_SHOW_VOICE, true) ? 0 : 8);
        this.btnMute.setVisibility(this.prefs.getBoolean(Preferences.PREF_BG_MGMT_SHOW_MUTE, true) ? 0 : 8);
        this.btnChat.setVisibility(this.prefs.getBoolean(Preferences.PREF_BG_MGMT_SHOW_CHAT, true) ? 0 : 8);
        this.btnChannels.setVisibility(this.prefs.getBoolean(Preferences.PREF_BG_MGMT_SHOW_CHANNELS, true) ? 0 : 8);
        this.btnServers.setVisibility(this.prefs.getBoolean(Preferences.PREF_BG_MGMT_SHOW_SERVERS, true) ? 0 : 8);
        this.txtPing.setVisibility(this.prefs.getBoolean(Preferences.PREF_BG_MGMT_SHOW_PING, true) ? 0 : 8);
        boolean isTransmitting = this.service.isVoiceTransmitting();
        this.btnVoice.setImageResource(isTransmitting ? R.drawable.mic_green : R.drawable.microphone);
        this.btnVoice.setContentDescription(this.context.getString(isTransmitting ? R.string.desc_voice_transmitting : R.string.desc_voice_silent));
        boolean isMuted = this.service.getCurrentMuteState();
        this.btnMute.setImageResource(isMuted ? R.drawable.mute_blue : R.drawable.speaker_blue);
        this.btnMute.setContentDescription(this.context.getString(isMuted ? R.string.desc_sound_muted : R.string.desc_sound_active));
        int flags = this.service.getTTInstance().getFlags();
        if ((flags & 32768) == 32768) {
            stateStr = this.context.getString(R.string.stat_online);
            stateColor = -16711936;
            ClientStatistics stats = new ClientStatistics();
            if (this.service.getTTInstance().getClientStatistics(stats) && stats.nUdpPingTimeMs >= 0) {
                this.lastKnownPing = stats.nUdpPingTimeMs;
            }
            if (this.lastKnownPing >= 0) {
                stateStr = stateStr + " (" + this.lastKnownPing + "ms)";
            }
        } else if ((flags & 8192) == 8192) {
            stateStr = this.context.getString(R.string.stat_connecting);
            stateColor = -256;
        } else {
            this.lastKnownPing = -1;
            stateStr = this.context.getString(R.string.stat_offline);
            stateColor = 0xffff0000;
        }
        this.txtPing.setText(stateStr);
        this.txtPing.setTextColor(stateColor);
        this.txtPing.setContentDescription(stateStr);
    }

    private void showChatTypeChoiceDialog() {
        UserAccount myAccount = new UserAccount();
        User me = this.service.getUsers().get(Integer.valueOf(this.service.getTTInstance().getMyUserID()));
        boolean isAdmin = !(me == null || (me.uUserType & 2) == 0) || (this.service.getTTInstance().getMyUserAccount(myAccount) && (myAccount.uUserType & 2) != 0);
        Context themedContext = new ContextThemeWrapper(this.context, R.style.AppTheme);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle(R.string.chat_dialog_title);
        List<String> options = new ArrayList<>();
        options.add(this.context.getString(R.string.chat_option_global));
        options.add(this.context.getString(R.string.chat_option_private));
        if (isAdmin) {
            options.add(this.context.getString(R.string.chat_option_broadcast));
        }
        builder.setItems((CharSequence[]) options.toArray(new String[0]), new DialogInterface.OnClickListener() { 
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                FloatingWindowManager.this.lambda$showChatTypeChoiceDialog$8(dialogInterface, i);
            }
        });
        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= 26) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

        public void lambda$showChatTypeChoiceDialog$8(DialogInterface dialog, int which) {
        if (which == 0) {
            openChatDialog("global", 0);
        } else if (which == 1) {
            showOnlineUsersSelectionDialog();
        } else if (which == 2) {
            openChatDialog("broadcast", 0);
        }
    }

    private void showOnlineUsersSelectionDialog() {
        final List<User> onlineUsers = new ArrayList<>();
        int myUserId = this.service.getTTInstance().getMyUserID();
        for (User u : this.service.getUsers().values()) {
            if (u.nUserID != myUserId) {
                onlineUsers.add(u);
            }
        }
        if (onlineUsers.isEmpty()) {
            Toast.makeText(this.context, R.string.chat_no_online_users, 0).show();
            return;
        }
        Collections.sort(onlineUsers, new Comparator<User>() { 
            @Override
            public int compare(User u1, User u2) {
                String name1 = Utils.getDisplayName(FloatingWindowManager.this.context, u1);
                String name2 = Utils.getDisplayName(FloatingWindowManager.this.context, u2);
                return name1.compareToIgnoreCase(name2);
            }
        });
        String[] userNames = new String[onlineUsers.size()];
        for (int i = 0; i < onlineUsers.size(); i++) {
            userNames[i] = Utils.getDisplayName(this.context, onlineUsers.get(i));
        }
        Context themedContext = new ContextThemeWrapper(this.context, R.style.AppTheme);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle(R.string.chat_select_user);
        builder.setItems(userNames, new DialogInterface.OnClickListener() { 
            @Override
            public final void onClick(DialogInterface dialogInterface, int i2) {
                FloatingWindowManager.this.lambda$showOnlineUsersSelectionDialog$9(onlineUsers, dialogInterface, i2);
            }
        });
        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= 26) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

        public void lambda$showOnlineUsersSelectionDialog$9(List onlineUsers, DialogInterface dialog, int which) {
        User u = (User) onlineUsers.get(which);
        openChatDialog("private", u.nUserID);
    }

    private void openChatDialog(final String type, final int userId) {
        String titleStr;
        final TextMessageAdapter adapter;
        Context themedContext = new ContextThemeWrapper(this.context, R.style.AppTheme);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        if ("global".equals(type)) {
            titleStr = this.context.getString(R.string.chat_option_global);
        } else {
            boolean equals = "broadcast".equals(type);
            Context context = this.context;
            if (equals) {
                titleStr = context.getString(R.string.chat_option_broadcast);
            } else {
                String title = context.getString(R.string.chat_option_private);
                User user = this.service.getUsers().get(Integer.valueOf(userId));
                if (user != null) {
                    titleStr = title + " - " + Utils.getDisplayName(this.context, user);
                } else {
                    titleStr = title;
                }
            }
        }
        builder.setTitle(titleStr);
        View chatView = LayoutInflater.from(themedContext).inflate(R.layout.activity_simple_chat, (ViewGroup) null);
        builder.setView(chatView);
        final TeamTalkBase ttclient = this.service.getTTInstance();
        if ("global".equals(type) || "broadcast".equals(type)) {
            adapter = new TextMessageAdapter(themedContext, null, this.service.getChatLogTextMsgs(), ttclient.getMyUserID());
        } else {
            adapter = new TextMessageAdapter(themedContext, null, this.service.getUserTextMsgs(userId), ttclient.getMyUserID());
        }
        ListView lv = (ListView) chatView.findViewById(R.id.simple_chat_listview);
        lv.setTranscriptMode(2);
        lv.setAdapter((ListAdapter) adapter);
        adapter.setListView(lv);
        final EditText send_msg = (EditText) chatView.findViewById(R.id.simple_chat_edittext);
        Button send_btn = (Button) chatView.findViewById(R.id.simple_chat_sendbtn);
        send_btn.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                FloatingWindowManager.this.lambda$openChatDialog$10(send_msg, ttclient, type, adapter, userId, view);
            }
        });
        final ClientEventListener.OnCmdUserTextMessageListener messageListener = new AnonymousClass4(type, adapter, userId);
        this.service.getEventHandler().registerOnCmdUserTextMessage(messageListener, true);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { 
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { 
            @Override
            public final void onDismiss(DialogInterface dialogInterface) {
                FloatingWindowManager.this.lambda$openChatDialog$12(messageListener, dialogInterface);
            }
        });
        if (Build.VERSION.SDK_INT >= 26) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.getWindow().clearFlags(8);
        dialog.getWindow().setSoftInputMode(5);
        dialog.show();
    }

        public void lambda$openChatDialog$10(EditText send_msg, TeamTalkBase ttclient, String type, TextMessageAdapter adapter, int userId, View v) {
        String msgText = send_msg.getText().toString();
        if (msgText.isEmpty()) {
            return;
        }
        User myself = this.service.getUsers().get(Integer.valueOf(ttclient.getMyUserID()));
        String name = Utils.getDisplayName(this.context, myself);
        MyTextMessage textmsg = new MyTextMessage(myself == null ? "" : name);
        textmsg.nFromUserID = ttclient.getMyUserID();
        textmsg.szMessage = msgText;
        if ("global".equals(type)) {
            textmsg.nMsgType = 2;
            textmsg.nChannelID = ttclient.getMyChannelID();
            int cmdid = 0;
            Iterator<MyTextMessage> it = textmsg.split().iterator();
            while (it.hasNext()) {
                cmdid = ttclient.doTextMessage(it.next());
            }
            if (cmdid > 0) {
                send_msg.setText("");
                adapter.notifyDataSetChanged();
                return;
            } else {
                Toast.makeText(this.context, R.string.text_con_cmderr, 1).show();
                return;
            }
        }
        if ("broadcast".equals(type)) {
            textmsg.nMsgType = 3;
            int cmdid2 = 0;
            Iterator<MyTextMessage> it2 = textmsg.split().iterator();
            while (it2.hasNext()) {
                cmdid2 = ttclient.doTextMessage(it2.next());
            }
            if (cmdid2 > 0) {
                send_msg.setText("");
                adapter.notifyDataSetChanged();
                return;
            } else {
                Toast.makeText(this.context, R.string.text_con_cmderr, 1).show();
                return;
            }
        }
        textmsg.nMsgType = 1;
        textmsg.nChannelID = 0;
        textmsg.nToUserID = userId;
        boolean sent = true;
        Iterator<MyTextMessage> it3 = textmsg.split().iterator();
        while (it3.hasNext()) {
            MyTextMessage m = it3.next();
            sent = sent && ttclient.doTextMessage(m) > 0;
            this.service.getUserTextMsgs(userId).add(m);
        }
        if (sent) {
            send_msg.setText("");
            adapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this.context, R.string.err_send_text_message, 1).show();
        }
    }

        /* renamed from: org.nekit.ttproplus.gui.FloatingWindowManager$4, reason: invalid class name */
        public class AnonymousClass4 implements ClientEventListener.OnCmdUserTextMessageListener {
        final TextMessageAdapter val$adapter;
        final String val$type;
        final int val$userId;

        AnonymousClass4(String str, TextMessageAdapter textMessageAdapter, int i) {
            this.val$type = str;
            this.val$adapter = textMessageAdapter;
            this.val$userId = i;
        }

        @Override
        public void onCmdUserTextMessage(final TextMessage textmessage) {
            Handler handler = new Handler(Looper.getMainLooper());
            final String str = this.val$type;
            final TextMessageAdapter textMessageAdapter = this.val$adapter;
            final int i = this.val$userId;
            handler.post(new Runnable() { 
                @Override
                public final void run() {
                    FloatingWindowManager.AnonymousClass4.lambda$onCmdUserTextMessage$0(str, textmessage, textMessageAdapter, i);
                }
            });
        }

                public static void lambda$onCmdUserTextMessage$0(String type, TextMessage textmessage, TextMessageAdapter adapter, int userId) {
            if ("global".equals(type) || "broadcast".equals(type)) {
                if (textmessage.nMsgType == 2 || textmessage.nMsgType == 3) {
                    adapter.notifyDataSetChanged();
                    return;
                }
                return;
            }
            if (textmessage.nFromUserID == userId && textmessage.nMsgType == 1) {
                adapter.notifyDataSetChanged();
            }
        }
    }

        public void lambda$openChatDialog$12(ClientEventListener.OnCmdUserTextMessageListener messageListener, DialogInterface dialogInterface) {
        this.service.getEventHandler().registerOnCmdUserTextMessage(messageListener, false);
    }

    private String getChannelPath(Channel channel, Map<Integer, Channel> allChannels) {
        if (channel.nParentID == 0) {
            String rootName = this.context.getString(R.string.root_channel);
            return rootName;
        }
        List<String> parts = new ArrayList<>();
        Channel curr = channel;
        while (curr != null && curr.nChannelID != 0) {
            parts.add(curr.szName);
            curr = allChannels.get(Integer.valueOf(curr.nParentID));
        }
        Collections.reverse(parts);
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private void joinChannelHelper(Context themedContext, final Channel selected) {
        final TeamTalkBase ttclient = this.service.getTTInstance();
        if (selected.bPassword) {
            AlertDialog.Builder passBuilder = new AlertDialog.Builder(themedContext);
            passBuilder.setTitle(R.string.chanpswlab);
            final EditText input = new EditText(themedContext);
            input.setInputType(Encoder.HBLKSIZE_s);
            passBuilder.setView(input);
            passBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    FloatingWindowManager.lambda$joinChannelHelper$13(input, ttclient, selected, dialogInterface, i);
                }
            });
            passBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            AlertDialog passDialog = passBuilder.create();
            if (Build.VERSION.SDK_INT >= 26) {
                passDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                passDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
            }
            passDialog.getWindow().clearFlags(8);
            passDialog.getWindow().setSoftInputMode(5);
            passDialog.show();
            return;
        }
        ttclient.doJoinChannelByID(selected.nChannelID, "");
    }

        public static void lambda$joinChannelHelper$13(EditText input, TeamTalkBase ttclient, Channel selected, DialogInterface d, int w) {
        String pass = input.getText().toString();
        ttclient.doJoinChannelByID(selected.nChannelID, pass);
    }

    private void showChannelSelectionDialog() {
        Map<Integer, Channel> allChannels = this.service.getChannels();
        if (allChannels == null || allChannels.isEmpty()) {
            Toast.makeText(this.context, R.string.err_connection, 0).show();
            return;
        }
        final Context themedContext = new ContextThemeWrapper(this.context, R.style.AppTheme);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle(R.string.desc_btn_channels);
        ListView listView = new ListView(themedContext);
        builder.setView(listView);
        final int[] currentViewedChannelId = {this.service.getTTInstance().getMyChannelID()};
        if (currentViewedChannelId[0] <= 0) {
            currentViewedChannelId[0] = this.service.getTTInstance().getRootChannelID();
        }
        final List<Object> items = new ArrayList<>();
        final ChannelUserAdapter adapter = new ChannelUserAdapter(themedContext, items);
        listView.setAdapter((ListAdapter) adapter);
        final Runnable refreshList = new Runnable() { 
            @Override
            public void run() {
                items.clear();
                int curId = currentViewedChannelId[0];
                Map<Integer, Channel> chans = FloatingWindowManager.this.service.getChannels();
                Channel curChan = chans.get(Integer.valueOf(curId));
                ArrayList arrayList = new ArrayList();
                ArrayList arrayList2 = new ArrayList();
                int rootId = FloatingWindowManager.this.service.getTTInstance().getRootChannelID();
                if (curId == 0) {
                    Channel root = chans.get(Integer.valueOf(rootId));
                    if (root != null) {
                        arrayList2.add(root);
                    }
                } else {
                    for (Channel c : chans.values()) {
                        if (c.nParentID == curId) {
                            if (c.nMaxUsers <= 0) {
                                arrayList.add(c);
                            } else {
                                arrayList2.add(c);
                            }
                        }
                    }
                }
                Collections.sort(arrayList, new Comparator<Channel>() { 
                    @Override
                    public int compare(Channel c1, Channel c2) {
                        return c1.szName.compareToIgnoreCase(c2.szName);
                    }
                });
                Collections.sort(arrayList2, new Comparator<Channel>() { 
                    @Override
                    public int compare(Channel c1, Channel c2) {
                        return c1.szName.compareToIgnoreCase(c2.szName);
                    }
                });
                ArrayList arrayList3 = new ArrayList();
                for (User u : FloatingWindowManager.this.service.getUsers().values()) {
                    if (u.nChannelID == curId) {
                        arrayList3.add(u);
                    }
                }
                Collections.sort(arrayList3, new Comparator<User>() { 
                    @Override
                    public int compare(User u1, User u2) {
                        return Utils.getDisplayName(themedContext, u1).compareToIgnoreCase(Utils.getDisplayName(themedContext, u2));
                    }
                });
                items.addAll(arrayList);
                items.addAll(arrayList3);
                if (curId > 0) {
                    if (curId == rootId) {
                        items.add("..");
                    } else if (curChan != null) {
                        items.add("..");
                    }
                }
                items.addAll(arrayList2);
                adapter.notifyDataSetChanged();
            }
        };
        refreshList.run();
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { 
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        final AlertDialog dialog = builder.create();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() { 
            @Override
            public final void onItemClick(AdapterView adapterView, View view, int i, long j) {
                FloatingWindowManager.this.lambda$showChannelSelectionDialog$17(items, currentViewedChannelId, refreshList, themedContext, dialog, adapterView, view, i, j);
            }
        });
        if (Build.VERSION.SDK_INT >= 26) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

        public void lambda$showChannelSelectionDialog$17(List items, final int[] currentViewedChannelId, final Runnable refreshList, final Context themedContext, final AlertDialog dialog, AdapterView parent, View view, int position, long id) {
        Object clicked = items.get(position);
        if ((clicked instanceof String) && "..".equals(clicked)) {
            int curId = currentViewedChannelId[0];
            int rootId = this.service.getTTInstance().getRootChannelID();
            if (curId == rootId) {
                currentViewedChannelId[0] = 0;
                refreshList.run();
                return;
            }
            Channel curChan = this.service.getChannels().get(Integer.valueOf(curId));
            if (curChan != null && curChan.nParentID >= 0) {
                currentViewedChannelId[0] = curChan.nParentID;
                refreshList.run();
                return;
            }
            return;
        }
        if (clicked instanceof Channel) {
            final Channel c = (Channel) clicked;
            if (c.nMaxUsers == 0) {
                currentViewedChannelId[0] = c.nChannelID;
                refreshList.run();
                return;
            }
            AlertDialog.Builder choiceBuilder = new AlertDialog.Builder(themedContext);
            choiceBuilder.setTitle(c.szName);
            String[] options = {themedContext.getString(R.string.action_join_channel), themedContext.getString(R.string.action_open_channel)};
            choiceBuilder.setItems(options, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    FloatingWindowManager.this.lambda$showChannelSelectionDialog$16(dialog, themedContext, c, currentViewedChannelId, refreshList, dialogInterface, i);
                }
            });
            AlertDialog choiceDialog = choiceBuilder.create();
            if (Build.VERSION.SDK_INT >= 26) {
                choiceDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                choiceDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
            }
            choiceDialog.show();
        }
    }

        public void lambda$showChannelSelectionDialog$16(AlertDialog dialog, Context themedContext, Channel c, int[] currentViewedChannelId, Runnable refreshList, DialogInterface choiceDialog, int choiceWhich) {
        if (choiceWhich == 0) {
            choiceDialog.dismiss();
            dialog.dismiss();
            joinChannelHelper(themedContext, c);
        } else {
            currentViewedChannelId[0] = c.nChannelID;
            refreshList.run();
        }
    }

            public class ChannelUserAdapter extends BaseAdapter {
        private final Context context;
        private final LayoutInflater inflater;
        private final List<Object> items;

        public ChannelUserAdapter(Context context, List<Object> items) {
            this.context = context;
            this.inflater = LayoutInflater.from(context);
            this.items = items;
        }

        @Override
        public int getCount() {
            return this.items.size();
        }

        @Override
        public Object getItem(int position) {
            return this.items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int iconRes;
            if (convertView == null) {
                convertView = this.inflater.inflate(R.layout.item_dialog_channel_user, parent, false);
            }
            ImageView itemIcon = (ImageView) convertView.findViewById(R.id.item_icon);
            TextView itemText = (TextView) convertView.findViewById(R.id.item_text);
            Object item = this.items.get(position);
            if (item instanceof String) {
                itemIcon.setVisibility(8);
                itemText.setText((String) item);
            } else if (item instanceof Channel) {
                Channel c = (Channel) item;
                if (c.nMaxUsers == 0) {
                    itemIcon.setVisibility(8);
                } else {
                    itemIcon.setVisibility(0);
                    int iconRes2 = c.bPassword ? R.drawable.channel_pink : R.drawable.channel_orange;
                    itemIcon.setImageResource(iconRes2);
                }
                int iconRes3 = c.nChannelID;
                int population = Utils.getUsers(iconRes3, FloatingWindowManager.this.service.getUsers()).size();
                String popStr = population > 0 ? " (" + population + ")" : "";
                itemText.setText(c.szName + popStr);
            } else if (item instanceof User) {
                User user = (User) item;
                itemIcon.setVisibility(0);
                boolean female = (user.nStatusMode & 256) != 0;
                boolean away = (user.nStatusMode & 1) != 0;
                boolean talking = (user.uUserState & 1) != 0;
                if (user.nUserID == FloatingWindowManager.this.service.getTTInstance().getMyUserID()) {
                    talking = FloatingWindowManager.this.service.isVoiceTransmitting();
                }
                if (talking) {
                    iconRes = female ? R.drawable.woman_green : R.drawable.man_green;
                } else if (female) {
                    iconRes = away ? R.drawable.woman_orange : R.drawable.woman_blue;
                } else {
                    iconRes = away ? R.drawable.man_orange : R.drawable.man_blue;
                }
                itemIcon.setImageResource(iconRes);
                itemText.setText(Utils.getDisplayName(this.context, user));
            }
            return convertView;
        }
    }

    private List<ServerEntry> getSavedServersHelper() {
        List<ServerEntry> savedServers = new ArrayList<>();
        SharedPreferences pref = this.context.getSharedPreferences("serverlist", 0);
        for (int i = 0; !pref.getString(i + ServerEntry.KEY_SERVERNAME, "").isEmpty(); i++) {
            ServerEntry entry = new ServerEntry();
            entry.servername = pref.getString(i + ServerEntry.KEY_SERVERNAME, "");
            entry.ipaddr = pref.getString(i + ServerEntry.KEY_IPADDR, "");
            entry.tcpport = pref.getInt(i + ServerEntry.KEY_TCPPORT, 0);
            entry.udpport = pref.getInt(i + ServerEntry.KEY_UDPPORT, 0);
            entry.encrypted = pref.getBoolean(i + ServerEntry.KEY_ENCRYPTED, false);
            entry.username = pref.getString(i + "username", "");
            entry.password = pref.getString(i + "password", "");
            entry.nickname = pref.getString(i + ServerEntry.KEY_NICKNAME, "");
            entry.statusmsg = pref.getString(i + ServerEntry.KEY_STATUSMSG, "");
            entry.rememberLastChannel = pref.getBoolean(i + ServerEntry.KEY_REMEMBER_LAST_CHANNEL, true);
            entry.channel = pref.getString(i + ServerEntry.KEY_CHANNEL, "");
            entry.chanpasswd = pref.getString(i + ServerEntry.KEY_CHANPASSWD, "");
            savedServers.add(entry);
        }
        return savedServers;
    }

    private void showServerSelectionDialog() {
        Context themedContext = new ContextThemeWrapper(this.context, R.style.AppTheme);
        final List<ServerEntry> savedServers = getSavedServersHelper();
        if (savedServers.isEmpty()) {
            Toast.makeText(this.context, R.string.no_saved_servers, 0).show();
            return;
        }
        String[] serverNames = new String[savedServers.size()];
        for (int i = 0; i < savedServers.size(); i++) {
            serverNames[i] = savedServers.get(i).servername;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle(R.string.title_select_server);
        builder.setItems(serverNames, new DialogInterface.OnClickListener() { 
            @Override
            public final void onClick(DialogInterface dialogInterface, int i2) {
                FloatingWindowManager.this.lambda$showServerSelectionDialog$18(savedServers, dialogInterface, i2);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { 
            @Override
            public final void onClick(DialogInterface dialogInterface, int i2) {
                dialogInterface.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= 26) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

        public void lambda$showServerSelectionDialog$18(List savedServers, DialogInterface dialog, int which) {
        ServerEntry selected = (ServerEntry) savedServers.get(which);
        dialog.dismiss();
        this.service.setServerEntry(selected);
        if (!this.service.reconnect()) {
            Toast.makeText(this.context, R.string.err_connection, 0).show();
            return;
        }
        Intent intent = new Intent(this.context, MainActivity.class);
        intent.putExtra(ServerEntry.KEY_SERVERNAME, selected.servername);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        this.context.startActivity(intent);
    }
}
