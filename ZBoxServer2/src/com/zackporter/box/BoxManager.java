package com.zackporter.box;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.zackporter.logging.Log;
import com.zackporter.util.FileInterface;
import com.zackporter.util.Lock;
import com.zackporter.util.S3Loc;

public class BoxManager {
	private volatile List<Box> boxes  = new ArrayList<>();
	private Thread syncThread;
	private boolean syncing=true;
	private volatile boolean syncLock=false;
	public BoxManager() {
	}
	public void init() {
		syncThread = new Thread() {
			public void run() {
				syncing=true;
				while (syncing) {
					sync();
					try {
						Thread.sleep(Main.BOXMANAGER_SYNC_WAIT);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		// THE SYNC THREAD IS CREATED BUT NOT STARTED
		
		this.loadAndInitializeBoxes();
		saveBoxesLocally();
	}
	public void startSyncThread() {
		Log.l("Starting BoxManager Sync Thread");
		syncThread.start();
	}
	public boolean isSyncing() {
		return syncThread.isAlive()&&syncing;
	}
	public void shutdownSyncThread() {
		Log.l("Beginning shutdown of sync thread");
		syncing=false;
		while(syncThread.isAlive()&&syncLock) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (syncThread.isAlive()){
			syncThread.suspend();;
		}
		Log.l("Shutdown complete");
	}
	public void shutdownSyncThreadFORCE() {
		Log.l("Beginning FORCE shutdown");
		syncing=false;
		syncThread.suspend();
		freeLock();
		Log.l("Shutdown conplete");
		
	}
	public List<Box> getBoxes(){
		return boxes;
	}
	public boolean localBoxesContains(String key) {
		return getBoxKey(key)!=null;
	}
	public void addBox(Box b) {
		getLock();
		boolean success=true;
		if (!S3I.isBoxRegistered(b)) {
			Log.info("Box "+b+" is not currently registered. Creating another. ");
			if (!S3I.doesObjectExist(new S3Loc(b.parentBoxLoc, Main.HASHFILE_NAME))) {
				S3I.putObjectSimple(new S3Loc(b.parentBoxLoc,Main.HASHFILE_NAME), "");
			}
			S3I.registerBox(b);
		}else {
			Log.info("Box already exists, no need to create it");
		}
		success = b.init(localBoxesContains(b.key));
		if (success) {
			boxes.add(b);
		}else {
			Log.warn("The folders for box "+b+" cannot be found. The box has been disregistered");
		}
		saveBoxesLocally();
		freeLock();
	}
	public void sync() {
		getLock();
		Log.info("Starting BoxManager Sync");
		for (Box b: boxes) {
			if (b.shouldSync()) {
				b.sync();
			}else {
				Log.info("Skipping sync on "+b+" because it doesnt need it");
			}
		}
		freeLock();
	}
	public Box getBoxFile(String file) {
		for (Box b : boxes) {
			if (b.folderLoc.equals(file)) {
				return b;
			}
		}
		return null;
	}
	public Box getBoxKey(String key) {
		for (Box b : boxes) {
			if (b.key.equals(key)) {
				return b;
			}
		}
		return null;
	}
	public void createBox(String key, String folder) {
		// TODO Fix this
		// IE: check to see if a box with this key already exists
		// before pretending to set its parent location
		// ALso make sure that it is re-added properly?
		// IE: not recreated/ deleted beforehand
		for (Box existing : boxes ) {
			if (existing.folderLoc.startsWith(folder) 
					|| folder.startsWith(existing.folderLoc)) {
				Log.err("Unable to create box because the path "+folder+
						"may contain or be contained by the other local box "+existing);
				return;
			}
		}
		Box temp= (new Box(key, folder, key.split("/")[0]));
		addBox(temp);
	}
	public void deleteBox(String key) {
		Box temp = new Box(key, "DELETING TEMP", "DELETING TEMP");
		remove(temp,false);
		//TODO REMOVE THE SMALLER ONES 
		getLock();
		if (S3I.isBoxRegistered(temp)) {
			S3I.deleteBox(temp);
			Log.info("Box "+key+" deleted.");
		}else {
			Log.warn("Unable to delete box "+key+" as it isnt registered. ");
		}
		freeLock();
	}
	public void remove(Box b, boolean deleteFiles) {
		getLock();
		if (getBoxKey(b.key)!=null) {
			Log.info("Removing box "+b);
			b.lock.freeLock();
			removeBoxKey(b.key);
			if (deleteFiles) {
				b.deleteLocal();
			}
			saveBoxesLocally();	
		}else {
			Log.warn("Unable to remove box "+b+" as it is not a managed box");
		}
		freeLock();
	}
	private void removeBoxKey(String key) {
		int i =0;
		for (Box b : boxes) {
			if (b.key.equals(key)) {
				break;
			}
			i++;
		}
		boxes.remove(i);
	}
	
	public void saveBoxesLocally(){
		List<String> lines = new ArrayList<>();
		for (Box b : boxes) {
			StringBuilder line = new StringBuilder();
			line.append(b.key);
			line.append(Main.SEP_SEQ);
			line.append(b.folderLoc);
			lines.add(line.toString());
		}
		FileInterface.WRITE(Main.BOXES_FILE_LOCAL, lines);
	}
	public void loadAndInitializeBoxes() {
		Log.info("Initializing all boxes");
		Collection<String> s3Lines;
		try {
			 s3Lines = S3I.loadFile(Main.BOXES_FILE_GLOBAL);
		}catch (Exception e) {
			Log.warn("No main .boxes file! Creating one");
			s3Lines = new ArrayList<String>();
			S3I.putObjectSimple(Main.BOXES_FILE_GLOBAL, "");
		}
		List<String> localLines = FileInterface.READ_BATCH(Main.BOXES_FILE_LOCAL);
		for (String line : localLines) {
			String[] partsLocal = line.split(Main.SEP_SEQ);
			String key = partsLocal[0];
			String folderLoc = partsLocal[1];
			String parentFolderLoc=null;
			for (String lineS3 : s3Lines) {
				String[] partsGlobal = lineS3.split(Main.SEP_SEQ);
				if (partsGlobal[0].equals(key)) {
					parentFolderLoc=partsGlobal[1];
					break;
				}
			}
			if (parentFolderLoc!=null) {
				boxes.add(new Box(key, folderLoc, parentFolderLoc));
			}else {
				Log.warn("Box "+key+" must have been deleted on a diff computer. It will no longer be managed.");
			}
		}
	}
	public List<String> getAllBoxKeysS3(){
		Collection<String> lines = S3I.loadFile(Main.BOXES_FILE_GLOBAL);
		List<String> keys = new ArrayList<>();
		for (String s : lines) {
			String[] parts = s.split(Main.SEP_SEQ);
			keys.add(parts[0]);
		}
		return keys;
	}
	public boolean loadBoxFromS3(String key, String location){
		Collection<String> lines = S3I.loadFile(Main.BOXES_FILE_GLOBAL);
		for (String s : lines) {
			String[] parts = s.split(Main.SEP_SEQ);
			if (parts[0].equals(key)) {
				if (parts.length!=2) {
					Log.err("Invalid box entry: "+s);
					return false;
				}
				addBox(new Box(parts[0], location, parts[1]));
				return true;
			}
		}
		return false;
	}
	// TODO add timeout
	private void getLock() {
		while (syncLock) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		syncLock=true;
	}
	private void freeLock() {
		syncLock=false;
	}
}
