package com.treasure.pcboard;

import android.content.*;
import android.net.Uri;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;

public final class RichContentHelper {
    public static final class ClipboardContent {
        public final Uri uri;
        public final String mimeType;
        public final String label;

        ClipboardContent(Uri uri, String mimeType, String label) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.label = label;
        }
    }

    private final Context context;

    public RichContentHelper(Context context) {
        this.context = context;
    }

    public ClipboardContent currentClipboardContent() {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) return null;
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        Uri uri = clip.getItemAt(0).getUri();
        if (uri == null) return null;
        String mime = context.getContentResolver().getType(uri);
        if (mime == null && clip.getDescription() != null && clip.getDescription().getMimeTypeCount() > 0) {
            mime = clip.getDescription().getMimeType(0);
        }
        if (mime == null) mime = "application/octet-stream";
        String label = clip.getDescription() == null || clip.getDescription().getLabel() == null
                ? "Clipboard content" : clip.getDescription().getLabel().toString();
        return new ClipboardContent(uri, mime, label);
    }

    public boolean canCommit(EditorInfo editorInfo, ClipboardContent content) {
        if (content == null || editorInfo == null || editorInfo.contentMimeTypes == null) return false;
        for (String accepted : editorInfo.contentMimeTypes) {
            if (mimeMatches(content.mimeType, accepted)) return true;
        }
        return false;
    }

    public boolean commit(InputConnection connection, EditorInfo editorInfo, ClipboardContent content) {
        if (connection == null || !canCommit(editorInfo, content)) return false;
        ClipDescription description = new ClipDescription(content.label, new String[]{content.mimeType});
        InputContentInfo inputContentInfo = new InputContentInfo(content.uri, description, null);
        try {
            return connection.commitContent(inputContentInfo,
                    InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
        } catch (SecurityException | IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean mimeMatches(String concrete, String accepted) {
        if (concrete == null || accepted == null) return false;
        if (accepted.equals("*/*") || accepted.equals(concrete)) return true;
        int slash = accepted.indexOf('/');
        return slash > 0 && accepted.endsWith("/*") && concrete.startsWith(accepted.substring(0, slash + 1));
    }
}
