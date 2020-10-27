package com.zackporter.creds;

import java.io.Console;
import java.util.List;

import com.zackporter.box.Main;
import com.zackporter.logging.Log;
import com.zackporter.util.Encryption;
import com.zackporter.util.FileInterface;
import com.zackporter.util.LocalLoc;

public class CredsManager {
	/* Creds File format:
	 * S3 IAM USERNAME
	 * S3 IAM SECRET ACCESS KEY
	 * S3 REGION
	 */
	public static final int IAM_USERNAME = 0;
	public static final int IAM_SECRET_KEY=1;
	public static final int AWS_REGION=2;
	private LocalLoc encrypted;
	private LocalLoc raw;
	
	public CredsManager(String folder) {
		encrypted = new LocalLoc(folder, Main.CREDS_ENCRYPTED_FILENAME);
		raw = new LocalLoc(folder, Main.CREDS_RAW_FILENAME);
	}
	public List<String> getCreds(){
		if(raw.getFile().exists()) {
			List<String> res = FileInterface.READ_BATCH(raw.getFile());
			if (res.size()==3) {
				return res;
			}else {
				Log.warn("Invalid raw data file. Attempting to re-decrypt");
			}
		}
		if (!encrypted.getFile().exists()) {
			Log.err("Unable to load credentials! No encrypted or decrypted files!");
			return null;
		}
		List<byte[]> data = FileInterface.READ_BYTES(encrypted.getFile());
		Console console = System.console();
		Log.println("---------------");
		Log.println("Logging in will require the password to this zbox.");
		String password = new String(console.readPassword("Password:"));
		List<String> res = Encryption.decryptAES(password, data);
		return res;
	}
	public void saveCreds(List<String> creds) {
		if (Main.IS_PERM_VERSION) {
			if (raw.getFile().exists()) {
				Log.info("Creds already saved. Not saving again");
			}else {
				Log.info("Saving creds for future use");
				FileInterface.WRITE(raw.getFile().getAbsolutePath(), creds);
			}
		}else {
			Log.info("Not perm version. Unable to save creds");
		}
	}
	public static void createEncryptedCreds(String rawfile, String targetFile) {
		List<String> rawCreds = FileInterface.READ_BATCH(rawfile);
		String entry1 = "RANDOM STRING 12432424L";
		String entry2 = "RANDOM STRING 543534534";
		boolean validCreds = false;
		while(!validCreds) {
			Log.println("Please enter a password to encrypt the file with.");
			Console console = System.console();
			entry1 = new String(console.readPassword("Password:"));
			entry2 = new String(console.readPassword("Repeat Password:"));
			if (entry1.equals(entry2)) {
				Log.println("Accepted password");
				validCreds=true;
			}else {
				Log.println("Passwords do not match! Please try again");
			}
		}
		List<byte[]> eCreds = Encryption.encryptAES(entry1, rawCreds);
		FileInterface.WRITE_BYTES(targetFile, eCreds);
		Log.println("Finished creating creds file");
	}
	
	
}
