package com.zackporter.util;

import com.zackporter.timing.TimeManager;

public enum Timing {
	UPLOAD(0,"upload"),
	GETOBJECTS(1,"getobjects"),
	DOWNLOAD(2,"download"),
	PUT_OBJECT(3,"putobject"),
	GET_LOCK(4,"getlock"),
	REFRESH_HASH(5,"refreshHash"),
	SYNC(6,"sync"),
	SAVE_HASHES_LOCALLY(7,"saveHashesLocally"),
	SAVE_HASHES_S3(8,"saveHashesS3"),
	LOAD_HASHES_LOCALLY(9,"loadHashesLocally"),
	LOAD_HASHES_S3(10,"loadHashesS3"),
	SYNC_TODO_CALC(11,"calcSyncTodos"),
	LIST_FILES(12,"listFiles"),
	UPDATE_MAPS(13, "updateMaps"),
	LOAD_FILE(14,"loadFile"),
	CALC_HASH(15,"calcHash")
;
	public final Integer id;
	public final String name;
	private Timing(int in, String name) {
		id=in;
		this.name=name;
	}
	@Override
	public String toString() {
		return id.toString()+"/"+name;
	}
	public static void initAll() {
		for (Timing t : Timing.values()) {
			TimeManager.registerProcess(t.id, t.name);
		}
	}
}
