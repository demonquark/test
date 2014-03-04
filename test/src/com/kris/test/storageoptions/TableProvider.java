package com.kris.test.storageoptions;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

public class TableProvider extends ContentProvider implements PipeDataWriter<Cursor> {

    private static final String TAG = "Class TableProvider";
    private static final String DATABASE_NAME = "test.db";
    private static final int DATABASE_VERSION = 2;
    private static HashMap<String, String> sNotesProjectionMap;

    private static final String[] READ_WORDS_PROJECTION = new String[] {
            TableContract.Words._ID,
            TableContract.Words.COLUMN_WORDID,  
            TableContract.Words.COLUMN_TRADITIONAL_CHINESE,
            TableContract.Words.COLUMN_SIMPLIFIED_CHINESE, 
            TableContract.Words.COLUMN_COUNT, 
    };
    
    private static final int READ_WORD_WORDID_INDEX = 1;
    private static final int READ_WORD_TRADITIONAL_CHINESE = 2;
    private static final int READ_WORD_COUNT_INDEX = 4;

    // The incoming URI matches the Notes URI pattern
    private static final int WORDS = 1;
    private static final int WORDS_ID = 2;
    private static final int PLAYLISTS = 3;
    private static final int PLAYLISTS_ID = 4;

    private static final UriMatcher sUriMatcher;
    private DatabaseHelper mOpenHelper;
    
    private static String happy = "a";

