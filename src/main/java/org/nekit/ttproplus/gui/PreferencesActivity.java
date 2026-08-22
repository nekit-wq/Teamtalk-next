package org.nekit.ttproplus.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import org.nekit.ttproplus.data.ServerEntry;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatCallback;
import androidx.appcompat.app.AppCompatDelegate;
import dk.bearware.TeamTalkBase;
import dk.bearware.User;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.AppInfo;
import org.nekit.ttproplus.data.Preferences;
import org.nekit.ttproplus.data.TTSWrapper;
import org.nekit.ttproplus.gui.PreferencesActivity;

public class PreferencesActivity extends PreferenceActivity implements TeamTalkConnectionListener {
    static final int ACTIVITY_REQUEST_BEARWAREID = 2;
    public static final int REQUEST_MEDIA_PROJECTION = 2001;
    public static final String TAG = "bearware";
    private static String pendingInputSource = null;
    private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() { 
        @Override
        public final boolean onPreferenceChange(Preference preference, Object obj) {
            return PreferencesActivity.lambda$static$0(preference, obj);
        }
    };
    private AppCompatDelegate appCompatDelegate = null;
    TeamTalkConnection mConnection;

    TeamTalkService getService() {
        return this.mConnection != null ? this.mConnection.getService() : null;
    }

    TeamTalkBase getClient() {
        TeamTalkService service = getService();
        return service != null ? service.getTTInstance() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
        this.mConnection = new TeamTalkConnection(this);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
    }

    @Override
    public MenuInflater getMenuInflater() {
        return getDelegate().getMenuInflater();
    }

    @Override
    public void setContentView(int layoutResID) {
        getDelegate().setContentView(layoutResID);
    }

