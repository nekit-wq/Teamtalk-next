package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import dk.bearware.TeamTalkBase;
import dk.bearware.User;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.BearWareAccount;
import org.nekit.ttproplus.data.BearWareAccountManager;
import org.nekit.ttproplus.data.Preferences;

public class ProfileActivity extends AppCompatActivity implements TeamTalkConnectionListener {

    private static final int REQUEST_BEARWARE = 101;

    private TeamTalkConnection mConnection;
    private TextView profileHeroNickname;
    private TextView profileHeroStatus;
    private TextView profileValueNickname;
    private TextView profileValueStatusmsg;
    private TextView profileValueGender;
    private MaterialSwitch switchProfileGender;
    private TextView profileValueBearware;
    private TextView profileValueClientname;

    TeamTalkService getService() {
        return mConnection != null ? mConnection.getService() : null;
    }

    TeamTalkBase getClient() {
        TeamTalkService service = getService();
        return service != null ? service.getTTInstance() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        mConnection = new TeamTalkConnection(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_profile);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        initializeViews();
    }

    private void initializeViews() {
        profileHeroNickname = findViewById(R.id.profile_hero_nickname);
        profileHeroStatus = findViewById(R.id.profile_hero_status);
        profileValueNickname = findViewById(R.id.profile_value_nickname);
        profileValueStatusmsg = findViewById(R.id.profile_value_statusmsg);
        profileValueGender = findViewById(R.id.profile_value_gender);
        switchProfileGender = findViewById(R.id.switch_profile_gender);
        profileValueBearware = findViewById(R.id.profile_value_bearware);
        profileValueClientname = findViewById(R.id.profile_value_clientname);

        View cardNick = findViewById(R.id.card_profile_nickname);
        if (cardNick != null) cardNick.setOnClickListener(v -> showEditNicknameDialog());

        View cardStatus = findViewById(R.id.card_profile_statusmsg);
        if (cardStatus != null) cardStatus.setOnClickListener(v -> showEditStatusMsgDialog());

        if (switchProfileGender != null) {
            switchProfileGender.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(Preferences.PREF_GENERAL_GENDER, isChecked).apply();
                if (profileValueGender != null) {
                    profileValueGender.setText(isChecked ? R.string.profile_gender_female : R.string.profile_gender_male);
                }
                updateServiceProfile();
            });
        }

        View cardGender = findViewById(R.id.card_profile_gender);
        if (cardGender != null) {
            cardGender.setOnClickListener(v -> {
                if (switchProfileGender != null) {
                    switchProfileGender.setChecked(!switchProfileGender.isChecked());
                }
            });
        }

        View cardBearware = findViewById(R.id.card_profile_bearware);
        if (cardBearware != null) {
            cardBearware.setOnClickListener(v -> {
                Intent edit = new Intent(this, WebLoginActivity.class);
                startActivityForResult(edit, REQUEST_BEARWARE);
            });
        }

        View cardClient = findViewById(R.id.card_profile_clientname);
        if (cardClient != null) cardClient.setOnClickListener(v -> showEditClientNameDialog());

        updateProfileUI();
    }

    private void updateProfileUI() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String nick = prefs.getString(Preferences.PREF_GENERAL_NICKNAME, "");
        if (TextUtils.isEmpty(nick)) {
            nick = getString(R.string.pref_default_nickname);
        }
        if (profileHeroNickname != null) profileHeroNickname.setText(nick);
        if (profileValueNickname != null) profileValueNickname.setText(nick);

        String status = prefs.getString(Preferences.PREF_GENERAL_STATUSMSG, "");
        if (profileHeroStatus != null) {
            profileHeroStatus.setText(TextUtils.isEmpty(status) ? getString(R.string.profile_status_empty) : status);
        }
        if (profileValueStatusmsg != null) {
            profileValueStatusmsg.setText(TextUtils.isEmpty(status) ? getString(R.string.profile_status_empty) : status);
        }

        boolean isFemale = prefs.getBoolean(Preferences.PREF_GENERAL_GENDER, false);
        if (switchProfileGender != null) {
            switchProfileGender.setChecked(isFemale);
        }
        if (profileValueGender != null) {
            profileValueGender.setText(isFemale ? R.string.profile_gender_female : R.string.profile_gender_male);
        }

        BearWareAccountManager manager = BearWareAccountManager.getInstance(this);
        if (profileValueBearware != null) {
            if (manager.hasAccounts()) {
                BearWareAccount active = manager.getActiveAccount();
                String name = active != null ? active.getDisplayName() : "";
                profileValueBearware.setText(getString(R.string.profile_bearware_active, name));
            } else {
                profileValueBearware.setText(R.string.profile_bearware_none);
            }
        }

        String clientName = prefs.getString(Preferences.PREF_GENERAL_CLIENTNAME, "");
        if (profileValueClientname != null) {
            profileValueClientname.setText(TextUtils.isEmpty(clientName) ? getString(R.string.profile_clientname_hint) : clientName);
        }
    }

    private void showEditNicknameDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(prefs.getString(Preferences.PREF_GENERAL_NICKNAME, ""));
        input.setSelection(input.getText().length());

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout layout = new FrameLayout(this);
        layout.setPadding(pad, pad / 2, pad, pad / 2);
        layout.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_nickname_title)
                .setView(layout)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    prefs.edit().putString(Preferences.PREF_GENERAL_NICKNAME, val).apply();
                    updateProfileUI();
                    updateServiceProfile();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditStatusMsgDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(prefs.getString(Preferences.PREF_GENERAL_STATUSMSG, ""));
        input.setSelection(input.getText().length());

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout layout = new FrameLayout(this);
        layout.setPadding(pad, pad / 2, pad, pad / 2);
        layout.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_statusmsg_title)
                .setView(layout)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    prefs.edit().putString(Preferences.PREF_GENERAL_STATUSMSG, val).apply();
                    updateProfileUI();
                    updateServiceProfile();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditClientNameDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(prefs.getString(Preferences.PREF_GENERAL_CLIENTNAME, ""));
        input.setSelection(input.getText().length());

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout layout = new FrameLayout(this);
        layout.setPadding(pad, pad / 2, pad, pad / 2);
        layout.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_clientname_title)
                .setView(layout)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    prefs.edit().putString(Preferences.PREF_GENERAL_CLIENTNAME, val).apply();
                    updateProfileUI();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateServiceProfile() {
        try {
            if (getService() == null || getClient() == null || getService().getUsers() == null) {
                return;
            }
            User myself = getService().getUsers().get(Integer.valueOf(getClient().getMyUserID()));
            if (myself != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                String nickname = prefs.getString(Preferences.PREF_GENERAL_NICKNAME, "");
                if (!TextUtils.isEmpty(nickname)) {
                    getClient().doChangeNickname(nickname);
                }
                int statusmode = myself.nStatusMode & (-257);
                String statusmsg = prefs.getString(Preferences.PREF_GENERAL_STATUSMSG, "");
                if (prefs.getBoolean(Preferences.PREF_GENERAL_GENDER, false)) {
                    statusmode |= 256;
                }
                getClient().doChangeStatus(statusmode, statusmsg);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mConnection != null && !mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), TeamTalkService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateProfileUI();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mConnection != null && mConnection.isBound()) {
            unbindService(mConnection);
            mConnection.setBound(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BEARWARE) {
            updateProfileUI();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        updateProfileUI();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {}
}
