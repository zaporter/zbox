package com.zackporter.ZboxClient;

import java.util.List;

import com.zackporter.logging.Log;
import com.zackporter.util.SimpleComm;

public class ZClient {
	static boolean finishedTransmission=false;
	public static void main(String[] args) {
		SimpleComm comm;
		comm = new SimpleComm("localhost",9082,9083) {
			public void receive(String s) {
				Log.println(s);
			}
		};
		comm.init();
		boolean connected = comm.testConnection(200);
		if (!connected) {
			Log.println("Unable to connect to zboxserver. Has it been started?");
			comm.shutdownSelf();
		}else {
			StringBuilder sb = new StringBuilder();
			for (String s : args) {
				sb.append(s);
				sb.append(" ");
			}
			comm.send(sb.toString());
		}
	}
}
