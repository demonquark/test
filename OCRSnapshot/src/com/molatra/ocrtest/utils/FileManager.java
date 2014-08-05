package com.molatra.ocrtest.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

public class FileManager {
	
	public static String TAG = "FileManager";
	
	public static boolean copyAssetsToSDCard(AssetManager am, boolean removeJet, String path, String output){
	    boolean copied = false;
		try {
		    String [] assets = am.list(path);
	        if (assets.length == 0) { 
	        	// if there are no children, this is either a file or an empty folder
	        	if(!(new File(output)).exists()){ copyAssetFileToCard(am, removeJet, path, output);  }
	        } else {
	        	// if there are children, this is clearly a folder
	            File outputFile = new File(output);
	            if (!outputFile.exists()) { outputFile.mkdir(); }
	            // copy the folder content
	            for (int i = 0; i < assets.length; ++i) { 
	            	copyAssetsToSDCard(am, removeJet,
	            			path + Gegevens.FILE_SEPARATOR + assets[i], 
	            			output + Gegevens.FILE_SEPARATOR + assets[i]); }
	        }
	        copied = true;
	    } catch (IOException ex) { Log.e(TAG, "I/O Exception " + ex.getMessage()); }
		
		return copied;
	}
	
	public static boolean copyAssetFileToCard(AssetManager am, boolean removeJet, String src, String dst) {
		boolean copied = false;
		
	    if(!removeJet || !( src.endsWith(Gegevens.FILE_EXT_JET) && new File(dst.replace(Gegevens.FILE_EXT_JET, "")).exists() )){ 
		    int read;
	        byte[] buffer = new byte[1024];
		    InputStream in = null;
		    OutputStream out = null;
		    try {
		    	in = am.open(src);
		    	out = new FileOutputStream(dst);
		        while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); }
		    	copied = true;
		    } catch (Exception e) { 
		    	Log.e(TAG, "Could not copy "+ src + " to " + dst + ". "+ e.getMessage()); 
		    } finally { 
		    	if(in != null){ try { in.close(); 
		    	} catch (IOException e) { e.printStackTrace(); } in = null; } 
		    	if(out != null){ try { out.flush(); out.close(); 
		    	} catch (IOException e) { e.printStackTrace(); } out = null; } 
		    }
		    
