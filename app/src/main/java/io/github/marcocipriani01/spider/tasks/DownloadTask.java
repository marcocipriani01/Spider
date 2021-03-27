package io.github.marcocipriani01.spider.tasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import io.github.marcocipriani01.spider.DirectoryElement;
import io.github.marcocipriani01.spider.R;
import io.github.marcocipriani01.spider.SpiderApp;

public class DownloadTask extends Thread {

    private final Context context;
    private final DirectoryElement element;
    private final ProgressDialog progressDialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    public DownloadTask(Context context, DirectoryElement element) {
        super();
        this.context = context;
        this.element = element;
        // Take CPU lock to prevent CPU from going off if the user
        // presses the power button during download
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            wakeLock.acquire(10 * 60);
        }
        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(String.format(context.getString(R.string.downloading_message), element.shortName, element.sizeMB));
        progressDialog.setMax(100);
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void run() {
        File dir = new File(Environment.getExternalStorageDirectory() + "/" + SpiderApp.DOWNLOAD_FOLDER);
        if (!dir.exists()) dir.mkdirs();
        try (BufferedInputStream bis = new BufferedInputStream(SpiderApp.channel.get(element.name));
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(
                     new File(Environment.getExternalStorageDirectory() + "/" + SpiderApp.DOWNLOAD_FOLDER + "/" + element.shortName)))) {
            byte[] buffer = new byte[1024];
            int readCount;
            long progress = 0;
            long size = element.size;
            while ((readCount = bis.read(buffer)) > 0) {
                bos.write(buffer, 0, readCount);
                progress += buffer.length;
                final int percentage = (int) (progress * 100 / size);
                Log.d("DOWNLOAD", "size: " + size);
                Log.d("DOWNLOAD", "progress: " + progress);
                Log.d("DOWNLOAD", "Writing: " + percentage + "%");
                handler.post(() -> {
                    // If we get here, length is known, now set indeterminate to false
                    progressDialog.setIndeterminate(false);
                    progressDialog.setProgress(percentage);
                });
            }
            handler.post(() -> {
                if (wakeLock != null) wakeLock.release();
                progressDialog.dismiss();
                Toast.makeText(context, "File downloaded in: " + Environment.getExternalStorageDirectory() + "/" + SpiderApp.DOWNLOAD_FOLDER, Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            Log.e("DOWNLOAD", e.getMessage(), e);
            handler.post(() -> {
                if (wakeLock != null) wakeLock.release();
                progressDialog.dismiss();
                Toast.makeText(context, "Download error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }
}