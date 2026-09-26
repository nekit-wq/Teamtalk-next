/*
 * Copyright (c) 2005-2018, BearWare.dk
 * 
 * Contact Information:
 *
 * Bjoern D. Rasmussen
 * Kirketoften 5
 * DK-8260 Viby J
 * Denmark
 * Email: contact@bearware.dk
 * Phone: +45 20 20 54 59
 * Web: http://www.bearware.dk
 *
 * This source code is part of the TeamTalk SDK owned by
 * BearWare.dk. Use of this file, or its compiled unit, requires a
 * TeamTalk SDK License Key issued by BearWare.dk.
 *
 * The TeamTalk SDK License Agreement along with its Terms and
 * Conditions are outlined in the file License.txt included with the
 * TeamTalk SDK distribution.
 *
 */

package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import dk.bearware.TeamTalkBase;
import dk.bearware.TextMessage;
import dk.bearware.TextMsgType;
import dk.bearware.User;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.ChatHistoryDbHelper;
import org.nekit.ttproplus.data.MyTextMessage;
import org.nekit.ttproplus.data.TextMessageAdapter;
import dk.bearware.events.ClientEventListener;

import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import org.nekit.ttproplus.data.ChatMessageEntry;

public class TextMessageActivity
extends AppCompatActivity implements TeamTalkConnectionListener,
        ClientEventListener.OnCmdUserTextMessageListener,
        ClientEventListener.OnCmdUserLoggedInListener,
        ClientEventListener.OnCmdUserLoggedOutListener {

    public static final String TAG = "bearware";
    
    public static final String EXTRA_USERID = "userid";
    
    TeamTalkConnection mConnection;
    TextMessageAdapter adapter;
    AccessibilityAssistant accessibilityAssistant;
    private long lastTypingTime = 0;
    private Handler typingHandler = new Handler();
    private Runnable stopTypingRunnable = () -> sendTypingStatus(false);
    private Runnable remoteTypingTimeoutRunnable = () -> updateTitle(false);

    TeamTalkService getService() {
        return mConnection.getService();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        mConnection = new TeamTalkConnection(this);
        setContentView(R.layout.activity_text_message);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        accessibilityAssistant = new AccessibilityAssistant(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.text_message, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if(id == R.id.action_settings) {
            return true;
        }
        else if (id == android.R.id.home) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            View v = getCurrentFocus();
            if ((v != null) && imm.isActive())
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();        
        
        // Bind to LocalService if not already
        if (!mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), TeamTalkService.class);
            if(!bindService(intent, mConnection, Context.BIND_AUTO_CREATE))
                Log.e(TAG, "Failed to bind to TeamTalk service");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unbind from the service
        if(mConnection.isBound()) {
            onServiceDisconnected(getService());
            unbindService(mConnection);
            mConnection.setBound(false);
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        int targetUserId = this.getIntent().getIntExtra(EXTRA_USERID, -1);
        final String peerUsername = getIntent().getStringExtra("peer_username");
        final String peerNickname = getIntent().getStringExtra("peer_nickname");
        final TeamTalkBase ttclient = service.getTTInstance();

        // If targetUserId <= 0 or user not found, try matching with active online users
        if ((targetUserId <= 0 || service.getUsers().get(targetUserId) == null) && service.getUsers() != null) {
            for (User u : service.getUsers().values()) {
                if (u == null) continue;
                if (!TextUtils.isEmpty(peerUsername) && !TextUtils.isEmpty(u.szUsername)
                        && peerUsername.equalsIgnoreCase(u.szUsername.trim())) {
                    targetUserId = u.nUserID;
                    getIntent().putExtra(EXTRA_USERID, targetUserId);
                    break;
                } else if (!TextUtils.isEmpty(peerNickname) && !TextUtils.isEmpty(u.szNickname)
                        && peerNickname.equalsIgnoreCase(u.szNickname.trim())) {
                    targetUserId = u.nUserID;
                    getIntent().putExtra(EXTRA_USERID, targetUserId);
                    break;
                }
            }
        }

        Vector<MyTextMessage> userMsgs = null;
        if (targetUserId > 0) {
            userMsgs = service.getUserTextMsgs(targetUserId);
        }

        if (userMsgs == null || userMsgs.isEmpty()) {
            String srvKey = service.getServerEntry() != null
                    ? (service.getServerEntry().ipaddr + ":" + service.getServerEntry().tcpport) : "";
            List<ChatMessageEntry> dbHistory = ChatHistoryDbHelper.getInstance(this)
                    .getMessagesForPeer(srvKey, peerUsername, peerNickname, targetUserId, 150);
            if (userMsgs == null) {
                userMsgs = targetUserId > 0 ? service.getUserTextMsgs(targetUserId) : new Vector<MyTextMessage>();
            }
            if (userMsgs.isEmpty()) {
                for (ChatMessageEntry entry : dbHistory) {
                    MyTextMessage m = new MyTextMessage(entry.getSenderDisplayName());
                    m.nMsgType = entry.getMsgType();
                    m.nFromUserID = entry.getFromUserId();
                    m.nToUserID = entry.getToUserId();
                    m.szFromUsername = entry.getFromUsername();
                    m.szMessage = entry.getMessageText();
                    m.time = new Date(entry.getTimestamp());
                    userMsgs.add(m);
                }
            }
        }

        int myUid = ttclient != null ? ttclient.getMyUserID() : 0;
        adapter = new TextMessageAdapter(this, accessibilityAssistant,
                                         userMsgs,
                                         myUid);

        ListView lv = findViewById(R.id.user_im_listview);
        lv.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        lv.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        Button send_btn = this.findViewById(R.id.user_im_sendbtn);
        final EditText send_msg = this.findViewById(R.id.user_im_edittext);
        send_btn.setOnClickListener(v -> {
            int uid = getIntent().getIntExtra(EXTRA_USERID, -1);
            User targetUser = (uid > 0 && service.getUsers() != null) ? service.getUsers().get(uid) : null;
            if (targetUser == null) {
                Toast.makeText(TextMessageActivity.this, R.string.user_offline_chat_hint, Toast.LENGTH_SHORT).show();
                return;
            }

            String newmsg = send_msg.getText().toString();
            if (newmsg.isEmpty())
                return;

            User myself = service.getUsers().get(ttclient.getMyUserID());
            String name = Utils.getDisplayName(getBaseContext(), myself);
            MyTextMessage textmsg = new MyTextMessage(myself == null ? "" : name);
            textmsg.nMsgType = TextMsgType.MSGTYPE_USER;
            textmsg.nChannelID = 0;
            textmsg.nFromUserID = ttclient.getMyUserID();
            textmsg.nToUserID = uid;
            textmsg.szMessage = newmsg;

            boolean sent = true;
            String srvKey = service.getServerEntry() != null ? (service.getServerEntry().ipaddr + ":" + service.getServerEntry().tcpport) : "";
            String targetName = Utils.getDisplayName(getBaseContext(), targetUser);
            for (MyTextMessage m : textmsg.split()) {
                sent = sent && ttclient.doTextMessage(m) > 0;
                service.getUserTextMsgs(uid).add(m);
                service.recordSessionPrivateMessage(uid, targetName, m.szMessage, true, System.currentTimeMillis());
                ChatHistoryDbHelper.getInstance(TextMessageActivity.this).saveOutgoingMessage(srvKey, m, name, targetName, "");
            }
            MyTextMessage.merge(service.getUserTextMsgs(uid));
            if (sent) {
                send_msg.setText("");
                adapter.notifyDataSetChanged();
                typingHandler.removeCallbacks(stopTypingRunnable);
                sendTypingStatus(false);
            } else {
                Toast.makeText(TextMessageActivity.this,
                               R.string.err_send_text_message,
                               Toast.LENGTH_LONG).show();
            }
        });

        service.getEventHandler().registerOnCmdUserTextMessage(this, true);
        service.getEventHandler().registerOnCmdUserLoggedIn(this, true);
        service.getEventHandler().registerOnCmdUserLoggedOut(this, true);

        send_msg.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (System.currentTimeMillis() - lastTypingTime > 2000) {
                    sendTypingStatus(true);
                    lastTypingTime = System.currentTimeMillis();
                }
                typingHandler.removeCallbacks(stopTypingRunnable);
                typingHandler.postDelayed(stopTypingRunnable, 7000);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        updateTitle();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
        if (service != null && service.getEventHandler() != null) {
            service.getEventHandler().registerOnCmdUserTextMessage(this, false);
            service.getEventHandler().registerOnCmdUserLoggedIn(this, false);
            service.getEventHandler().registerOnCmdUserLoggedOut(this, false);
        }
    }

    @Override
    public void onCmdUserLoggedIn(User user) {
        runOnUiThread(this::updateTitle);
    }

    @Override
    public void onCmdUserLoggedOut(User user) {
        runOnUiThread(this::updateTitle);
    }

    void updateTitle() {
        updateTitle(false);
    }

    void updateTitle(boolean isTyping) {
        String title = getResources().getString(R.string.title_activity_text_message);
        int userid = this.getIntent().getIntExtra(EXTRA_USERID, -1);
        String peerNickname = getIntent().getStringExtra("peer_nickname");
        String peerUsername = getIntent().getStringExtra("peer_username");

        typingHandler.removeCallbacks(remoteTypingTimeoutRunnable);
        if (isTyping) {
            typingHandler.postDelayed(remoteTypingTimeoutRunnable, 9000);
        }

        User user = (getService() != null && getService().getUsers() != null && userid > 0)
                ? getService().getUsers().get(userid) : null;

        if (user == null && getService() != null && getService().getUsers() != null) {
            for (User u : getService().getUsers().values()) {
                if (u == null) continue;
                if (!TextUtils.isEmpty(peerUsername) && !TextUtils.isEmpty(u.szUsername)
                        && peerUsername.equalsIgnoreCase(u.szUsername.trim())) {
                    user = u;
                    getIntent().putExtra(EXTRA_USERID, u.nUserID);
                    break;
                } else if (!TextUtils.isEmpty(peerNickname) && !TextUtils.isEmpty(u.szNickname)
                        && peerNickname.equalsIgnoreCase(u.szNickname.trim())) {
                    user = u;
                    getIntent().putExtra(EXTRA_USERID, u.nUserID);
                    break;
                }
            }
        }

        View sendBtn = findViewById(R.id.user_im_sendbtn);
        EditText sendMsg = findViewById(R.id.user_im_edittext);

        if (user != null) {
            String name = Utils.getDisplayName(getBaseContext(), user);
            if (isTyping) {
                setTitle(getString(R.string.text_user_typing_title, name));
            } else {
                setTitle(title + " - " + name);
            }
            if (sendBtn != null) sendBtn.setEnabled(true);
            if (sendMsg != null) sendMsg.setHint(R.string.editmsg);
        } else {
            String displayName = !TextUtils.isEmpty(peerNickname) ? peerNickname
                    : (!TextUtils.isEmpty(peerUsername) ? peerUsername : ("#" + userid));
            setTitle(title + " - " + displayName + " (" + getString(R.string.user_offline) + ")");
            if (sendBtn != null) sendBtn.setEnabled(false);
            if (sendMsg != null) sendMsg.setHint(R.string.user_offline_chat_hint);
        }
    }

    @Override
    public void onCmdUserTextMessage(TextMessage textmessage) {
        int userid = TextMessageActivity.this.getIntent().getExtras().getInt(EXTRA_USERID);
        
        if (textmessage.nFromUserID == userid && textmessage.nMsgType == TextMsgType.MSGTYPE_CUSTOM) {
            if (textmessage.szMessage != null && textmessage.szMessage.startsWith("typing\r\n")) {
                updateTitle(textmessage.szMessage.endsWith("1"));
            }
            return;
        }

        if(adapter != null && textmessage.nFromUserID == userid && textmessage.nMsgType == TextMsgType.MSGTYPE_USER) {
            updateTitle(false);
            accessibilityAssistant.lockEvents();
            adapter.notifyDataSetChanged();
            accessibilityAssistant.unlockEvents();
        }
    }

    private void sendTypingStatus(boolean typing) {
        if (getService() == null || getService().getTTInstance() == null) return;
        TextMessage msg = new TextMessage();
        msg.nMsgType = TextMsgType.MSGTYPE_CUSTOM;
        msg.nToUserID = getIntent().getExtras().getInt(EXTRA_USERID);
        msg.nChannelID = 0;
        msg.nFromUserID = getService().getTTInstance().getMyUserID();
        msg.szMessage = "typing\r\n" + (typing ? "1" : "0");
        getService().getTTInstance().doTextMessage(msg);
    }
}