    @Override
    public void setContentView(View view) {
        getDelegate().setContentView(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().setContentView(view, params);
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().addContentView(view, params);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getDelegate().onPostResume();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        getDelegate().setTitle(title);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        getDelegate().onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        getDelegate().onStart();
        if (!this.mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), (Class<?>) TeamTalkService.class);
            Log.d("bearware", "Binding TeamTalk service");
            if (!bindService(intent, this.mConnection, 1)) {
                Log.e("bearware", "Failed to bind to TeamTalk service");
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        getDelegate().onStop();
        updateSettings();
        if (this.mConnection.isBound()) {
            Log.d("bearware", "Unbinding TeamTalk service");
            unbindService(this.mConnection);
            this.mConnection.setBound(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getDelegate().onDestroy();
    }

    @Override
    public void invalidateOptionsMenu() {
        getDelegate().invalidateOptionsMenu();
    }

    void updateSettings() {
        if (getService() == null || getClient() == null || getService().getUsers() == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        User myself = getService().getUsers().get(Integer.valueOf(getClient().getMyUserID()));
        if (myself != null) {
            ServerEntry serverEntry = getService().getServerEntry();
            String nickname = serverEntry != null ? serverEntry.nickname : "";
            if (TextUtils.isEmpty(nickname)) {
                nickname = prefs.getString(Preferences.PREF_GENERAL_NICKNAME, "");
            }
            if (!nickname.equals(myself.szNickname)) {
                getClient().doChangeNickname(nickname);
            }
            int statusmode = myself.nStatusMode & (-257);
            String statusmsg = serverEntry != null ? serverEntry.statusmsg : "";
            if (TextUtils.isEmpty(statusmsg)) {
                statusmsg = prefs.getString(Preferences.PREF_GENERAL_STATUSMSG, "");
            }
            if (prefs.getBoolean(Preferences.PREF_GENERAL_GENDER, false)) {
                statusmode |= 256;
            }
            getClient().doChangeStatus(statusmode, statusmsg);
        }
        int mf_volume = prefs.getInt(Preferences.PREF_SOUNDSYSTEM_MEDIAFILE_VOLUME, 50);
        int mf_volume2 = Utils.refVolume(mf_volume);
        for (User u : getService().getUsers().values()) {
            getClient().setUserVolume(u.nUserID, 4, mf_volume2);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getDelegate().onPostCreate(savedInstanceState);
        getDelegate().getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return GeneralPreferenceFragment.class.getName().equals(fragmentName) || BackgroundMgmtPreferenceFragment.class.getName().equals(fragmentName) || SoundEventsPreferenceFragment.class.getName().equals(fragmentName) || ConnectionPreferenceFragment.class.getName().equals(fragmentName) || ServerListPreferenceFragment.class.getName().equals(fragmentName) || TtsPreferenceFragment.class.getName().equals(fragmentName) || SoundSystemPreferenceFragment.class.getName().equals(fragmentName) || AntiSpamPreferenceFragment.class.getName().equals(fragmentName) || AboutPreferenceFragment.class.getName().equals(fragmentName) || SoundPacksPreferenceFragment.class.getName().equals(fragmentName) || RecordingPreferenceFragment.class.getName().equals(fragmentName) || DisplayPreferenceFragment.class.getName().equals(fragmentName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 16908332) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode != 2 || resultCode != -1) && requestCode == 2001) {
            if (resultCode == -1 && data != null) {
                TeamTalkService service = getService();
                if (service != null) {
                    service.setMediaProjectionData(resultCode, data);
                }
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                String source = pendingInputSource != null ? pendingInputSource : "internal";
                prefs.edit().putString(Preferences.PREF_SOUNDSYSTEM_INPUT_SOURCE, source).apply();
                pendingInputSource = null;
                recreate();
                return;
            }
            Toast.makeText(this, "Screen capture permission denied", 0).show();
        }
    }

    @Override
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

        public static boolean lambda$static$0(Preference preference, Object value) {
        String stringValue = value.toString();
        if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);
            preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
            return true;
        }
        if (preference instanceof RingtonePreference) {
            if (!TextUtils.isEmpty(stringValue)) {
                Ringtone ringtone = RingtoneManager.getRingtone(preference.getContext(), Uri.parse(stringValue));
                if (ringtone == null) {
                    preference.setSummary((CharSequence) null);
                    return true;
                }
                String name = ringtone.getTitle(preference.getContext());
                preference.setSummary(name);
                return true;
            }
            return true;
        }
        if (preference instanceof EditTextPreference) {
            if (Preferences.PREF_GENERAL_CLIENTNAME.equals(preference.getKey())) {
                preference.setSummary(TextUtils.isEmpty(stringValue) ? AppInfo.APPNAME_SHORT : stringValue);
                return true;
            }
            if (Preferences.PREF_GENERAL_CLIENTVERSION.equals(preference.getKey())) {
                preference.setSummary(TextUtils.isEmpty(stringValue) ? preference.getContext().getString(R.string.pref_summary_clientversion_default) : stringValue);
                return true;
            }
        }
        preference.setSummary(stringValue);
        return true;
    }

    public static void bindPreferenceSummaryToValue(Preference preference) {
        if (preference == null) return;
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
    }

    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            PreferencesActivity.bindPreferenceSummaryToValue(findPreference(Preferences.PREF_GENERAL_NICKNAME));
            PreferencesActivity.bindPreferenceSummaryToValue(findPreference(Preferences.PREF_GENERAL_STATUSMSG));
            PreferencesActivity.bindPreferenceSummaryToValue(findPreference(Preferences.PREF_GENERAL_CLIENTNAME));
            PreferencesActivity.bindPreferenceSummaryToValue(findPreference(Preferences.PREF_GENERAL_CLIENTVERSION));

            Preference fileMgrPref = findPreference(Preferences.PREF_GENERAL_DEFAULT_FILE_MANAGER);
            if (fileMgrPref != null) {
                PreferencesActivity.bindPreferenceSummaryToValue(fileMgrPref);
            }

            Preference checkUpdatePref = findPreference("check_updates");
            if (checkUpdatePref != null) {
                checkUpdatePref.setOnPreferenceClickListener(pref -> {
                    AppUpdateManager.checkUpdate(getActivity(), true);
                    return true;
                });
            }

            Preference bearwareLogin = findPreference(Preferences.PREF_GENERAL_BEARWARE_CHECKED);
            if (bearwareLogin != null) {
                bearwareLogin.setOnPreferenceChangeListener((preference, o) -> {
                    Intent edit = new Intent(getActivity(), (Class<?>) WebLoginActivity.class);
                    getActivity().startActivityForResult(edit, 2);
                    return true;
                });
            }
        }

