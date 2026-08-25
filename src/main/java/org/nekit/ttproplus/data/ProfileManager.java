package org.nekit.ttproplus.data;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nekit.ttproplus.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfileManager {

    private static final String PREF_GLOBAL_ACTIVE_PROFILE = "global_active_profile_id";
    private static final String PREF_GLOBAL_PROFILES_JSON = "global_profiles_list_json";
    public static final String DEFAULT_PROFILE_ID = "default";

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
    }

    public static Profile createProfile(Context context, String name, boolean cloneCurrent) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Profile newProfile = new Profile(id, name, System.currentTimeMillis());

        if (cloneCurrent) {
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

        // Delete profile prefs file
        context.getSharedPreferences(target.getPrefsName(), Context.MODE_PRIVATE).edit().clear().commit();

        if (getActiveProfileId(context).equals(profileId)) {
            setActiveProfile(context, DEFAULT_PROFILE_ID);
        }
        return true;
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

        final int finalSelectedIndex = selectedIndex;
        builder.setSingleChoiceItems(names, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
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
            }
        });

        builder.setPositiveButton(R.string.profile_action_new, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showCreateProfileDialog(activity, onProfileSwitched);
            }
        });

        builder.setNeutralButton(R.string.profile_action_manage, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showManageProfilesDialog(activity, onProfileSwitched);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private static void showCreateProfileDialog(final Activity activity, final Runnable onProfileSwitched) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.profile_create_title);

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.profile_name_hint);
        builder.setView(input);

        builder.setPositiveButton(R.string.button_create_clean, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) name = activity.getString(R.string.profile_unnamed);
                Profile p = createProfile(activity, name, false);
                setActiveProfile(activity, p.id);
                Toast.makeText(activity, activity.getString(R.string.profile_created_toast, p.name), Toast.LENGTH_SHORT).show();
                if (onProfileSwitched != null) onProfileSwitched.run();
            }
        });

        builder.setNeutralButton(R.string.button_clone_current, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) name = activity.getString(R.string.profile_unnamed);
                Profile p = createProfile(activity, name, true);
                setActiveProfile(activity, p.id);
                Toast.makeText(activity, activity.getString(R.string.profile_cloned_toast, p.name), Toast.LENGTH_SHORT).show();
                if (onProfileSwitched != null) onProfileSwitched.run();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
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
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final Profile selected = profiles.get(which);
                showProfileActionsDialog(activity, selected, onProfileSwitched);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
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

        builder.setItems(actions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
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
                    rBuilder.setNegativeButton(android.R.string.cancel, null);
                    rBuilder.show();
                } else if (which == 1) {
                    // Clone
                    Profile cloned = createProfile(activity, profile.name + " (Copy)", true);
                    Toast.makeText(activity, activity.getString(R.string.profile_cloned_toast, cloned.name), Toast.LENGTH_SHORT).show();
                    if (onProfileSwitched != null) onProfileSwitched.run();
                } else if (which == 2) {
                    // Delete
                    AlertDialog.Builder dBuilder = new AlertDialog.Builder(activity);
                    dBuilder.setTitle(R.string.profile_delete_confirm_title);
                    dBuilder.setMessage(activity.getString(R.string.profile_delete_confirm_msg, profile.name));
                    dBuilder.setPositiveButton(R.string.button_delete, (d, w) -> {
                        deleteProfile(activity, profile.id);
                        Toast.makeText(activity, R.string.profile_deleted_toast, Toast.LENGTH_SHORT).show();
                        if (onProfileSwitched != null) onProfileSwitched.run();
                    });
                    dBuilder.setNegativeButton(android.R.string.cancel, null);
                    dBuilder.show();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
}
