package org.nekit.ttproplus.gui;

import org.nekit.ttproplus.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.nekit.ttproplus.data.AppInfo;
import org.nekit.ttproplus.data.BearWareAccount;
import org.nekit.ttproplus.data.BearWareAccountManager;

public class WebLoginActivity extends AppCompatActivity {

    private LinearLayout accountsContainer;
    private TextView noAccountsText;
    private EditText bearwareIdEdit;
    private EditText bearwarePasswdEdit;
    private ImageButton btnTogglePassword;
    private boolean isPasswordVisible = false;

    private final ExecutorService loginExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_login);

        EdgeToEdgeHelper.enableEdgeToEdge(this);

        accountsContainer = findViewById(R.id.accounts_container);
        noAccountsText = findViewById(R.id.no_accounts_text);
        bearwareIdEdit = findViewById(R.id.bearwareidedit);
        bearwarePasswdEdit = findViewById(R.id.bearwarepasswdedit);
        btnTogglePassword = findViewById(R.id.btn_toggle_password);

        Button createBtn = findViewById(R.id.createbearwareidbutton);
        Button loginBtn = findViewById(R.id.bearwareloginbutton);

        createBtn.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.BEARWARE_REGISTRATION_WEBSITE));
            startActivity(browserIntent);
        });

        loginBtn.setOnClickListener(v -> performValidateLogin());

        if (btnTogglePassword != null && bearwarePasswdEdit != null) {
            btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        }

        setupUI();
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            bearwarePasswdEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            btnTogglePassword.setContentDescription(getString(R.string.hide_password));
        } else {
            bearwarePasswdEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            btnTogglePassword.setImageResource(R.drawable.ic_visibility);
            btnTogglePassword.setContentDescription(getString(R.string.show_password));
        }
        bearwarePasswdEdit.setSelection(bearwarePasswdEdit.getText().length());
    }

    private void setupUI() {
        if (accountsContainer == null) return;
        accountsContainer.removeAllViews();

        BearWareAccountManager manager = BearWareAccountManager.getInstance(this);
        List<BearWareAccount> accounts = manager.getAccounts();
        BearWareAccount activeAccount = manager.getActiveAccount();

        if (accounts.isEmpty()) {
            if (noAccountsText != null) noAccountsText.setVisibility(View.VISIBLE);
        } else {
            if (noAccountsText != null) noAccountsText.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (BearWareAccount account : accounts) {
                View itemView = inflater.inflate(R.layout.item_bearware_account, accountsContainer, false);
                MaterialCardView card = itemView.findViewById(R.id.card_account);
                RadioButton radio = itemView.findViewById(R.id.account_radio);
                TextView usernameView = itemView.findViewById(R.id.account_username);
                TextView nicknameView = itemView.findViewById(R.id.account_nickname);
                ImageButton deleteBtn = itemView.findViewById(R.id.btn_delete_account);

                usernameView.setText(account.username);
                boolean isActive = activeAccount != null && account.username.equalsIgnoreCase(activeAccount.username);
                radio.setChecked(isActive);

                if (account.nickname != null && !account.nickname.trim().isEmpty() && !account.nickname.equalsIgnoreCase(account.username)) {
                    nicknameView.setText(account.nickname + (isActive ? " • " + getString(R.string.text_active_account) : ""));
                } else {
                    nicknameView.setText(isActive ? getString(R.string.text_active_account) : "");
                }

                card.setOnClickListener(v -> {
                    manager.setActiveAccount(account.username);
                    setupUI();
                });

                deleteBtn.setOnClickListener(v -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.action_remove_account)
                            .setMessage(getString(R.string.msg_confirm_remove_account, account.username))
                            .setPositiveButton(R.string.action_remove_account, (dialog, which) -> {
                                manager.removeAccount(account.username);
                                setupUI();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });

                accountsContainer.addView(itemView);
            }
        }
    }

    private void performValidateLogin() {
        if (bearwareIdEdit == null || bearwarePasswdEdit == null) return;
        final String inputUser = bearwareIdEdit.getText().toString().trim();
        final String inputPasswd = bearwarePasswdEdit.getText().toString();

        if (inputUser.isEmpty()) {
            Toast.makeText(this, R.string.bwidlab, Toast.LENGTH_SHORT).show();
            return;
        }

        loginExecutor.execute(() -> {
            String xml = Utils.getURL(AppInfo.getBearWareTokenUrl(getBaseContext(), inputUser, inputPasswd));
            Log.d(AppInfo.TAG, "BearWare auth response: " + xml);

            String parsedUser = inputUser;
            String parsedNick = "";
            String parsedToken = "";

            try {
                if (xml != null && !xml.isEmpty()) {
                    InputSource src = new InputSource(new StringReader(xml));
                    DocumentBuilderFactory dbf = Utils.createSecureDocumentBuilderFactory();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document document = db.parse(src);
                    XPathFactory factory = XPathFactory.newInstance();
                    XPath xPath = factory.newXPath();

                    parsedUser = (String) xPath.evaluate("/teamtalk/bearware/username", document, XPathConstants.STRING);
                    parsedNick = (String) xPath.evaluate("/teamtalk/bearware/nickname", document, XPathConstants.STRING);
                    parsedToken = (String) xPath.evaluate("/teamtalk/bearware/token", document, XPathConstants.STRING);
                }
            } catch (XPathExpressionException | ParserConfigurationException | IOException | SAXException e) {
                Log.e(AppInfo.TAG, "XML parse failed: " + e);
            }

            final String finalUser = (parsedUser != null && !parsedUser.isEmpty()) ? parsedUser : inputUser;
            final String finalNick = parsedNick;
            final String finalToken = parsedToken;

            runOnUiThread(() -> {
                if (isFinishing()) return;

                if (finalToken != null && finalToken.length() > 0) {
                    BearWareAccountManager.getInstance(WebLoginActivity.this)
                            .addOrUpdateAccount(finalUser, finalToken, finalNick);

                    bearwarePasswdEdit.setText("");
                    bearwareIdEdit.setText("");

                    String msg = getResources().getString(R.string.text_bearwarelogin_success, finalNick, finalUser);
                    Toast.makeText(WebLoginActivity.this, msg, Toast.LENGTH_LONG).show();

                    setupUI();
                    setResult(RESULT_OK);
                } else {
                    String msg = getResources().getString(R.string.text_bearwarelogin_failed, finalNick, finalUser);
                    Toast.makeText(WebLoginActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loginExecutor.shutdown();
    }
}
