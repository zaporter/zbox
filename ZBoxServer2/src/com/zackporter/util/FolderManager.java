package com.zackporter.util;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zackporter.box.Box;
import com.zackporter.box.Main;
import com.zackporter.box.S3I;
import com.zackporter.logging.Log;
import com.zackporter.timing.TimeManager;

public class FolderManager {
	private LocalLoc loc;
	public LocalLoc hashLoc;
	private Box box;
	private long lastRefresh=-1;
	public FolderManager(Box b) {
		this.loc=new LocalLoc(b.folderLoc, "");
		this.hashLoc= new LocalLoc(b.folderLoc, Main.HASHFILE_NAME);
		this.box=b;
	}
	public long getLastRefresh() {
		if (lastRefresh==-1) {
			try {
				lastRefresh = Long.parseLong(loadHashesLocally().get(Main.HASHES_LAST_REF_KEY));
			}catch (Exception e) {
				Log.warn("Unable to get last hash refresh. Setting to -1.");
				lastRefresh=-1;
			}
		}
		return lastRefresh;
	}
	public void setLastRefresh(long in) {
		lastRefresh=in;
	}
	public Map<LocalLoc, String> generateHashMap(){
		return refreshHashMap(null);
	}
	public Map<LocalLoc, String> refreshHashMap(Map<LocalLoc, String> old){
		TimeManager.notifyStarted(Timing.REFRESH_HASH.id);
		long lastHashRefresh = getLastRefresh();
		if (old == null) {
			old = new HashMap<LocalLoc, String>();
		}
		Map<LocalLoc,String> newMap = new HashMap<>();
		List<String> files  = LanguageInterface.bashRead("find "+box.folderLoc);
		for (String s : files) {
			if (s.contains(Main.HASHFILE_NAME)) {
				continue;
			}
			LocalLoc l = new LocalLoc(box.folderLoc, s.substring(box.folderLoc.length()));
			if (!l.getFile().isDirectory()) {
				String result = String.valueOf(l.getFile().lastModified());
				if (result.length()>3) {
					result = result.substring(0, result.length()-3).concat("000");
					newMap.put(l,result);
				}// else it has not yet been created
			}
		}
	
		setLastRefresh(System.currentTimeMillis());
        newMap.put(Main.HASHES_LAST_REF_KEY, getLastRefresh()+"");
        TimeManager.notifyFinished(Timing.REFRESH_HASH.id);
        return newMap;
	}
	public  String getHash(File file) {
		try {
			TimeManager.notifyStarted(Timing.CALC_HASH.id);
			String result =  Encryption.getSHA1FileChecksum(file);//file.lastModified()+"";
			TimeManager.notifyFinished(Timing.CALC_HASH.id);
			return result;
		}catch (Exception e) {
			Log.err("Unable to calculate Sha1 file checksum for: "+file.getAbsolutePath());
			e.printStackTrace();
			return null;
		}
	}
	public List<LocalLoc> listFiles(){
		TimeManager.notifyStarted(Timing.LIST_FILES.id);
		List<LocalLoc> files = recursiveFindFiles(loc,new ArrayList<LocalLoc>(),true);
		files = filterFiles(files);
		TimeManager.notifyFinished(Timing.LIST_FILES.id);
		return files;
	}
	public List<LocalLoc> listALLFiles(){
		List<LocalLoc> files = recursiveFindFiles(loc,new ArrayList<LocalLoc>(),true);
		return files;
	}
	private List<LocalLoc> filterFiles(List<LocalLoc> in){
		List<LocalLoc> ret = new ArrayList<>();
		fileloop:
		for (LocalLoc loc : in) {
			if (!loc.getGlobalStr().contains(Main.HASHFILE_NAME)
					/*&&!loc.getGlobalStr().contains(Main.LOGS_FOLDER)*/) {
				String raw = loc.toString();
				int i =0;
				while (i++<raw.length()-1) {
					if (raw.charAt(i)==13) {
						Log.warn("Unable to sync file "+loc+". It has a carriage return and I hate it deeply. ");
						continue fileloop;
					}
				}
				ret.add(loc);
				
			}
		}
		return ret;
	}
	private  List<LocalLoc> recursiveFindFiles(LocalLoc loc, List<LocalLoc> fullList,boolean original){
		File[] containing = loc.listFiles();
		if (containing==null) {
			fullList.add(loc);
			return fullList;
		}
		for(File f : containing) {
			recursiveFindFiles(new LocalLoc(loc.getPrefixStr(), loc.getLocalStr()+"/"+f.getName()),fullList,false);
		}
		return fullList;
	}
	public boolean mapEquals(Map<String, String> a, Map<String, String>b) {
		for (String key : a.keySet()) {
			if (b.containsKey(key)) {
				if (!a.get(key).equals(b.get(key))) {
					return false;
				}
			}else {
				return false;
			}
		}
		return true;
	}
	public Map<FileLocation, String> hashesCollectionToMap(Collection<String> hashes){
		Map<FileLocation, String> map = new HashMap<>();
		for (String s:hashes) {
			String[] parts = s.split(Main.HASH_SEP_SEQ);
			if (parts.length!=2) {
				Log.warn("Invalid hash line: "+s+" in folder "+loc+" length: "+parts.length);
			}else {
				map.put(FileLocation.loadFileLocation(parts[0]),
						parts[1]);
			}
		}
		return map;
	}
	// TODO : new StringBuilder(capacity) optimization (map.getKeySet().size()*15)
	public String hashesMapToString(Map<FileLocation, String> map) {
		StringBuilder contents = new StringBuilder();
		for (FileLocation key : map.keySet()) {
			contents.append(key.getSaveString());
			contents.append(Main.HASH_SEP_SEQ);
			contents.append(map.get(key));
			contents.append("\n");
		}
		return contents.toString();
	}
	//TODO see if this works
	public  Map<FileLocation, String> toFileLocationsLoc(Map<LocalLoc, String> in){
		Map<FileLocation, String> result = new HashMap<>();
		result.putAll(in);
		return result;
		/*Map<? extends FileLocation, String> result = in;
		//result.putAll(in);
		return (Map<FileLocation, String>) result;*/
	}
	public  Map<FileLocation, String> toFileLocationsS3(Map<S3Loc, String> in){
		Map<FileLocation, String> result = new HashMap<>();
		result.putAll(in);
		return result;
	}
	public Map<LocalLoc, String> fromFileLocationsLoc(Map<FileLocation, String> in){
		Map<LocalLoc, String> result = new HashMap<>();
		for (FileLocation f : in.keySet()) {
			if (f instanceof LocalLoc) {
				result.put((LocalLoc)f, in.get(f));
			}else {
				Log.err("Invalid type "+f.toString());
			}
		}
		return result;
	}
	public Map<S3Loc, String> fromFileLocationsS3(Map<FileLocation, String> in){
		Map<S3Loc, String> result = new HashMap<>();
		for (FileLocation f : in.keySet()) {
			if (f==null) {
				continue;
			}
			if (f instanceof S3Loc) {
				result.put((S3Loc)f, in.get(f));	
			}else {
				result.put(f.getS3Instance(box), in.get(f));
				//Log.err("Invalid type "+f.toString());
			}
		}
		return result;
	}
	
