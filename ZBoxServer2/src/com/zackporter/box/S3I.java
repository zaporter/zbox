package com.zackporter.box;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.zackporter.creds.CredsManager;
import com.zackporter.logging.Log;
import com.zackporter.textmanipulation.ProgressBar;
//import org.apache.commons.logging.LogFactory
import com.zackporter.timing.TimeManager;
import com.zackporter.util.LocalLoc;
import com.zackporter.util.S3Loc;
import com.zackporter.util.Timing;

public class S3I {
	private static AmazonS3 s3;
	private static volatile int semBatchUpload=0;
	private static volatile int semBatchDownload=0;
	private static AmazonS3 login() {
		CredsManager cm = new CredsManager(Main.CREDS_FOLDER);
		boolean success = false;
		int tries = 0;
		AmazonS3 s3Client=null;
		List<String> creds=null;
		while (!(success || tries++>3)) {
			try {
				success=true;
				creds = cm.getCreds();
				String key = creds.get(CredsManager.IAM_USERNAME);
				String secret = creds.get(CredsManager.IAM_SECRET_KEY);
				String region = creds.get(CredsManager.AWS_REGION);
				Log.info("Creds loaded. Attempting login.");
				BasicAWSCredentials awsCreds =
						new BasicAWSCredentials(key, secret);
				s3Client = AmazonS3ClientBuilder.standard()
										.withRegion(region)
				                        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
				                        .build();
			}catch (Exception e) {
				Log.warn("Unable to login with provided password");
				success=false;
			}
		}
		if (success) {
			cm.saveCreds(creds);
			return s3Client;
		}
		else {
			Main.prepareForShutdown();
			Main.shutdown();
		}
		// unreachable code
		return null;
	}
	public static AmazonS3 init(String bucketName) {
		//AmazonS3ClientBuilder builder = AmazonS3Client.builder();
		AmazonS3 s3 = login();
	
		Log.l("Login success? Attempting bucket listing");
		//AmazonS3 s3= AmazonS3ClientBuilder.defaultClient();
	    List<Bucket> buckets = s3.listBuckets();
	    boolean containsBucket=false;
	    for(Bucket b:buckets) {
	    	if (b.getName().equals(bucketName)) {
	    		containsBucket=true;
	    	}
	    }
	    if(!containsBucket) {
        	Log.err("BUCKET "+bucketName+" doesnt exist! Cannot login. Exiting.");
        	System.exit(1);
        }else {
        	Log.info("Sucessfully logged in. Bucket "+bucketName+" exists");
        	Log.info("S3I created sucessfully");
        }
	    S3I.s3=s3;
	    return s3;
	}
	public static void putObjectSimple(S3Loc loc, String contents) {
		TimeManager.notifyStarted(Timing.PUT_OBJECT.id);
		s3.putObject(loc.getBucket(), loc.getGlobalStr(), contents);
		Log.info("Uploaded object (Simple String) "+loc);
		TimeManager.notifyFinished(Timing.PUT_OBJECT.id);
	}
	public static void putObjectSimple(S3Loc loc, List<String> lines) {
		StringBuilder sb = new StringBuilder();
		for (String s : lines) {
			sb.append(s);
			sb.append("\n");
		}
		putObjectSimple(loc, sb.toString());
	}
	public static void putObjectFileSimple(S3Loc loc, File contents) {
		TimeManager.notifyStarted(Timing.PUT_OBJECT.id);
		s3.putObject(loc.getBucket(), loc.getGlobalStr(), contents);
		Log.info("Uploaded object (File) "+loc);
		TimeManager.notifyFinished(Timing.PUT_OBJECT.id);
	}
	public static boolean deleteObject(S3Loc loc) {
		try {
			s3.deleteObject(loc.getBucket(), loc.getGlobalStr());
			Log.info("Deleted object "+loc);
			
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
	}
	public static boolean doesObjectExist(S3Loc loc) {
		return s3.doesObjectExist(loc.getBucket(), loc.getGlobalStr());
	}
	public static boolean uploadFile(Box box, LocalLoc loc)  {
		TimeManager.notifyStarted(Timing.UPLOAD.id);
		if (!loc.getFile().exists()) {
			Log.warn("Cannot upload "+loc.getGlobalStr()+". It doesnt exist");
			TimeManager.notifyFinished(Timing.UPLOAD.id);
			return false;
		}
		S3Loc newLoc = loc.getCorrespondingS3(box);
        TransferManager xfer_mgr = TransferManagerBuilder.defaultTransferManager();//.standard().withS3Client(s3).build();
        try {
            Upload xfer = xfer_mgr.upload(newLoc.getBucket(), newLoc.getGlobalStr(), loc.getFile());
            xfer.waitForCompletion();
        } catch (Exception e) {
        	Log.err("Cannot upload file: "+loc.getGlobalStr()+" to box: "+box.toString()+" at path: "+newLoc.getGlobalStr());
            e.printStackTrace();
            TimeManager.notifyFinished(Timing.UPLOAD.id);
            return false;
        }
        xfer_mgr.shutdownNow();
        TimeManager.notifyFinished(Timing.UPLOAD.id);
      //  Log.info("Uploaded file: "+loc+" to "+box);
        return true;
	}
	private static void deleteBoxFiles(Box box) {
		Log.info("Starting delete-box "+box.toString());
		List<S3Loc> files = getAllObjects(box);
		for (S3Loc s:files) {
			deleteObject(s);
			int c =0;
			if (c++%10==0) {
				Log.l(ProgressBar.produce(25, (c*100)/files.size()));
			}
		}
		Log.info("Deleted box: "+box.toString());
	}
	public static boolean downloadFile(Box box, S3Loc file) {
		TimeManager.notifyStarted(Timing.DOWNLOAD.id);
		try {
			LocalLoc newLoc = file.getCorrespondingLocal(box);
			S3Object s3object = s3.getObject(box.bucket, file.getGlobalStr());
			S3ObjectInputStream inputStream = s3object.getObjectContent();
			FileUtils.copyInputStreamToFile(inputStream, newLoc.getFile());
		} catch (Exception e) {
			Log.err("Unable to download file "+file+" From S3. Box:"+box.toString());
			e.printStackTrace();
			TimeManager.notifyFinished(Timing.DOWNLOAD.id);
			return false;
		}
		Log.info("Downloaded file: "+file.toString()+" to box: "+box);
		TimeManager.notifyFinished(Timing.DOWNLOAD.id);
		return true;
	}
	public static Collection<String> loadFile(S3Loc loc) {
		TimeManager.notifyStarted(Timing.LOAD_FILE.id);
		Collection<String> result = null;
	    try (final S3Object s3Object = s3.getObject(loc.getBucket(), loc.getGlobalStr());
	         final InputStreamReader streamReader = new InputStreamReader(s3Object.getObjectContent(), StandardCharsets.UTF_8);
	         final BufferedReader reader = new BufferedReader(streamReader)) {
	    	 Log.info("Loaded file: "+loc);
	    	result = reader.lines().collect(Collectors.toSet());
	    } catch (final IOException e) {
	        Log.err(e.getMessage());
	        result = Collections.emptySet();
	    }
	    TimeManager.notifyFinished(Timing.LOAD_FILE.id);
	    return result;
	}
	public static Collection<String> getAllBoxesRaw(){
		Collection<String> boxes;
		try {
			boxes = loadFile(Main.BOXES_FILE_GLOBAL);
		}catch (Exception e) {
			// TODO: prove that this is not !!!extremely!!! dangerous
			boxes = new ArrayList<String>();
		}
	    return boxes;
	}
	public static Collection<String> getAllBoxKeys(){
		Collection<String> keys = new ArrayList<String>();
		for (String k : getAllBoxesRaw()) {
			keys.add(k.split(Main.SEP_SEQ)[0]);
		}
		return keys;
	}
	public static boolean isBoxRegistered(String key){
		return getAllBoxKeys().contains(key);
	}
	public static boolean isBoxRegistered(Box b){
		return getAllBoxKeys().contains(b.key);
	}
	public static void registerBox(Box box) {
		if (isBoxRegistered(box)) {
			Log.warn("Tried to add "+box.toString()+" to "+Main.BOXES_FILE_GLOBAL+" yet it already exists!");
			return;
		}
		Collection<String> lines = getAllBoxesRaw();
		lines.add(box.key+Main.SEP_SEQ+box.parentBoxLoc);
		StringBuilder res = new StringBuilder();
		for (String s:lines) {
			res.append(s);
			res.append("\n");
		}
		putObjectSimple(Main.BOXES_FILE_GLOBAL,res.toString());
		Log.info("Registered box: "+box);
	}
	public static void removeBox(Box box) {
		if (!isBoxRegistered(box)) {
			Log.warn("Box "+box+" cannot be removed as it doesnt exist (in the file at least!)");
			return;
		}
		Collection<String> lines = getAllBoxesRaw();
		StringBuilder res = new StringBuilder();
		for (String s:lines) {
			if (s.split(Main.SEP_SEQ)[0].startsWith(box.key)) {
				// If it equals the key, do not add it.
			}else {
				res.append(s);
				res.append("\n");	
			}
		}
		putObjectSimple(Main.BOXES_FILE_GLOBAL,res.toString());
		Log.info("Removed box "+box);
	}
	public static void deleteBox(Box box) {
		Log.info("Deleting box "+box);
		removeBox(box);
		deleteBoxFiles(box);
		Log.info("Finished deleting box "+box);
	}
	public static List<S3Loc> getAllObjects(Box box) {
		TimeManager.notifyStarted(Timing.GETOBJECTS.id);
		ObjectListing listing = s3.listObjects(box.bucket, box.key);
		List<S3ObjectSummary> summaries = listing.getObjectSummaries();
		while (listing.isTruncated()) {
		   listing = s3.listNextBatchOfObjects(listing);
		   summaries.addAll (listing.getObjectSummaries());
		}
		List<S3Loc> keys = new ArrayList<>();
		for (S3ObjectSummary s3os : summaries) {
			keys.add(new S3Loc(box.bucket,
					box.key,
					s3os.getKey().substring(box.key.length()+1)));
		}
		TimeManager.notifyFinished(Timing.GETOBJECTS.id);
		Log.info("Got all objects in: "+box);
		return keys;
	}
}
