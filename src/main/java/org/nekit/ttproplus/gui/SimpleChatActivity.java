package org.nekit.ttproplus.gui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import dk.bearware.TeamTalkBase;
import dk.bearware.TextMessage;
import dk.bearware.TextMsgType;
import dk.bearware.User;
import dk.bearware.events.ClientEventListener;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.MyTextMessage;
import org.nekit.ttproplus.data.TextMessageAdapter;

public class SimpleChatActivity extends AppCompatActivity 
        implements TeamTalkConnectionListener, ClientEventListener.OnCmdUserTextMessageListener {

    public static final String TAG = "bearware";

    private TeamTalkConnection mConnection;
    private TextMessageAdapter adapter;
    private AccessibilityAssistant accessibilityAssistant;
    
    private String chatType;
    private int userid;

    private TeamTalkService getService() {
        return mConnection.getService();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        chatType = intent.getStringExtra("chat_type");
        userid = intent.getIntExtra("userid", 0);

        mConnection = new TeamTalkConnection(this);
        setContentView(R.layout.activity_simple_chat);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        accessibilityAssistant = new AccessibilityAssistant(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
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
    public boolean onSupportNavigateUp() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        View v = getCurrentFocus();
        if ((v != null) && imm.isActive())
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        finish();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), TeamTalkService.class);
            if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
                Log.e(TAG, "Failed to bind to TeamTalk service");
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mConnection.isBound()) {
            onServiceDisconnected(getService());
            unbindService(mConnection);
            mConnection.setBound(false);
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        final TeamTalkBase ttclient = service.getTTInstance();
        
        if ("global".equals(chatType)) {
            adapter = new TextMessageAdapter(this, accessibilityAssistant,
                    service.getChatLogTextMsgs(),
                    ttclient.getMyUserID());
        } else {
            adapter = new TextMessageAdapter(this, accessibilityAssistant,
                    service.getUserTextMsgs(userid),
                    ttclient.getMyUserID());
        }

        ListView lv = findViewById(R.id.simple_chat_listview);
        lv.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        lv.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        Button send_btn = this.findViewById(R.id.simple_chat_sendbtn);
        final EditText send_msg = this.findViewById(R.id.simple_chat_edittext);

        send_btn.setOnClickListener(v -> {
            String msgText = send_msg.getText().toString();
            if (msgText.isEmpty())
                return;

            User myself = service.getUsers().get(ttclient.getMyUserID());
            String name = Utils.getDisplayName(getBaseContext(), myself);
            
            if ("global".equals(chatType)) {
                MyTextMessage textmsg = new MyTextMessage(myself == null ? "" : name);
                textmsg.nMsgType = TextMsgType.MSGTYPE_CHANNEL;
                textmsg.nChannelID = ttclient.getMyChannelID();
                textmsg.nFromUserID = ttclient.getMyUserID();
                textmsg.szMessage = msgText;

                int cmdid = 0;
                for (MyTextMessage m : textmsg.split()) {
                    cmdid = ttclient.doTextMessage(m);
                }

                if (cmdid > 0) {
                    send_msg.setText("");
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(SimpleChatActivity.this,
                            R.string.text_con_cmderr,
                            Toast.LENGTH_LONG).show();
                }
            } else {
                MyTextMessage textmsg = new MyTextMessage(myself == null ? "" : name);
                textmsg.nMsgType = TextMsgType.MSGTYPE_USER;
                textmsg.nChannelID = 0;
                textmsg.nFromUserID = ttclient.getMyUserID();
                textmsg.nToUserID = userid;
                textmsg.szMessage = msgText;

                boolean sent = true;
                for (MyTextMessage m : textmsg.split()) {
                    sent = sent && ttclient.doTextMessage(m) > 0;
                    service.getUserTextMsgs(userid).add(m);
                }
                if (sent) {
                    send_msg.setText("");
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(SimpleChatActivity.this,
                            R.string.err_send_text_message,
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        service.getEventHandler().registerOnCmdUserTextMessage(this, true);
        updateTitle();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
        service.getEventHandler().registerOnCmdUserTextMessage(this, false);
    }

    void updateTitle() {
        if ("global".equals(chatType)) {
            setTitle(R.string.chat_option_global);
        } else {
            String title = getResources().getString(R.string.chat_option_private);
            User user = getService().getUsers().get(userid);
            if (user != null) {
                String name = Utils.getDisplayName(getBaseContext(), user);
                setTitle(title + " - " + name);
            } else {
                setTitle(title);
            }
        }
    }

    @Override
    public void onCmdUserTextMessage(TextMessage textmessage) {
        if ("global".equals(chatType)) {
            if (adapter != null && textmessage.nMsgType == TextMsgType.MSGTYPE_CHANNEL) {
                accessibilityAssistant.lockEvents();
                adapter.notifyDataSetChanged();
                accessibilityAssistant.unlockEvents();
            }
        } else {
            if (adapter != null && textmessage.nFromUserID == userid &&
                    textmessage.nMsgType == TextMsgType.MSGTYPE_USER) {
                accessibilityAssistant.lockEvents();
                adapter.notifyDataSetChanged();
                accessibilityAssistant.unlockEvents();
            }
        }
    }
}
