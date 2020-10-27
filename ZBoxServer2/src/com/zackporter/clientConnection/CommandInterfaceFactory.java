package com.zackporter.clientConnection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.zackporter.box.Box;
import com.zackporter.box.BoxManager;
import com.zackporter.box.Main;
import com.zackporter.commandInterpret.CommandArg;
import com.zackporter.commandInterpret.CommandInterpreter;
import com.zackporter.commandInterpret.CommandType;
import com.zackporter.creds.CredsManager;
import com.zackporter.logging.Log;
import com.zackporter.timing.TimeManager;
import com.zackporter.util.InstallManager;
import com.zackporter.util.LocalLoc;

public class CommandInterfaceFactory {
	public String getPWD() {
		return "";
	}
	public CommandInterpreter genCIClient(BoxManager bm, ZServer connection) {
		List<CommandType> ct = new ArrayList<CommandType>();
		ct.add(
				new CommandType("ping",new CommandArg[] {
				}, 
				"    say pong") {
				public void execute(List<String> out) {
					out.add("pong");
				}
			});
		ct.add(
				new CommandType("remove",new CommandArg[] {
					new CommandArg("path","p",true),
					new CommandArg("box","b",true),
					new CommandArg("files","f",false),
				}, 
				"    Removes box from local system \n"
				+ "    Format: remove [options]\n"
				+ "    Example: remove -b test1box [options]\n"
				+ "    Options: (One of two first required)\n"
				+ "    -p, -path [relative file path]: specifies root folder of box\n"
				+ "    -b, -box [box name]: specifies name of box\n"
				+ "    -f, -files: Removes all files present in the box") {
				public void execute(List<String> out) {
					boolean deleteFiles = getArg("files").triggered;
					if (getArg("path").triggered){
						String file = (getPWD()+getArg("path").argument);
						Box b = bm.getBoxFile(file);
						if (b!=null) {
							bm.remove(b, deleteFiles);
							Log.l("Removed box "+b.toString());
						}else {
							Log.l("Invalid box path: "+getArg("path").argument);
							return;
						}
					}else if (getArg("box").triggered) {
						Box b = bm.getBoxKey(getArg("box").argument);
						if (b!=null) {
							bm.remove(b, deleteFiles);
							Log.l("Removed box "+b.toString());
						}else {
							Log.l("Invalid box name: "+getArg("box").argument);
							return;
						}
					}else {
						Log.l("Remove requires either path or box to be specified");
					}
				}
			});
		ct.add(
				new CommandType("create",new CommandArg[] {
					new CommandArg("#0","",true),
					new CommandArg("#1","",true),
				}, 
				"    Link a pre-existing box to a folder or create a new box if none exist.\n"
				+ "    Format: create [box name] [folder path]\n"
				+ "    Example: create test1box /home/joe/Documents\n"
				+ "    Expected outcome: Link the public box test1box\n"
				+ "    to /home/joe/Documents and upload all current files while \n"
				+ "    downloading those that are already there.") {
				public void execute(List<String> out) {
					String box = getParameter(0).argument;
					File file = new File(getPWD()+getParameter(1).argument);
					bm.createBox(box, file.getAbsolutePath());
					Log.l("Created box "+bm.getBoxKey(box));
				}
			});
		ct.add(
				new CommandType("delete",new CommandArg[] {
					new CommandArg("#0","",true),
				}, 
				"    Deletes box from S3. \n"
				+ "    Format: delete [box name]\n"
				+ "    This DOES NOT remove the box from the local filesystem\n"
				+ "    Warning: This will require a password and should be done carefully") {
				public void execute(List<String> out) {
					bm.deleteBox(getParameter(0).argument);
				}
			});
		ct.add(
				new CommandType("ls",new CommandArg[] {
					new CommandArg("#0","",false),
					new CommandArg("unsynced","u",false),
					new CommandArg("synced","s",false),
					new CommandArg("all","a",false),
					new CommandArg("deleted","d",false),
				}, 
				"    List files of a certain type.\n"
				+ "    Format: ls [box name] [options]\n"
				+ "    Example: ls test1box -a -h\n"
				+ "    If no box is specified, all boxes will be listed instead\n"
				+ "    Options: \n"
				+ "    -s, -synced: lists all synced files\n"
				+ "    -a, -all: lists all files in the box\n"
				+ "    -d, -deleted: lists all recently deleted files") {
				public void execute(List<String> out) {
					if (getParameter(0).triggered) {
						boolean s = getArg("s").triggered;
						boolean a = getArg("a").triggered;
						boolean d = getArg("d").triggered;
						Box b = bm.getBoxKey(getParameter(0).argument);
						if (b==null) {
							Log.l("Invalid box: "+getParameter(0).argument);
							return;
						}
						Log.l(b.toString());
						
						if (a) {
							for (LocalLoc file : b.getFiles()) {
								Log.l(file);
							}
						}else if (s) {
							for (LocalLoc file : b.getSynced()) {
								Log.l(file);
							}
						}else if (d) {
							
						}
					}else {
						List<String> keys = bm.getAllBoxKeysS3();
						List<String> formattedKeys = new ArrayList<>();
						for (String key : keys) {
							if (bm.localBoxesContains(key)) {
								formattedKeys.add(bm.getBoxKey(key).toString());
							}else {
								formattedKeys.add(key);
							}
						}
						Log.l(formattedKeys);
					}
				}
			});
		ct.add(
				new CommandType("getOutput",new CommandArg[] {
					new CommandArg("","",false),
				}, 
				"    Hooks up an output-stream of current log data.\n"
				+ "    Format: getOutput\n"
				+ "    Warning: This must be exited with ctrl+C") {
				public void execute(List<String> out) {
					
				}
			});
		ct.add(
				new CommandType("timingInfo",new CommandArg[] {
				}, 
				"    Prints timing info for current session\n"
				+ "    Format: timingInfo") {
				public void execute(List<String> out) {
					TimeManager.printResults();
				}
			});
		ct.add(
				new CommandType("sync",new CommandArg[] {
					new CommandArg("path","p",true),
					new CommandArg("box","b",true),
				}, 
				"    Force a sync of a box or of all boxes.\n"
				+ "    Format: sync [options]\n"
				+ "    OR Format: sync\n"
				+ "    If no argument is provided, all boxes are synced.\n"
				+ "    Options: (One of two required for single sync)\n"
				+ "    -p, -path [relative file path]: specifies root folder of box\n"
				+ "    -b, -box [box name]: specifies name of box") {
				public void execute(List<String> out) {
					if (getArg("path").triggered) {
						String file = (getPWD()+getArg("path").argument);
						Box b = bm.getBoxFile(file);
						if (b==null) {
							Log.l("No valid box found at the provided file: "+getArg("path").argument);
							return;
						}
						b.sync();
					}else if (getArg("box").triggered) {
						Box b = bm.getBoxKey(getArg("box").argument);
						if (b==null) {
							Log.l("No valid box found: "+getArg("box").argument);
							return;
						}
						b.sync();
						Log.l("Synced box "+b);
					}else {
						bm.sync();
					}
				}
			});
		ct.add(
				new CommandType("start",new CommandArg[] {
				}, 
				"    ") {
				public void execute(List<String> out) {
					bm.startSyncThread();
					Log.l("Starting Box Manager");
				}
			});
		ct.add(
				new CommandType("shutdown",new CommandArg[] {
				new CommandArg("force","f",false),
				new CommandArg("thread","t",false)}, 
				"    Shutdown the box:"
				+ "		Options:"
				+ "		-f, -force: force shutdown now. (Otherwise will finish sync in progress if any then shutdown)\n"
				+ "     -t, -thread: only shut down the sync thread") {
				public void execute(List<String> out) {
					if (getArg("force").triggered) {
						bm.shutdownSyncThreadFORCE();
					}else {
						bm.shutdownSyncThread();
					}
					Log.l("Shut down Box Manager");
					if (!getArg("thread").triggered) {
						Main.prepareForShutdown();
						connection.comm.shutdownOther();
						connection.comm.shutdownSelf();
						Main.shutdown();
					}
					// ZServer does the rest of the shutdown
				}
			});
		/**
		 * ct.add(
			new CommandType("",new CommandArg[] {
				new CommandArg("","",false),
			}, 
			"") {
			public void execute(List<String> out) {
			}
		});
		 */
		CommandInterpreter ci = new CommandInterpreter("ZBox",ct);
		return ci;
	}
	public CommandInterpreter genCIMain() {
		List<CommandType> ct = new ArrayList<CommandType>();
		ct.add(new CommandType("start",new CommandArg[] {
				}, 
				"    Start the ZBox server (Equivalent to no args)") {
				public void execute(List<String> out) {
				Main.startZBox();
				};
		});
		ct.add(new CommandType("install",new CommandArg[] {
		}, 
			"    install zbox at ~/.zbox") {
			public void execute(List<String> out) {
				InstallManager.install();
			};
		});
		ct.add(new CommandType("uninstall",new CommandArg[] {
		}, 
				"    uninstall zbox from ~/.zbox") {
			public void execute(List<String> out) {
				InstallManager.uninstall();
		};
		});
		ct.add(new CommandType("encryptCreds",new CommandArg[] {
				new CommandArg("#0","",true),
				new CommandArg("#1","",true),
		}, 
				"    Generate the credentials file required to login\n"
				+ "   Format: encryptCreds [input] [output]") {
			public void execute(List<String> out) {
				CredsManager.createEncryptedCreds(
						getParameter(0).argument, 
						getParameter(1).argument);
			};
		});
				
		/**
		 * ct.add(
			new CommandType("",new CommandArg[] {
				new CommandArg("","",false),
			}, 
			"") {
			public void execute(List<String> out) {
			}
		});
		 */
		CommandInterpreter ci = new CommandInterpreter("ZBox",ct);
		return ci;
	}
}
