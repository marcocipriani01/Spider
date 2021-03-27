package io.github.marcocipriani01.spider;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

public class FolderActivity extends AppCompatActivity {

    private static final int READ_REQUEST_CODE_UPLOAD = 84;
    private final PathHandler pathHandler = new PathHandler();
    private final ArrayList<DirectoryElement> elements = new ArrayList<>();
    private ActionBar actionBar;
    private FolderAdapter adapter;
    private CoordinatorLayout coordinator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);
        coordinator = findViewById(R.id.folder_activity_coordinator);
        actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        this.<FloatingActionButton>findViewById(R.id.upload_fab).setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, READ_REQUEST_CODE_UPLOAD);
        });

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_view);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            new GetFilesTask(pathHandler.getCurrentPath()).start();
            swipeRefreshLayout.setRefreshing(false);
        });
        RecyclerView recyclerView = findViewById(R.id.folder_list);
        adapter = new FolderAdapter(elements);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(this, recyclerView, new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        DirectoryElement element = elements.get(position);
                        if (element.isDirectory || element.sftpInfo.getAttrs().isLink()) {
                            pathHandler.updatePath(element.name);
                            new GetFilesTask(pathHandler.getCurrentPath()).start();
                        }
                    }

                    @Override
                    public void onLongItemClick(View view, int position) {
                        DirectoryElement element = elements.get(position);
                        if (!element.isDirectory && !element.sftpInfo.getAttrs().isLink())
                            new DownloadTask(element).start();
                    }
                })
        );
        new GetFilesTask(pathHandler.getCurrentPath()).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        pathHandler.updatePath("..");
        new GetFilesTask(pathHandler.getCurrentPath()).start();
    }

    @Override
    public void finish() {
        super.finish();
        SpiderApp.channel.disconnect();
        SpiderApp.session.disconnect();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == READ_REQUEST_CODE_UPLOAD && resultCode == Activity.RESULT_OK) {
            if (resultData != null)
                new UploadTask(resultData.getData(), SpiderApp.currentPath).start();
        }
    }

    private class GetFilesTask extends Thread {

        private final String TAG = SpiderApp.getTag(GetFilesTask.class);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final String path;

        public GetFilesTask(String path) {
            super();
            this.path = path;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            Log.d(TAG, "Started do on background");
            try {
                Session session = SpiderApp.session;
                Log.d(TAG, "session is: " + session.getHost());
                SpiderApp.channel.cd(path);
                final Vector<ChannelSftp.LsEntry> list = SpiderApp.channel.ls("*");
                Log.d(TAG, "Files are:");
                for (ChannelSftp.LsEntry entry : list) {
                    Log.d(TAG, entry.getFilename());
                }
                handler.post(() -> {
                    elements.clear();
                    elements.add(new DirectoryElement("..", true, 0, null));
                    if (list != null) {
                        for (ChannelSftp.LsEntry entry : list) {
                            Log.d(TAG, "Adding to elements:" + entry.getFilename());
                            elements.add(new DirectoryElement(entry.getFilename(), entry.getAttrs().isDir(),
                                    entry.getAttrs().getSize(), entry));
                        }
                        Collections.sort(elements);
                    }
                    adapter.notifyDataSetChanged();
                    actionBar.setTitle(SpiderApp.currentPath);
                });
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    public class UploadTask extends Thread {

        private final String TAG = SpiderApp.getTag(UploadTask.class);
        private final Uri file;
        private final String path;
        private final ProgressDialog progressDialog;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private PowerManager.WakeLock wakeLock;

        public UploadTask(Uri file, String remotePath) {
            this.file = file;
            this.path = remotePath;
            // Take CPU lock to prevent CPU from going off if the user presses the power button during download
            PowerManager pm = (PowerManager) FolderActivity.this.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
                wakeLock.acquire(10 * 60);
            }
            progressDialog = new ProgressDialog(FolderActivity.this);
            progressDialog.setMessage("Uploading: " + file.getLastPathSegment());
            progressDialog.setMax(100);
            progressDialog.setIndeterminate(true);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        public void run() {
            String name = file.getLastPathSegment();
            if (name != null) {
                int cut = name.lastIndexOf('/');
                if (cut != -1) name = name.substring(cut + 1);
            }
            try (InputStream bis = getContentResolver().openInputStream(file);
                 BufferedOutputStream bos = new BufferedOutputStream(SpiderApp.channel.put(path + "/" + name))) {
                byte[] buffer = new byte[1024];
                Log.d(TAG, "Destination: " + path + "/" + name);
                int readCount;
                long progress = 0;
                long size = bis.available();
                Log.d(TAG, "Upload size: " + size);
                while ((readCount = bis.read(buffer)) > 0) {
                    bos.write(buffer, 0, readCount);
                    progress += buffer.length;
                    final int percentage = (int) (progress * 100 / size);
                    Log.d(TAG, "Writing: " + percentage + "%");
                    handler.post(() -> {
                        // If we get here, length is known, now set indeterminate to false
                        progressDialog.setIndeterminate(false);
                        progressDialog.setProgress(percentage);
                    });
                }
                handler.post(() -> {
                    if (wakeLock != null) wakeLock.release();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, R.string.file_uploaded, Snackbar.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> {
                    if (wakeLock != null) wakeLock.release();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, String.format(getString(R.string.upload_error), e.getMessage()), Snackbar.LENGTH_SHORT).show();
                });
            }
        }
    }

    public class DownloadTask extends Thread {

        private final String TAG = SpiderApp.getTag(DownloadTask.class);
        private final DirectoryElement element;
        private final ProgressDialog progressDialog;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private PowerManager.WakeLock wakeLock;

        public DownloadTask(DirectoryElement element) {
            super();
            this.element = element;
            // Take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager pm = (PowerManager) FolderActivity.this.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
                wakeLock.acquire(10 * 60);
            }
            progressDialog = new ProgressDialog(FolderActivity.this);
            progressDialog.setMessage(String.format(getString(R.string.downloading_message), element.shortName, element.sizeMB));
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
                    Log.d(TAG, "size: " + size);
                    Log.d(TAG, "progress: " + progress);
                    Log.d(TAG, "Writing: " + percentage + "%");
                    handler.post(() -> {
                        // If we get here, length is known, now set indeterminate to false
                        progressDialog.setIndeterminate(false);
                        progressDialog.setProgress(percentage);
                    });
                }
                handler.post(() -> {
                    if (wakeLock != null) wakeLock.release();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, String.format(getString(R.string.file_downloaded_message),
                            Environment.getExternalStorageDirectory() + "/" + SpiderApp.DOWNLOAD_FOLDER),
                            Snackbar.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> {
                    if (wakeLock != null) wakeLock.release();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, String.format(getString(R.string.download_error), e.getMessage()),
                            Snackbar.LENGTH_SHORT).show();
                });
            }
        }
    }
}