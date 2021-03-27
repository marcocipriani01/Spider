package io.github.marcocipriani01.spider;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.github.marcocipriani01.spider.tasks.ConnectTask;

public class ConnectionActivity extends AppCompatActivity {

    private static final int READ_REQUEST_CODE_PRIVATE_KEY = 42;
    private SwitchCompat useId, pemPasswordSwitch;
    private EditText passwordField, pemPasswordField;
    private Button privateKeyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connection_activity);
        useId = findViewById(R.id.register_switch_id);
        passwordField = findViewById(R.id.register_password);
        privateKeyButton = findViewById(R.id.register_button_choose_file);
        privateKeyButton.setVisibility(View.GONE);
        pemPasswordSwitch = findViewById(R.id.register_switch_pem_password);
        pemPasswordSwitch.setVisibility(View.GONE);
        pemPasswordField = findViewById(R.id.register_pem_password);
        pemPasswordField.setVisibility(View.GONE);
        useId.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                passwordField.setVisibility(View.GONE);
                privateKeyButton.setVisibility(View.VISIBLE);
                pemPasswordSwitch.setVisibility(View.VISIBLE);
            } else {
                passwordField.setVisibility(View.VISIBLE);
                privateKeyButton.setVisibility(View.GONE);
                pemPasswordSwitch.setVisibility(View.GONE);
                pemPasswordField.setVisibility(View.GONE);
            }
        });
        pemPasswordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                pemPasswordField.setVisibility(View.VISIBLE);
            } else {
                pemPasswordField.setVisibility(View.INVISIBLE);

            }
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 84);
        }
    }

    /**
     * Called when the user taps the Send button
     */
    public void saveConnection(View view) {
        String user = this.<EditText>findViewById(R.id.register_user).getText().toString(),
                ip = this.<EditText>findViewById(R.id.register_ip).getText().toString(),
                port = this.<EditText>findViewById(R.id.register_port).getText().toString(),
                password = passwordField.getText().toString(),
                pemPassword = pemPasswordField.getText().toString();

        SpiderApp.currentPath = "/home/" + user;
        if (!user.isEmpty() && !ip.isEmpty()) {
            if (useId.isChecked()) {
                if (SpiderApp.private_bytes == null) {
                    Snackbar.make(view, "Check PEM key file", Snackbar.LENGTH_SHORT).show();
                    return;
                }
            } else if (password.isEmpty()) {
                Snackbar.make(view, "Please input a password", Snackbar.LENGTH_SHORT).show();
                return;
            }
            new ConnectTask(ConnectionActivity.this, user, ip, password,
                    port.isEmpty() ? 22 : Integer.parseInt(port), useId.isChecked(),
                    pemPasswordSwitch.isChecked(), pemPassword) {
                @Override
                protected void onResult(Session session) {
                    Intent FolderIntent = new Intent(ConnectionActivity.this, FolderActivity.class);
                    Log.d("REGISTER ACTIVITY", "launching Folder activity");
                    ConnectionActivity.this.startActivity(FolderIntent);
                }

                @Override
                protected void onError(Exception e) {
                    Snackbar.make(view, "Could not connect", Snackbar.LENGTH_SHORT).show();
                }
            }.start();
        } else {
            Snackbar.make(view, "Check the input fields", Snackbar.LENGTH_SHORT).show();
        }
    }

    /**
     * Fires an intent to spin up the "file chooser" UI and select an image.
     */
    public void performFileSearchPrivate(View view) {
        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Filter to show only images, using the image MIME data type.
        // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
        // To search for all documents available via installed storage providers, it would be "*/*".
        intent.setType("*/*");
        startActivityForResult(intent, READ_REQUEST_CODE_PRIVATE_KEY);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == READ_REQUEST_CODE_PRIVATE_KEY && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            //Uri uri = null;
            if (resultData != null) {
                Uri uri = resultData.getData();
                try {
                    byte[] filecontent_bytes = readBytes(getContentResolver().openInputStream(uri));
                    if (new String(filecontent_bytes).split("\n")[0].equals("-----BEGIN RSA PRIVATE KEY-----") ||
                            pemPasswordSwitch.isChecked()) {
                        SpiderApp.private_bytes = filecontent_bytes;
                    } else {
                        SpiderApp.private_bytes = null;
                        Toast.makeText(getApplicationContext(), "Selected is not in PEM format", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public byte[] readBytes(InputStream inputStream) throws IOException {
        // this dynamically extends to take the bytes you read
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        // we need to know how may bytes were read to write them to the byteBuffer
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        // and then we can return your byte array.
        return byteBuffer.toByteArray();
    }
}