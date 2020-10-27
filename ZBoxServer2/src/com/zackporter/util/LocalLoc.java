package com.zackporter.util;

import java.io.File;
import java.util.List;

import com.zackporter.box.Box;

public class LocalLoc extends FileLocation{
	
	public LocalLoc(File folder, String file) {
		init(folder.getAbsolutePath(), file);
	}
	public LocalLoc(Box b, String file) {
		this(b.folderLoc, file);
	}
	public LocalLoc(String folder, String file) {
		init(folder, file);
	}
	public S3Loc getCorrespondingS3(Box b) {
		return new S3Loc(b,this.getLocalStr());
	}
	public LocalLoc clone() {
		return new LocalLoc(this.getPrefixStr(), this.getLocalStr());
	}
	public File[] listFiles(){
		return this.getFile().listFiles();
	}
}
