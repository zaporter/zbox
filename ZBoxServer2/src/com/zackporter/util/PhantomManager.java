package com.zackporter.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zackporter.box.Box;
import com.zackporter.box.Main;
import com.zackporter.box.S3I;
import com.zackporter.logging.Log;

public class PhantomManager {
	private S3Loc file;
	private Box box;
	public PhantomManager(Box b, S3Loc file) {
		this.file=file;
		this.box=b;
	}
	private String getTempName(String local) {
		for (int i = local.length()-1; i>0; i--) {
			if (local.charAt(i)=='.') {
				return local.substring(i);
			}
		}
		return local;
	}
	public void publishPhantoms(Set<S3Loc> current, Map<FileLocation, FileUpdate> todo) {
		clearPhantomFile();
		List<String> files = new ArrayList<String>();
		for (S3Loc s : current) {
			files.add(s.getLocalStr());
		}
		for (FileLocation fl : todo.keySet()) {
			FileUpdate update = todo.get(fl);
			if (update==(FileUpdate.TO_ADD_GLOBALLY)) {
				files.add(fl.getLocalStr());
			}
		}
		S3I.putObjectSimple(file, files);
		Log.info("Uploaded Phantoms for other computers");
	}
	public void createLocalPhantoms(Set<LocalLoc> current) {
		if (!S3I.doesObjectExist(file)) {
			Log.warn("No phantom file present. Ignoring");
			return;
		}
		Collection<String> lines = S3I.loadFile(file);
		Set<String> currentStr = new HashSet<>();
		for (LocalLoc l : current) {
			currentStr.add(l.getLocalStr());
		}
		for (String f : lines) {
			if (currentStr.contains(f)) {
				continue;
			} 
			// ELSE it will be something that will be added next sync
			LocalLoc ll = new LocalLoc(box, getTempName(f) + Main.PHANTOM_TEMP_SUFFIX);
			FileInterface.WRITE(ll.getFile(), ""
					+ "This is a temporary file to indicate the file will be uploaded soon");
		}
		Log.info("Created all local phantoms");
	}
	public void updatePhantoms(FileLocation real, FileUpdate update) {
		if (update!=FileUpdate.TO_ADD_LOCALLY) {
			return;
		}
		String temp = getTempName(real.getLocalStr()) + Main.PHANTOM_TEMP_SUFFIX;
		LocalLoc file = new LocalLoc(box, temp);
		if (file.getFile().exists()) {
			FileInterface.DELETE_FILE(file.getFile());
			Log.info("Deleted phantom for "+real);
		} else {
			Log.warn("Cannot remove phantom for file "+real+" because it doesnt exist");
		}
	}
	public void clearPhantomFile() {
		S3I.deleteObject(file);
	}
}
