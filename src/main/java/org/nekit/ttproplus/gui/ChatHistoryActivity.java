package org.nekit.ttproplus.gui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.ChatHistoryDbHelper;
import org.nekit.ttproplus.data.ChatMessageEntry;
import org.nekit.ttproplus.data.ServerEntry;

public class ChatHistoryActivity extends AppCompatActivity implements TeamTalkConnectionListener {

    private TeamTalkConnection mConnection;
    private ChatHistoryDbHelper dbHelper;

    private EditText searchEdit;
    private ImageButton btnClearSearch;
    private Spinner filterSpinner;
    private TextView countText;
    private ListView listView;
    private TextView emptyView;
    private Button btnClear;
    private Button btnExport;

    private ChatHistoryAdapter adapter;
    private final List<ChatMessageEntry> messageList = new ArrayList<>();
    private String currentServerKey = "";
    private int currentFilterType = 0; // 0 = All, 1 = Channel, 2 = Private
    private String currentSearchQuery = "";

    TeamTalkService getService() {
        return mConnection != null ? mConnection.getService() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_activity_chat_history);
        }

        dbHelper = ChatHistoryDbHelper.getInstance(this);

        searchEdit = findViewById(R.id.history_search_edit);
        btnClearSearch = findViewById(R.id.history_btn_clear_search);
        filterSpinner = findViewById(R.id.history_filter_spinner);
        countText = findViewById(R.id.history_count_text);
        listView = findViewById(R.id.history_listview);
        emptyView = findViewById(R.id.history_empty_view);
        btnClear = findViewById(R.id.history_btn_clear);
        btnExport = findViewById(R.id.history_btn_export);

        adapter = new ChatHistoryAdapter();
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);

        setupFilterSpinner();
        setupSearch();
        setupActions();

        mConnection = new TeamTalkConnection(this);
        Intent intent = new Intent(this, TeamTalkService.class);
        if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            loadMessages();
        }
    }

    private void setupFilterSpinner() {
        String[] filters = new String[]{
                getString(R.string.history_filter_all),
                getString(R.string.history_filter_channel),
                getString(R.string.history_filter_private)
        };
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, filters);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(spinnerAdapter);

        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilterType = position;
                loadMessages();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupSearch() {
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s != null ? s.toString().trim() : "";
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                }
                loadMessages();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> searchEdit.setText(""));
        }
    }

    private void setupActions() {
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < messageList.size()) {
                showItemOptionsDialog(messageList.get(position));
            }
        });

        btnClear.setOnClickListener(v -> showClearConfirmDialog());
        btnExport.setOnClickListener(v -> exportHistory());
    }

    private void loadMessages() {
        int filterCode = 0;
        if (currentFilterType == 1) {
            filterCode = ChatMessageEntry.TYPE_CHANNEL;
        } else if (currentFilterType == 2) {
            filterCode = ChatMessageEntry.TYPE_PRIVATE;
        }

        List<ChatMessageEntry> result = dbHelper.queryHistory(currentServerKey, filterCode, currentSearchQuery, 500, 0);
        messageList.clear();
        messageList.addAll(result);
        adapter.notifyDataSetChanged();

        if (countText != null) {
            countText.setText(getString(R.string.history_count_format, messageList.size()));
        }
    }

    private void showItemOptionsDialog(final ChatMessageEntry msg) {
        String[] options = new String[]{
                getString(R.string.history_action_copy_text),
                getString(R.string.history_action_copy_full),
                getString(R.string.history_action_share)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(msg.getSenderDisplayName());
        builder.setItems(options, (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (which == 0) {
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("text", msg.getMessageText()));
                    Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show();
                }
            } else if (which == 1) {
                if (clipboard != null) {
                    String full = "[" + msg.getFormattedTime() + "] " + msg.getSenderDisplayName() + ": " + msg.getMessageText();
                    clipboard.setPrimaryClip(ClipData.newPlainText("text", full));
                    Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show();
                }
            } else if (which == 2) {
                String full = "[" + msg.getFormattedTime() + "] " + msg.getSenderDisplayName() + ": " + msg.getMessageText();
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("text/plain");
                sendIntent.putExtra(Intent.EXTRA_TEXT, full);
                startActivity(Intent.createChooser(sendIntent, getString(R.string.history_action_share)));
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showClearConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.history_clear_dialog_title);
        builder.setMessage(R.string.history_clear_dialog_message);
        builder.setPositiveButton(R.string.history_action_clear, (dialog, which) -> {
            int filterCode = 0;
            if (currentFilterType == 1) {
                filterCode = ChatMessageEntry.TYPE_CHANNEL;
            } else if (currentFilterType == 2) {
                filterCode = ChatMessageEntry.TYPE_PRIVATE;
            }
            dbHelper.clearHistory(currentServerKey, filterCode);
            loadMessages();
            Toast.makeText(this, R.string.history_cleared_toast, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void exportHistory() {
        if (messageList.isEmpty()) {
            Toast.makeText(this, R.string.history_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        int filterCode = 0;
        if (currentFilterType == 1) {
            filterCode = ChatMessageEntry.TYPE_CHANNEL;
        } else if (currentFilterType == 2) {
            filterCode = ChatMessageEntry.TYPE_PRIVATE;
        }

        String exportedText = dbHelper.exportHistoryToText(currentServerKey, filterCode);
        try {
            File exportDir = new File(getCacheDir(), "exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            File exportFile = new File(exportDir, "teamtalk_history_" + System.currentTimeMillis() + ".txt");
            FileOutputStream fos = new FileOutputStream(exportFile);
            fos.write(exportedText.getBytes(StandardCharsets.UTF_8));
            fos.close();

            Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", exportFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.title_activity_chat_history));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.history_action_export)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.history_export_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        if (service != null && service.getServerEntry() != null) {
            ServerEntry entry = service.getServerEntry();
            currentServerKey = entry.ipaddr + ":" + entry.tcpport;
        }
        loadMessages();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
    }

    @Override
    protected void onDestroy() {
        if (mConnection.isBound()) {
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class ChatHistoryAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return messageList.size();
        }

        @Override
        public Object getItem(int position) {
            return messageList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return messageList.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(ChatHistoryActivity.this)
                        .inflate(R.layout.item_chat_history, parent, false);
                holder = new ViewHolder();
                holder.senderText = convertView.findViewById(R.id.history_item_sender);
                holder.targetText = convertView.findViewById(R.id.history_item_target);
                holder.timeText = convertView.findViewById(R.id.history_item_time);
                holder.msgText = convertView.findViewById(R.id.history_item_text);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ChatMessageEntry msg = messageList.get(position);
            holder.senderText.setText(msg.getSenderDisplayName());

            String targetStr;
            if (msg.getMsgType() == ChatMessageEntry.TYPE_PRIVATE) {
                targetStr = msg.isOutgoing() ? "-> " + msg.getToNickname() : "Личное";
            } else if (msg.getMsgType() == ChatMessageEntry.TYPE_BROADCAST) {
                targetStr = "Оповещение";
            } else if (msg.getMsgType() == ChatMessageEntry.TYPE_SYSTEM) {
                targetStr = "Система";
            } else {
                targetStr = msg.getChannelName().isEmpty() ? "Канал" : msg.getChannelName();
            }
            holder.targetText.setText(targetStr);
            holder.timeText.setText(msg.getFormattedTime());
            holder.msgText.setText(msg.getMessageText());

            // TalkBack content description
            convertView.setContentDescription(
                    msg.getSenderDisplayName() + ", " + targetStr + ", " + msg.getFormattedTime() + ". " + msg.getMessageText()
            );

            return convertView;
        }

        class ViewHolder {
            TextView senderText;
            TextView targetText;
            TextView timeText;
            TextView msgText;
        }
    }
}
