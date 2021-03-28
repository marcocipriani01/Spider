package io.github.marcocipriani01.spider;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Vector;

public class FolderActivity extends AppCompatActivity {

    public static final String EXTRA_REMOTE_FOLDER = "REMOTE_FOLDER";
    private static final int STORAGE_PERMISSION_REQUEST = 20;
    private static final int READ_REQUEST_CODE_UPLOAD = 30;
    private final ArrayList<DirectoryElement> elements = new ArrayList<>();
    private final HandlerThread sftpThread = new HandlerThread("SFTP thread");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActionBar actionBar;
    private FolderAdapter adapter;
    private CoordinatorLayout coordinator;
    private String currentPath;
    private Handler sftpHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentPath = Objects.requireNonNull(getIntent().getStringExtra(EXTRA_REMOTE_FOLDER));
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
            sftpHandler.post(new GetFilesTask());
            swipeRefreshLayout.setRefreshing(false);
        });
        RecyclerView recyclerView = findViewById(R.id.folder_list);
        adapter = new FolderAdapter(elements);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addOnItemTouchListener(new FolderItemListener(this, recyclerView));

        sftpThread.start();
        sftpHandler = new Handler(sftpThread.getLooper());
        sftpHandler.post(new GetFilesTask());
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
        sftpHandler.post(new GetFilesTask(".."));
    }

    @Override
    public void finish() {
        super.finish();
        sftpThread.quit();
        SpiderApp.channel.disconnect();
        SpiderApp.channel = null;
        SpiderApp.session.disconnect();
        SpiderApp.session = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if ((requestCode == READ_REQUEST_CODE_UPLOAD) && (resultCode == Activity.RESULT_OK) && (resultData != null))
            sftpHandler.post(new UploadTask(resultData.getData(), currentPath));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((requestCode == STORAGE_PERMISSION_REQUEST) && (grantResults[0] != PackageManager.PERMISSION_GRANTED))
            Snackbar.make(coordinator, R.string.storage_permission_required, Snackbar.LENGTH_SHORT).show();
    }

    private class GetFilesTask implements Runnable {

        private final String TAG = SpiderApp.getTag(GetFilesTask.class);
        private final String targetPath;

        GetFilesTask() {
            this.targetPath = null;
        }

        GetFilesTask(String dir) {
            if (dir.equals("..")) { // Check if going up
                if (currentPath.equals("/")) {
                    this.targetPath = null;
                } else if (currentPath.endsWith("/")) {
                    String tmp = currentPath.substring(0, currentPath.lastIndexOf("/"));
                    this.targetPath = tmp.substring(0, tmp.lastIndexOf("/") + 1);
                } else {
                    this.targetPath = currentPath.substring(0, currentPath.lastIndexOf("/") + 1);
                }
            } else {
                if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 2);
                if (currentPath.equals("/") || currentPath.endsWith("/")) {
                    this.targetPath = currentPath + dir;
                } else {
                    this.targetPath = currentPath + "/" + dir;
                }
            }
            Log.d(TAG, "Going to " + dir + " (" + this.targetPath + ")");
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            try {
                Vector<ChannelSftp.LsEntry> list;
                if (targetPath == null) {
                    list = SpiderApp.channel.ls("*");
                } else {
                    SpiderApp.channel.cd(targetPath);
                    list = SpiderApp.channel.ls("*");
                    currentPath = targetPath;
                }
                handler.post(() -> {
                    elements.clear();
                    if (!currentPath.equals("/"))
                        elements.add(new DirectoryElement("..", true, 0, null));
                    for (ChannelSftp.LsEntry entry : list) {
                        elements.add(new DirectoryElement(entry.getFilename(), entry.getAttrs().isDir(),
                                entry.getAttrs().getSize(), entry));
                    }
                    Collections.sort(elements);
                    adapter.notifyDataSetChanged();
                    actionBar.setTitle(currentPath);
                });
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> Snackbar.make(coordinator,
                        String.format(getString(R.string.read_error), e.getMessage()), Snackbar.LENGTH_SHORT).show());
            }
        }
    }

    private class UploadTask implements Runnable {

        private final String TAG = SpiderApp.getTag(UploadTask.class);
        private final Uri file;
        private final String path;
        private final ProgressDialog progressDialog;
        private PowerManager.WakeLock wakeLock;

        UploadTask(Uri file, String remotePath) {
            this.file = file;
            this.path = remotePath;
            // Take CPU lock to prevent CPU from going off if the user presses the power button during download
            PowerManager pm = (PowerManager) FolderActivity.this.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
                wakeLock.acquire(10 * 60);
            }
            progressDialog = new ProgressDialog(FolderActivity.this);
            progressDialog.setMessage(String.format(getString(R.string.uploading), file.getLastPathSegment()));
            progressDialog.setMax(100);
            progressDialog.setIndeterminate(true);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        public void run() {
            String name = null;
            if (file.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(file, null, null, null, null)) {
                    if ((cursor != null) && cursor.moveToFirst())
                        name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
            if (name == null) {
                name = file.getPath();
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
                    sftpHandler.post(new GetFilesTask());
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

    private class DownloadTask implements Runnable {

        private final String TAG = SpiderApp.getTag(DownloadTask.class);
        private final DirectoryElement element;
        private final ProgressDialog progressDialog;
        private PowerManager.WakeLock wakeLock;

        DownloadTask(DirectoryElement element) {
            this.element = element;
            // Take CPU lock to prevent CPU from going off if the user presses the power button during download
            PowerManager pm = (PowerManager) FolderActivity.this.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
                wakeLock.acquire(10 * 60);
            }
            progressDialog = new ProgressDialog(FolderActivity.this);
            progressDialog.setMessage(String.format(getString(R.string.downloading_message), element.shortName, element.sizeMB));
            progressDialog.setMax(100);
            progressDialog.setIndeterminate(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        public void run() {
            File dir = new File(Environment.getExternalStorageDirectory() + "/" + SpiderApp.DOWNLOAD_FOLDER);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(Environment.getExternalStorageDirectory() + "/" + SpiderApp.DOWNLOAD_FOLDER + "/" + element.shortName);
            try (BufferedInputStream bis = new BufferedInputStream(SpiderApp.channel.get(element.name));
                 BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(
                         file))) {
                byte[] buffer = new byte[1024];
                int readCount;
                long progress = 0;
                long size = element.size;
                while ((readCount = bis.read(buffer)) > 0) {
                    bos.write(buffer, 0, readCount);
                    progress += buffer.length;
                    final int percentage = (int) (progress * 100 / size);
                    handler.post(() -> progressDialog.setProgress(percentage));
                }
                handler.post(() -> {
                    if (wakeLock != null) wakeLock.release();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, String.format(getString(R.string.file_downloaded_message),
                            Environment.getExternalStorageDirectory() + "/" + SpiderApp.DOWNLOAD_FOLDER),
                            Snackbar.LENGTH_SHORT).setAction("Open", v -> {
                        Intent intent = new Intent();
                        intent.setDataAndType(FileProvider.getUriForFile(FolderActivity.this,
                                BuildConfig.APPLICATION_ID + ".provider", file), "*/*");
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, "Open file"));
                    }).show();
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

    private class DeleteTask implements Runnable {

        private final String TAG = SpiderApp.getTag(DeleteTask.class);
        private final DirectoryElement element;

        DeleteTask(DirectoryElement element) {
            this.element = element;
        }

        @Override
        public void run() {
            try {
                String path = currentPath + (currentPath.endsWith("/") ? "" : "/") + element.name;
                if (element.isDirectory) {
                    recursiveFolderDelete(path);
                } else {
                    SpiderApp.channel.rm(path);
                }
                sftpHandler.post(new GetFilesTask());
                handler.post(() -> Snackbar.make(coordinator,
                        getString(R.string.deleted_ok), Snackbar.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> Snackbar.make(coordinator,
                        String.format(getString(R.string.delete_error), e.getMessage()),
                        Snackbar.LENGTH_SHORT).show());
            }
        }

        /**
         * @see <a href="https://stackoverflow.com/a/41490348/6267019">Source</a>
         */
        @SuppressWarnings("unchecked")
        private void recursiveFolderDelete(String path) throws SftpException {
            // List source directory structure.
            Collection<ChannelSftp.LsEntry> list = SpiderApp.channel.ls(path);
            // Iterate objects in the list to get file/folder names.
            for (ChannelSftp.LsEntry item : list) {
                String filename = item.getFilename();
                if (item.getAttrs().isDir()) {
                    if (!(".".equals(filename) || "..".equals(filename))) { // If it is a sub-directory
                        try {
                            SpiderApp.channel.rmdir(path + "/" + filename);
                        } catch (Exception e) {
                            // If sub-directory is not empty and error occurs,
                            // repeat on this directory to clear its contents.
                            recursiveFolderDelete(path + "/" + filename);
                        }
                    }
                } else {
                    SpiderApp.channel.rm(path + "/" + filename); // Remove file.
                }
            }
            SpiderApp.channel.rmdir(path); // delete the parent directory after empty
        }
    }

    private class FolderItemListener implements RecyclerView.OnItemTouchListener {

        private final GestureDetector gestureDetector;

        FolderItemListener(Context context, RecyclerView recyclerView) {
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (child != null) {
                        if (ContextCompat.checkSelfPermission(FolderActivity.this,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                            DirectoryElement element = elements.get(recyclerView.getChildAdapterPosition(child));
                            if (element.isDirectory) {
                                new AlertDialog.Builder(FolderActivity.this).setTitle(R.string.app_name)
                                        .setMessage(String.format(getString(R.string.delete_folder_question), element.name))
                                        .setIcon(R.drawable.delete)
                                        .setPositiveButton(android.R.string.ok, (dialog, which) ->
                                                sftpHandler.post(new DeleteTask(element)))
                                        .setNegativeButton(android.R.string.cancel, null).show();
                            } else if (element.isLink()) {
                                Snackbar.make(coordinator, R.string.links_unsupported, Snackbar.LENGTH_SHORT).show();
                            } else {
                                new AlertDialog.Builder(FolderActivity.this).setTitle(R.string.app_name)
                                        .setMessage(String.format(getString(R.string.select_action), element.name))
                                        .setIcon(R.drawable.file)
                                        .setPositiveButton(R.string.download, (dialog, which) ->
                                                sftpHandler.post(new DownloadTask(element)))
                                        .setNeutralButton(R.string.delete, (dialog, which) ->
                                                sftpHandler.post(new DeleteTask(element)))
                                        .setNegativeButton(android.R.string.cancel, null).show();
                            }
                        } else {
                            ActivityCompat.requestPermissions(FolderActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
                        }
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent e) {
            View childView = view.findChildViewUnder(e.getX(), e.getY());
            if ((childView != null) && gestureDetector.onTouchEvent(e)) {
                DirectoryElement element = elements.get(view.getChildAdapterPosition(childView));
                if (element.isDirectory || element.isLink())
                    sftpHandler.post(new GetFilesTask(element.name));
                return true;
            }
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent motionEvent) {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        }
    }
}