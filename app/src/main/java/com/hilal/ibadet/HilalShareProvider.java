package com.hilal.ibadet;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;

public class HilalShareProvider extends ContentProvider {
    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + ".sharefiles")
                .appendPath(file.getName()).build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) throw new FileNotFoundException();
        File file = new File(new File(getContext().getCacheDir(), "shared_virds"), name);
        if (!file.isFile()) throw new FileNotFoundException();
        return file;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "application/octet-stream" : mime;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            cursor.addRow(new Object[]{file.getName().replaceFirst("^\\d+-", ""), file.length()});
            return cursor;
        } catch (Exception ignored) { return null; }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
