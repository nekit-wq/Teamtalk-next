package org.nekit.ttproplus.gui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import java.util.ArrayList;
import java.util.List;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.data.Preferences;
import org.nekit.ttproplus.gui.WelcomeActivity;

public class WelcomeActivity extends AppCompatActivity {
    private static final int PAGE_COUNT = 4;
    private static final int PAGE_GREETING = 0;
    private static final int PAGE_IMPORT = 3;
    private static final int PAGE_PERMISSIONS = 1;
    private static final int PAGE_SETTINGS = 2;
    private WelcomePagerAdapter adapter;
    private LinearLayout dotsContainer;
    private Button nextBtn;
    private SharedPreferences prefs;
    private Button skipBtn;
    private ViewPager viewPager;

        @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        this.viewPager = (ViewPager) findViewById(R.id.welcome_pager);
        this.dotsContainer = (LinearLayout) findViewById(R.id.welcome_dots_container);
        this.skipBtn = (Button) findViewById(R.id.welcome_skip_btn);
        this.nextBtn = (Button) findViewById(R.id.welcome_next_btn);
        this.adapter = new WelcomePagerAdapter(getSupportFragmentManager());
        this.viewPager.setAdapter(this.adapter);
        setupDots(0);
        this.viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() { 
            @Override
            public void onPageSelected(int position) {
                WelcomeActivity.this.setupDots(position);
                WelcomeActivity welcomeActivity = WelcomeActivity.this;
                if (position == 3) {
                    welcomeActivity.nextBtn.setText(R.string.welcome_finish);
                } else {
                    welcomeActivity.nextBtn.setText(R.string.welcome_next);
                }
            }
        });
        this.skipBtn.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                WelcomeActivity.this.lambda$onCreate$0(view);
            }
        });
        this.nextBtn.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                WelcomeActivity.this.lambda$onCreate$1(view);
            }
        });
    }

        public void lambda$onCreate$0(View v) {
        finishWelcome();
    }

        public void lambda$onCreate$1(View v) {
        int current = this.viewPager.getCurrentItem();
        if (current == 2) {
            saveBasicSettings();
        }
        if (current < 3) {
            this.viewPager.setCurrentItem(current + 1);
        } else {
            saveBasicSettings();
            finishWelcome();
        }
    }

        public void setupDots(int currentPosition) {
        this.dotsContainer.removeAllViews();
        for (int i = 0; i < 4; i++) {
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(14.0f);
            dot.setPadding(8, 0, 8, 0);
            if (i == currentPosition) {
                dot.setTextColor(getResources().getColor(android.R.color.white));
            } else {
                dot.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
            this.dotsContainer.addView(dot);
        }
    }

    private void saveBasicSettings() {
        SettingsFragment settingsFragment = this.adapter.getSettingsFragment();
        if (settingsFragment != null) {
            settingsFragment.saveSettings(this.prefs);
        }
    }

    private void finishWelcome() {
        this.prefs.edit().putBoolean(Preferences.PREF_WELCOME_SHOWN, true).apply();
        finish();
    }

    @Override
    public void onBackPressed() {
        int current = this.viewPager.getCurrentItem();
        if (current > 0) {
            this.viewPager.setCurrentItem(current - 1);
        } else {
            finishWelcome();
        }
    }

        public static class GreetingFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_welcome_greeting, container, false);
        }
    }

        public static class PermissionsFragment extends Fragment {
        private static final int PERM_REQUEST_CODE = 1001;
        private Button batteryBtn;
        private TextView batteryStatusText;
        private Button requestBtn;
        private TextView statusText;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_welcome_permissions, container, false);
            this.requestBtn = (Button) view.findViewById(R.id.welcome_request_permissions_btn);
            this.statusText = (TextView) view.findViewById(R.id.welcome_permissions_status);
            this.batteryBtn = (Button) view.findViewById(R.id.welcome_request_battery_btn);
            this.batteryStatusText = (TextView) view.findViewById(R.id.welcome_battery_status);
            this.requestBtn.setOnClickListener(new View.OnClickListener() { 
                @Override
                public final void onClick(View view2) {
                    WelcomeActivity.PermissionsFragment.this.lambda$onCreateView$0(view2);
                }
            });
            this.batteryBtn.setOnClickListener(new View.OnClickListener() { 
                @Override
                public final void onClick(View view2) {
                    WelcomeActivity.PermissionsFragment.this.lambda$onCreateView$1(view2);
                }
            });
            updateStatus();
            return view;
        }

                public void lambda$onCreateView$0(View v) {
            requestAllNeededPermissions();
        }

                public void lambda$onCreateView$1(View v) {
            requestIgnoreBatteryOptimizations();
        }

        @Override
        public void onResume() {
            super.onResume();
            updateStatus();
        }

        public void updateStatus() {
            if (getContext() == null) {
                return;
            }
            if (this.statusText != null && this.requestBtn != null) {
                List<String> needed = getNeededPermissions();
                boolean isEmpty = needed.isEmpty();
                TextView textView = this.statusText;
                if (isEmpty) {
                    textView.setText(R.string.welcome_permissions_granted);
                    this.requestBtn.setVisibility(8);
                } else {
                    textView.setText(R.string.welcome_permissions_pending);
                    this.requestBtn.setVisibility(0);
                }
            }
            if (this.batteryStatusText != null && this.batteryBtn != null) {
                if (Build.VERSION.SDK_INT >= 23) {
                    PowerManager pm = (PowerManager) getContext().getSystemService("power");
                    boolean isIgnoring = pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
                    TextView textView2 = this.batteryStatusText;
                    if (isIgnoring) {
                        textView2.setText(R.string.welcome_battery_granted);
                        this.batteryBtn.setVisibility(8);
                        return;
                    } else {
                        textView2.setText(R.string.welcome_battery_pending);
                        this.batteryBtn.setVisibility(0);
                        return;
                    }
                }
                this.batteryStatusText.setText(R.string.welcome_battery_granted);
                this.batteryBtn.setVisibility(8);
            }
        }

        public void requestAllNeededPermissions() {
            if (getContext() == null) {
                return;
            }
            List<String> needed = getNeededPermissions();
            if (!needed.isEmpty() && getActivity() != null) {
                requestPermissions((String[]) needed.toArray(new String[0]), 1001);
            } else {
                updateStatus();
            }
        }

        public void requestIgnoreBatteryOptimizations() {
            if (getContext() == null || Build.VERSION.SDK_INT < 23) {
                return;
            }
            String packageName = getContext().getPackageName();
            Intent intent = new Intent();
            intent.setAction("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
            intent.setData(Uri.parse("package:" + packageName));
            try {
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent fallback = new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS");
                    startActivity(fallback);
                } catch (Exception e2) {
                    try {
                        Intent appDetails = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                        appDetails.setData(Uri.parse("package:" + packageName));
                        startActivity(appDetails);
                    } catch (Exception e3) {
                    }
                }
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            updateStatus();
        }

        private List<String> getNeededPermissions() {
            List<String> needed = new ArrayList<>();
            if (getContext() == null) {
                return needed;
            }
            String[] possiblePermissions = {"android.permission.RECORD_AUDIO", "android.permission.READ_PHONE_STATE", Build.VERSION.SDK_INT >= 31 ? "android.permission.BLUETOOTH_CONNECT" : "android.permission.BLUETOOTH", "android.permission.POST_NOTIFICATIONS", "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
            for (String p : possiblePermissions) {
                if (p != null && !p.isEmpty() && ((Build.VERSION.SDK_INT >= 33 || (!p.equals("android.permission.READ_MEDIA_AUDIO") && !p.equals("android.permission.READ_MEDIA_IMAGES") && !p.equals("android.permission.READ_MEDIA_VIDEO") && !p.equals("android.permission.POST_NOTIFICATIONS"))) && ((Build.VERSION.SDK_INT < 33 || (!p.equals("android.permission.READ_EXTERNAL_STORAGE") && !p.equals("android.permission.WRITE_EXTERNAL_STORAGE"))) && ((Build.VERSION.SDK_INT < 29 || !p.equals("android.permission.WRITE_EXTERNAL_STORAGE")) && ((Build.VERSION.SDK_INT >= 31 || !p.equals("android.permission.BLUETOOTH_CONNECT")) && ContextCompat.checkSelfPermission(getContext(), p) != 0 && !needed.contains(p)))))) {
                    needed.add(p);
                }
            }
            return needed;
        }
    }

        public static class SettingsFragment extends Fragment {
        private EditText clientNameEdit;
        private CheckBox genderFemale;
        private EditText nicknameEdit;
        private EditText statusMsgEdit;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_welcome_settings, container, false);
            this.nicknameEdit = (EditText) view.findViewById(R.id.welcome_nickname);
            this.statusMsgEdit = (EditText) view.findViewById(R.id.welcome_status_msg);
            this.clientNameEdit = (EditText) view.findViewById(R.id.welcome_client_name);
            this.genderFemale = (CheckBox) view.findViewById(R.id.welcome_gender_female);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            this.nicknameEdit.setText(prefs.getString(Preferences.PREF_GENERAL_NICKNAME, ""));
            this.statusMsgEdit.setText(prefs.getString(Preferences.PREF_GENERAL_STATUSMSG, ""));
            this.clientNameEdit.setText(prefs.getString(Preferences.PREF_GENERAL_CLIENTNAME, ""));
            this.genderFemale.setChecked(prefs.getBoolean(Preferences.PREF_GENERAL_GENDER, false));
            return view;
        }

        public void saveSettings(SharedPreferences prefs) {
            if (this.nicknameEdit == null) {
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();
            String nickname = this.nicknameEdit.getText().toString().trim();
            editor.putString(Preferences.PREF_GENERAL_NICKNAME, nickname);
            String statusMsg = this.statusMsgEdit.getText().toString().trim();
            editor.putString(Preferences.PREF_GENERAL_STATUSMSG, statusMsg);
            String clientName = this.clientNameEdit.getText().toString().trim();
            editor.putString(Preferences.PREF_GENERAL_CLIENTNAME, clientName);
            editor.putBoolean(Preferences.PREF_GENERAL_GENDER, this.genderFemale.isChecked());
            editor.apply();
        }
    }

        public static class ImportFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_welcome_import, container, false);
        }
    }

            public class WelcomePagerAdapter extends FragmentPagerAdapter {
        private PermissionsFragment permissionsFragment;
        private SettingsFragment settingsFragment;

        public WelcomePagerAdapter(FragmentManager fm) {
            super(fm, 1);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 1:
                    this.permissionsFragment = new PermissionsFragment();
                    return this.permissionsFragment;
                case 2:
                    this.settingsFragment = new SettingsFragment();
                    return this.settingsFragment;
                case 3:
                    return new ImportFragment();
                default:
                    return new GreetingFragment();
            }
        }

        @Override
        public int getCount() {
            return 4;
        }

        public SettingsFragment getSettingsFragment() {
            return this.settingsFragment;
        }

        public PermissionsFragment getPermissionsFragment() {
            return this.permissionsFragment;
        }
    }
}
