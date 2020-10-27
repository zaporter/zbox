package com.zackporter.box;

import java.io.File;
import java.util.ArrayList;

import com.zackporter.clientConnection.CommandInterfaceFactory;
import com.zackporter.clientConnection.ZServer;
import com.zackporter.logging.Log;
import com.zackporter.util.InstallManager;
import com.zackporter.util.LocalLoc;
import com.zackporter.util.S3Loc;
import com.zackporter.util.Timing;

public class Main {
	public static final String VERSION="1.0";
	public static final String SEP_SEQ="-ZBOX_SEP-";
	public static final String HASH_SEP_SEQ="-HASH_SEP-";
	public static final String DEFAULT_BUCKET="zackbox";
	public static final String ZBOX_HOME="/home/zack/.zbox";
	public static final String BOXES_FILE_LOCAL=ZBOX_HOME+"/boxes.txt";
	public static final String LOGS_FOLDER=ZBOX_HOME+"/logs";
	public static final String JARS_FOLDER=ZBOX_HOME+"/jars";
	public static final String CREDS_FOLDER=ZBOX_HOME+"/credentials";
	public static final String CREDS_ENCRYPTED_FILENAME="encryptedCreds.txt";
	public static final String CREDS_RAW_FILENAME="secretRawCreds.txt";
	public static final String CLIENT_JAR_NAME="ZBC.jar";
	public static final String SERVER_JAR_NAME="ZBS.jar";
	public static final LocalLoc CLIENT_JAR 
		= new LocalLoc(JARS_FOLDER, CLIENT_JAR_NAME);
	public static final LocalLoc SERVER_JAR
		= new LocalLoc(JARS_FOLDER, SERVER_JAR_NAME);
	public static final String PHANTOM_TEMP_SUFFIX=".ztemp";
	public static final String LOCKFILE_NAME=".lock.txt";
	public static final LocalLoc HASHES_LAST_REF_KEY = new LocalLoc("LAST_HASH_REFRESH","LAST_HASH_REFRESH");
	public static final String HASHFILE_NAME=".hashes.txt";
	public static final long SESSION_ID=System.currentTimeMillis();
	public static final String LOCK_PREFIX = "LOCK("+SESSION_ID+")";
	public static final int LOCK_TIMEOUT=30000;
	public static final int LOCK_REFRESH=15000;
	public static final int SYNC_WAIT=10000;
	public static final int BOXMANAGER_SYNC_WAIT=1000;
	public static final int GETLOCK_WAIT=5000;
	public static final int GLO_UPDATE_DIFF=30000;
	public static final int LOC_UPDATE_DIFF=10000;
	public static final boolean IS_PERM_VERSION=true;
	public static final S3Loc BOXES_FILE_GLOBAL=new S3Loc(DEFAULT_BUCKET, "boxes.txt","");
	public static final int TO_UPLOAD_BATCH_LIMIT=10;
	public static final int TO_DOWNLOAD_BATCH_LIMIT=10;
	// TODO: Dont really delete the files in S3, just transfer them
	// to a different bucket
	
	// TODO: Use a global temp file for logging so as to avoid updating the folder
	// TODO: Use one big local log file as well
	// TODO: create installation stuff
	public static void main(String[] args) {
		if (args.length==0) {
			startZBox();
		}else {
			CommandInterfaceFactory cif = new CommandInterfaceFactory();
			Log.l(cif.genCIMain().interpret(args));
		}
	}
	public static void startZBox() {
		Log.l("Starting with session id: "+SESSION_ID);
		Timing.initAll();
		S3I.init(DEFAULT_BUCKET);
		BoxManager bm = new BoxManager();
		bm.init();
		ZServer serv = new ZServer();
		CommandInterfaceFactory cif = new CommandInterfaceFactory();
		serv.start(cif.genCIClient(bm, serv));
		bm.startSyncThread();
		Log.l("Finished Main Thread");
	}
	public static void prepareForShutdown() {
		Log.l("Shutting down application");
		if (!IS_PERM_VERSION) {
			InstallManager.uninstall();
		}
	}
	public static void shutdown() {
		System.exit(1);
	}
}
