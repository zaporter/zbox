package com.zackporter.box;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import com.zackporter.logging.FilePublisher;
import com.zackporter.logging.Log;
import com.zackporter.logging.Publisher;
import com.zackporter.textmanipulation.ProgressBar;
import com.zackporter.timing.TimeManager;
import com.zackporter.util.FileInterface;
import com.zackporter.util.FileLocation;
import com.zackporter.util.FileUpdate;
import com.zackporter.util.FolderManager;
import com.zackporter.util.LocalLoc;
import com.zackporter.util.Lock;
import com.zackporter.util.PhantomManager;
import com.zackporter.util.S3Loc;
import com.zackporter.util.Timing;

public class Box {
	public String key;
	public String bucket;
	public String folderLoc;
	public String parentBoxLoc;
	public Lock lock;
	private FolderManager folder;
	private PhantomManager phantomManager;
	private File localFolder;
	private File logsFolder;
	private Semaphore semSync;
	volatile long lastSyncTime=0;
	private Semaphore semUpLock;
	volatile int numDone=0;
	static private int numThreads= 100;
	
	public Box(String key, String folderLoc, String parentBox) {
		this.key=key;
		this.bucket=Main.DEFAULT_BUCKET;
		this.folderLoc=folderLoc;
		this.parentBoxLoc=parentBox;
		// TODO: IF THE LOCAL FILE IS NONEXISTENT BUT EXISTS GLOBALLY, DELETE IT 
		this.lock=new Lock(new S3Loc(parentBox, Main.LOCKFILE_NAME));
		this.folder=new FolderManager(this);
		phantomManager = new PhantomManager(this, new S3Loc(this, "phantoms.txt"));
		localFolder = (new File(folderLoc));
		logsFolder = (new File(Main.LOGS_FOLDER));
	}
	public boolean init(boolean shouldAlreadyExist) {
		if (!shouldAlreadyExist) {
			createFolders();
		}
		return doFoldersExist();
	}
	public void cleanupForRemoval() {
		Log.l("Starting cleanup for box "+this);
		FileInterface.DELETE_FILE(folder.hashLoc.getFile());
	}
	private void createFolders() {
		Log.info("Creating folders for logs and main for "+this);
		localFolder.mkdir();
		logsFolder.mkdir();
	}
	private boolean doFoldersExist() {
		return localFolder.exists() && logsFolder.exists();
	}
	public String toString() {
		return "["+bucket+", "+key+", "+folderLoc+", "+parentBoxLoc+"]";
	}
	public void sync() {
		sync(Main.GLO_UPDATE_DIFF, Main.LOC_UPDATE_DIFF);
	}
	public void sync(int dTGloUpdate, int dTLocUpdate) {
		TimeManager.notifyStarted(Timing.SYNC.id);
		Log.info("Starting sync for box "+this);
		String timeStamp = new SimpleDateFormat("[yyyy.MM.dd.HH.mm.ss]").format(new Date());
		LocalLoc syncLoc = new LocalLoc(Main.ZBOX_HOME,
				"logs/Sync_"+lock.getHostName()+"_"+key+"_"+timeStamp);
		Publisher syncpub = new FilePublisher(syncLoc.getFile());
		syncpub.setThreadID(Thread.currentThread().getId());
		Log.registerPublisher(syncpub);
	//	phantomManager.createLocalPhantoms(folder.loadHashesLocally().keySet());
		// get lock
		lock.getLock();
		// BEGIN SYNC CODE
		Map<LocalLoc,String> oldHash = folder.loadHashesLocally();
		Log.info("Hashes loaded locally");
		Map<LocalLoc,String> newHash = folder.refreshHashMap(oldHash);
		Log.info("Hashes updated locally");
		Map<S3Loc, String> gloHash = folder.loadHashesS3();
		Log.info("Hashes downloaded from S3");
		// get the sync todos
		Map<FileLocation, FileUpdate> todo = folder.getSyncTodos(oldHash, newHash, gloHash);
		Log.info("Sync todos created");
		//phantomManager.publishPhantoms(gloHash.keySet(), todo);
		Log.l("---SyncStart--- Num Updates: "+todo.size());
		//int numCompleted=0;
		lastSyncTime = System.currentTimeMillis();
		numDone=0;
		semUpLock = new Semaphore(1);
		semSync = new Semaphore(numThreads);
		Box box = this;
		// precompute saved hash (Timestamp)
		final Map<String, String> stringGloHash = new HashMap<>();
		for (S3Loc l : gloHash.keySet()) {
			stringGloHash.put(l.getLocalStr(), gloHash.get(l));
		}
		final Map<FileLocation,Long> dataMap = new HashMap<>();
		for (FileLocation g : todo.keySet()) {
				if (g.isS3Instance()) {
					dataMap.put(g, Long.parseLong(stringGloHash.get(g.getLocalStr())));
				}else {
					dataMap.put(g,0l);
				}
		}
		for (FileLocation file : todo.keySet()) {
			try {
				semSync.acquire();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			Thread t = new Thread() {
				public void run() {
					try{
						FileUpdate update = todo.get(file);
						boolean success = update.executeOn(file, box,dataMap.get(file));
						if (success) {
							semUpLock.acquire();
							FileUpdate.updateMaps(oldHash, newHash, gloHash, update, file,box);
							if (update.isGlobal) {
								if ((System.currentTimeMillis() - lastSyncTime)>dTGloUpdate) {
									folder.saveHashesS3(gloHash);
									lastSyncTime=System.currentTimeMillis();
								}
							}else {
								if ((System.currentTimeMillis() - lastSyncTime)>dTLocUpdate) {
									folder.saveHashesLocally(oldHash);
									lastSyncTime=System.currentTimeMillis();
								}
							}
							semUpLock.release();
						}
					}catch(Exception e) {
						e.printStackTrace();
					}
					semSync.release();
					numDone++;
					if (((numDone)*100)/todo.size()%10==0){
						Log.l(ProgressBar.produce(25, ((numDone)*100)/todo.size()));
					}
				}
			};
			t.start();
		}
		
		while(semSync.availablePermits()!=numThreads) {
			try {
				Thread.sleep(100);
				Log.l("Wating for : "+semSync.availablePermits());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Log.l("---SyncEnd---");
		// END SYNC CODE
		folder.saveHashesS3(gloHash);
		folder.saveHashesLocally(newHash);
		Log.info("Sync almost finished -- freeing lock");
		Log.removePublisher(syncpub);
		S3I.putObjectFileSimple(syncLoc.getCorrespondingS3(this), syncLoc.getFile());
		lock.freeLock();
		Log.info("Sync finished for "+this);
		TimeManager.notifyFinished(Timing.SYNC.id);
	}
	public List<LocalLoc> getFiles(){
		return folder.listFiles();
	}
	
	public Set<LocalLoc> getSynced(){
		return folder.loadHashesLocally().keySet();
	}
	public void deleteLocal() {
		Log.info("Starting delete");
		for (LocalLoc l : folder.listALLFiles()) {
			FileInterface.DELETE_FILE(l.getFile(), true);
		}
		Log.info("Delete complete");
	}
	public boolean hasUnsyncedFiles() {
		for (File f : getSubFoldersRecursive(new File(folderLoc), new ArrayList<>())) {
			if (f.lastModified()>folder.getLastRefresh()) {
				return true;
			}
		}
		return false;
	}
	private  List<File> getSubFoldersRecursive(File current, List<File> list){
		list.add(current);
		for (File f:current.listFiles()) {
			if (f.isDirectory()) {
				getSubFoldersRecursive(f,list);
			}
		}
		return list;
	}
	public boolean shouldSync() {
		return hasUnsyncedFiles()||((System.currentTimeMillis() - folder.getLastRefresh())>Main.SYNC_WAIT);
	}
	
	
}