package com.kris.test;

import android.net.Uri;
import android.provider.BaseColumns;

public final class TableContract {
	public static final String AUTHORITY = "com.kris.provider.test";
	public static final String SCHEME = "content://";
	
	/** Don't load */
	private TableContract(){}
	
	public static final class Words implements BaseColumns {
		private Words() {}
		
        public static final String TABLE_NAME 		= "words";
        public static final String PATH 			= "/words";
        public static final String PATH_ID 			= "/words/";
        public static final int ID_PATH_POSITION 	= 1;
        
        public static final Uri CONTENT_URI 			= Uri.parse(SCHEME + AUTHORITY + PATH);
        public static final Uri CONTENT_ID_URI_BASE 	= Uri.parse(SCHEME + AUTHORITY + PATH_ID);
        public static final Uri CONTENT_ID_URI_PATTERN 	= Uri.parse(SCHEME + AUTHORITY + PATH_ID + "/#");

        public static final String CONTENT_TYPE 		= "vnd.android.cursor.dir/vnd.kris.words";
        public static final String CONTENT_ITEM_TYPE 	= "vnd.android.cursor.item/vnd.kris.words";

        public static final String COLUMN_WORDID = "wordId";
        public static final String COLUMN_SIMPLIFIED_CHINESE = "simplifiedChinese";
        public static final String COLUMN_TRADITIONAL_CHINESE = "traditionalChinese";
        public static final String COLUMN_COUNT = "count";

        public static final String DEFAULT_SORT_ORDER 	= COLUMN_WORDID +" ASC";
	}
	
	public static final class Playlists implements BaseColumns {
		private Playlists() {}
		
        public static final String TABLE_NAME 		= "playlists";
        public static final String PATH 			= "/playlists";
        public static final String PATH_ID 		= "/playlists/";
        public static final int ID_PATH_POSITION 	= 1;
        
        public static final Uri CONTENT_URI 			= Uri.parse(SCHEME + AUTHORITY + PATH);
        public static final Uri CONTENT_ID_URI_BASE 	= Uri.parse(SCHEME + AUTHORITY + PATH_ID);
        public static final Uri CONTENT_ID_URI_PATTERN 	= Uri.parse(SCHEME + AUTHORITY + PATH_ID + "/#");

        public static final String CONTENT_TYPE 		= "vnd.android.cursor.dir/vnd.kris.playlists";
        public static final String CONTENT_ITEM_TYPE 	= "vnd.android.cursor.item/vnd.kris.playlists";

    	public static final String COLUMN_PLAYLIST_NAME = "name";
    	public static final String COLUMN_PLAYLISTID 	= "playListId";
    	public static final String COLUMN_MISSING_WORDS = "missingWords";
    	public static final String COLUMN_SIZE  		= "size";		

    	public static final String DEFAULT_SORT_ORDER 	= COLUMN_PLAYLISTID +" ASC";
	}

}
