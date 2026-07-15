package com.kingalexgilbert.kingpost;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * Minimal read-only provider for videos copied into this app's private cache/share directory.
 * The provider is not exported; access is granted temporarily through the share Intent.
 */
public final class ShareFileProvider extends ContentProvider {

    public static final String PATH_SEGMENT = "video";

    public static Uri uriForFile(android.content.Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".shareprovider")
                .appendPath(PATH_SEGMENT)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        File file = resolveFile(uri);
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null && mime.startsWith("video/")) {
                return mime;
            }
        }
        return "video/*";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = resolveFile(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;

        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(file.getName());
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(file.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider");
        }
        File file = resolveFile(uri);
        if (!file.isFile()) {
            throw new FileNotFoundException("Shared video is unavailable");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    private File resolveFile(Uri uri) {
        if (getContext() == null
                || uri == null
                || uri.getPathSegments().size() != 2
                || !PATH_SEGMENT.equals(uri.getPathSegments().get(0))) {
            throw new SecurityException("Invalid shared-video URI");
        }

        String requestedName = uri.getLastPathSegment();
        if (requestedName == null || requestedName.isEmpty()) {
            throw new SecurityException("Missing shared-video name");
        }

        File shareDirectory = new File(getContext().getCacheDir(), "share");
        File requestedFile = new File(shareDirectory, requestedName);
        try {
            String directoryPath = shareDirectory.getCanonicalPath() + File.separator;
            String filePath = requestedFile.getCanonicalPath();
            if (!filePath.startsWith(directoryPath)) {
                throw new SecurityException("Invalid shared-video path");
            }
            return requestedFile;
        } catch (IOException exception) {
            throw new SecurityException("Could not resolve shared-video path", exception);
        }
    }
}
