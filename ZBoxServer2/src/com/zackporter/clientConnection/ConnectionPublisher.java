package com.zackporter.clientConnection;

import com.zackporter.logging.LogLevel;
import com.zackporter.logging.Publisher;
import com.zackporter.util.SimpleComm;

public class ConnectionPublisher extends Publisher{
	private SimpleComm comm;
	public ConnectionPublisher(SimpleComm comm) {
		this.setThreadID(comm.receiveThreadID);
		this.comm=comm;
	}
	@Override
	public void publish(LogLevel level, String prefix, String text) {
		comm.send(prefix+text);
	}

}
