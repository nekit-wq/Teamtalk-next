package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
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
import org.nekit.ttproplus.data.Preferences;

public class WebLoginActivity extends AppCompatActivity {

    void setupUI() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        String username = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, "");

        Button createBtn = findViewById(R.id.createbearwareidbutton);
        Button resetBtn = findViewById(R.id.resetbearwareidbutton);
        Button loginBtn = findViewById(R.id.bearwareloginbutton);

        createBtn.setOnClickListener(createClick);
        resetBtn.setOnClickListener(resetClick);
        loginBtn.setOnClickListener(loginClick);

        EditText bearwareid = findViewById(R.id.bearwareidedit);
        TextView passwdlabel = findViewById(R.id.bearwarepasswdlabel);
        EditText bearwarepasswd = findViewById(R.id.bearwarepasswdedit);

        if (username.length() > 0) {
            createBtn.setVisibility(View.GONE);
            bearwareid.setEnabled(false);
            bearwarepasswd.setVisibility(View.GONE);
            resetBtn.setVisibility(View.VISIBLE);
            loginBtn.setVisibility(View.GONE);
            passwdlabel.setVisibility(View.GONE);
        }
        else {
            createBtn.setVisibility(View.VISIBLE);
            bearwareid.setEnabled(true);
            bearwarepasswd.setVisibility(View.VISIBLE);
            resetBtn.setVisibility(View.GONE);
            loginBtn.setVisibility(View.VISIBLE);
            passwdlabel.setVisibility(View.VISIBLE);
        }

        bearwareid.setText(username);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_login);

        EdgeToEdgeHelper.enableEdgeToEdge(this);

        setupUI();
    }

    View.OnClickListener createClick = view -> {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.BEARWARE_REGISTRATION_WEBSITE));
        startActivity(browserIntent);
    };

    View.OnClickListener resetClick = view -> {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, "");
        editor.putString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, "");
        editor.apply();
        setupUI();
    };

    private final ExecutorService loginExecutor = Executors.newSingleThreadExecutor();

    View.OnClickListener loginClick = view -> performValidateLogin();

    private void performValidateLogin() {
        EditText bearwareid = findViewById(R.id.bearwareidedit);
        EditText bearwarepasswd = findViewById(R.id.bearwarepasswdedit);
        final String inputUser = bearwareid.getText().toString();
        final String inputPasswd = bearwarepasswd.getText().toString();

        loginExecutor.execute(() -> {
            String xml = Utils.getURL(AppInfo.getBearWareTokenUrl(getBaseContext(), inputUser, inputPasswd));
            Log.d(AppInfo.TAG, xml);

            String parsedUser = inputUser;
            String parsedNick = "";
            String parsedToken = "";

            try {
                InputSource src = new InputSource(new StringReader(xml));
                DocumentBuilderFactory dbf = Utils.createSecureDocumentBuilderFactory();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document document = db.parse(src);
                XPathFactory factory = XPathFactory.newInstance();
                XPath xPath = factory.newXPath();

                parsedUser = (String) xPath.evaluate("/teamtalk/bearware/username", document, XPathConstants.STRING);
                parsedNick = (String) xPath.evaluate("/teamtalk/bearware/nickname", document, XPathConstants.STRING);
                parsedToken = (String) xPath.evaluate("/teamtalk/bearware/token", document, XPathConstants.STRING);
            } catch (XPathExpressionException | ParserConfigurationException | IOException | SAXException e) {
                Log.e(AppInfo.TAG, "XML parse failed: " + e);
            }

            final String finalUser = parsedUser;
            final String finalNick = parsedNick;
            final String finalToken = parsedToken;

            runOnUiThread(() -> {
                if (isFinishing()) return;

                if (finalToken != null && finalToken.length() > 0) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(WebLoginActivity.this.getBaseContext());
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, finalUser);
                    editor.putString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, finalToken);
                    editor.apply();

                    String msg = WebLoginActivity.this.getResources().getString(R.string.text_bearwarelogin_success, finalNick, finalUser);
                    Toast.makeText(WebLoginActivity.this, msg, Toast.LENGTH_LONG).show();

                    WebLoginActivity.this.setupUI();
                    setResult(RESULT_OK);
                } else {
                    String msg = WebLoginActivity.this.getResources().getString(R.string.text_bearwarelogin_failed, finalNick, finalUser);
                    Toast.makeText(WebLoginActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
