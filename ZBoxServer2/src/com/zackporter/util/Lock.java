package com.zackporter.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import com.zackporter.box.Main;
import com.zackporter.box.S3I;
import com.zackporter.logging.Log;
import com.zackporter.timing.TimeManager;

public class Lock {
	private S3Loc loc;
	private volatile boolean lockThreadRunning=false;
	public Lock(S3Loc location) {
		loc=location;
	}
	public void insertLockFile() {
		S3I.putObjectSimple(loc, getLockContents());
	}
	public String getLockContents() {
		String lock = getLockPrefix();
		lock+=Main.SEP_SEQ;
		lock+=System.currentTimeMillis();
		return lock;
	}
	private String getLockPrefix() {
		String lock = Main.LOCK_PREFIX+Main.SEP_SEQ;
		lock+=getHostName();
		return lock;
	}
	public  String getHostName() {
		String hostname = "Unknown";
		try{
		    InetAddress addr;
		    addr = InetAddress.getLocalHost();
		    hostname = addr.getHostName();
		} catch (UnknownHostException ex) {
		    Log.err("Hostname can not be resolved");
		}
		return hostname;
	}
	public boolean isLocked() {
		return S3I.doesObjectExist(loc);
	}
	public boolean shouldHaveLock() {
		return lockThreadRunning;
	}
	public boolean isLockOutdated() {
		if (!isLocked()) {
			return true;
		}
		Collection<String> lockfile;
		try { 
			lockfile = S3I.loadFile(loc);
		} catch (Exception e) {
			Log.warn("Error checking for outdated file. Ignoring request.");
			return false;
		}
		if (lockfile.size()!=1) {
			Log.err("Invalid lock file. It has too many lines on lock: "+loc);
			return false;
		}
		String lock = (String) lockfile.toArray()[0];
		String[] lockParts = lock.split(Main.SEP_SEQ);
		if (lockParts.length!=3) {
			Log.err("UNABLE TO CHECK LOCK. It doesnt have 3 parts "+lock);
			return false;
		}
		long lockTime = Long.parseLong(lockParts[2]);
		if (System.currentTimeMillis()-lockTime > Main.LOCK_TIMEOUT) {
			return true;
		}
		return false;
	}
	public boolean hasLock() {
		if (isLocked()) {
			Collection<String> file;
			try {
				file = S3I.loadFile(loc);
			}catch (Exception e) {
				Log.warn("Unable to load lockfile from S3. Assuming no lock.");
				e.printStackTrace();
				return false;
			}
			if (((String)file.toArray()[0]).startsWith(getLockPrefix())) {
				return true;
			}
		}
		return false;
	}
	public void getLock() {
		 getLock(Integer.MAX_VALUE);
	}
	public void getLock(int retries) {
		TimeManager.notifyStarted(Timing.GET_LOCK.id);
		while (isLocked()) {
			if (isLockOutdated()) {
				Log.info("Outdated lock. Stealing lock as "+getLockPrefix());
				break;
			}else {
				Log.info("box "+loc+" is recently locked. Waiting "+Main.GETLOCK_WAIT+" milis");
				try {
					Thread.sleep(Main.GETLOCK_WAIT);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		insertLockFile();
		Thread lockThread = new Thread() {
			public void run() {
				lockThreadRunning=true;
				while (lockThreadRunning) {
					insertLockFile();
					try {
						Thread.sleep(Main.LOCK_REFRESH);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		if (hasLock()) {
			Log.info(""+loc+" got a lock");
			lockThread.start();
		}else {
			Log.warn("Failed to get lock. Retrying. (THIS SHOULD BE A RARE OCCURENCE)");
			// This should only happen if two dueling computers happen to try and get a lock at
			// the same time and both notice it is open at the same time and try to take it.
			// Then they wait a varying free amount of time and try again
			freeLock();
			try {
				Thread.sleep((int)(Math.random()*500)); 
			}catch (Exception e) {
				e.printStackTrace();
			}
			getLock();
		}
		TimeManager.notifyFinished(Timing.GET_LOCK.id);
	}
	public void freeLock() {
		lockThreadRunning=false;
		if (this.hasLock()) {
			S3I.deleteObject(loc);
		}
		Log.info(""+loc+" freed lock");
	}
	
	public void freeLockFORCE() {
		Log.warn("Force removing lock "+loc);
		Log.warn("This is not reccomended and may cause concurrency issues");
		lockThreadRunning=false;
		S3I.deleteObject(loc);
	}
	
}
