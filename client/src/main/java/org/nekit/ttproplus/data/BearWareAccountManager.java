package org.nekit.ttproplus.data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.nekit.ttproplus.R;
import org.nekit.ttproplus.gui.WebLoginActivity;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BearWareAccountManager {
    private static volatile BearWareAccountManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final List<BearWareAccount> accounts = new ArrayList<>();

    public interface OnAccountSelectedListener {
        void onAccountSelected(BearWareAccount account);
    }

    public static BearWareAccountManager getInstance(Context ctx) {
        if (instance == null) {
            synchronized (BearWareAccountManager.class) {
                if (instance == null) {
                    instance = new BearWareAccountManager(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private BearWareAccountManager(Context ctx) {
        this.context = ctx;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        loadAccounts();
    }

    private synchronized void loadAccounts() {
        accounts.clear();
        String json = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_ACCOUNTS_JSON, null);
        if (json != null && !json.trim().isEmpty()) {
            try {
                Type listType = new TypeToken<ArrayList<BearWareAccount>>() {}.getType();
                List<BearWareAccount> loaded = gson.fromJson(json, listType);
                if (loaded != null) {
                    accounts.addAll(loaded);
                }
            } catch (Exception ignored) {
            }
        }

        // Migrate legacy single account if needed
        String legacyUser = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, "");
        String legacyToken = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, "");
        if (!legacyUser.isEmpty() && !legacyToken.isEmpty()) {
            boolean exists = false;
            for (BearWareAccount acc : accounts) {
                if (acc.username.equalsIgnoreCase(legacyUser)) {
                    exists = true;
                    if (acc.token.isEmpty()) {
                        acc.token = legacyToken;
                    }
                    break;
                }
            }
            if (!exists) {
                BearWareAccount migrated = new BearWareAccount(legacyUser, legacyToken, legacyUser);
                accounts.add(0, migrated);
                saveAccounts();
            }
        }
    }

    private synchronized void saveAccounts() {
        String json = gson.toJson(accounts);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Preferences.PREF_GENERAL_BEARWARE_ACCOUNTS_JSON, json);

        BearWareAccount active = getActiveAccount();
        if (active != null) {
            editor.putString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, active.username);
            editor.putString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, active.token);
            editor.putString(Preferences.PREF_GENERAL_BEARWARE_ACTIVE_USERNAME, active.username);
        } else if (accounts.isEmpty()) {
            editor.putString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, "");
            editor.putString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, "");
            editor.putString(Preferences.PREF_GENERAL_BEARWARE_ACTIVE_USERNAME, "");
        }
        editor.apply();
    }

    public synchronized List<BearWareAccount> getAccounts() {
        return new ArrayList<>(accounts);
    }

    public synchronized boolean hasAccounts() {
        return !accounts.isEmpty();
    }

    public synchronized BearWareAccount getAccount(String username) {
        if (username == null || username.trim().isEmpty()) return null;
        for (BearWareAccount acc : accounts) {
            if (acc.username.equalsIgnoreCase(username)) {
                return acc;
            }
        }
        return null;
    }

    public synchronized BearWareAccount getActiveAccount() {
        if (accounts.isEmpty()) return null;
        String activeUser = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_ACTIVE_USERNAME, "");
        for (BearWareAccount acc : accounts) {
            if (acc.username.equalsIgnoreCase(activeUser)) {
                return acc;
            }
        }
        return accounts.get(0);
    }

    public synchronized void setActiveAccount(String username) {
        if (username == null) return;
        for (BearWareAccount acc : accounts) {
            if (acc.username.equalsIgnoreCase(username)) {
                acc.lastUsedTimestamp = System.currentTimeMillis();
                prefs.edit()
                        .putString(Preferences.PREF_GENERAL_BEARWARE_ACTIVE_USERNAME, acc.username)
                        .putString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, acc.username)
                        .putString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, acc.token)
                        .apply();
                saveAccounts();
                return;
            }
        }
    }

    public synchronized void addOrUpdateAccount(String username, String token, String nickname) {
        if (username == null || username.trim().isEmpty()) return;
        username = username.trim();
        token = token != null ? token.trim() : "";
        nickname = nickname != null ? nickname.trim() : username;

        for (BearWareAccount acc : accounts) {
            if (acc.username.equalsIgnoreCase(username)) {
                acc.token = token;
                if (!nickname.isEmpty()) {
                    acc.nickname = nickname;
                }
                acc.lastUsedTimestamp = System.currentTimeMillis();
                setActiveAccount(acc.username);
                saveAccounts();
                return;
            }
        }

        BearWareAccount newAcc = new BearWareAccount(username, token, nickname);
        accounts.add(0, newAcc);
        setActiveAccount(newAcc.username);
        saveAccounts();
    }

    public synchronized void removeAccount(String username) {
        if (username == null) return;
        Iterator<BearWareAccount> it = accounts.iterator();
        while (it.hasNext()) {
            BearWareAccount acc = it.next();
            if (acc.username.equalsIgnoreCase(username)) {
                it.remove();
                break;
            }
        }
        saveAccounts();
    }

    /**
     * Shows a Material You selection dialog if multiple accounts exist,
     * or prompts to login if none exist, or allows selecting the desired account.
     */
    public void selectAccountAndExecute(Activity activity, String title, OnAccountSelectedListener listener) {
        if (activity == null || activity.isFinishing()) return;

        List<BearWareAccount> list = getAccounts();
        if (list.isEmpty()) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_title_bearware)
                    .setMessage(R.string.err_publish_server_login)
                    .setPositiveButton(R.string.action_login, (dialog, which) -> {
                        Intent intent = new Intent(activity, WebLoginActivity.class);
                        activity.startActivity(intent);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        if (list.size() == 1) {
            BearWareAccount single = list.get(0);
            setActiveAccount(single.username);
            listener.onAccountSelected(single);
            return;
        }

        BearWareAccount active = getActiveAccount();
        CharSequence[] items = new CharSequence[list.size()];
        int selectedIndex = 0;
        for (int i = 0; i < list.size(); i++) {
            BearWareAccount acc = list.get(i);
            String name = acc.getDisplayName();
            if (active != null && acc.username.equalsIgnoreCase(active.username)) {
                selectedIndex = i;
                name += " (" + activity.getString(R.string.text_active_account) + ")";
            }
            items[i] = name;
        }

        final int[] chosen = new int[]{selectedIndex};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title != null ? title : activity.getString(R.string.title_select_bearware_account))
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> chosen[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (chosen[0] >= 0 && chosen[0] < list.size()) {
                        BearWareAccount selected = list.get(chosen[0]);
                        setActiveAccount(selected.username);
                        listener.onAccountSelected(selected);
                    }
                })
                .setNeutralButton(R.string.action_manage_accounts, (dialog, which) -> {
                    Intent intent = new Intent(activity, WebLoginActivity.class);
                    activity.startActivity(intent);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
