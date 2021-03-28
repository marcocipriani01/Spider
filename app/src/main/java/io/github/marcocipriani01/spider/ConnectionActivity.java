package io.github.marcocipriani01.spider;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ConnectionActivity extends AppCompatActivity {

    private static final int PEM_CHOOSER_REQUEST = 10;
    private static final int STORAGE_PERMISSION_REQUEST = 20;
    public static byte[] privateKeyBytes;
    private final String TAG = SpiderApp.getTag(ConnectionActivity.class);
    private SwitchCompat usePEMKeySwitch, pemPasswordSwitch;
    private TextInputEditText passwordField, pemPasswordField, usernameField, ipField, portField;
    private CheckBox savePasswordBox;
    private SharedPreferences preferences;
    private Button privateKeyButton;
    private View rootView;
    private String remoteFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connection_activity);
        rootView = findViewById(R.id.connection_root_view);
        savePasswordBox = findViewById(R.id.sftp_save_password_box);
        usePEMKeySwitch = findViewById(R.id.sftp_pem_switch);
        passwordField = findViewById(R.id.sftp_password_field);
        usernameField = this.findViewById(R.id.sftp_username_field);
        ipField = this.findViewById(R.id.sftp_ip_field);
        portField = this.findViewById(R.id.sftp_port_field);
        View passwordFieldLayout = findViewById(R.id.password_field_layout),
                pemPasswordLayout = findViewById(R.id.pem_password_layout);
        privateKeyButton = findViewById(R.id.sftp_choose_pem_btn);
        pemPasswordSwitch = findViewById(R.id.sftp_pem_has_password_switch);
        pemPasswordField = findViewById(R.id.sftp_pem_password_field);
        usePEMKeySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                passwordFieldLayout.setVisibility(View.GONE);
                privateKeyButton.setVisibility(View.VISIBLE);
                pemPasswordSwitch.setVisibility(View.VISIBLE);
                pemPasswordLayout.setVisibility(pemPasswordSwitch.isChecked() ? View.VISIBLE : View.GONE);
            } else {
                passwordFieldLayout.setVisibility(View.VISIBLE);
                privateKeyButton.setVisibility(View.GONE);
                pemPasswordSwitch.setVisibility(View.GONE);
                pemPasswordLayout.setVisibility(View.GONE);
            }
        });
        pemPasswordSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                pemPasswordLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        preferences = getPreferences(Context.MODE_PRIVATE);
        ipField.setText(preferences.getString(SpiderApp.IP_PREF, ""));
        usernameField.setText(preferences.getString(SpiderApp.USERNAME_PREF, ""));
        portField.setText(preferences.getString(SpiderApp.PORT_PREF, "22"));
        usePEMKeySwitch.setChecked(preferences.getBoolean(SpiderApp.USE_PEM_PREF, false));
        pemPasswordSwitch.setChecked(preferences.getBoolean(SpiderApp.USE_PEM_PASSWORD_PREF, false));
        boolean savePasswords = preferences.getBoolean(SpiderApp.SAVE_PASSWORDS_PREF, true);
        savePasswordBox.setChecked(savePasswords);
        savePasswordBox.setSelected(savePasswords);
        if (savePasswords) {
            passwordField.setText(preferences.getString(SpiderApp.PASSWORD_PREF, ""));
            pemPasswordField.setText(preferences.getString(SpiderApp.PEM_PASSWORD_PREF, ""));
        }
        if (privateKeyBytes != null)
            privateKeyButton.setText(R.string.private_key_selected);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
    }

    @SuppressWarnings("ConstantConditions")
    public void connectAction(View view) {
        String user = usernameField.getText().toString(),
                ip = ipField.getText().toString();
        remoteFolder = "/home/" + user;
        if (!user.isEmpty() && !ip.isEmpty()) {
            String portString = portField.getText().toString(),
                    password = passwordField.getText().toString(),
                    pemPassword = pemPasswordField.getText().toString();
            boolean usePEM = usePEMKeySwitch.isChecked(),
                    usePEMPassword = pemPasswordSwitch.isChecked();
            if (usePEM && (privateKeyBytes == null)) {
                Snackbar.make(rootView, R.string.no_pem_loaded, Snackbar.LENGTH_SHORT).show();
            } else if (password.isEmpty()) {
                Snackbar.make(rootView, R.string.please_write_password, Snackbar.LENGTH_SHORT).show();
            } else {
                try {
                    int port = portString.isEmpty() ? 22 : Integer.parseInt(portString);
                    boolean savePasswords = savePasswordBox.isChecked();
                    preferences.edit().putBoolean(SpiderApp.SAVE_PASSWORDS_PREF, savePasswords)
                            .putString(SpiderApp.USERNAME_PREF, user)
                            .putString(SpiderApp.IP_PREF, ip)
                            .putString(SpiderApp.PORT_PREF, portString)
                            .putBoolean(SpiderApp.USE_PEM_PREF, usePEM)
                            .putBoolean(SpiderApp.USE_PEM_PASSWORD_PREF, usePEMPassword)
                            .putString(SpiderApp.PASSWORD_PREF, savePasswords ? password : "")
                            .putString(SpiderApp.PEM_PASSWORD_PREF, savePasswords ? pemPassword : "").apply();
                    if ((port <= 0) || (port >= 0xFFFF)) {
                        Snackbar.make(rootView, R.string.invalid_port, Snackbar.LENGTH_SHORT).show();
                    } else {
                        new ConnectTask(user, ip, password, port, usePEM,
                                usePEMPassword, pemPassword).start();
                    }
                } catch (NumberFormatException e) {
                    Snackbar.make(rootView, R.string.invalid_port, Snackbar.LENGTH_SHORT).show();
                }
            }
        } else {
            Snackbar.make(rootView, R.string.something_missing, Snackbar.LENGTH_SHORT).show();
        }
    }

    public void chosePEMKeyAction(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PEM_CHOOSER_REQUEST);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if ((requestCode == PEM_CHOOSER_REQUEST) && (resultCode == Activity.RESULT_OK) && (resultData != null)) {
            try {
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                InputStream inputStream = getContentResolver().openInputStream(resultData.getData());
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                byte[] bytes = byteBuffer.toByteArray();
                if (pemPasswordSwitch.isChecked() || new String(bytes).contains("PRIVATE KEY")) {
                    privateKeyBytes = bytes;
                    privateKeyButton.setText(R.string.private_key_selected);
                } else {
                    privateKeyBytes = null;
                    Snackbar.make(rootView, R.string.pem_key_error, Snackbar.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                Snackbar.make(rootView, R.string.could_not_read_key, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((requestCode == STORAGE_PERMISSION_REQUEST) && (grantResults[0] != PackageManager.PERMISSION_GRANTED))
            Snackbar.make(rootView, R.string.storage_permission_required, Snackbar.LENGTH_SHORT).show();
    }

    public class ConnectTask extends Thread {

        private final String TAG = SpiderApp.getTag(ConnectTask.class);
        private final ProgressDialog progressDialog;
        private final String user;
        private final String ip;
        private final String password;
        private final int port;
        private final boolean useKey;
        private final boolean usePEM;
        private final String pemPassword;
        private final Handler handler = new Handler(Looper.getMainLooper());

        public ConnectTask(String user, String ip, String password, int port,
                           boolean useKey, boolean usePEM, String pemPassword) {
            super();
            this.user = user;
            this.ip = ip;
            this.password = password;
            this.port = port;
            this.useKey = useKey;
            this.usePEM = usePEM;
            this.pemPassword = pemPassword;
            progressDialog = new ProgressDialog(ConnectionActivity.this);
            progressDialog.setMessage(getString(R.string.connecting));
            progressDialog.show();
        }

        @Override
        public void run() {
            try {
                JSch jsch = new JSch();
                Session session;
                if (!useKey) {
                    session = jsch.getSession(user, ip, port);
                    session.setPassword(password);
                } else {
                    if (usePEM) {
                        jsch.addIdentity("connection", privateKeyBytes, null, pemPassword.getBytes());
                    } else {
                        jsch.addIdentity("connection", privateKeyBytes, null, null);
                    }
                    session = jsch.getSession(user, ip, port);
                    session.setConfig("PreferredAuthentications", "publickey");
                }
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect(15000);
                ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(15000);
                Log.d(TAG, "Connected, returning session");
                SpiderApp.session = session;
                SpiderApp.channel = channel;
                handler.post(() -> {
                    progressDialog.dismiss();
                    Intent intent = new Intent(ConnectionActivity.this, FolderActivity.class);
                    intent.putExtra(FolderActivity.EXTRA_REMOTE_FOLDER, remoteFolder);
                    ConnectionActivity.this.startActivity(intent);
                });
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> {
                    progressDialog.dismiss();
                    Snackbar.make(rootView, R.string.connection_error, Snackbar.LENGTH_SHORT).show();
                });
            }
        }
    }
}