            @Override
            public void onResume() {
                super.onResume();
                if (getActivity() == null) return;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
                String username = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, "");
                CheckBoxPreference preference = (CheckBoxPreference) findPreference(Preferences.PREF_GENERAL_BEARWARE_CHECKED);
                if (preference != null) {
                    preference.setChecked(username.length() > 0);
                }
            }
        }

        public static class ServerListPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_serverlist);
        }

        @Override
        public void onResume() {
            super.onResume();
        }
    }

        public static class SoundPacksPreferenceFragment extends PreferenceFragment {
        private static final int REQUEST_CUSTOM_FILE_PICKER = 101;
        private static final int REQUEST_PICK_SOUND = 100;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_soundpacks);
            Preference packPref = findPreference("sound_pack_preference");
            if (packPref instanceof ListPreference) {
                PreferencesActivity.bindPreferenceSummaryToValue(packPref);
            }
            Preference importPref = findPreference("import_sound_pack");
            if (importPref != null) {
                importPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() { 
                    @Override
                    public final boolean onPreferenceClick(Preference preference) {
                        boolean lambda$onCreate$0;
                        lambda$onCreate$0 = PreferencesActivity.SoundPacksPreferenceFragment.this.lambda$onCreate$0(preference);
                        return lambda$onCreate$0;
                    }
                });
            }
        }

                public boolean lambda$onCreate$0(Preference pref) {
            Utils.showFilePicker((Fragment) this, 100, 101, false, "audio/*");
            return true;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == 100) {
                getActivity();
                if (resultCode == -1 && data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        importSoundFile(uri);
                        return;
                    }
                    return;
                }
            }
            if (requestCode == 101) {
                getActivity();
                if (resultCode == -1 && data != null) {
                    String path = data.getStringExtra(CustomFilePickerActivity.EXTRA_FILE_PATH);
                    if (path != null) {
                        importSoundFile(Uri.fromFile(new File(path)));
                        return;
                    }
                    return;
                }
            }
            super.onActivityResult(requestCode, resultCode, data);
        }

        private void importSoundFile(final Uri uri) {
            final Context context = getActivity();
            if (context == null) {
                return;
            }
            final String[] eventKeys = {"serverlost", "on", "off", "user_message", "channel_message", "channel_message_sent", "broadcast_message", "fileupdate", "voiceact_enable", "voiceact_disable", "voiceact_on", "voiceact_off", "intercept", "interceptend", "txqueue_start", "txqueue_stop", "user_join", "user_left", "logged_on", "logged_off"};
            final String[] eventLabels = {"Server lost", "Voice TX on", "Voice TX off", "Private message", "Channel message", "Channel message sent", "Broadcast message", "Files updated", "Voice activation enable", "Voice activation disable", "Voice act triggered", "Voice act stopped", "Intercept start", "Intercept end", "TX queue start", "TX queue stop", "User joined", "User left", "User logged in", "User logged off"};
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Assign sound to event");
            builder.setItems(eventLabels, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    PreferencesActivity.SoundPacksPreferenceFragment.lambda$importSoundFile$1(eventKeys, context, uri, eventLabels, dialogInterface, i);
                }
            });
            builder.setNegativeButton(android.R.string.cancel, (DialogInterface.OnClickListener) null);
            builder.show();
        }

                public static void lambda$importSoundFile$1(String[] eventKeys, Context context, Uri uri, String[] eventLabels, DialogInterface dialog, int which) {
            String fileName = eventKeys[which] + ".ogg";
            File soundsDir = new File(context.getFilesDir(), "sounds");
            soundsDir.mkdirs();
            File destFile = new File(soundsDir, fileName);
            try {
                InputStream in = context.getContentResolver().openInputStream(uri);
                try {
                    FileOutputStream out = new FileOutputStream(destFile);
                    try {
                        byte[] buf = new byte[8192];
                        while (true) {
                            int len = in.read(buf);
                            if (len <= 0) {
                                break;
                            } else {
                                out.write(buf, 0, len);
                            }
                        }
                        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
                        editor.putString("sound_pack_preference", "custom");
                        editor.putString("custom_sound_" + eventKeys[which], destFile.getAbsolutePath());
                        editor.apply();
                        Toast.makeText(context, "Sound imported for " + eventLabels[which], 0).show();
                        out.close();
                        if (in != null) {
                            in.close();
                        }
                    } finally {
                    }
                } finally {
                }
            } catch (IOException e) {
                Toast.makeText(context, "Failed to import sound: " + e.getMessage(), 1).show();
            }
        }
    }

        public static class SoundEventsPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_soundevents);
        }
    }

        public static class ConnectionPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_connection);
        }
    }

        public static class TtsPreferenceFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Preference masterToggle = findPreference("pref_master_tts_toggle");
            if (masterToggle != null && !"pref_master_tts_toggle".equals(key)) {
                PreferencesActivity.updateMasterToggleText(masterToggle, getPreferenceScreen());
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_tts);
            final Preference masterToggle = findPreference("pref_master_tts_toggle");
            if (masterToggle != null) {
                PreferencesActivity.updateMasterToggleText(masterToggle, getPreferenceScreen());
                masterToggle.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() { 
                    @Override
                    public final boolean onPreferenceClick(Preference preference) {
                        boolean lambda$onCreate$0;
                        lambda$onCreate$0 = PreferencesActivity.TtsPreferenceFragment.this.lambda$onCreate$0(masterToggle, preference);
                        return lambda$onCreate$0;
                    }
                });
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
            TTSWrapper tts = new TTSWrapper(getActivity().getBaseContext(), prefs.getString("pref_speech_engine", TTSWrapper.defaultEngineName));
            List<TextToSpeech.EngineInfo> engines = tts.getEngines();
            ListPreference enginePrefs = (ListPreference) findPreference("pref_speech_engine");
            ArrayList<String> entries = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();
            for (TextToSpeech.EngineInfo info : engines) {
                entries.add(info.label);
                values.add(info.name);
            }
            enginePrefs.setEntries((CharSequence[]) entries.toArray(new CharSequence[engines.size()]));
            enginePrefs.setEntryValues((CharSequence[]) values.toArray(new CharSequence[engines.size()]));
            if (Build.VERSION.SDK_INT <= 26) {
                CheckBoxPreference mTtsPref = (CheckBoxPreference) findPreference("pref_a11y_volume");
                PreferenceCategory mTtsCat = (PreferenceCategory) findPreference("tts_def");
                mTtsCat.removePreference(mTtsPref);
            }
        }

                public boolean lambda$onCreate$0(Preference masterToggle, Preference preference) {
            boolean allChecked = PreferencesActivity.isAllChecked(getPreferenceScreen());
            PreferencesActivity.toggleAll(getPreferenceScreen(), !allChecked);
            PreferencesActivity.updateMasterToggleText(masterToggle, getPreferenceScreen());
            return true;
        }
    }

        public static class SoundSystemPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_soundsystem);
            Preference eqPref = findPreference("pref_open_equalizer");
            if (eqPref != null) {
                eqPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() { 
                    @Override
                    public final boolean onPreferenceClick(Preference preference) {
                        boolean lambda$onCreate$0;
                        lambda$onCreate$0 = PreferencesActivity.SoundSystemPreferenceFragment.this.lambda$onCreate$0(preference);
                        return lambda$onCreate$0;
                    }
                });
            }
            ListPreference inputSourcePref = (ListPreference) findPreference(Preferences.PREF_SOUNDSYSTEM_INPUT_SOURCE);
            if (inputSourcePref != null) {
                inputSourcePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() { 
                    @Override
                    public final boolean onPreferenceChange(Preference preference, Object obj) {
                        boolean lambda$onCreate$1;
                        lambda$onCreate$1 = PreferencesActivity.SoundSystemPreferenceFragment.this.lambda$onCreate$1(preference, obj);
                        return lambda$onCreate$1;
                    }
                });
            }
        }

                public boolean lambda$onCreate$0(Preference preference) {
            Intent intent = new Intent(getActivity(), (Class<?>) EqualizerActivity.class);
            startActivity(intent);
            return true;
        }

                public boolean lambda$onCreate$1(Preference preference, Object newValue) {
            String value = (String) newValue;
            if (("internal".equals(value) || "mixed".equals(value)) && !TeamTalkService.hasMediaProjectionData()) {
                PreferencesActivity.pendingInputSource = value;
                MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getActivity().getSystemService("media_projection");
                if (mediaProjectionManager != null) {
                    getActivity().startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 2001);
                    return false;
                }
                return false;
            }
            return true;
        }
    }

        public static class AboutPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_about);
        }
    }

        public static class AntiSpamPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_antispam);
        }
    }

        public static class RecordingPreferenceFragment extends PreferenceFragment {
        private static final int REQUEST_FOLDER_PICKER = 3001;
        private static final int REQUEST_FOLDER_SAF = 3002;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_recording);
            ListPreference formatPref = (ListPreference) findPreference(Preferences.PREF_RECORDING_FORMAT);
            final Preference bitratePref = findPreference(Preferences.PREF_RECORDING_MP3_BITRATE);
            if (formatPref != null && bitratePref != null) {
                bitratePref.setEnabled("mp3".equals(formatPref.getValue()));
                formatPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() { 
                    @Override
                    public final boolean onPreferenceChange(Preference preference, Object obj) {
                        return PreferencesActivity.RecordingPreferenceFragment.lambda$onCreate$0(bitratePref, preference, obj);
                    }
                });
            }
            Preference pathPref = findPreference(Preferences.PREF_RECORDING_PATH);
            if (pathPref != null) {
                updatePathSummary(pathPref);
                pathPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() { 
                    @Override
                    public final boolean onPreferenceClick(Preference preference) {
                        boolean lambda$onCreate$1;
                        lambda$onCreate$1 = PreferencesActivity.RecordingPreferenceFragment.this.lambda$onCreate$1(preference);
                        return lambda$onCreate$1;
                    }
                });
            }
        }

                public static boolean lambda$onCreate$0(Preference bitratePref, Preference preference, Object newValue) {
            bitratePref.setEnabled("mp3".equals(newValue));
            return true;
        }

                public boolean lambda$onCreate$1(Preference preference) {
            showFolderPickerDialog();
            return true;
        }

        private void updatePathSummary(Preference pathPref) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String path = prefs.getString(Preferences.PREF_RECORDING_PATH, "");
            if (path.isEmpty()) {
                File defaultDir = Utils.getRecordingsDirectory(getActivity());
                pathPref.setSummary(getString(R.string.recording_current_path, new Object[]{defaultDir.getAbsolutePath()}));
            } else {
                pathPref.setSummary(getString(R.string.recording_current_path, new Object[]{path}));
            }
        }

        private void showFolderPickerDialog() {
            Utils.showFilePicker((Fragment) this, 3002, 3001, true, (String) null);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            Uri treeUri;
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode != -1 || data == null) {
                return;
            }
            String folderPath = null;
            if (requestCode == 3001) {
                folderPath = data.getStringExtra(CustomFilePickerActivity.EXTRA_FILE_PATH);
            } else if (requestCode == 3002 && (treeUri = data.getData()) != null && (folderPath = resolveTreeUriToPath(treeUri)) == null) {
                folderPath = treeUri.toString();
            }
            if (folderPath != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                prefs.edit().putString(Preferences.PREF_RECORDING_PATH, folderPath).apply();
                Preference pathPref = findPreference(Preferences.PREF_RECORDING_PATH);
                if (pathPref != null) {
                    updatePathSummary(pathPref);
                }
            }
        }

        private String resolveTreeUriToPath(Uri treeUri) {
            try {
                String docId = DocumentsContract.getTreeDocumentId(treeUri);
                if (docId != null && docId.contains(":")) {
                    String[] parts = docId.split(":");
                    String type = parts[0];
                    String relativePath = parts.length > 1 ? parts[1] : "";
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relativePath;
                    }
                    return null;
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
    }

        public static class BackgroundMgmtPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_background_mgmt);
            PreferencesActivity.bindPreferenceSummaryToValue(findPreference(Preferences.PREF_BG_MGMT_DISPLAY_TYPE));
            CheckBoxPreference bgMgmtPref = (CheckBoxPreference) findPreference(Preferences.PREF_BG_MGMT_ENABLED);
            if (bgMgmtPref != null) {
                bgMgmtPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() { 
                    @Override
                    public final boolean onPreferenceChange(Preference preference, Object obj) {
                        boolean lambda$onCreate$0;
                        lambda$onCreate$0 = PreferencesActivity.BackgroundMgmtPreferenceFragment.this.lambda$onCreate$0(preference, obj);
                        return lambda$onCreate$0;
                    }
                });
            }
        }

                public boolean lambda$onCreate$0(Preference preference, Object newValue) {
            boolean enabled = ((Boolean) newValue).booleanValue();
            String type = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Preferences.PREF_BG_MGMT_DISPLAY_TYPE, "window");
            if (!enabled || !"window".equals(type) || Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(getActivity())) {
                return true;
            }
            Toast.makeText(getActivity(), R.string.bg_mgmt_permission_required, 1).show();
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getActivity().getPackageName()));
            startActivity(intent);
            return false;
        }

        @Override
        public void onResume() {
            super.onResume();
            CheckBoxPreference bgMgmtPref = (CheckBoxPreference) findPreference(Preferences.PREF_BG_MGMT_ENABLED);
            if (bgMgmtPref != null) {
                String type = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Preferences.PREF_BG_MGMT_DISPLAY_TYPE, "window");
                if (Build.VERSION.SDK_INT >= 23 && "window".equals(type) && !Settings.canDrawOverlays(getActivity())) {
                    bgMgmtPref.setChecked(false);
                }
            }
        }
    }

        public static class DisplayPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_display);
            Preference themePref = findPreference(ThemeHelper.THEME_PREF_KEY);
            if (themePref != null) {
                themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    Activity activity = getActivity();
                    if (activity != null) {
                        PreferenceManager.getDefaultSharedPreferences(activity)
                                .edit()
                                .putString(ThemeHelper.THEME_PREF_KEY, String.valueOf(newValue))
                                .commit();
                        ThemeHelper.applyTheme(activity);
                        activity.recreate();
                    }
                    return true;
                });
            }
        }
    }

    private AppCompatDelegate getDelegate() {
        if (this.appCompatDelegate == null) {
            this.appCompatDelegate = AppCompatDelegate.create(this, (AppCompatCallback) null);
        }
        return this.appCompatDelegate;
    }

        public static boolean isAllChecked(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference p = group.getPreference(i);
            if (!"pref_master_tts_toggle".equals(p.getKey()) && !"pref_master_sound_toggle".equals(p.getKey())) {
                if (p instanceof CheckBoxPreference) {
                    if (!((CheckBoxPreference) p).isChecked()) {
                        return false;
                    }
                } else if ((p instanceof PreferenceGroup) && !isAllChecked((PreferenceGroup) p)) {
                    return false;
                }
            }
        }
        return true;
    }

        public static void toggleAll(PreferenceGroup group, boolean check) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference p = group.getPreference(i);
            if (!"pref_master_tts_toggle".equals(p.getKey()) && !"pref_master_sound_toggle".equals(p.getKey())) {
                if (p instanceof CheckBoxPreference) {
                    ((CheckBoxPreference) p).setChecked(check);
                } else if (p instanceof PreferenceGroup) {
                    toggleAll((PreferenceGroup) p, check);
                }
            }
        }
    }

        public static void updateMasterToggleText(Preference masterToggle, PreferenceGroup root) {
        if (masterToggle != null && root != null) {
            boolean allChecked = isAllChecked(root);
            masterToggle.setTitle(allChecked ? R.string.action_deselect_all : R.string.action_select_all);
        }
    }
}
