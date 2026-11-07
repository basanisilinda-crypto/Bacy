package za.co.loopmanager;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Shell-only, read-only USB status. Never exposes customer identifiers or credentials. */
public final class OwnershipStatusProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        String nonce=uri.getQueryParameter("nonce");
        if(!"/status".equals(uri.getPath())||nonce==null||!nonce.matches("[0-9]{2,20}"))throw new IllegalArgumentException("Invalid ownership status request");
        String[] fields=OwnershipCheckReceiver.inspect(getContext()).split(";");
        MatrixCursor result=new MatrixCursor(new String[]{"nonce","owner","active","linked"});
        result.addRow(new Object[]{nonce,fields[0].substring(6),fields[1].substring(7),fields[2].substring(7)});return result;
    }
    @Override public String getType(Uri uri){return "vnd.android.cursor.item/vnd.loopsecure.usb-status";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only USB status");}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException("Read-only USB status");}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException("Read-only USB status");}
}