    static {

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        
        sUriMatcher.addURI(TableContract.AUTHORITY, TableContract.Words.TABLE_NAME, WORDS);
        sUriMatcher.addURI(TableContract.AUTHORITY, TableContract.Words.TABLE_NAME+"/#", WORDS_ID);

        sNotesProjectionMap = new HashMap<String, String>();
        sNotesProjectionMap.put(TableContract.Words._ID, TableContract.Words._ID);
        sNotesProjectionMap.put(TableContract.Words.COLUMN_WORDID, TableContract.Words.COLUMN_WORDID);
        sNotesProjectionMap.put(TableContract.Words.COLUMN_SIMPLIFIED_CHINESE, TableContract.Words.COLUMN_SIMPLIFIED_CHINESE);
        sNotesProjectionMap.put(TableContract.Words.COLUMN_TRADITIONAL_CHINESE,TableContract.Words.COLUMN_TRADITIONAL_CHINESE);
        sNotesProjectionMap.put(TableContract.Words.COLUMN_COUNT,TableContract.Words.COLUMN_COUNT);
    }
	
    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) { super(context, DATABASE_NAME, null, DATABASE_VERSION); }
        
        @Override public void onCreate(SQLiteDatabase db) {
        	Log.i(TAG, "Creating a database " + TableContract.Words.TABLE_NAME);
            db.execSQL("CREATE TABLE " + TableContract.Words.TABLE_NAME + " ("
                    + TableContract.Words._ID + " INTEGER PRIMARY KEY,"
                    + TableContract.Words.COLUMN_WORDID + " INTEGER,"
                    + TableContract.Words.COLUMN_TRADITIONAL_CHINESE + " TEXT,"
                    + TableContract.Words.COLUMN_SIMPLIFIED_CHINESE + " INTEGER,"
                    + TableContract.Words.COLUMN_COUNT + " INTEGER"
                    + ");");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
            db.execSQL("DROP TABLE IF EXISTS notes");
            onCreate(db);
        }
    }

    @Override public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }
    
    
	public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType, Bundle opts, Cursor c) {
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_WORD_TRADITIONAL_CHINESE));
            pw.println("");
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) { pw.flush(); }
            try { fout.close(); } catch (IOException e) { }
        }
	}

	@Override public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;

        int count;

        switch (sUriMatcher.match(uri)) {
            case WORDS:
                count = db.delete( TableContract.Words.TABLE_NAME, where, whereArgs );
                break;
            case WORDS_ID:
            	finalWhere = TableContract.Words._ID + " = " + uri.getPathSegments().get(TableContract.Words.ID_PATH_POSITION);
                if (where != null) { finalWhere = finalWhere + " AND " + where; }
                count = db.delete( TableContract.Words.TABLE_NAME, finalWhere, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);

        // Returns the number of rows deleted.
        return count;
	}

	@Override public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
	        case WORDS:
	            return TableContract.Words.CONTENT_TYPE;
	        case WORDS_ID:
	            return TableContract.Words.CONTENT_ITEM_TYPE;
	        default:
	            throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override public Uri insert(Uri uri, ContentValues initialValues) {
		
		if (sUriMatcher.match(uri) != WORDS) { throw new IllegalArgumentException("Unknown URI " + uri); }

        if (initialValues == null) { return null; }

        // A map to hold the new record's values.
        ContentValues values; values = new ContentValues(initialValues);
        
        if (values.containsKey(TableContract.Words.COLUMN_WORDID) == false) { return null;  }

        if (values.containsKey(TableContract.Words.COLUMN_TRADITIONAL_CHINESE) == false) {
            values.put(TableContract.Words.COLUMN_TRADITIONAL_CHINESE, ""); }

        if (values.containsKey(TableContract.Words.COLUMN_SIMPLIFIED_CHINESE) == false) {
            values.put(TableContract.Words.COLUMN_SIMPLIFIED_CHINESE, ""); }

        if (values.containsKey(TableContract.Words.COLUMN_COUNT) == false) {
            values.put(TableContract.Words.COLUMN_COUNT, 1); }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert( TableContract.Words.TABLE_NAME, TableContract.Words.COLUMN_SIMPLIFIED_CHINESE, values );

        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(TableContract.Words.CONTENT_ID_URI_BASE, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            Log.i(TAG,"Inserted a row: "+ noteUri.toString());
            return noteUri;
        }

        // If the insert didn't succeed, then the rowID is <= 0. Throws an exception.
        throw new SQLException("Failed to insert row into " + uri);	
	}


	@Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

	       // Constructs a new query builder and sets its table name
	       SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
	       qb.setTables(TableContract.Words.TABLE_NAME); 
	       
	       switch (sUriMatcher.match(uri)) {
	           // If the incoming URI is for notes, chooses the Notes projection
	           case WORDS:
	               qb.setProjectionMap(sNotesProjectionMap);
	               break;
	           case WORDS_ID:
	               qb.setProjectionMap(sNotesProjectionMap);
	               qb.appendWhere( TableContract.Words._ID +  "=" +
	                   uri.getPathSegments().get(TableContract.Words.ID_PATH_POSITION));
	               break;
	           default:
	               // If the URI doesn't match any of the known patterns, throw an exception.
	               throw new IllegalArgumentException("Unknown URI " + uri);
	       }

	       String orderBy;
	       if (!TextUtils.isEmpty(sortOrder)) { orderBy = sortOrder; 
	       } else { orderBy = TableContract.Words.DEFAULT_SORT_ORDER;}

	       // Opens the database object in "read" mode, since no writes need to be done.
	       SQLiteDatabase db = mOpenHelper.getReadableDatabase();
	       Log.i(TAG, "Reading a database " + db.getVersion());
	       
	       Cursor c = qb.query(
	           db,            // The database to query
	           projection,    // The columns to return from the query
	           selection,     // The columns for the where clause
	           selectionArgs, // The values for the where clause
	           null,          // don't group the rows
	           null,          // don't filter by row groups
	           orderBy        // The sort order
	       );

	       // Tells the Cursor what URI to watch, so it knows when its source data changes
	       c.setNotificationUri(getContext().getContentResolver(), uri);
	       return c;
	}
	
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {

        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        if (mimeTypes != null) {

            // Retrieves the note for this URI. Uses the query method defined for this provider,
            // rather than using the database query method.
            Cursor c = query(
                    uri,                    // The URI of a note
                    READ_WORDS_PROJECTION,   // Gets a projection containing the note's ID, title,
                    null,                   // No WHERE clause, get all matching records
                    null,                   // Since there is no WHERE clause, no selection criteria
                    null                    // Use the default sort order (modification date,
                                            // descending
            );


            // If the query fails or the cursor is empty, stop
            if (c == null || !c.moveToFirst()) {

                // If the cursor is empty, simply close the cursor and return
                if (c != null) {
                    c.close();
                }

                // If the cursor is null, throw an exception
                throw new FileNotFoundException("Unable to query " + uri);
            }

            // Start a new thread that pipes the stream data back to the caller.
            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        // If the MIME type is not supported, return a read-only handle to the file.
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }	

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

}
