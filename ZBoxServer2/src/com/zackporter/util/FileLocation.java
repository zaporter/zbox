package com.zackporter.util;

import java.io.File;
import java.util.Map;

import com.zackporter.box.Box;
import com.zackporter.box.Main;
import com.zackporter.logging.Log;

public abstract class FileLocation {
	protected static final String SAVE_S3_PREFIX="S3";
	protected static final String SAVE_LOC_PREFIX="LO";
	private String prefix;
	private String location;
	private File file;
	public String getGlobalStr() {
		if (this instanceof S3Loc) {
			if (location=="" || location == null) {
				return prefix;
			}else {
				return prefix.concat("/").concat(location);
			}
		}else {
			return file.getAbsolutePath();
		}
	}
	public boolean isS3Instance() {
		return (this instanceof S3Loc);
	}
	public boolean isLocInstance() {
		return (this instanceof LocalLoc);
	}
	public FileLocation getFromMap(Map<S3Loc, String> map) {
		for (S3Loc j : map.keySet()) {
			if (j.getLocalStr().equals(this.location)) {
				return j;
			}
		}
		return null;
	}
	public String getDataMap(Map<S3Loc, String> map) {
		return map.get(getFromMap(map));
	}
	public String getLocalStr() {
		return location;
	}
	public String getPrefixStr() {
		return prefix;
	}
	public File getFile() {
		return file;
	}
	public S3Loc getS3Instance(Box b) {
		return new S3Loc(b.key,location);
	}
	public LocalLoc getLocInstance(Box b) {
		return new LocalLoc(b.folderLoc,location);
	}
	protected void init(String pre, String loc) {
		if (loc.length()>0&&pre.length()>0 &&(
			loc.charAt(0) == '/' || pre.charAt(pre.length()-1)=='/')) {
			loc=loc.substring(1);
		}
		this.prefix=pre;
		this.location=loc;
		this.file=new File(pre+"/"+loc);
		
	}
	public String getSaveString() {
		StringBuilder str = new StringBuilder();
		if (this instanceof S3Loc) {
			str.append(SAVE_S3_PREFIX);
		}else if (this instanceof LocalLoc) {
			str.append(SAVE_LOC_PREFIX);
		}else {
			Log.err("Unable to determine if a FileLocation is S3 or Local. Was a new type added?");
		}
		str.append(Main.SEP_SEQ);
		str.append(prefix);
		str.append(Main.SEP_SEQ);
		str.append(location);
		
		return str.toString();
	}
	@Override
	public String toString() {
		return getGlobalStr();
	}
	public static FileLocation loadFileLocation(String line) {
		String[] parts = line.split(Main.SEP_SEQ);
		FileLocation file;
		String type = parts[0];
		if (type.equals(SAVE_S3_PREFIX)) {
			file = new S3Loc(parts[1],parts[2]);
		}else if (type.equals(SAVE_LOC_PREFIX)) {
			file = new LocalLoc(parts[1], parts[2]);
		}else {
			Log.err("Invalid file type: "+type);
			return null;
		}
		return file;
	}
	
}