		    if(removeJet && dst.endsWith(Gegevens.FILE_EXT_JET)){ 
		    	(new File (dst)).renameTo(new File(dst.replace(Gegevens.FILE_EXT_JET, ""))); 
		    }
	    }
	    
	    return copied;
	}
	
	public static void deleteFile(File file){
		if (file.isDirectory()) {
	        String[] children = file.list();
	        for (int i = 0; i < children.length; i++) {
	        	deleteFile(new File(file, children[i]));
	        }
	    } else {
	    	file.delete();
	    }
	}
	
	
	/**
	 * <p>Reads and returns the content of a file as UTF-8 String.</p>
	 * 
	 * @param file the absolute path to the file
	 * @return String containing file content or null if file does not exist
	 */
	public static String readStringFromFile(File file) {
        Log.v(TAG, "Reading file: " + file.getName()+ ((file.exists() ? " (exists)" : " (does not exist)")));
		
		// make sure we have a file
		if(!file.exists()){ return null; }
		
        byte[] b  = new byte[(int)file.length()];
		int len = b.length;

		InputStream in = null;
		try{
			in = new FileInputStream(file);
			int total = 0;
			while (total < len) {
				int result = in.read(b, total, len - total);
				if (result == -1) { break; }
				total += result;
			}
		} catch (IOException e) { e.printStackTrace(); 
		} finally {
			if (in != null) { 
				try { in.close(); } catch (IOException e) { e.printStackTrace(); } 
			} 
		}
		
//        Loggen.v(FileManager.class, "Reading file: " + Charset.forName("UTF-8").decode(ByteBuffer.wrap(b)).toString());

        return Charset.forName("UTF-8").decode(ByteBuffer.wrap(b)).toString();
	}

	/**
	 * <p>Writes a (UTF-8) String to a file. The method will overwrite an existing file.</p>
	 * 
	 * @param file the absolute path to the file
	 * @param string the String you wish to write to the file
	 * @return String containing file content or null if file does not exist
	 */
	public static boolean writeStringToFile(File file, String string) {
        Log.v(TAG, "Writing to file: " + file.getName() );
		
        boolean succesful = false;
        
		// make sure we have a file
        
		if(file.getParentFile() != null){ file.getParentFile().mkdirs(); }
		Writer out = null;
		try { 
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(file.getAbsolutePath()), "UTF8"));
			out.write(string);
			out.flush();
			out.close();
			succesful = true;
			
		} catch (UnsupportedEncodingException e) { e.printStackTrace();
		} catch (IOException e)  { e.printStackTrace();
		} catch (Exception e) { e.printStackTrace(); 
		} finally {
			if (out != null) { 
				try { out.close(); } catch (IOException e) { e.printStackTrace(); } 
			} 
		}
		
		return succesful;
	}
	
	
	public static void writeBitmapToFile(String filename, Bitmap bmp) throws IOException{
		FileOutputStream out = new FileOutputStream(filename);
		bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
		out.close();
	}
	
	public static Bitmap readBitmapFromFile(File file, int reqWidth, int reqHeight) {
		return BitmapTools.decodeSampledBitmapFromResource(file.getAbsolutePath(), reqWidth, reqHeight);
	}

	public static Bitmap readBitmapFromFile(File file) {
	    // return the read bitmap
	    return BitmapFactory.decodeFile(file.getAbsolutePath());
	}
	
	public static void deleteAssetsFromSDCard(){
		FileManager.deleteFile(new File(Environment.getExternalStorageDirectory(), 
				Gegevens.FILE_USERDIRSD + Gegevens.FILE_SEPARATOR + Gegevens.FILE_CACHE));
	}

	public static void copyAssetsToSDCard(Context c){
		
		// Get the application directory
		File appdir = new File(Environment.getExternalStorageDirectory(),Gegevens.FILE_USERDIRSD);
		
		// Check if the dummy data has already been copied (bit of a hack)
		File dummycheck = new File (appdir, 
				Gegevens.FILE_FILES + Gegevens.FILE_SEPARATOR + Gegevens.FILE_DUMMYCHECK);

		if(!dummycheck.exists()){
			Log.v(TAG, "Copying assets to the SD card.");
			// copy the asset files to the SD card
			if(!appdir.exists()){ appdir.mkdirs(); }
			FileManager.copyAssetsToSDCard(c.getAssets(), false,
					Gegevens.APP_NAME, appdir.getAbsolutePath());
		}

	}
	
	public static boolean isExternalStorageAvailable() {
		return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
	}

	public static File getApplicationFileDir(Context c, String folderName) {
		return new File(getApplicationFileDir(c), folderName);
	}
	
	public static File getApplicationFileDir(Context c) {
		File ocrDir;
		
		if(FileManager.isExternalStorageAvailable()){
			// If we have access to the external card. Create a folder there. 
			if(c != null){
				ocrDir = c.getExternalFilesDir(null);
			} else {
				// If we have access to the external card. Create a folder there. 
				ocrDir = new File(Environment.getExternalStorageDirectory(), 
						Gegevens.FILE_USERDIRSD + Gegevens.FILE_SEPARATOR + Gegevens.FILE_FILES);
			}
		} else {
			// If we have no access to the external card. Create a subfolder in the data folder
			if(c != null){
				ocrDir = c.getFilesDir();
			} else {
				// If we have no access to the external card. Create a subfolder in the data folder 
				ocrDir = new File(Environment.getDataDirectory(), 
						Gegevens.FILE_USERDIRPHONE + Gegevens.FILE_SEPARATOR + Gegevens.FILE_FILES);
			}
		}

		// Make sure the directory exists
		if(!ocrDir.exists()){ ocrDir.mkdirs(); }
		Log.v(TAG, "Application file directory is: " + ocrDir.getAbsolutePath());

		return ocrDir;
	}

}
