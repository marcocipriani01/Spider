package io.github.marcocipriani01.spider.tasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import io.github.marcocipriani01.spider.R;
import io.github.marcocipriani01.spider.SpiderApp;

public abstract class ConnectTask extends Thread {

    private static final String TAG = SpiderApp.getTag(ConnectTask.class);
    private final ProgressDialog dialog;
    private final String user;
    private final String ip;
    private final String password;
    private final int port;
    private final boolean useKey;
    private final boolean usePEM;
    private final String pemPassword;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public ConnectTask(Context context, String user, String ip, String password,
                       int port, boolean useKey, boolean usePEM, String pemPassword) {
        super();
        this.user = user;
        this.ip = ip;
        this.password = password;
        this.port = port;
        this.useKey = useKey;
        this.usePEM = usePEM;
        this.pemPassword = pemPassword;
        dialog = new ProgressDialog(context);
        this.dialog.setMessage(context.getString(R.string.connecting));
        this.dialog.show();
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
                    jsch.addIdentity("connection", SpiderApp.private_bytes, null, pemPassword.getBytes());
                } else {
                    jsch.addIdentity("connection", SpiderApp.private_bytes, null, null);
                }
                session = jsch.getSession(user, ip, port);
                session.setConfig("PreferredAuthentications", "publickey");
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            Log.d(TAG, "Connected, returning session");
            SpiderApp.session = session;
            SpiderApp.channel = channel;
            handler.post(() -> {
                dialog.dismiss();
                onResult(session);
            });
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            handler.post(() -> {
                dialog.dismiss();
                onError(e);
            });
        }
    }

    protected abstract void onResult(Session session);

    protected abstract void onError(Exception e);
}