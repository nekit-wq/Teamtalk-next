package org.nekit.ttproplus.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import dk.bearware.Channel;
import dk.bearware.StreamType;
import dk.bearware.TeamTalkBase;
import dk.bearware.TextMessage;
import dk.bearware.TextMsgType;
import dk.bearware.User;
import dk.bearware.UserAccount;
import dk.bearware.UserRight;
import dk.bearware.UserType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;

public class SelectUsersActivity extends AppCompatActivity implements TeamTalkConnectionListener {

    private TeamTalkConnection mConnection;
    private ListView usersListView;
    private EditText searchInput;

    private final List<User> allUsers = new ArrayList<>();
    private final List<User> displayedUsers = new ArrayList<>();
    private final Set<Integer> selectedUserIds = new HashSet<>();
    private UsersAdapter adapter;
    private boolean filterInChannelOnly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_users);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.bulk_users_title);
            actionBar.setSubtitle(getString(R.string.bulk_users_selected_count, 0));
        }

        usersListView = findViewById(R.id.users_list);
        searchInput = findViewById(R.id.search_user_input);

        adapter = new UsersAdapter();
        usersListView.setAdapter(adapter);

        usersListView.setOnItemClickListener((parent, view, position, id) -> {
            User user = displayedUsers.get(position);
            if (selectedUserIds.contains(user.nUserID)) {
                selectedUserIds.remove(user.nUserID);
            } else {
                selectedUserIds.add(user.nUserID);
            }
            adapter.notifyDataSetChanged();
            updateCount();
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        mConnection = new TeamTalkConnection(this);
        Intent intent = new Intent(this, TeamTalkService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_select_users, menu);
        MenuItem filterAll = menu.findItem(R.id.action_filter_all_users);
        MenuItem filterChan = menu.findItem(R.id.action_filter_channel_users);
        if (filterAll != null) filterAll.setChecked(!filterInChannelOnly);
        if (filterChan != null) filterChan.setChecked(filterInChannelOnly);
        MenuItem nextItem = menu.findItem(R.id.action_bulk_next);
        if (nextItem != null) {
            nextItem.setEnabled(!selectedUserIds.isEmpty());
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.action_bulk_next) {
            if (selectedUserIds.isEmpty()) {
                Toast.makeText(this, R.string.bulk_users_no_users_selected, Toast.LENGTH_SHORT).show();
            } else {
                showCategoryMenuDialog();
            }
            return true;
        }
        if (id == R.id.action_bulk_select_all) {
            for (User u : displayedUsers) {
                selectedUserIds.add(u.nUserID);
            }
            adapter.notifyDataSetChanged();
            updateCount();
            return true;
        }
        if (id == R.id.action_bulk_deselect_all) {
            selectedUserIds.clear();
            adapter.notifyDataSetChanged();
            updateCount();
            return true;
        }
        if (id == R.id.action_filter_all_users) {
            item.setChecked(true);
            filterInChannelOnly = false;
            applyFilter();
            return true;
        }
        if (id == R.id.action_filter_channel_users) {
            item.setChecked(true);
            filterInChannelOnly = true;
            applyFilter();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final dk.bearware.events.ClientEventListener.OnCmdUserLoggedInListener userLoggedInListener = user -> runOnUiThread(this::loadUsers);
    private final dk.bearware.events.ClientEventListener.OnCmdUserLoggedOutListener userLoggedOutListener = user -> runOnUiThread(() -> {
        selectedUserIds.remove(user.nUserID);
        loadUsers();
    });
    private final dk.bearware.events.ClientEventListener.OnCmdUserJoinedChannelListener userJoinedListener = user -> runOnUiThread(this::loadUsers);
    private final dk.bearware.events.ClientEventListener.OnCmdUserLeftChannelListener userLeftListener = (channelid, user) -> runOnUiThread(this::loadUsers);
    private final dk.bearware.events.ClientEventListener.OnCmdUserUpdateListener userUpdateListener = user -> runOnUiThread(this::loadUsers);

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TeamTalkService service = getService();
        if (service != null && service.getEventHandler() != null) {
            service.getEventHandler().registerOnCmdUserLoggedIn(userLoggedInListener, false);
            service.getEventHandler().registerOnCmdUserLoggedOut(userLoggedOutListener, false);
            service.getEventHandler().registerOnCmdUserJoinedChannel(userJoinedListener, false);
            service.getEventHandler().registerOnCmdUserLeftChannel(userLeftListener, false);
            service.getEventHandler().registerOnCmdUserUpdate(userUpdateListener, false);
        }
        if (mConnection.isBound()) {
            unbindService(mConnection);
            mConnection.setBound(false);
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        if (service != null && service.getEventHandler() != null) {
            service.getEventHandler().registerOnCmdUserLoggedIn(userLoggedInListener, true);
            service.getEventHandler().registerOnCmdUserLoggedOut(userLoggedOutListener, true);
            service.getEventHandler().registerOnCmdUserJoinedChannel(userJoinedListener, true);
            service.getEventHandler().registerOnCmdUserLeftChannel(userLeftListener, true);
            service.getEventHandler().registerOnCmdUserUpdate(userUpdateListener, true);
        }
        loadUsers();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {}

    private TeamTalkService getService() {
        return mConnection != null ? mConnection.getService() : null;
    }

    private TeamTalkBase getClient() {
        TeamTalkService s = getService();
        return s != null ? s.getTTInstance() : null;
    }

    private void loadUsers() {
        TeamTalkService service = getService();
        TeamTalkBase client = getClient();
        if (service == null || client == null) return;

        allUsers.clear();
        int myUserId = client.getMyUserID();
        for (User u : service.getUsers().values()) {
            if (u.nUserID != myUserId) {
                allUsers.add(u);
            }
        }
        Collections.sort(allUsers, (o1, o2) -> {
            String n1 = Utils.getDisplayName(SelectUsersActivity.this, o1);
            String n2 = Utils.getDisplayName(SelectUsersActivity.this, o2);
            return n1.compareToIgnoreCase(n2);
        });
        applyFilter();
    }

    private void applyFilter() {
        TeamTalkBase client = getClient();
        String query = searchInput.getText().toString().trim().toLowerCase(java.util.Locale.getDefault());
        int myChanId = client != null ? client.getMyChannelID() : -1;

        displayedUsers.clear();
        for (User u : allUsers) {
            if (filterInChannelOnly && (myChanId <= 0 || u.nChannelID != myChanId)) {
                continue;
            }
            if (!query.isEmpty()) {
                String displayName = Utils.getDisplayName(this, u).toLowerCase(java.util.Locale.getDefault());
                String nick = u.szNickname != null ? u.szNickname.toLowerCase(java.util.Locale.getDefault()) : "";
                String user = u.szUsername != null ? u.szUsername.toLowerCase(java.util.Locale.getDefault()) : "";
                if (!displayName.contains(query) && !nick.contains(query) && !user.contains(query)) {
                    continue;
                }
            }
            displayedUsers.add(u);
        }
        adapter.notifyDataSetChanged();
        updateCount();
    }

    private void updateCount() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(getString(R.string.bulk_users_selected_count, selectedUserIds.size()));
        }
        invalidateOptionsMenu();
    }

    private void showCategoryMenuDialog() {
        TeamTalkBase client = getClient();
        TeamTalkService service = getService();
        if (client == null || service == null) return;

        UserAccount myAccount = new UserAccount();
        client.getMyUserAccount(myAccount);

        boolean isAdmin = (myAccount.uUserType & UserType.USERTYPE_ADMIN) != 0;
        boolean banRight = (myAccount.uUserRights & UserRight.USERRIGHT_BAN_USERS) != 0;
        boolean moveRight = (myAccount.uUserRights & UserRight.USERRIGHT_MOVE_USERS) != 0;
        boolean kickRight = (myAccount.uUserRights & UserRight.USERRIGHT_KICK_USERS) != 0;
        boolean msgRight = (myAccount.uUserRights & UserRight.USERRIGHT_TEXTMESSAGE_USER) != 0;
        int myUserId = client.getMyUserID();
        int myChanId = client.getMyChannelID();
        boolean isChannelOp = (myChanId > 0) && client.isChannelOperator(myUserId, myChanId);

        final List<CategoryItem> categories = new ArrayList<>();

        if (isAdmin || msgRight) {
            categories.add(new CategoryItem(CategoryType.MESSAGES, getString(R.string.bulk_cat_messages)));
        }

        categories.add(new CategoryItem(CategoryType.AUDIO, getString(R.string.bulk_cat_audio)));

        if (isAdmin || isChannelOp || kickRight || moveRight) {
            categories.add(new CategoryItem(CategoryType.CHANNEL, getString(R.string.bulk_cat_channel)));
        }

        if (isAdmin || kickRight || banRight) {
            categories.add(new CategoryItem(CategoryType.SERVER, getString(R.string.bulk_cat_server)));
        }

        categories.add(new CategoryItem(CategoryType.SUBSCRIPTIONS, getString(R.string.bulk_cat_subscriptions)));

        String[] categoryTitles = new String[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            categoryTitles[i] = categories.get(i).title;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.bulk_users_actions_title, selectedUserIds.size()));
        builder.setItems(categoryTitles, (dialog, which) -> {
            CategoryItem selectedCat = categories.get(which);
            showSubActionsDialog(selectedCat.type, isAdmin, isChannelOp, kickRight, moveRight, banRight, msgRight);
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private enum CategoryType {
        MESSAGES, AUDIO, CHANNEL, SERVER, SUBSCRIPTIONS
    }

    private static class CategoryItem {
        final CategoryType type;
        final String title;
        CategoryItem(CategoryType type, String title) {
            this.type = type;
            this.title = title;
        }
    }

    private void showSubActionsDialog(CategoryType cat, boolean isAdmin, boolean isChannelOp, boolean kickRight, boolean moveRight, boolean banRight, boolean msgRight) {
        List<String> actionTitles = new ArrayList<>();
        List<Runnable> actionRunnables = new ArrayList<>();

        switch (cat) {
            case MESSAGES:
                if (isAdmin || msgRight) {
                    actionTitles.add(getString(R.string.bulk_action_send_pm));
                    actionRunnables.add(this::performSendPm);
                }
                break;

            case AUDIO:
                actionTitles.add(getString(R.string.bulk_action_mute_voice));
                actionRunnables.add(() -> performMuteVoice(true));

                actionTitles.add(getString(R.string.bulk_action_unmute_voice));
                actionRunnables.add(() -> performMuteVoice(false));

                actionTitles.add(getString(R.string.bulk_action_mute_media));
                actionRunnables.add(() -> performMuteMedia(true));

                actionTitles.add(getString(R.string.bulk_action_set_volume));
                actionRunnables.add(this::performSetVolume);
                break;

            case CHANNEL:
                if (isAdmin || isChannelOp || moveRight) {
                    actionTitles.add(getString(R.string.bulk_action_move_channel));
                    actionRunnables.add(this::performMoveChannel);
                }
                if (isAdmin || isChannelOp || kickRight) {
                    actionTitles.add(getString(R.string.bulk_action_kick_channel));
                    actionRunnables.add(this::performKickChannel);
                }
                break;

            case SERVER:
                if (isAdmin || kickRight) {
                    actionTitles.add(getString(R.string.bulk_action_kick_server));
                    actionRunnables.add(this::performKickServer);
                }
                if (isAdmin || banRight) {
                    actionTitles.add(getString(R.string.bulk_action_ban_server));
                    actionRunnables.add(this::performBanServer);
                }
                break;

            case SUBSCRIPTIONS:
                actionTitles.add(getString(R.string.bulk_action_sub_all));
                actionRunnables.add(() -> performSubscriptions(true));

                actionTitles.add(getString(R.string.bulk_action_unsub_all));
                actionRunnables.add(() -> performSubscriptions(false));
                break;
        }

        if (actionTitles.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.bulk_users_actions_title, selectedUserIds.size()));
        builder.setItems(actionTitles.toArray(new String[0]), (dialog, which) -> {
            actionRunnables.get(which).run();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void performSendPm() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.bulk_send_pm_title, selectedUserIds.size()));
        final EditText input = new EditText(this);
        input.setHint(R.string.bulk_send_pm_hint);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            TeamTalkBase client = getClient();
            if (client == null) return;
            TextMessage msg = new TextMessage();
            msg.nMsgType = TextMsgType.MSGTYPE_USER;
            msg.szMessage = text;
            for (int uid : selectedUserIds) {
                msg.nToUserID = uid;
                client.doTextMessage(msg);
            }
            Toast.makeText(SelectUsersActivity.this, getString(R.string.bulk_send_pm_sent, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void performMuteVoice(boolean mute) {
        TeamTalkBase client = getClient();
        if (client == null) return;
        for (int uid : selectedUserIds) {
            client.setUserMute(uid, StreamType.STREAMTYPE_VOICE, mute);
        }
        Toast.makeText(this, getString(R.string.bulk_action_completed, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
    }

    private void performMuteMedia(boolean mute) {
        TeamTalkBase client = getClient();
        if (client == null) return;
        for (int uid : selectedUserIds) {
            client.setUserMute(uid, StreamType.STREAMTYPE_MEDIAFILE_AUDIO, mute);
        }
        Toast.makeText(this, getString(R.string.bulk_action_completed, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
    }

    private void performSetVolume() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.bulk_action_set_volume);
        final SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(50);
        seekBar.setPadding(30, 20, 30, 20);
        builder.setView(seekBar);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            int vol = seekBar.getProgress();
            TeamTalkBase client = getClient();
            TeamTalkService service = getService();
            if (client == null || service == null) return;
            for (int uid : selectedUserIds) {
                client.setUserVolume(uid, StreamType.STREAMTYPE_VOICE, Utils.refVolume(vol));
                User u = service.getUsers().get(uid);
                if (u != null) {
                    u.nVolumeVoice = Utils.refVolume(vol);
                }
            }
            Toast.makeText(SelectUsersActivity.this, getString(R.string.bulk_action_completed, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void performMoveChannel() {
        TeamTalkService service = getService();
        TeamTalkBase client = getClient();
        if (service == null || client == null) return;

        final List<Channel> channels = new ArrayList<>(service.getChannels().values());
        Collections.sort(channels, (c1, c2) -> c1.szName.compareToIgnoreCase(c2.szName));

        String[] channelNames = new String[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            channelNames[i] = channels.get(i).szName;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.bulk_move_channel_title);
        builder.setItems(channelNames, (dialog, which) -> {
            int targetChanId = channels.get(which).nChannelID;
            for (int uid : selectedUserIds) {
                client.doMoveUser(uid, targetChanId);
            }
            Toast.makeText(SelectUsersActivity.this, getString(R.string.bulk_action_completed, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void performKickChannel() {
        TeamTalkBase client = getClient();
        TeamTalkService service = getService();
        if (client == null || service == null) return;

        for (int uid : selectedUserIds) {
            User u = service.getUsers().get(uid);
            int chanId = (u != null) ? u.nChannelID : 0;
            if (chanId > 0) {
                client.doKickUser(uid, chanId);
            }
        }
        Toast.makeText(this, getString(R.string.bulk_action_completed, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
    }

    private void performKickServer() {
        TeamTalkBase client = getClient();
        if (client == null) return;
        for (int uid : selectedUserIds) {
            client.doKickUser(uid, 0);
        }
        Toast.makeText(this, getString(R.string.bulk_action_completed, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
    }

    private void performBanServer() {
        TeamTalkBase client = getClient();
        if (client == null) return;
        for (int uid : selectedUserIds) {
            client.doBanUser(uid, 0);
        }
        Toast.makeText(this, getString(R.string.bulk_action_completed, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
    }

    private void performSubscriptions(boolean subscribe) {
        TeamTalkBase client = getClient();
        if (client == null) return;
        int flags = StreamType.STREAMTYPE_VOICE | StreamType.STREAMTYPE_VIDEOCAPTURE | StreamType.STREAMTYPE_MEDIAFILE;
        for (int uid : selectedUserIds) {
            if (subscribe) {
                client.doSubscribe(uid, flags);
            } else {
                client.doUnsubscribe(uid, flags);
            }
        }
        Toast.makeText(this, getString(R.string.bulk_action_completed, selectedUserIds.size()), Toast.LENGTH_SHORT).show();
    }

    private class UsersAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return displayedUsers.size();
        }

        @Override
        public Object getItem(int position) {
            return displayedUsers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return displayedUsers.get(position).nUserID;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SelectUsersActivity.this).inflate(R.layout.item_selectable_user, parent, false);
            }

            User user = displayedUsers.get(position);
            CheckBox checkBox = convertView.findViewById(R.id.user_checkbox);
            TextView txtName = convertView.findViewById(R.id.user_name);
            TextView txtChannel = convertView.findViewById(R.id.user_channel);

            checkBox.setChecked(selectedUserIds.contains(user.nUserID));
            txtName.setText(Utils.getDisplayName(SelectUsersActivity.this, user));

            TeamTalkService service = getService();
            if (service != null) {
                Channel chan = service.getChannels().get(user.nChannelID);
                txtChannel.setText(chan != null ? chan.szName : getString(R.string.root_channel));
            } else {
                txtChannel.setText("");
            }

            return convertView;
        }
    }
}