	public void saveHashesLocally(Map<LocalLoc,String> hashes) {
		TimeManager.notifyStarted(Timing.SAVE_HASHES_LOCALLY.id);
		String str = hashesMapToString(toFileLocationsLoc(hashes));
		FileInterface.WRITE(hashLoc.getFile(), str);
		TimeManager.notifyFinished(Timing.SAVE_HASHES_LOCALLY.id);
	}
	public void saveHashesS3(Map<S3Loc,String> hashes) {
		TimeManager.notifyStarted(Timing.SAVE_HASHES_S3.id);
		Collection<String> currentLines = S3I.loadFile(
				new S3Loc(box.bucket,box.parentBoxLoc, hashLoc.getLocalStr()));
		Map<S3Loc, String> currentHashes = fromFileLocationsS3(hashesCollectionToMap(currentLines));
		Map<S3Loc, String> futureHashes = new HashMap<>();
		for (S3Loc l : currentHashes.keySet()) {
			if (!l.getGlobalStr().startsWith(box.key)) {
				futureHashes.put(l, currentHashes.get(l));
			}
		}
		futureHashes.putAll(hashes);
		String toSave = hashesMapToString(toFileLocationsS3(futureHashes));
		S3I.putObjectSimple(new S3Loc(box.parentBoxLoc,
				hashLoc.getLocalStr()), toSave);
		TimeManager.notifyFinished(Timing.SAVE_HASHES_S3.id);
	}
	public Map<LocalLoc, String> loadHashesLocally(){
		TimeManager.notifyStarted(Timing.LOAD_HASHES_LOCALLY.id);
		Map<LocalLoc, String> result=null;
		if (hashLoc.getFile().exists()) {
			List<String> strs = FileInterface.READ_BATCH(hashLoc.getFile());
			result= fromFileLocationsLoc(hashesCollectionToMap(strs));
		}else {
			Log.warn("Unable to load hash file! Pretending it does not exist");
			result= new HashMap<>();
		}
		TimeManager.notifyFinished(Timing.LOAD_HASHES_LOCALLY.id);
		return result;
	}
	public Map<S3Loc, String> loadHashesS3(){
		TimeManager.notifyStarted(Timing.LOAD_HASHES_S3.id);
		Collection<String> strs = S3I.loadFile(
				new S3Loc(box.bucket,box.parentBoxLoc, hashLoc.getLocalStr()));
		Map<S3Loc, String> hashes = fromFileLocationsS3(hashesCollectionToMap(strs));
		Map<S3Loc, String> filteredHashes = new HashMap<>();
		for (S3Loc l : hashes.keySet()) {
			if (l.getGlobalStr().startsWith(box.key)) {
				String prefix = l.getGlobalStr().substring(0, box.key.length());
				String local = l.getGlobalStr().substring(box.key.length()+1);
				filteredHashes.put(new S3Loc(prefix, local), hashes.get(l));
			}
		}
		TimeManager.notifyFinished(Timing.LOAD_HASHES_S3.id);
		return filteredHashes;
	}
	private static final String LO_IND = "L";
	private static final String S3_IND = "S";
	public Map<String, String> stripHashPrefixesLo(Map<LocalLoc, String> newHashF){
		Map<String, String> out = new HashMap<>();
		for (FileLocation l : newHashF.keySet()) {
			out.put(l.getLocalStr(), newHashF.get(l));
		}		
		return out;
	}
	public Map<String, String> stripHashPrefixesS3(Map<S3Loc, String> newHashF){
		Map<String, String> out = new HashMap<>();
		for (FileLocation l : newHashF.keySet()) {
			out.put(l.getLocalStr(), newHashF.get(l));
		}		
		return out;
	}
	public Map<FileLocation, FileUpdate> replaceFileLists(Map<String, FileUpdate> in){
		Map<FileLocation, FileUpdate> out = new HashMap<>();
		for (String s : in.keySet()) {
			if (s.startsWith(LO_IND)) {
				out.put(new LocalLoc(box, s.substring(1)), in.get(s));
			}else {
				out.put(new S3Loc(box, s.substring(1)), in.get(s));
			}
		}
		return out;
	}
	public Map<FileLocation, FileUpdate> getSyncTodos(
			Map<LocalLoc, String> oldHashF, 
			Map<LocalLoc, String> newHashF,
			Map<S3Loc, String> gloHashF){
		TimeManager.notifyStarted(Timing.SYNC_TODO_CALC.id);
		Map<String, FileUpdate> tempTodos = new HashMap<>();
		String lastRefKeyIgnore = Main.HASHES_LAST_REF_KEY.getLocalStr();
		Map<String, String> oldHash = stripHashPrefixesLo(oldHashF);
		Map<String, String> newHash = stripHashPrefixesLo(newHashF);
		Map<String, String> gloHash = stripHashPrefixesS3(gloHashF);
		/*
		 * FILES THAT ARE NEW TO THE LOCAL HASH
		 */
		Log.info("NEW");

		for (String s : newHash.keySet()) {
			if (s.equals(lastRefKeyIgnore)) {
				continue;
			}
			if (!oldHash.containsKey(s) && !gloHash.containsKey(s)) {
				tempTodos.put(LO_IND.concat(s), FileUpdate.TO_ADD_GLOBALLY);
			}
		}
		/*
		 * FILES THAT ARE PRESENT IN THE GLOBAL HASH BUT NOT LOCALLY
		 */
		Log.info("NEW1");

		for (String s : gloHash.keySet()) {
			if (s.equals(lastRefKeyIgnore)) {
				Log.warn("The hash last refresh key somehow got into the global hashes");
				continue;
			}
			if (!oldHash.containsKey(s) &&
					!newHash.containsKey(s)) {
				tempTodos.put(
						S3_IND.concat(s),
						FileUpdate.TO_ADD_LOCALLY);
			}
		}
		/*
		 * FILES THAT WERE REMOVED FROM THE LOCAL HASH
		 */
		Log.info("NEW2");

		for (String s : gloHash.keySet()) {
			if (s.equals(lastRefKeyIgnore)) {
				continue;
			}
			if (!newHash.containsKey(s) && oldHash.containsKey(s)) {
				tempTodos.put(
						S3_IND.concat(s),
						FileUpdate.TO_DELETE_GLOBALLY);
			}
		}
		/*
		 * FILES THAT ARE NO LONGER PRESENT IN THE GLOBAL HASH
		 */
		Log.info("NEW3");

		for (String s : newHash.keySet()) {
			if (s.equals(lastRefKeyIgnore)) {
				continue;
			}
			if (oldHash.containsKey(s) && !gloHash.containsKey(s)) {
				tempTodos.put(
						LO_IND.concat(s),
						FileUpdate.TO_DELETE_LOCALLY);
			}
		}
		/*
		 * FILES WHICH HAVE CHANGED HASHES LOCALLY
		 */
		Log.info("NEW5");

		for (String key : newHash.keySet()) {
			if (key.equals(lastRefKeyIgnore)) {
				continue;
			}
			if (oldHash.containsKey(key) && gloHash.containsKey(key)) {
				if (!newHash.get(key).equals(oldHash.get(key)) &&
						oldHash.get(key).equals(gloHash.get(key))) {
					tempTodos.put(
							LO_IND.concat(key),
							FileUpdate.TO_UPDATE_GLOBALLY);
				}
			}
		}
		/*
		 * FILES WHICH HAVE CHANGED HASHES GLOBALLY
		 */
		Log.info("NEW6");

		for (String key : gloHash.keySet()) {
			if (key.equals(lastRefKeyIgnore)) {
				continue;
			}
			if (oldHash.containsKey(key) && newHash.containsKey(key)) {
				if (!gloHash.get(key).equals(newHash.get(key)) &&
						oldHash.get(key).equals(newHash.get(key))) {
					Log.info("1TAHASHDSAH"+gloHash.get(key));
					Log.info("2TAHASHDSAH"+newHash.get(key));
					Log.info("3TAHASHDSAH"+oldHash.get(key));


					tempTodos.put(
							S3_IND.concat(key),
							FileUpdate.TO_UPDATE_LOCALLY);
				}
			}
		}
		Log.info("NEW7");

		/*
		 * FILES WHICH HAVE MOVED PLACES LOCALLY
		 */
		/*for (FileLocation newP: todos.keySet()) {
			if (todos.get(newP).equals(FileUpdate.TO_ADD_LOCALLY)) {
				for (FileLocation oldP : todos.keySet()) {
					if (todos.get(oldP).equals(FileUpdate.TO_DELETE_LOCALLY)) {
						if (oldP.getLocalStr().equals(newP.getLocalStr())) {
							String newHashStr=gloHash.get(newP.getLocalStr());
							String oldHashStr=gloHash.get(oldP.getLocalStr());
							if (newHashStr.equals(oldHashStr)) {
								// TODO DO SOMETHING
							}
						}
					}
				}
			}
		}*/
		// TODO this
		/*
		 * FILES WHICH HAVE MOVED PLACES GLOBALLY
		 */
		// TODO this
		TimeManager.notifyFinished(Timing.SYNC_TODO_CALC.id);
		return replaceFileLists(tempTodos);
	}
	
	
}
