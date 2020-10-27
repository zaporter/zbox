package com.zackporter.util;

import com.zackporter.box.Box;
import com.zackporter.box.Main;

public class S3Loc extends FileLocation{
	private String bucket;
	public String getBucket() {
		return bucket;
	}
	public void setBucket(String bucket) {
		this.bucket=bucket;
	}
	public S3Loc(Box b, String file) {
		init(b.key,file);
		setBucket(b.bucket);
	}
	public S3Loc(String bucket, String key, String file) {
		init(key,file);
		setBucket(bucket);
	}
	public S3Loc(String key, String file) {
		init(key,file);
		setBucket(Main.DEFAULT_BUCKET);
	}
	public LocalLoc getCorrespondingLocal(Box b) {
		return new LocalLoc(b.folderLoc,this.getLocalStr());
	}
}
