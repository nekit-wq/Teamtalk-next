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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import dk.bearware.ClientEvent;
import dk.bearware.SoundLevel;
import dk.bearware.StreamType;
import dk.bearware.Subscription;
import dk.bearware.TeamTalkBase;
import dk.bearware.UserAccount;
import dk.bearware.UserRight;
import dk.bearware.Channel;
import dk.bearware.User;
import dk.bearware.UserState;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;

public class UserPropActivity extends AppCompatActivity implements TeamTalkConnectionListener {

    public final static String EXTRA_USERID = "userid";
    public static final String TAG = "bearware";
    
    private TeamTalkConnection mConnection;
    private User user = new User();
    private boolean isUpdatingUi = false;
    private boolean isInitialized = false;

    // Cached View References (ViewHolder Pattern for Activity)
    private TextView tvNickname;
    private TextView tvUsername;
    private TextView tvUserid;
    private TextView tvStatusmsg;
    private TextView tvClientname;
    private TextView tvClientversion;
    private TextView tvIpaddress;
    private TextView tvLocation;
    private TextView tvChannel;
    private String currentClientNameDisplay = "";
    private String currentLocationDisplay = "";
    private String currentChannelDisplay = "";
    private SeekBar voiceVol;
    private Button defVoiceBtn;
    private SwitchCompat voiceMute;
    private SwitchCompat voiceLeftSpeakerSwitch;
    private SwitchCompat voiceRightSpeakerSwitch;
    private SeekBar mediaVol;
    private Button defMfBtn;
    private SwitchCompat mediaMute;
    private SwitchCompat mediaLeftSpeakerSwitch;
    private SwitchCompat mediaRightSpeakerSwitch;
    private SwitchCompat subscribeTxtmsg;
    private SwitchCompat subscribeChanmsg;
    private SwitchCompat subscribeBcastmsg;
    private SwitchCompat subscribeVoice;
    private SwitchCompat subscribeVid;
    private SwitchCompat subscribeDesk;
    private SwitchCompat subscribeMedia;
    private SwitchCompat subscribeIntercepttxtmsg;
    private SwitchCompat subscribeInterceptchanmsg;
    private SwitchCompat subscribeInterceptvoice;
    private SwitchCompat subscribeInterceptvid;
    private SwitchCompat subscribeInterceptdesk;
    private SwitchCompat subscribeInterceptmedia;
    private SwitchCompat transmitVoice;
    private SwitchCompat transmitVid;
    private SwitchCompat transmitDesk;
    private SwitchCompat transmitMedia;
    private SwitchCompat transmitChanmsg;

    TeamTalkService getService() {
        TeamTalkService service = mConnection != null ? mConnection.getService() : null;
        if (service == null) {
            service = TeamTalkService.getInstance();
        }
        return service;
    }

    TeamTalkBase getClient() {
        TeamTalkService service = getService();
        return service != null ? service.getTTInstance() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_prop);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initViews();
        setupListeners();
        mConnection = new TeamTalkConnection(this);

