package org.nekit.ttproplus.gui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.nekit.ttproplus.R;
import org.nekit.ttproplus.data.AntiSpamStore;
import org.nekit.ttproplus.data.Preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IgnoreListActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String MODE_IGNORE = "ignore";
    public static final String MODE_WHITELIST = "whitelist";

    private final List<String> nicks = new ArrayList<>();
    private String prefKey;
    private boolean whitelistMode;
    private TextView hintView;
    private android.widget.ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_antispam_list);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        whitelistMode = MODE_WHITELIST.equals(getIntent().getStringExtra(EXTRA_MODE));
        prefKey = whitelistMode ? Preferences.PREF_ANTISPAM_WHITELIST : Preferences.PREF_ANTISPAM_IGNORE_LIST;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(whitelistMode
                    ? R.string.title_activity_whitelist
                    : R.string.title_activity_ignore_list);
        }

        hintView = findViewById(R.id.txt_antispam_list_hint);
        ListView listView = findViewById(R.id.list_antispam_nicks);
        Button addButton = findViewById(R.id.btn_antispam_add);

        adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nicks);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> confirmRemove(nicks.get(position)));
        addButton.setOnClickListener(v -> showAddDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        nicks.clear();
        nicks.addAll(AntiSpamStore.getNicks(this, prefKey));
        Collections.sort(nicks, String.CASE_INSENSITIVE_ORDER);
        adapter.notifyDataSetChanged();
        if (nicks.isEmpty()) {
            hintView.setText(whitelistMode
                    ? R.string.antispam_whitelist_empty
                    : R.string.antispam_ignore_empty);
        } else {
            hintView.setText(whitelistMode
                    ? R.string.antispam_whitelist_hint
                    : R.string.antispam_ignore_hint);
        }
    }

    private void showAddDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.antispam_nickname_hint);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(getResources().getDisplayMetrics().density * 20);
        wrapper.setPadding(padding, padding / 2, padding, 0);
        wrapper.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(whitelistMode
                        ? R.string.antispam_add_whitelist_title
                        : R.string.antispam_add_ignore_title)
                .setView(wrapper)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        // Keep the dialog open when the nickname is invalid or already present.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nick = input.getText().toString().trim();
            if (nick.isEmpty()) {
                input.setError(getString(R.string.antispam_nickname_hint));
                return;
            }
            boolean added = AntiSpamStore.addNick(this, prefKey, nick);
            Toast.makeText(this,
                    getString(added ? R.string.antispam_nickname_added : R.string.antispam_nickname_exists, nick),
                    Toast.LENGTH_SHORT).show();
            if (added) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                }
                dialog.dismiss();
                reload();
            }
        });
        input.requestFocus();
    }

    private void confirmRemove(final String nick) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.antispam_remove_title, nick))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    AntiSpamStore.removeNick(this, prefKey, nick);
                    Toast.makeText(this, getString(R.string.antispam_nickname_removed, nick), Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
