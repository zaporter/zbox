package com.zackporter.util;

import java.io.Console;
import java.io.File;
import java.util.List;

import com.zackporter.box.Main;
import com.zackporter.creds.CredsManager;
import com.zackporter.logging.Log;

public class InstallManager {
	
	public static boolean isInstalled() {
		return (new File(Main.BOXES_FILE_LOCAL)).exists();
	}
	private static boolean isRoot() {
		String processStr = LanguageInterface.bash("id -u");
		int process = Integer.parseInt(processStr.replaceAll("\n", ""));
		return process==0;
	}
	public static void install() {
		Log.l("Starting install");
		if (isInstalled()) {
			// TODO update rather than uninstall / reinstall
			Log.warn("Tried to install ZBox when it already exists!");
			Log.warn("Uninstalling and reinstalling");
			uninstall();
		}
		if (!isRoot()) {
			Log.err("Install must be run as root. Please try again with sudo");
			return;
		}
		// ---- CREATE FOLDERS -----
		(new File(Main.ZBOX_HOME)).mkdir();
		(new File(Main.LOGS_FOLDER)).mkdir();
		(new File(Main.JARS_FOLDER)).mkdir();
		(new File(Main.CREDS_FOLDER)).mkdir();
		LanguageInterface.bash("chmod -R 755 "+Main.ZBOX_HOME);
		String username = LanguageInterface.bash("logname");
		
		if (username.equals("")) {
			Log.l("Account discovery did not work. Please input your account name:");
			Console console = System.console();
			username = console.readLine("Acc: ");
		}
		Log.l("Account to own folder:"+username);
		LanguageInterface.bash("chown -R "+username+" "+Main.ZBOX_HOME);
		FileInterface.WRITE(Main.BOXES_FILE_LOCAL, "");
		LanguageInterface.bash("chmod 766 "+Main.BOXES_FILE_LOCAL);
		Log.l("Created Directories");
		// ---- MOVE JARS ------------
		File client = new File(Main.CLIENT_JAR_NAME);
		File server = new File(Main.SERVER_JAR_NAME);
		if (client.exists() && server.exists()) {
			try {
				FileInterface.COPY(client.getAbsolutePath(), Main.CLIENT_JAR.getGlobalStr());
				FileInterface.COPY(server.getAbsolutePath(), Main.SERVER_JAR.getGlobalStr());
			}catch(Exception e) {
				e.printStackTrace();
			}
			Log.l("Moved Jars");
		}else {
			Log.err("Unable to move jars as they dont exist. Exiting now");
			return;
		}
		// ---------- CREDS -----------------
		Log.l("Moving creds");
		try {
			FileInterface.COPY(Main.CREDS_ENCRYPTED_FILENAME, Main.CREDS_FOLDER+"/"+Main.CREDS_ENCRYPTED_FILENAME);
		}catch(Exception e) {
			e.printStackTrace();
			Log.warn("Unable to install creds");
		}
		CredsManager cm = new CredsManager(Main.CREDS_FOLDER);
		cm.saveCreds(cm.getCreds());
		Log.l("Creds installed");
		// ---------- COMMANDS  -----------------
		Log.l("Creating links in /usr/bin");
		FileInterface.WRITE("/usr/bin/zbox", "#!/bin/bash\njava -jar "+Main.CLIENT_JAR.getGlobalStr()+" $*");
		FileInterface.WRITE("/usr/bin/zboxserver", "#!/bin/bash\njava -jar "+Main.SERVER_JAR.getGlobalStr()+" $*");
		Log.l(LanguageInterface.bash("chmod 755 /usr/bin/zbox"));
		Log.l(LanguageInterface.bash("chmod 755 /usr/bin/zboxserver"));
		// ----------- START ON BOOT ----------------
		Log.l("Creating startup call");
		FileInterface.WRITE("/etc/init.d/zboxserver", "#!/bin/bash\njava -jar "+Main.SERVER_JAR.getGlobalStr()+" $*");
		Log.l(LanguageInterface.bash("update-rc.d /etc/init.d/zboxserver"));
		Log.l(LanguageInterface.bash("chmod 755 /etc/init.d/zboxserver"));
		Log.l("Install Complete");
		
	}
	public static void uninstall() {
		Log.l("Uninstalling");
		if (!isRoot()) {
			Log.err("Uninstall must be run as root. Please try again with sudo");
			return;
		}
		List<String> lines = FileInterface.READ_BATCH(Main.BOXES_FILE_LOCAL);
		for (String line : lines) {
			String[] parts = line.split(Main.SEP_SEQ);
			if (parts.length==2) {
				String folder = parts[1];
				FileInterface.DELETE_FILE(folder+"/"+Main.HASHFILE_NAME);
			}else {
				Log.warn("There are not two parts to the string: "+line);
			}
		}
		FileInterface.DELETE_FOLDER_RECURSIVE(new File(Main.ZBOX_HOME));
		FileInterface.DELETE_FILE("/usr/bin/zbox");
		FileInterface.DELETE_FILE("/usr/bin/zboxserver");
		Log.l("Uninstall Finished");
	}
}
