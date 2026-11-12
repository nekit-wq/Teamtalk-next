package org.nekit.ttproplus.gui;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.nekit.ttproplus.R;
import org.nekit.ttproplus.plugin.PluginAction;
import org.nekit.ttproplus.plugin.PluginContainer;
import org.nekit.ttproplus.plugin.PluginInfo;
import org.nekit.ttproplus.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PluginManagerActivity extends AppCompatActivity implements PluginManager.PluginLifecycleListener {

    private static final int REQUEST_PICK_PLUGIN = 1001;

    private ListView listPlugins;
    private View layoutEmpty;
    private TextView textPluginsPath;
    private PluginAdapter adapter;
    private final List<PluginContainer> pluginList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugin_manager);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_activity_plugin_manager);
        }

        listPlugins = findViewById(R.id.list_plugins);
        layoutEmpty = findViewById(R.id.layout_empty_plugins);
        textPluginsPath = findViewById(R.id.text_plugins_path);

        if (PluginManager.getInstance().getContext() == null) {
            PluginManager.getInstance().init(getApplicationContext(), null);
        }

        File extDir = PluginManager.getInstance().getExternalPluginsDir();
        String displayPath = extDir != null ? extDir.getAbsolutePath() : "";
        textPluginsPath.setText(displayPath);

        findViewById(R.id.banner_plugins_dir).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Plugins Path", displayPath));
                Toast.makeText(this, R.string.plugin_folder_copied, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_install_plugin).setOnClickListener(v -> pickPluginFile());

        adapter = new PluginAdapter();
        listPlugins.setAdapter(adapter);

        PluginManager.getInstance().registerLifecycleListener(this);
        refreshPlugins();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PluginManager.getInstance().unregisterLifecycleListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.plugin_reload_btn)
                .setIcon(android.R.drawable.ic_popup_sync)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == 1) {
            PluginManager.getInstance().reloadPlugins();
            Toast.makeText(this, R.string.plugin_reload_btn, Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPluginsChanged() {
        refreshPlugins();
    }

    private void refreshPlugins() {
        pluginList.clear();
        pluginList.addAll(PluginManager.getInstance().getLoadedPlugins());
        adapter.notifyDataSetChanged();

        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(pluginList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void pickPluginFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_PLUGIN);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                fallback.setType("*/*");
                startActivityForResult(Intent.createChooser(fallback, getString(R.string.plugin_install_btn)), REQUEST_PICK_PLUGIN);
            } catch (Exception ex) {
                Toast.makeText(this, getString(R.string.plugin_picker_error, ex.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_PLUGIN && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                installPluginFromUri(uri);
            }
        }
    }

    private String resolveFileName(Uri uri) {
        String fileName = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        fileName = cursor.getString(idx);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            String path = uri.getPath();
            if (path != null) {
                int last = path.lastIndexOf('/');
                fileName = last >= 0 ? path.substring(last + 1) : path;
            }
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "plugin_" + System.currentTimeMillis() + ".jar";
        }
        fileName = new File(fileName).getName();
        if (!fileName.toLowerCase().endsWith(".jar") && !fileName.toLowerCase().endsWith(".dex") && !fileName.toLowerCase().endsWith(".apk") && !fileName.toLowerCase().endsWith(".zip")) {
            fileName += ".jar";
        }
        return fileName;
    }

    private void installPluginFromUri(Uri uri) {
        String fileName = resolveFileName(uri);
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IOException(getString(R.string.plugin_err_cannot_read_file));
            }
            PluginContainer container = PluginManager.getInstance().installPlugin(is, fileName);
            Toast.makeText(this, getString(R.string.plugin_installed_success, container.getInfo().getName()), Toast.LENGTH_LONG).show();
            refreshPlugins();
        } catch (Throwable t) {
            Log.e("PluginManagerActivity", "Failed to install plugin: " + fileName, t);
            String errorMsg = t.getLocalizedMessage() != null ? t.getLocalizedMessage() : t.getClass().getSimpleName();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.plugin_install_error_title)
                    .setMessage(getString(R.string.plugin_install_error_message, fileName, errorMsg))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private class PluginAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return pluginList.size();
        }

        @Override
        public PluginContainer getItem(int position) {
            return pluginList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(PluginManagerActivity.this).inflate(R.layout.item_plugin, parent, false);
            }

            PluginContainer container = getItem(position);
            PluginInfo info = container.getInfo();

            TextView nameView = convertView.findViewById(R.id.plugin_name);
            TextView versionView = convertView.findViewById(R.id.plugin_version);
            TextView authorIdView = convertView.findViewById(R.id.plugin_author_id);
            TextView descView = convertView.findViewById(R.id.plugin_description);
            TextView errorView = convertView.findViewById(R.id.plugin_error);
            SwitchCompat switchEnable = convertView.findViewById(R.id.plugin_switch);
            Button btnDelete = convertView.findViewById(R.id.plugin_btn_delete);
            Button btnAction = convertView.findViewById(R.id.plugin_btn_action);

            nameView.setText(info.getName());
            versionView.setText("v" + info.getVersion());

            String meta = info.getId();
            if (info.getAuthor() != null && !info.getAuthor().isEmpty()) {
                meta += " • " + info.getAuthor();
            }
            authorIdView.setText(meta);

            if (info.getDescription() != null && !info.getDescription().isEmpty()) {
                descView.setText(info.getDescription());
                descView.setVisibility(View.VISIBLE);
            } else {
                descView.setVisibility(View.GONE);
            }

            if (container.getLastError() != null) {
                errorView.setText(container.getLastError());
                errorView.setVisibility(View.VISIBLE);
            } else {
                errorView.setVisibility(View.GONE);
            }

            switchEnable.setOnCheckedChangeListener(null);
            switchEnable.setChecked(container.isEnabled());
            switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    boolean ok = PluginManager.getInstance().enablePlugin(info.getId());
                    if (!ok) {
                        switchEnable.setChecked(false);
                        Toast.makeText(PluginManagerActivity.this, R.string.plugin_status_error, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(PluginManagerActivity.this, R.string.plugin_enabled_toast, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    PluginManager.getInstance().disablePlugin(info.getId());
                    Toast.makeText(PluginManagerActivity.this, R.string.plugin_disabled_toast, Toast.LENGTH_SHORT).show();
                }
            });

            // Check if plugin has registered actions
            List<String> actions = container.getRegisteredActions();
            if (!actions.isEmpty() && container.isEnabled()) {
                String actionId = actions.get(0);
                PluginAction action = PluginManager.getInstance().getRegisteredActions().get(actionId);
                if (action != null) {
                    btnAction.setText(action.getTitle());
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setOnClickListener(v -> action.onActionClick(PluginManagerActivity.this));
                } else {
                    btnAction.setVisibility(View.GONE);
                }
            } else {
                btnAction.setVisibility(View.GONE);
            }

            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(PluginManagerActivity.this)
                        .setMessage(getString(R.string.plugin_delete_confirm, info.getName()))
                        .setPositiveButton(R.string.button_remove, (dialog, which) -> {
                            PluginManager.getInstance().deletePlugin(info.getId());
                            Toast.makeText(PluginManagerActivity.this, R.string.plugin_deleted, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });

            return convertView;
        }
    }
}
