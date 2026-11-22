package co.themax.fixture;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

public class ChatProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(), "chat.txt"), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri u,String[] cols,String where,String[] args,String sort) { return null; }
    @Override public int delete(Uri u,String where,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v,String where,String[] args) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri u,ContentValues v) { throw new UnsupportedOperationException(); }
}
