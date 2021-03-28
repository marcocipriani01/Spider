package io.github.marcocipriani01.spider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    private final List<DirectoryElement> directoryElement;

    public FolderAdapter(List<DirectoryElement> dir) {
        directoryElement = dir;
    }

    @NonNull
    @Override
    public FolderAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View contactView = inflater.inflate(R.layout.item_connection, parent, false);
        return new ViewHolder(contactView);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderAdapter.ViewHolder viewHolder, int position) {
        DirectoryElement directoryElement = this.directoryElement.get(position);
        if (directoryElement.isDirectory) {
            viewHolder.icon.setImageResource(R.drawable.folder);
        } else if (directoryElement.isLink()) {
            viewHolder.icon.setImageResource(R.drawable.link);
        } else {
            viewHolder.icon.setImageResource(R.drawable.file);
        }
        viewHolder.filename.setText(directoryElement.name);
    }

    @Override
    public int getItemCount() {
        return directoryElement.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public TextView filename;
        public ImageView icon;

        public ViewHolder(View itemView) {
            super(itemView);
            filename = itemView.findViewById(R.id.filename);
            icon = itemView.findViewById(R.id.icon);
        }
    }
}