package org.nekit.ttproplus.gui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import dk.bearware.TeamTalkBase;
import dk.bearware.UserAccount;
import dk.bearware.events.ClientEventListener;
import java.util.Objects;
import java.util.Locale;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.ServerEntry;
import org.nekit.ttproplus.databinding.ActivityServerEntryBinding;

public class ServerEntryActivity extends AppCompatActivity implements TeamTalkConnectionListener, ClientEventListener.OnCmdMyselfLoggedInListener {
    private static final int MAX_PORT = 65535;
    private static final int MIN_PORT = 1;
    private static final String TAG = "bearware";
    private ActivityServerEntryBinding binding;
    private TeamTalkConnection mConnection;
    private ServerEntry serverentry;

    TeamTalkService getService() {
        return this.mConnection != null ? this.mConnection.getService() : null;
    }

    TeamTalkBase getClient() {
        TeamTalkService service = getService();
        return service != null ? service.getTTInstance() : null;
    }

        @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        this.mConnection = new TeamTalkConnection(this);
        this.binding = ActivityServerEntryBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setupListeners();
    }

        @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        ServerEntry entry = Utils.getServerEntry(getIntent());
        if (entry != null) {
            showServer(entry);
        } else {
            this.binding.serverStatusSection.setVisibility(8);
            hideJoinCode();
        }
    }

        public void lambda$setupListeners$0(CompoundButton buttonView, boolean isChecked) {
        onWebLoginChanged(isChecked);
    }

    private void setupListeners() {
        this.binding.webLoginCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { 
            @Override
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                ServerEntryActivity.this.lambda$setupListeners$0(compoundButton, z);
            }
        });
        this.binding.rememberLastChannelCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { 
            @Override
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                ServerEntryActivity.this.lambda$setupListeners$1(compoundButton, z);
            }
        });
        this.binding.tcpPortEdit.addTextChangedListener(new PortTextWatcher(this.binding.tcpPortEdit));
        this.binding.udpPortEdit.addTextChangedListener(new PortTextWatcher(this.binding.udpPortEdit));
        this.binding.copyJoincodeBtn.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                ServerEntryActivity.this.lambda$setupListeners$2(view);
            }
        });
    }

        public void lambda$setupListeners$1(CompoundButton buttonView, boolean isChecked) {
        setChannelViewsVisibility(!isChecked);
    }

        public void lambda$setupListeners$2(View v) {
        String joincode = this.binding.joincodeEdit.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService("clipboard");
        ClipData clip = ClipData.newPlainText("label", joincode);
        clipboard.setPrimaryClip(clip);
    }

    private void setChannelViewsVisibility(boolean visible) {
        int visibility = visible ? 0 : 8;
        this.binding.channelLabel.setVisibility(visibility);
        this.binding.channelLayout.setVisibility(visibility);
        this.binding.channelPasswordLabel.setVisibility(visibility);
        this.binding.channelPasswordLayout.setVisibility(visibility);
    }

        public static final class PortTextWatcher implements TextWatcher {
        private final TextInputEditText editText;

        private PortTextWatcher(TextInputEditText editText) {
            this.editText = editText;
        }

        public TextInputEditText editText() {
            return this.editText;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            String text = s.toString().trim();
            if (text.isEmpty()) {
                this.editText.setError(null);
                return;
            }
            try {
                int port = Integer.parseInt(text);
                if (port >= 1 && port <= 65535) {
                    this.editText.setError(null);
                } else {
                    this.editText.setError("Port must be between 1 and 65535");
                }
            } catch (NumberFormatException e) {
                this.editText.setError("Invalid port number");
            }
        }
    }

        @Override
    public void onResume() {
        super.onResume();
        if (this.mConnection.isBound()) {
            resetTeamTalkService();
            getService().getEventHandler().registerOnCmdMyselfLoggedIn(this, true);
        }
    }

        @Override
    public void onPause() {
        super.onPause();
        if (this.mConnection.isBound()) {
            getService().getEventHandler().registerOnCmdMyselfLoggedIn(this, false);
        }
    }

    private void resetTeamTalkService() {
        getService().resetState();
        getClient().closeSoundInputDevice();
        getClient().closeSoundOutputDevice();
    }

        @Override
    public void onStart() {
        super.onStart();
        if (this.serverentry != null) {
            showServer(this.serverentry);
            this.serverentry = null;
        }
        bindToTeamTalkService();
    }

        @Override
    public void onStop() {
        super.onStop();
        if (isFinishing()) {
            unbindFromTeamTalkService();
        }
    }

        @Override
    public void onDestroy() {
        super.onDestroy();
        unbindFromTeamTalkService();
        this.binding = null;
        Log.d("bearware", "Activity destroyed " + hashCode());
    }

    private void bindToTeamTalkService() {
        if (!this.mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), (Class<?>) TeamTalkService.class);
            if (!bindService(intent, this.mConnection, 1)) {
                Log.e("bearware", "Failed to bind to TeamTalk service");
            }
        }
    }

    private void unbindFromTeamTalkService() {
        if (this.mConnection.isBound()) {
            getService().resetState();
            onServiceDisconnected(getService());
            unbindService(this.mConnection);
            this.mConnection.setBound(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.server_entry, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_connect) {
            connectToServer();
            return true;
        }
        if (itemId == R.id.action_saveserver) {
            saveServerAndFinish();
            return true;
        }
        if (itemId == 16908332) {
            setResult(0);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void connectToServer() {
        this.serverentry = getServerEntry();
        getService().setServerEntry(this.serverentry);
        if (!getService().reconnect()) {
            Toast.makeText(this, R.string.err_connection, 1).show();
        }
    }

    private void saveServerAndFinish() {
        ServerEntry server = getServerEntry();
        server.servertype = ServerEntry.ServerType.LOCAL;
        Intent intent = Utils.putServerEntry(getIntent(), server);
        setResult(-1, intent);
        finish();
    }

    private ServerEntry getServerEntry() {
        ServerEntry server = new ServerEntry();
        server.servername = getTextValue(this.binding.serverNameEdit);
        server.ipaddr = getTextValue(this.binding.ipAddressEdit);
        server.tcpport = parsePort(getTextValue(this.binding.tcpPortEdit));
        server.udpport = parsePort(getTextValue(this.binding.udpPortEdit));
        server.encrypted = this.binding.encryptedCheckbox.isChecked();
        server.username = getTextValue(this.binding.usernameEdit);
        server.password = getTextValue(this.binding.passwordEdit);
        server.nickname = getTextValue(this.binding.nicknameEdit);
        server.statusmsg = getTextValue(this.binding.statusmsgEdit);
        server.rememberLastChannel = this.binding.rememberLastChannelCheckbox.isChecked();
        server.channel = getTextValue(this.binding.channelEdit);
        server.chanpasswd = getTextValue(this.binding.channelPasswordEdit);
        return server;
    }

    private int parsePort(String portStr) {
        int defaultPort = getDefaultPort();
        if (portStr.isEmpty()) {
            return defaultPort;
        }
        try {
            int port = Integer.parseInt(portStr);
            return (port < 1 || port > 65535) ? defaultPort : port;
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    private int getDefaultPort() {
        try {
            return Integer.parseInt(getString(R.string.default_port));
        } catch (NumberFormatException e) {
            return 10333;
        }
    }

    private String getTextValue(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void showServer(ServerEntry entry) {
        populateServerInfo(entry);
        populateServerStatus(entry);
        populateConnectionSettings(entry);
        populateAuthenticationSettings(entry);
        populateChannelSettings(entry);
        populateJoinCodeSettings(entry);
    }

    private void populateServerInfo(ServerEntry entry) {
        this.binding.serverNameEdit.setText(entry.servername);
    }

    private void populateServerStatus(ServerEntry entry) {
        boolean isLocal = entry.servertype == ServerEntry.ServerType.LOCAL;
        this.binding.serverStatusSection.setVisibility(isLocal ? 8 : 0);
        if (!isLocal) {
            this.binding.userCountText.setText(formatServerInfo(R.string.pref_title_server_usercount, String.valueOf(entry.stats_usercount)));
            this.binding.motdText.setText(formatServerInfo(R.string.pref_title_server_motd, entry.stats_motd));
            this.binding.countryText.setText(formatServerInfo(R.string.pref_title_server_country, getCountryDisplayName(entry.stats_country)));
        }
    }

    private String getCountryDisplayName(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return countryCode;
        }
        try {
            Locale locale = new Locale("", countryCode.toUpperCase(Locale.ROOT));
            String displayName = locale.getDisplayCountry();
            return displayName.isEmpty() ? countryCode : displayName;
        } catch (Exception e) {
            return countryCode;
        }
    }

    private void populateConnectionSettings(ServerEntry entry) {
        this.binding.ipAddressEdit.setText(entry.ipaddr);
        this.binding.tcpPortEdit.setText(String.valueOf(entry.tcpport));
        this.binding.udpPortEdit.setText(String.valueOf(entry.udpport));
        this.binding.encryptedCheckbox.setChecked(entry.encrypted);
    }

    private void populateAuthenticationSettings(ServerEntry entry) {
        boolean weblogin = Utils.isWebLogin(entry.username);
        this.binding.usernameEdit.setText(entry.username);
        this.binding.passwordEdit.setText(entry.password);
        setAuthFieldsEnabled(!weblogin);
        this.binding.webLoginCheckbox.setChecked(weblogin);
        this.binding.nicknameEdit.setText(entry.nickname);
        this.binding.statusmsgEdit.setText(entry.statusmsg);
    }

    private void populateChannelSettings(ServerEntry entry) {
        this.binding.rememberLastChannelCheckbox.setChecked(entry.rememberLastChannel);
        this.binding.channelEdit.setText(entry.channel);
        this.binding.channelPasswordEdit.setText(entry.chanpasswd);
        setChannelViewsVisibility(!entry.rememberLastChannel);
    }

    private void populateJoinCodeSettings(ServerEntry entry) {
        if (!entry.joincode.isEmpty()) {
            this.binding.joincodeEdit.setText(entry.joincode);
        } else {
            hideJoinCode();
        }
    }

    private void hideJoinCode() {
        this.binding.prefTitleJoincode.setVisibility(8);
        this.binding.joincodeLayout.setVisibility(8);
        this.binding.textJoincode.setVisibility(8);
        this.binding.joincodeEdit.setVisibility(8);
        this.binding.copyJoincodeBtn.setVisibility(8);
    }

    private String formatServerInfo(int titleResId, String value) {
        return getString(titleResId) + ": " + value;
    }

    private void setAuthFieldsEnabled(boolean enabled) {
        this.binding.usernameEdit.setEnabled(enabled);
        this.binding.passwordEdit.setEnabled(enabled);
    }

    private void onWebLoginChanged(boolean weblogin) {
        setAuthFieldsEnabled(!weblogin);
        if (weblogin) {
            this.binding.usernameEdit.setText("bearware");
            this.binding.passwordEdit.setText("");
            return;
        }
        ServerEntry entry = this.serverentry != null ? this.serverentry : Utils.getServerEntry(getIntent());
        if (entry != null) {
            this.binding.usernameEdit.setText(entry.username);
            this.binding.passwordEdit.setText(entry.password);
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        service.getEventHandler().registerOnCmdMyselfLoggedIn(this, true);
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
        service.getEventHandler().unregisterListener(this);
    }

    @Override
    public void onCmdMyselfLoggedIn(int my_userid, UserAccount useraccount) {
        Intent intent = new Intent(getBaseContext(), (Class<?>) MainActivity.class);
        startActivity(intent.putExtra(ServerEntry.KEY_SERVERNAME, this.serverentry.servername));
    }
}
