package org.nekit.ttproplus.gui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.nekit.ttproplus.R;

public class CrashReportActivity extends AppCompatActivity {

    public static final String EXTRA_ERROR_TEXT = "extra_error_text";
    public static final String EXTRA_DEVICE_INFO = "extra_device_info";

    private String errorText = "";
    private String deviceInfo = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_report);

        Intent intent = getIntent();
        if (intent != null) {
            errorText = intent.getStringExtra(EXTRA_ERROR_TEXT);
            deviceInfo = intent.getStringExtra(EXTRA_DEVICE_INFO);
        }

        if (errorText == null || errorText.isEmpty()) {
            errorText = "No stack trace available.";
        }

        TextView tvDeviceInfo = findViewById(R.id.tv_device_info);
        if (tvDeviceInfo != null && deviceInfo != null) {
            tvDeviceInfo.setText(deviceInfo);
        }

        TextView tvStacktrace = findViewById(R.id.tv_crash_stacktrace);
        if (tvStacktrace != null) {
            tvStacktrace.setText(errorText);
        }

        Button btnCopy = findViewById(R.id.btn_copy_error);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                String fullReport = (deviceInfo != null ? deviceInfo + "\n\n" : "") + errorText;
                ClipData clip = ClipData.newPlainText("TeamTalk Crash Report", fullReport);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                }
                Toast.makeText(CrashReportActivity.this, R.string.crash_copied_toast, Toast.LENGTH_SHORT).show();
            });
        }

        Button btnRestart = findViewById(R.id.btn_restart_app);
        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                Intent restartIntent = new Intent(CrashReportActivity.this, ServerListActivity.class);
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(restartIntent);
                finish();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            });
        }

        Button btnClose = findViewById(R.id.btn_close_app);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                finishAffinity();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            });
        }
    }
}
