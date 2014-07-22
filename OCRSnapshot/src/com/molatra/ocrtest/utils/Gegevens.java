package com.molatra.ocrtest.utils;

/** File containing application constants  */
public class Gegevens {
	
	public static final boolean debug	= true;

	/** Application package names */
	public static final String APP_NAME 			= "com.molatra.ocrtest";
	
	/** Preferences */
	public static final String PREF_LANGUAGE 		= "language";

	/** Intent and Bundle Extras */
	public static final String EXTRA_MSGID			= "msgId";
	public static final String EXTRA_LANGUAGE		= "chi_sim";
	public static final String EXTRA_MESSENGER		= "messenger";
	public static final String EXTRA_URI			= "uri";
	public static final String EXTRA_STATE			= "state";
	
	/** Folder and file names */
	public static final String FILE_EXT_MP3 		= ".mp3";
	public static final String FILE_EXT_RAW 		= ".raw";
	public static final String FILE_EXT_WAV 		= ".wav";
	public static final String FILE_EXT_TXT 		= ".txt";
	public static final String FILE_EXT_LOG 		= ".log";
	public static final String FILE_EXT_JET 		= ".jet";
	public static final String FILE_EXT_JPG 		= ".jpg";
	public static final String FILE_EXT_PNG 		= ".png";
	public static final String FILE_EXT_GIF 		= ".gif";
	public static final String FILE_EXT_DAT 		= ".dat";
	public static final String FILE_SEPARATOR 		= "/"; 
	public static final String FILE_CACHE			= "cache";
	public static final String FILE_CAMERAIMG		= "ocrsnapshotphoto";
	public static final String FILE_INPUTIMG		= "ocrsnapshotinput";
	public static final String FILE_IMAGES			= "images";
	public static final String FILE_FILES			= "files";
	public static final String FILE_USERDIRPHONE	= "data" + FILE_SEPARATOR + APP_NAME; 
	public static final String FILE_USERDIRSD 		= "Android" + FILE_SEPARATOR + "data" + FILE_SEPARATOR + APP_NAME;
	public static final String FILE_DUMMYCHECK		= "dummycheck.dat";

	/** Fragment tags */
	public static final String FRAG_DIALOG 			= "dialog";
	
	/** Request and result codes */
	public static final int CODE_CAMERA 			= 86001;
	public static final int CODE_GALLERY 			= 86002;
	
	/** Frequently used URLs. Note: there is no trailing slash. */
	public static final String URL_ANDROID_MARKET 		= "http://market.android.com";

}