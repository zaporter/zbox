package com.zackporter.util;

import java.util.Map;

import com.zackporter.box.Box;
import com.zackporter.box.S3I;
import com.zackporter.logging.Log;
import com.zackporter.timing.TimeManager;

public enum FileUpdate {
	TO_ADD_GLOBALLY(true){
		public boolean executeOn(FileLocation loc, Box box) {
			return S3I.uploadFile(box, (LocalLoc) loc);
		}
	},
	TO_ADD_LOCALLY(false){
		public boolean executeOn(FileLocation loc, Box box, long lastUpdated) {
			boolean success = S3I.downloadFile(box, (S3Loc)loc);
			Log.l((loc.getLocInstance(box)).getFile().lastModified());

			(loc.getLocInstance(box)).getFile().setLastModified(lastUpdated);
			Log.l((loc.getLocInstance(box)).getFile().lastModified());

			return success;
		}
	},
	TO_DELETE_LOCALLY(false){
		public boolean executeOn(FileLocation loc, Box box) {
			FileInterface.DELETE_FILE(loc.getFile(), true);
			return true;
		}
	},
	TO_DELETE_GLOBALLY(true){
		public boolean executeOn(FileLocation loc, Box box) {
			return S3I.deleteObject((S3Loc) loc);
		}
	},
	TO_UPDATE_LOCALLY(false){
		public boolean executeOn(FileLocation loc, Box box, long lastUpdated) {
			Log.l("about to update "+loc.getGlobalStr()+" "+lastUpdated);
			Log.l((loc.getLocInstance(box)).getFile().lastModified());

			boolean success = S3I.downloadFile(box, (S3Loc)loc);
			Log.l((loc.getLocInstance(box)).getFile().lastModified());
Log.l((loc.getLocInstance(box)).getFile());
			Log.l((loc.getLocInstance(box)).getFile().setLastModified(lastUpdated));
			Log.l((loc.getLocInstance(box)).getFile().lastModified());

			return success;
		}
	},
	TO_UPDATE_GLOBALLY(true){
		public boolean executeOn(FileLocation loc, Box box) {
			return S3I.uploadFile(box, (LocalLoc)loc);
		}
	},
	TO_MOVE_LOCALLY(false){
		public boolean executeOn(FileLocation loc, Box box) {
			Log.warn("UNIMPLEMENTED");
			return false;
		}
	},
	TO_MOVE_GLOBALLY(true){
		public boolean executeOn(FileLocation loc, Box box) {
			Log.warn("UNIMPLEMENTED");
			return false;
		}
	};
	public boolean isGlobal;
	private FileUpdate(boolean in) {
		isGlobal=in;
	}
	public boolean executeOn(FileLocation loc, Box box) {
		Log.warn("UNIMPLEMENTED");
		return false;
	}
	public boolean executeOn(FileLocation loc, Box box, long lastUpdated) {
		return executeOn(loc,box);
	}
	public static void updateMaps(
			Map<LocalLoc, String> oldH,
			Map<LocalLoc, String> newH,
			Map<S3Loc, String> gloH,
			FileUpdate todo, 
			FileLocation loc,
			Box b) {
		TimeManager.notifyStarted(Timing.UPDATE_MAPS.id);
		LocalLoc local = loc.getLocInstance(b);
		S3Loc s3L = loc.getS3Instance(b);
		switch (todo) {
		case TO_ADD_LOCALLY:
			putLoc(oldH, local, getHashS3(gloH, s3L));
			break;
		case TO_DELETE_LOCALLY:
			removeLoc(oldH, loc.getLocalStr());
			break;
		case TO_UPDATE_LOCALLY:
			putLoc(oldH, local, getHashS3(gloH, s3L));
			break;
		case TO_MOVE_LOCALLY:
			Log.warn("UNIMPLEMENTED");
			break;
		case TO_ADD_GLOBALLY:
			putS3(gloH, s3L, getHashLoc(newH, local));
			putLoc(oldH, local, getHashLoc(newH, local));
			break;
		case TO_DELETE_GLOBALLY:
			removeS3(gloH, loc.getLocalStr());
			removeLoc(oldH, loc.getLocalStr());
			break;
		case TO_UPDATE_GLOBALLY:
			putS3(gloH, s3L, getHashLoc(newH, local));
			putLoc(oldH, local, getHashLoc(newH, local));
			break;
		case TO_MOVE_GLOBALLY:
			Log.warn("UNIMPLEMENTED");
			break;
		default:
			Log.err("Unknown fileupdate type "+loc);
			break;
		}
		TimeManager.notifyFinished(Timing.UPDATE_MAPS.id);
		
	}
	private static String getHashLoc(Map<LocalLoc, String> map, LocalLoc j) {
		for (LocalLoc l : map.keySet()) {
			if (l.getLocalStr().equals(j.getLocalStr())) {
				return map.get(l);
			}
		}
		return null;
	}
	private static String getHashS3(Map<S3Loc, String> map, S3Loc j) {
		for (S3Loc l : map.keySet()) {
			if (l.getLocalStr().equals(j.getLocalStr())) {
				return map.get(l);
			}
		}
		return null;
	}
	private static void putLoc(Map<LocalLoc, String> map, LocalLoc loc, String hash) {
		LocalLoc toPlace=loc;
		for (LocalLoc j : map.keySet()) {
			if (j.getLocalStr().equals(loc.getLocalStr())) {
				toPlace = j;
				break;
			}
		}
		map.put(toPlace, hash);
	}
	private static void putS3(Map<S3Loc, String> map, S3Loc loc, String hash) {
		S3Loc toPlace = loc;
		for (S3Loc j : map.keySet()) {
			if (j.getLocalStr().equals(loc.getLocalStr())) {
				toPlace = j;
				break;
			}
		}
		map.put(toPlace, hash);
	}
	private static void removeLoc(Map<LocalLoc, String> map, String del) {
		FileLocation toDel=null;
		for (FileLocation j : map.keySet()) {
			if (j.getLocalStr().equals(del)) {
				toDel=j;
				break;
			}
		}
		if (toDel!=null) {
			map.remove(toDel);
		}
	}
	private static void removeS3(Map<S3Loc, String> map, String del) {
		FileLocation toDel=null;
		for (FileLocation j : map.keySet()) {
			if (j.getLocalStr().equals(del)) {
				toDel=j;
				break;
			}
		}
		if (toDel!=null) {
			map.remove(toDel);
		}
	}
}