        // Instant render from in-memory service instance
        TeamTalkService directService = TeamTalkService.getInstance();
        if (directService != null && directService.getTTInstance() != null && getIntent() != null && getIntent().getExtras() != null) {
            int userid = getIntent().getExtras().getInt(EXTRA_USERID);
            if (directService.getTTInstance().getUser(userid, user)) {
                showUser();
                this.isInitialized = true;
            }
        }
    }

    private void initViews() {
        this.tvNickname = findViewById(R.id.user_nickname);
        this.tvUsername = findViewById(R.id.user_username);
        this.tvUserid = findViewById(R.id.user_userid);
        this.tvStatusmsg = findViewById(R.id.user_statusmsg);
        this.tvClientname = findViewById(R.id.user_clientname);
        this.tvClientversion = findViewById(R.id.user_clientversion);
        this.tvIpaddress = findViewById(R.id.user_ipaddress);
        this.tvLocation = findViewById(R.id.user_location);
        this.tvChannel = findViewById(R.id.user_channel);
        this.voiceVol = findViewById(R.id.user_vol_voiceSeekBar);
        this.defVoiceBtn = findViewById(R.id.defVoiceVolBtn);
        this.voiceMute = findViewById(R.id.user_mutevoiceSwitch);
        this.voiceLeftSpeakerSwitch = findViewById(R.id.user_voice_left_speaker_switch);
        this.voiceRightSpeakerSwitch = findViewById(R.id.user_voice_right_speaker_switch);
        this.mediaVol = findViewById(R.id.user_vol_mediaSeekBar);
        this.defMfBtn = findViewById(R.id.defMfVolBtn);
        this.mediaMute = findViewById(R.id.user_mutemediaSwitch);
        this.mediaLeftSpeakerSwitch = findViewById(R.id.user_media_left_speaker_switch);
        this.mediaRightSpeakerSwitch = findViewById(R.id.user_media_right_speaker_switch);
        this.subscribeTxtmsg = findViewById(R.id.user_subscribetxtmsgSwitch);
        this.subscribeChanmsg = findViewById(R.id.user_subscribechanmsgSwitch);
        this.subscribeBcastmsg = findViewById(R.id.user_subscribebcastmsgSwitch);
        this.subscribeVoice = findViewById(R.id.user_subscribevoiceSwitch);
        this.subscribeVid = findViewById(R.id.user_subscribevidSwitch);
        this.subscribeDesk = findViewById(R.id.user_subscribedeskSwitch);
        this.subscribeMedia = findViewById(R.id.user_subscribemediaSwitch);
        this.subscribeIntercepttxtmsg = findViewById(R.id.user_subscribeintercepttxtmsgSwitch);
        this.subscribeInterceptchanmsg = findViewById(R.id.user_subscribeinterceptchanmsgSwitch);
        this.subscribeInterceptvoice = findViewById(R.id.user_subscribeinterceptvoiceSwitch);
        this.subscribeInterceptvid = findViewById(R.id.user_subscribeinterceptvidSwitch);
        this.subscribeInterceptdesk = findViewById(R.id.user_subscribeinterceptdeskSwitch);
        this.subscribeInterceptmedia = findViewById(R.id.user_subscribeinterceptmediaSwitch);
        this.transmitVoice = findViewById(R.id.user_transmitvoiceSwitch);
        this.transmitVid = findViewById(R.id.user_transmitvidSwitch);
        this.transmitDesk = findViewById(R.id.user_transmitdeskSwitch);
        this.transmitMedia = findViewById(R.id.user_transmitmediaSwitch);
        this.transmitChanmsg = findViewById(R.id.user_transmitchanmsgSwitch);

        if (this.voiceVol != null) this.voiceVol.setMax(100);
        if (this.mediaVol != null) this.mediaVol.setMax(100);
        if (this.tvClientversion != null) this.tvClientversion.setVisibility(View.GONE);

        Button btnCopyAll = findViewById(R.id.btn_copy_all_user_data);
        if (btnCopyAll != null) {
            btnCopyAll.setOnClickListener(v -> copyAllUserData());
        }

        setupCopyableFields();
    }

    private void copyToClipboard(String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            android.content.ClipData clip = android.content.ClipData.newPlainText(label, value);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, getString(R.string.user_field_copied, value), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupCopyableFields() {
        View.OnClickListener clickListener = v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            String valueToCopy = "";
            String label = "";
            if (v == tvNickname) {
                valueToCopy = user.szNickname != null ? user.szNickname : "";
                label = getString(R.string.user_prop_title_nickname);
            } else if (v == tvUsername) {
                valueToCopy = user.szUsername != null ? user.szUsername : "";
                label = getString(R.string.user_prop_title_username);
            } else if (v == tvUserid) {
                valueToCopy = String.valueOf(user.nUserID);
                label = getString(R.string.user_prop_title_userid);
            } else if (v == tvStatusmsg) {
                valueToCopy = user.szStatusMsg != null ? user.szStatusMsg : "";
                label = getString(R.string.user_prop_title_statusmsg);
            } else if (v == tvClientname) {
                valueToCopy = (currentClientNameDisplay != null && !currentClientNameDisplay.isEmpty()) ? currentClientNameDisplay : (user.szClientName != null ? user.szClientName : "");
                label = getString(R.string.user_prop_title_clientname);
            } else if (v == tvIpaddress) {
                valueToCopy = user.szIPAddress != null ? user.szIPAddress : "";
                label = getString(R.string.user_prop_title_ipaddress);
            } else if (v == tvLocation) {
                valueToCopy = (currentLocationDisplay != null && !currentLocationDisplay.isEmpty()) ? currentLocationDisplay : "";
                label = getString(R.string.user_prop_title_location);
            } else if (v == tvChannel) {
                valueToCopy = (currentChannelDisplay != null && !currentChannelDisplay.isEmpty()) ? currentChannelDisplay : "";
                label = getString(R.string.pref_title_channel);
            }

            if (!valueToCopy.isEmpty()) {
                copyToClipboard(label, valueToCopy);
            } else if (v instanceof TextView) {
                CharSequence text = ((TextView) v).getText();
                if (text != null && text.length() > 0) {
                    copyToClipboard(label, text.toString());
                }
            }
        };

        View.OnLongClickListener longClickListener = v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if (v instanceof TextView) {
                CharSequence text = ((TextView) v).getText();
                if (text != null && text.length() > 0) {
                    copyToClipboard("Field", text.toString());
                    return true;
                }
            }
            return false;
        };

        this.tvNickname.setOnClickListener(clickListener);
        this.tvUsername.setOnClickListener(clickListener);
        this.tvUserid.setOnClickListener(clickListener);
        this.tvStatusmsg.setOnClickListener(clickListener);
        this.tvClientname.setOnClickListener(clickListener);
        this.tvIpaddress.setOnClickListener(clickListener);
        this.tvLocation.setOnClickListener(clickListener);
        if (this.tvChannel != null) {
            this.tvChannel.setOnClickListener(clickListener);
            this.tvChannel.setOnLongClickListener(longClickListener);
        }

        this.tvNickname.setOnLongClickListener(longClickListener);
        this.tvUsername.setOnLongClickListener(longClickListener);
        this.tvUserid.setOnLongClickListener(longClickListener);
        this.tvStatusmsg.setOnLongClickListener(longClickListener);
        this.tvClientname.setOnLongClickListener(longClickListener);
        this.tvIpaddress.setOnLongClickListener(longClickListener);
        this.tvLocation.setOnLongClickListener(longClickListener);
    }

    private void applyVoiceVolume(int progress) {
        int refVol = Utils.refVolume(progress);
        this.user.nVolumeVoice = refVol;
        TeamTalkBase c = getClient();
        if (c != null) {
            c.setUserVolume(this.user.nUserID, StreamType.STREAMTYPE_VOICE, refVol);
            c.pumpMessage(ClientEvent.CLIENTEVENT_USER_STATECHANGE, this.user.nUserID);
        }
        TeamTalkService srv = getService();
        if (srv != null && srv.getUsers() != null) {
            User cached = srv.getUsers().get(Integer.valueOf(this.user.nUserID));
            if (cached != null) {
                cached.nVolumeVoice = refVol;
            }
        }
    }

    private void applyMediaVolume(int progress) {
        int refVol = Utils.refVolume(progress);
        this.user.nVolumeMediaFile = refVol;
        TeamTalkBase c = getClient();
        if (c != null) {
            c.setUserVolume(this.user.nUserID, StreamType.STREAMTYPE_MEDIAFILE_AUDIO, refVol);
            c.pumpMessage(ClientEvent.CLIENTEVENT_USER_STATECHANGE, this.user.nUserID);
        }
        TeamTalkService srv = getService();
        if (srv != null && srv.getUsers() != null) {
            User cached = srv.getUsers().get(Integer.valueOf(this.user.nUserID));
            if (cached != null) {
                cached.nVolumeMediaFile = refVol;
            }
        }
    }

    private void setupListeners() {
        this.voiceLeftSpeakerSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (this.isUpdatingUi) return;
            TeamTalkBase c = getClient();
            if (c == null) return;
            boolean right = this.voiceRightSpeakerSwitch.isChecked();
            c.setUserStereo(this.user.nUserID, StreamType.STREAMTYPE_VOICE, checked, right);
            c.pumpMessage(ClientEvent.CLIENTEVENT_USER_STATECHANGE, this.user.nUserID);
        });

        this.voiceRightSpeakerSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (this.isUpdatingUi) return;
            TeamTalkBase c = getClient();
            if (c == null) return;
            boolean left = this.voiceLeftSpeakerSwitch.isChecked();
            c.setUserStereo(this.user.nUserID, StreamType.STREAMTYPE_VOICE, left, checked);
            c.pumpMessage(ClientEvent.CLIENTEVENT_USER_STATECHANGE, this.user.nUserID);
        });

        this.mediaLeftSpeakerSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (this.isUpdatingUi) return;
            TeamTalkBase c = getClient();
            if (c == null) return;
            boolean right = this.mediaRightSpeakerSwitch.isChecked();
            c.setUserStereo(this.user.nUserID, StreamType.STREAMTYPE_MEDIAFILE_AUDIO, checked, right);
            c.pumpMessage(ClientEvent.CLIENTEVENT_USER_STATECHANGE, this.user.nUserID);
        });

        this.mediaRightSpeakerSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (this.isUpdatingUi) return;
            TeamTalkBase c = getClient();
            if (c == null) return;
            boolean left = this.mediaLeftSpeakerSwitch.isChecked();
            c.setUserStereo(this.user.nUserID, StreamType.STREAMTYPE_MEDIAFILE_AUDIO, left, checked);
            c.pumpMessage(ClientEvent.CLIENTEVENT_USER_STATECHANGE, this.user.nUserID);
        });

        SeekBar.OnSeekBarChangeListener volListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (UserPropActivity.this.isUpdatingUi) return;
                if (seekBar == UserPropActivity.this.voiceVol) {
                    applyVoiceVolume(progress);
                } else if (seekBar == UserPropActivity.this.mediaVol) {
                    applyMediaVolume(progress);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar arg0) {}
            @Override public void onStopTrackingTouch(SeekBar arg0) {}
        };
        this.voiceVol.setOnSeekBarChangeListener(volListener);
        this.mediaVol.setOnSeekBarChangeListener(volListener);

        this.defVoiceBtn.setOnClickListener(v -> {
            int defPercent = Utils.refVolumeToPercent(SoundLevel.SOUND_VOLUME_DEFAULT);
            this.voiceVol.setProgress(defPercent);
            applyVoiceVolume(defPercent);
        });
        this.defMfBtn.setOnClickListener(v -> {
            int defPercent = Utils.refVolumeToPercent(SoundLevel.SOUND_VOLUME_DEFAULT);
            this.mediaVol.setProgress(defPercent);
            applyMediaVolume(defPercent);
        });

        CompoundButton.OnCheckedChangeListener muteListener = (btn, checked) -> {
            if (this.isUpdatingUi) return;
            TeamTalkBase c = getClient();
            if (c == null) return;

            if (btn == this.voiceMute) {
                c.setUserMute(this.user.nUserID, StreamType.STREAMTYPE_VOICE, checked);
                c.pumpMessage(ClientEvent.CLIENTEVENT_USER_STATECHANGE, this.user.nUserID);
            } else if (btn == this.mediaMute) {
                c.setUserMute(this.user.nUserID, StreamType.STREAMTYPE_MEDIAFILE_AUDIO, checked);
                c.pumpMessage(ClientEvent.CLIENTEVENT_USER_STATECHANGE, this.user.nUserID);
            } else if (btn == this.subscribeTxtmsg) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_USER_MSG, checked);
            } else if (btn == this.subscribeChanmsg) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_CHANNEL_MSG, checked);
            } else if (btn == this.subscribeBcastmsg) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_BROADCAST_MSG, checked);
            } else if (btn == this.subscribeVoice) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_VOICE, checked);
            } else if (btn == this.subscribeVid) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_VIDEOCAPTURE, checked);
            } else if (btn == this.subscribeDesk) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_DESKTOP, checked);
            } else if (btn == this.subscribeMedia) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_MEDIAFILE, checked);
            } else if (btn == this.subscribeIntercepttxtmsg) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_INTERCEPT_USER_MSG, checked);
            } else if (btn == this.subscribeInterceptchanmsg) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_INTERCEPT_CHANNEL_MSG, checked);
            } else if (btn == this.subscribeInterceptvoice) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_INTERCEPT_VOICE, checked);
            } else if (btn == this.subscribeInterceptvid) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_INTERCEPT_VIDEOCAPTURE, checked);
            } else if (btn == this.subscribeInterceptdesk) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_INTERCEPT_DESKTOP, checked);
            } else if (btn == this.subscribeInterceptmedia) {
                Utils.toggleSubscription(c, this.user, Subscription.SUBSCRIBE_INTERCEPT_MEDIAFILE, checked);
            }

            TeamTalkService srv = getService();
            Channel chan = (srv != null && srv.getChannels() != null) ? srv.getChannels().get(this.user.nChannelID) : null;
            if (chan != null) {
                if (btn == this.transmitVoice) {
                    Utils.toggleTransmitUsers(this.user, chan, StreamType.STREAMTYPE_VOICE, checked);
                    c.doUpdateChannel(chan);
                } else if (btn == this.transmitVid) {
                    Utils.toggleTransmitUsers(this.user, chan, StreamType.STREAMTYPE_VIDEOCAPTURE, checked);
                    c.doUpdateChannel(chan);
                } else if (btn == this.transmitDesk) {
                    Utils.toggleTransmitUsers(this.user, chan, StreamType.STREAMTYPE_DESKTOP, checked);
                    c.doUpdateChannel(chan);
                } else if (btn == this.transmitMedia) {
                    Utils.toggleTransmitUsers(this.user, chan, StreamType.STREAMTYPE_MEDIAFILE, checked);
                    c.doUpdateChannel(chan);
                } else if (btn == this.transmitChanmsg) {
                    Utils.toggleTransmitUsers(this.user, chan, StreamType.STREAMTYPE_CHANNELMSG, checked);
                    c.doUpdateChannel(chan);
                }
            }
        };

        this.voiceMute.setOnCheckedChangeListener(muteListener);
        this.mediaMute.setOnCheckedChangeListener(muteListener);
        this.subscribeTxtmsg.setOnCheckedChangeListener(muteListener);
        this.subscribeChanmsg.setOnCheckedChangeListener(muteListener);
        this.subscribeBcastmsg.setOnCheckedChangeListener(muteListener);
        this.subscribeVoice.setOnCheckedChangeListener(muteListener);
        this.subscribeVid.setOnCheckedChangeListener(muteListener);
        this.subscribeDesk.setOnCheckedChangeListener(muteListener);
        this.subscribeMedia.setOnCheckedChangeListener(muteListener);
        this.subscribeIntercepttxtmsg.setOnCheckedChangeListener(muteListener);
        this.subscribeInterceptchanmsg.setOnCheckedChangeListener(muteListener);
        this.subscribeInterceptvoice.setOnCheckedChangeListener(muteListener);
        this.subscribeInterceptvid.setOnCheckedChangeListener(muteListener);
        this.subscribeInterceptdesk.setOnCheckedChangeListener(muteListener);
        this.subscribeInterceptmedia.setOnCheckedChangeListener(muteListener);
        this.transmitVoice.setOnCheckedChangeListener(muteListener);
        this.transmitVid.setOnCheckedChangeListener(muteListener);
        this.transmitDesk.setOnCheckedChangeListener(muteListener);
        this.transmitMedia.setOnCheckedChangeListener(muteListener);
        this.transmitChanmsg.setOnCheckedChangeListener(muteListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_prop, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_copy_all_user_data) {
            copyAllUserData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void copyAllUserData() {
        StringBuilder sb = new StringBuilder();
        if (user.szNickname != null && !user.szNickname.isEmpty()) {
            sb.append(getString(R.string.user_prop_title_nickname)).append(" ").append(user.szNickname).append("\n");
        }
        if (user.szUsername != null && !user.szUsername.isEmpty()) {
            sb.append(getString(R.string.user_prop_title_username)).append(" ").append(user.szUsername).append("\n");
        }
        sb.append(getString(R.string.user_prop_title_userid)).append(" ").append(user.nUserID).append("\n");
        if (user.szStatusMsg != null && !user.szStatusMsg.isEmpty()) {
            sb.append(getString(R.string.user_prop_title_statusmsg)).append(" ").append(user.szStatusMsg).append("\n");
        }
        if (tvClientname != null && tvClientname.getText() != null) {
            sb.append(tvClientname.getText().toString()).append("\n");
        }
        if (user.szIPAddress != null && !user.szIPAddress.isEmpty()) {
            sb.append(getString(R.string.user_prop_title_ipaddress)).append(" ").append(user.szIPAddress).append("\n");
        }
        if (tvLocation != null && tvLocation.getVisibility() == View.VISIBLE && tvLocation.getText() != null) {
            sb.append(tvLocation.getText().toString()).append("\n");
        }
        TeamTalkService srv = getService();
        if (srv != null && srv.getChannels() != null) {
            Channel chan = srv.getChannels().get(user.nChannelID);
            if (chan != null) {
                sb.append(getString(R.string.pref_title_channel)).append(": ").append(chan.szName).append(" (ID: ").append(chan.nChannelID).append(")\n");
            }
        }
        sb.append(getString(R.string.user_prop_title_voice_volume)).append(": ").append(Utils.refVolumeToPercent(user.nVolumeVoice)).append("%");
        if ((user.uUserState & UserState.USERSTATE_MUTE_VOICE) != 0) {
            sb.append(" [").append(getString(R.string.user_prop_title_mute_voice)).append("]");
        }
        sb.append("\n");

        sb.append(getString(R.string.user_prop_title_media_volume)).append(": ").append(Utils.refVolumeToPercent(user.nVolumeMediaFile)).append("%");
        if ((user.uUserState & UserState.USERSTATE_MUTE_MEDIAFILE) != 0) {
            sb.append(" [").append(getString(R.string.user_prop_title_mute_media)).append("]");
        }
        sb.append("\n");

        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            android.content.ClipData clip = android.content.ClipData.newPlainText("User Info", sb.toString().trim());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.user_data_copied, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!this.mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), TeamTalkService.class);
            if (!bindService(intent, this.mConnection, Context.BIND_AUTO_CREATE)) {
                Log.e(TAG, "Failed to bind to TeamTalk service");
            }
        } else if (!this.isInitialized) {
            updateUserData();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.mConnection.isBound()) {
            unbindService(this.mConnection);
            this.mConnection.setBound(false);
        }
    }

    private void updateUserData() {
        if (getIntent() != null && getIntent().getExtras() != null) {
            int userid = getIntent().getExtras().getInt(EXTRA_USERID);
            TeamTalkBase client = getClient();
            if (client == null || !client.getUser(userid, this.user)) {
                setResult(RESULT_CANCELED);
                finish();
            } else {
                showUser();
                this.isInitialized = true;
            }
        }
    }

    void showUser() {
        TeamTalkBase client = getClient();
        if (client == null) return;

        this.isUpdatingUi = true;
        try {
            String nick = user.szNickname != null ? user.szNickname : "";
            this.tvNickname.setText(getString(R.string.user_prop_title_nickname) + " " + nick);
            this.tvNickname.setContentDescription(getString(R.string.user_prop_title_nickname) + ": " + nick + ". " + getString(R.string.tap_to_copy_hint));

            String uname = user.szUsername != null ? user.szUsername : "";
            this.tvUsername.setText(getString(R.string.user_prop_title_username) + " " + uname);
            this.tvUsername.setContentDescription(getString(R.string.user_prop_title_username) + ": " + uname + ". " + getString(R.string.tap_to_copy_hint));

            this.tvUserid.setText(getString(R.string.user_prop_title_userid) + " " + user.nUserID);
            this.tvUserid.setContentDescription(getString(R.string.user_prop_title_userid) + ": " + user.nUserID + ". " + getString(R.string.tap_to_copy_hint));

            String smsg = user.szStatusMsg != null ? user.szStatusMsg : "";
            this.tvStatusmsg.setText(getString(R.string.user_prop_title_statusmsg) + " " + smsg);
            this.tvStatusmsg.setContentDescription(getString(R.string.user_prop_title_statusmsg) + ": " + smsg + ". " + getString(R.string.tap_to_copy_hint));

            String clientNameText = (user.szClientName != null && !user.szClientName.isEmpty()) ? user.szClientName : "TeamTalk";
            String fullDisplay;
            if (user.uVersion > 0 && !clientNameText.matches(".*\\d+\\.\\d+.*")) {
                String verText = ((user.uVersion >> 16) & 0xFF) + "." + ((user.uVersion >> 8) & 0xFF) + "." + (user.uVersion & 0xFF);
                fullDisplay = clientNameText + " " + verText;
            } else {
                fullDisplay = clientNameText;
            }
            this.currentClientNameDisplay = fullDisplay;
            this.tvClientname.setText(getString(R.string.user_prop_title_clientname) + " " + fullDisplay);
            this.tvClientname.setContentDescription(getString(R.string.user_prop_title_clientname) + ": " + fullDisplay + ". " + getString(R.string.tap_to_copy_hint));

            String ip = user.szIPAddress != null ? user.szIPAddress : "";
            this.tvIpaddress.setText(getString(R.string.user_prop_title_ipaddress) + " " + ip);
            this.tvIpaddress.setContentDescription(getString(R.string.user_prop_title_ipaddress) + ": " + ip + ". " + getString(R.string.tap_to_copy_hint));

            UserAccount myAccount = new UserAccount();
            if (client.getMyUserAccount(myAccount) &&
                (myAccount.uUserRights & UserRight.USERRIGHT_VIEW_ALL_USERS) != 0 &&
                user.szIPAddress != null && !user.szIPAddress.isEmpty()) {
                this.tvLocation.setVisibility(View.VISIBLE);
                this.currentLocationDisplay = getString(R.string.location_unknown);
                this.tvLocation.setText(getString(R.string.user_prop_title_location) + " " + this.currentLocationDisplay);
                this.tvLocation.setContentDescription(getString(R.string.user_prop_title_location) + ": " + this.currentLocationDisplay + ". " + getString(R.string.tap_to_copy_hint));
                final String ipAddr = user.szIPAddress;
                this.tvLocation.post(() -> {
                    IpGeoLocator.getLocation(ipAddr, UserPropActivity.this, result -> {
                        if (result != null && !result.isEmpty()) {
                            UserPropActivity.this.currentLocationDisplay = result;
                            UserPropActivity.this.tvLocation.setText(getString(R.string.user_prop_title_location) + " " + result);
                            UserPropActivity.this.tvLocation.setContentDescription(getString(R.string.user_prop_title_location) + ": " + result + ". " + getString(R.string.tap_to_copy_hint));
                        } else {
                            UserPropActivity.this.tvLocation.setVisibility(View.GONE);
                        }
                    });
                });
            } else {
                this.tvLocation.setVisibility(View.GONE);
            }

            TeamTalkService srv = getService();
            Channel chan = (srv != null && srv.getChannels() != null) ? srv.getChannels().get(user.nChannelID) : null;
            if (this.tvChannel != null) {
                if (chan != null) {
                    this.tvChannel.setVisibility(View.VISIBLE);
                    this.currentChannelDisplay = chan.szName + " (ID: " + chan.nChannelID + ")";
                    this.tvChannel.setText(getString(R.string.pref_title_channel) + ": " + this.currentChannelDisplay);
                    this.tvChannel.setContentDescription(getString(R.string.pref_title_channel) + ": " + this.currentChannelDisplay + ". " + getString(R.string.tap_to_copy_hint));
                } else {
                    this.tvChannel.setVisibility(View.GONE);
                }
            }

            this.voiceVol.setProgress(Utils.refVolumeToPercent(user.nVolumeVoice));
            this.mediaVol.setProgress(Utils.refVolumeToPercent(user.nVolumeMediaFile));
            this.voiceMute.setChecked((user.uUserState & UserState.USERSTATE_MUTE_VOICE) != 0);
            this.mediaMute.setChecked((user.uUserState & UserState.USERSTATE_MUTE_MEDIAFILE) != 0);

            boolean voiceL = user.stereoPlaybackVoice == null || user.stereoPlaybackVoice.length < 1 || user.stereoPlaybackVoice[0];
            boolean voiceR = user.stereoPlaybackVoice == null || user.stereoPlaybackVoice.length < 2 || user.stereoPlaybackVoice[1];
            this.voiceLeftSpeakerSwitch.setChecked(voiceL);
            this.voiceRightSpeakerSwitch.setChecked(voiceR);

            boolean mediaL = user.stereoPlaybackMediaFile == null || user.stereoPlaybackMediaFile.length < 1 || user.stereoPlaybackMediaFile[0];
            boolean mediaR = user.stereoPlaybackMediaFile == null || user.stereoPlaybackMediaFile.length < 2 || user.stereoPlaybackMediaFile[1];
            this.mediaLeftSpeakerSwitch.setChecked(mediaL);
            this.mediaRightSpeakerSwitch.setChecked(mediaR);

            this.subscribeTxtmsg.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_USER_MSG) != 0);
            this.subscribeChanmsg.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_CHANNEL_MSG) != 0);
            this.subscribeBcastmsg.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_BROADCAST_MSG) != 0);
            this.subscribeVoice.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_VOICE) != 0);
            this.subscribeVid.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_VIDEOCAPTURE) != 0);
            this.subscribeDesk.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_DESKTOP) != 0);
            this.subscribeMedia.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_MEDIAFILE) != 0);
            this.subscribeIntercepttxtmsg.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_INTERCEPT_USER_MSG) != 0);
            this.subscribeInterceptchanmsg.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_INTERCEPT_CHANNEL_MSG) != 0);
            this.subscribeInterceptvoice.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_INTERCEPT_VOICE) != 0);
            this.subscribeInterceptvid.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_INTERCEPT_VIDEOCAPTURE) != 0);
            this.subscribeInterceptdesk.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_INTERCEPT_DESKTOP) != 0);
            this.subscribeInterceptmedia.setChecked((user.uLocalSubscriptions & Subscription.SUBSCRIBE_INTERCEPT_MEDIAFILE) != 0);

            if (chan != null) {
                this.transmitVoice.setChecked(Utils.isTransmitAllowed(user, chan, StreamType.STREAMTYPE_VOICE));
                this.transmitVid.setChecked(Utils.isTransmitAllowed(user, chan, StreamType.STREAMTYPE_VIDEOCAPTURE));
                this.transmitDesk.setChecked(Utils.isTransmitAllowed(user, chan, StreamType.STREAMTYPE_DESKTOP));
                this.transmitMedia.setChecked(Utils.isTransmitAllowed(user, chan, StreamType.STREAMTYPE_MEDIAFILE));
                this.transmitChanmsg.setChecked(Utils.isTransmitAllowed(user, chan, StreamType.STREAMTYPE_CHANNELMSG));
            }
        } finally {
            this.isUpdatingUi = false;
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        if (!this.isInitialized) {
            updateUserData();
        }
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
    }
}
