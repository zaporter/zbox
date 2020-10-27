package com.zackporter.clientConnection;

import java.util.List;

import com.zackporter.box.Main;
import com.zackporter.commandInterpret.CommandInterpreter;
import com.zackporter.logging.Log;
import com.zackporter.logging.LogLevel;
import com.zackporter.logging.Publisher;
import com.zackporter.util.SimpleComm;

public class ZServer {
	public SimpleComm comm;
	public ConnectionPublisher pub;
	private boolean working=false;
	public ZServer() {
		
	}
	public void start(CommandInterpreter ci) {
		comm = new SimpleComm("localhost",9083,9082) {
			public void receive(String s) {
				Log.l("RECEIVED: "+s);
				try {
					pub.enable();
					if (s.equals(comm.SHUTDOWN_ALERT)) {
						
					}else {
					// TODO
						
					}
					List<String> res = ci.interpret(s);
					
					pub.disable();
					for (String k : res) {
						send(k);
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
				shutdownOther();
			}
		};
		comm.init();
		pub = new ConnectionPublisher(comm);
		pub.disable();
		Log.registerPublisher(pub);
	}
	
	public void shutdown() {
		comm.shutdownSelf();
	}
	
}
