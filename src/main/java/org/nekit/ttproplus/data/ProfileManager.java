package org.nekit.ttproplus.data;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nekit.ttproplus.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfileManager {

    private static final String PREF_GLOBAL_ACTIVE_PROFILE = "global_active_profile_id";
    private static final String PREF_GLOBAL_PROFILES_JSON = "global_profiles_list_json";
    private static final String PREF_GLOBAL_OPEN_TABS_JSON = "global_open_tabs_json";
    public static final String PREF_SHOW_PROFILE_TABS = "pref_show_profile_tabs";
    public static final String DEFAULT_PROFILE_ID = "default";
    private static final String SERVERLIST_NAME = "serverlist";

    public interface ProfileCallback {
        void onProfileSelected(Profile profile);
    }

    public static class Profile {
        public String id;
        public String name;
        public long createdAt;

        public Profile(String id, String name, long createdAt) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
        }

        public String getPrefsName() {
            if (DEFAULT_PROFILE_ID.equals(id)) {
                return null; // uses default shared preferences
            }
            return "profile_prefs_" + id;
        }

        public String getServerListName() {
            if (DEFAULT_PROFILE_ID.equals(id)) {
                return SERVERLIST_NAME;
            }
            return SERVERLIST_NAME + "_" + id;
        }
    }

    public static List<Profile> getProfiles(Context context) {
        List<Profile> list = new ArrayList<>();
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String raw = globalPrefs.getString(PREF_GLOBAL_PROFILES_JSON, null);

        if (raw == null || raw.trim().isEmpty()) {
            Profile def = new Profile(DEFAULT_PROFILE_ID, context.getString(R.string.profile_default_name), System.currentTimeMillis());
            list.add(def);
            saveProfilesList(context, list);
            return list;
        }

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new Profile(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.optLong("created_at", System.currentTimeMillis())
                ));
            }
        } catch (Exception e) {
            list.clear();
            list.add(new Profile(DEFAULT_PROFILE_ID, context.getString(R.string.profile_default_name), System.currentTimeMillis()));
        }

        if (list.isEmpty()) {
            list.add(new Profile(DEFAULT_PROFILE_ID, context.getString(R.string.profile_default_name), System.currentTimeMillis()));
            saveProfilesList(context, list);
        }
        return list;
    }

    private static void saveProfilesList(Context context, List<Profile> profiles) {
        try {
            JSONArray arr = new JSONArray();
            for (Profile p : profiles) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.id);
                obj.put("name", p.name);
                obj.put("created_at", p.createdAt);
                arr.put(obj);
            }
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(PREF_GLOBAL_PROFILES_JSON, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static String getActiveProfileId(Context context) {
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return globalPrefs.getString(PREF_GLOBAL_ACTIVE_PROFILE, DEFAULT_PROFILE_ID);
    }

    public static Profile getActiveProfile(Context context) {
        String activeId = getActiveProfileId(context);
        List<Profile> list = getProfiles(context);
        for (Profile p : list) {
            if (p.id.equals(activeId)) {
                return p;
            }
        }
        return list.get(0);
    }

    public static SharedPreferences getProfilePreferences(Context context) {
        Profile active = getActiveProfile(context);
        String pName = active.getPrefsName();
        if (pName == null) {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
        return context.getSharedPreferences(pName, Context.MODE_PRIVATE);
    }

    public static void setActiveProfile(Context context, String profileId) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_GLOBAL_ACTIVE_PROFILE, profileId)
                .commit();

        openNewTab(context, profileId);
    }

    public static List<String> getOpenTabIds(Context context) {
        List<String> openTabs = new ArrayList<>();
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String raw = globalPrefs.getString(PREF_GLOBAL_OPEN_TABS_JSON, null);

        List<Profile> allProfiles = getProfiles(context);
        Map<String, Boolean> existingIds = new HashMap<>();
        for (Profile p : allProfiles) existingIds.put(p.id, true);

        if (raw != null && !raw.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.getString(i);
                    if (existingIds.containsKey(id) && !openTabs.contains(id)) {
                        openTabs.add(id);
                    }
                }
            } catch (Exception ignored) {}
        }

        String activeId = getActiveProfileId(context);
        if (!openTabs.contains(activeId)) {
            openTabs.add(0, activeId);
            saveOpenTabIds(context, openTabs);
        }

        if (openTabs.isEmpty()) {
            openTabs.add(DEFAULT_PROFILE_ID);
            saveOpenTabIds(context, openTabs);
        }

        return openTabs;
    }

    public static void saveOpenTabIds(Context context, List<String> openTabs) {
        try {
            JSONArray arr = new JSONArray();
            for (String id : openTabs) {
                arr.put(id);
            }
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(PREF_GLOBAL_OPEN_TABS_JSON, arr.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    public static void openNewTab(Context context, String profileId) {
        List<String> openTabs = getOpenTabIds(context);
        if (!openTabs.contains(profileId)) {
            openTabs.add(profileId);
            saveOpenTabIds(context, openTabs);
        }
    }

    public static void closeTab(Context context, String profileId, Runnable onTabClosed) {
        List<String> openTabs = getOpenTabIds(context);
        if (openTabs.size() <= 1) {
            return;
        }

        openTabs.remove(profileId);
        if (openTabs.isEmpty()) {
            openTabs.add(DEFAULT_PROFILE_ID);
        }

        saveOpenTabIds(context, openTabs);

        String activeId = getActiveProfileId(context);
        if (activeId.equals(profileId)) {
            setActiveProfile(context, openTabs.get(0));
        }

        if (onTabClosed != null) {
            onTabClosed.run();
        }
    }

    public static Profile createProfile(Context context, String name, boolean cloneCurrent) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Profile newProfile = new Profile(id, name, System.currentTimeMillis());

        if (cloneCurrent) {
            // Clone preferences
            SharedPreferences currentPrefs = getProfilePreferences(context);
            SharedPreferences targetPrefs = context.getSharedPreferences(newProfile.getPrefsName(), Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = targetPrefs.edit();
            for (Map.Entry<String, ?> entry : currentPrefs.getAll().entrySet()) {
                Object val = entry.getValue();
                String k = entry.getKey();
                if (val instanceof Boolean) edit.putBoolean(k, (Boolean) val);
                else if (val instanceof Integer) edit.putInt(k, (Integer) val);
                else if (val instanceof Long) edit.putLong(k, (Long) val);
                else if (val instanceof Float) edit.putFloat(k, (Float) val);
                else if (val instanceof String) edit.putString(k, (String) val);
            }
            edit.commit();

            // Clone server list
            Profile currentProfile = getActiveProfile(context);
            SharedPreferences currentSrvPrefs = context.getSharedPreferences(currentProfile.getServerListName(), Context.MODE_PRIVATE);
            SharedPreferences targetSrvPrefs = context.getSharedPreferences(newProfile.getServerListName(), Context.MODE_PRIVATE);
            SharedPreferences.Editor srvEdit = targetSrvPrefs.edit();
            for (Map.Entry<String, ?> entry : currentSrvPrefs.getAll().entrySet()) {
                Object val = entry.getValue();
                String k = entry.getKey();
                if (val instanceof Boolean) srvEdit.putBoolean(k, (Boolean) val);
                else if (val instanceof Integer) srvEdit.putInt(k, (Integer) val);
                else if (val instanceof Long) srvEdit.putLong(k, (Long) val);
                else if (val instanceof Float) srvEdit.putFloat(k, (Float) val);
                else if (val instanceof String) srvEdit.putString(k, (String) val);
            }
            srvEdit.commit();
        }

        List<Profile> list = getProfiles(context);
        list.add(newProfile);
        saveProfilesList(context, list);
        return newProfile;
    }

    public static boolean renameProfile(Context context, String profileId, String newName) {
        List<Profile> list = getProfiles(context);
        for (Profile p : list) {
            if (p.id.equals(profileId)) {
                p.name = newName;
                saveProfilesList(context, list);
                return true;
            }
        }
        return false;
    }

    public static boolean deleteProfile(Context context, String profileId) {
        if (DEFAULT_PROFILE_ID.equals(profileId)) {
            return false; // Cannot delete default profile
        }
        List<Profile> list = getProfiles(context);
        Profile target = null;
        for (Profile p : list) {
            if (p.id.equals(profileId)) {
                target = p;
                break;
            }
        }
        if (target == null) return false;

        list.remove(target);
        saveProfilesList(context, list);

        // Delete profile prefs file & server list
        context.getSharedPreferences(target.getPrefsName(), Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(target.getServerListName(), Context.MODE_PRIVATE).edit().clear().commit();

        List<String> openTabs = getOpenTabIds(context);
        openTabs.remove(profileId);
        if (openTabs.isEmpty()) openTabs.add(DEFAULT_PROFILE_ID);
        saveOpenTabIds(context, openTabs);

        if (getActiveProfileId(context).equals(profileId)) {
            setActiveProfile(context, openTabs.get(0));
        }
        return true;
    }

    public static void setupProfileTabsBar(final Activity activity, final Runnable onProfileChanged) {
        if (activity == null || activity.isFinishing()) return;

        final LinearLayout tabsContainer = activity.findViewById(R.id.profile_tabs_container);
        final HorizontalScrollView scrollView = activity.findViewById(R.id.profile_tabs_scroll);
        if (tabsContainer == null || scrollView == null) return;

        boolean showTabs = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(PREF_SHOW_PROFILE_TABS, true);
        if (!showTabs) {
            scrollView.setVisibility(View.GONE);
            return;
        }
        scrollView.setVisibility(View.VISIBLE);

        tabsContainer.removeAllViews();

        final List<String> openTabIds = getOpenTabIds(activity);
        final String activeId = getActiveProfileId(activity);
        final List<Profile> allProfiles = getProfiles(activity);
        final Map<String, Profile> profileMap = new HashMap<>();
        for (Profile p : allProfiles) profileMap.put(p.id, p);

        View activeView = null;
        LayoutInflater inflater = LayoutInflater.from(activity);

        for (final String tabId : openTabIds) {
            final Profile profile = profileMap.containsKey(tabId) ? profileMap.get(tabId) : new Profile(tabId, tabId, 0);
            final boolean isActive = tabId.equals(activeId);

            View tabView = inflater.inflate(R.layout.item_profile_tab, tabsContainer, false);
            TextView tabTitle = tabView.findViewById(R.id.tab_title);
            ImageButton btnClose = tabView.findViewById(R.id.tab_btn_close);

            tabTitle.setText(profile.name);

            if (isActive) {
                tabView.setBackgroundResource(R.drawable.tab_profile_active);
                tabTitle.setTypeface(null, Typeface.BOLD);
                tabTitle.setTextColor(Color.WHITE);
                activeView = tabView;
            } else {
                tabView.setBackgroundResource(R.drawable.tab_profile_inactive);
                tabTitle.setTypeface(null, Typeface.NORMAL);
            }

            tabView.setOnClickListener(v -> {
                if (!tabId.equals(activeId)) {
                    setActiveProfile(activity, tabId);
                    if (onProfileChanged != null) {
                        onProfileChanged.run();
                    }
                }
            });

            if (openTabIds.size() > 1) {
                btnClose.setVisibility(View.VISIBLE);
                btnClose.setOnClickListener(v -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(R.string.tab_close_confirm_title);
                    builder.setMessage(activity.getString(R.string.tab_close_confirm_msg, profile.name));
                    builder.setPositiveButton(R.string.button_close, (dialog, which) -> {
                        closeTab(activity, tabId, onProfileChanged);
                    });
                    builder.setNegativeButton(R.string.button_cancel, null);
                    builder.show();
                });
            } else {
                btnClose.setVisibility(View.GONE);
            }

            tabsContainer.addView(tabView);
        }

        // Add [ + ] button
        LinearLayout addTabBtn = new LinearLayout(activity);
        addTabBtn.setOrientation(LinearLayout.HORIZONTAL);
        addTabBtn.setGravity(android.view.Gravity.CENTER);
        addTabBtn.setBackgroundResource(R.drawable.tab_profile_add);
        addTabBtn.setPadding(24, 8, 24, 8);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (int) (34 * activity.getResources().getDisplayMetrics().density)
        );
        params.setMargins(6, 4, 6, 4);
        addTabBtn.setLayoutParams(params);

        TextView addText = new TextView(activity);
        addText.setText("+ " + activity.getString(R.string.tab_new_profile));
        addText.setTextSize(12);
        addText.setTypeface(null, Typeface.BOLD);
        addTabBtn.addView(addText);

        addTabBtn.setOnClickListener(v -> {
            showNewTabDialog(activity, onProfileChanged);
        });

        tabsContainer.addView(addTabBtn);

        if (activeView != null) {
            final View targetView = activeView;
            scrollView.post(() -> {
                int scrollX = targetView.getLeft() - (scrollView.getWidth() / 4);
                scrollView.smoothScrollTo(Math.max(0, scrollX), 0);
            });
        }
    }

    public static void showNewTabDialog(final Activity activity, final Runnable onProfileChanged) {
        if (activity == null || activity.isFinishing()) return;

        final List<Profile> allProfiles = getProfiles(activity);
        final List<String> openTabIds = getOpenTabIds(activity);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.dialog_new_tab_title);

        List<String> options = new ArrayList<>();
        options.add(activity.getString(R.string.dialog_new_tab_new_profile));
        options.add(activity.getString(R.string.dialog_new_tab_clone_profile));

        final List<Profile> unopenedProfiles = new ArrayList<>();
        for (Profile p : allProfiles) {
            if (!openTabIds.contains(p.id)) {
                unopenedProfiles.add(p);
            }
        }
        if (!unopenedProfiles.isEmpty()) {
            options.add(activity.getString(R.string.dialog_new_tab_existing_profile));
        }

        builder.setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which == 0) {
                showCreateProfileDialog(activity, false, newProfile -> {
                    openNewTab(activity, newProfile.id);
                    setActiveProfile(activity, newProfile.id);
                    if (onProfileChanged != null) onProfileChanged.run();
                });
            } else if (which == 1) {
                showCreateProfileDialog(activity, true, newProfile -> {
                    openNewTab(activity, newProfile.id);
                    setActiveProfile(activity, newProfile.id);
                    if (onProfileChanged != null) onProfileChanged.run();
                });
            } else if (which == 2) {
                showPickExistingProfileDialog(activity, unopenedProfiles, selectedProfile -> {
                    openNewTab(activity, selectedProfile.id);
                    setActiveProfile(activity, selectedProfile.id);
                    if (onProfileChanged != null) onProfileChanged.run();
                });
            }
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    private static void showPickExistingProfileDialog(final Activity activity, final List<Profile> profiles, final ProfileCallback callback) {
        String[] items = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            items[i] = profiles.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.dialog_new_tab_existing_profile);
        builder.setItems(items, (dialog, which) -> {
            if (callback != null) {
                callback.onProfileSelected(profiles.get(which));
            }
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    public static void showCreateProfileDialog(final Activity activity, final boolean isClone, final ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(isClone ? R.string.profile_action_clone : R.string.profile_create_title);

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        if (isClone) {
            Profile current = getActiveProfile(activity);
            input.setText(current.name + " (" + activity.getString(R.string.profile_clone_postfix) + ")");
            input.setSelection(input.getText().length());
        } else {
            input.setHint(R.string.profile_name_hint);
        }
        builder.setView(input);

        builder.setPositiveButton(isClone ? R.string.button_clone_current : R.string.button_create_clean, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = activity.getString(R.string.profile_unnamed);
            Profile p = createProfile(activity, name, isClone);
            Toast.makeText(activity, activity.getString(isClone ? R.string.profile_cloned_toast : R.string.profile_created_toast, p.name), Toast.LENGTH_SHORT).show();
            if (callback != null) {
                callback.onProfileSelected(p);
            }
        });

        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    public static void showProfileSwitcher(final Activity activity, final Runnable onProfileSwitched) {
        if (activity == null || activity.isFinishing()) return;

        final List<Profile> profiles = getProfiles(activity);
        final String currentActiveId = getActiveProfileId(activity);

        String[] names = new String[profiles.size()];
        int selectedIndex = 0;
        for (int i = 0; i < profiles.size(); i++) {
            Profile p = profiles.get(i);
            names[i] = p.name + (p.id.equals(currentActiveId) ? " (" + activity.getString(R.string.profile_active_label) + ")" : "");
            if (p.id.equals(currentActiveId)) {
                selectedIndex = i;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.profile_manager_title);

        builder.setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
            Profile chosen = profiles.get(which);
            if (!chosen.id.equals(currentActiveId)) {
                setActiveProfile(activity, chosen.id);
                Toast.makeText(activity, activity.getString(R.string.profile_switched_toast, chosen.name), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                if (onProfileSwitched != null) {
                    onProfileSwitched.run();
                }
            } else {
                dialog.dismiss();
            }
        });

        builder.setPositiveButton(R.string.profile_action_new, (dialog, which) -> {
            showNewTabDialog(activity, onProfileSwitched);
        });

        builder.setNeutralButton(R.string.profile_action_manage, (dialog, which) -> {
            showManageProfilesDialog(activity, onProfileSwitched);
        });

        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    private static void showManageProfilesDialog(final Activity activity, final Runnable onProfileSwitched) {
        final List<Profile> profiles = getProfiles(activity);
        String[] items = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            items[i] = profiles.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.profile_manage_title);
        builder.setItems(items, (dialog, which) -> {
            final Profile selected = profiles.get(which);
            showProfileActionsDialog(activity, selected, onProfileSwitched);
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    private static void showProfileActionsDialog(final Activity activity, final Profile profile, final Runnable onProfileSwitched) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(profile.name);

        String[] actions;
        if (DEFAULT_PROFILE_ID.equals(profile.id)) {
            actions = new String[]{activity.getString(R.string.profile_action_rename), activity.getString(R.string.profile_action_clone)};
        } else {
            actions = new String[]{activity.getString(R.string.profile_action_rename), activity.getString(R.string.profile_action_clone), activity.getString(R.string.profile_action_delete)};
        }

        builder.setItems(actions, (dialog, which) -> {
            if (which == 0) {
                // Rename
                AlertDialog.Builder rBuilder = new AlertDialog.Builder(activity);
                rBuilder.setTitle(R.string.profile_rename_title);
                final EditText rInput = new EditText(activity);
                rInput.setText(profile.name);
                rInput.setSelection(profile.name.length());
                rBuilder.setView(rInput);
                rBuilder.setPositiveButton(android.R.string.ok, (d, w) -> {
                    String nn = rInput.getText().toString().trim();
                    if (!nn.isEmpty()) {
                        renameProfile(activity, profile.id, nn);
                        Toast.makeText(activity, R.string.profile_renamed_toast, Toast.LENGTH_SHORT).show();
                        if (onProfileSwitched != null) onProfileSwitched.run();
                    }
                });
                rBuilder.setNegativeButton(R.string.button_cancel, null);
                rBuilder.show();
            } else if (which == 1) {
                // Clone
                Profile cloned = createProfile(activity, profile.name + " (" + activity.getString(R.string.profile_clone_postfix) + ")", true);
                Toast.makeText(activity, activity.getString(R.string.profile_cloned_toast, cloned.name), Toast.LENGTH_SHORT).show();
                if (onProfileSwitched != null) onProfileSwitched.run();
            } else if (which == 2) {
                // Delete
                AlertDialog.Builder dBuilder = new AlertDialog.Builder(activity);
                dBuilder.setTitle(R.string.profile_delete_confirm_title);
                dBuilder.setMessage(activity.getString(R.string.profile_delete_confirm_msg, profile.name));
                dBuilder.setPositiveButton(R.string.action_delete, (d, w) -> {
                    deleteProfile(activity, profile.id);
                    Toast.makeText(activity, R.string.profile_deleted_toast, Toast.LENGTH_SHORT).show();
                    if (onProfileSwitched != null) onProfileSwitched.run();
                });
                dBuilder.setNegativeButton(R.string.button_cancel, null);
                dBuilder.show();
            }
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }
}
