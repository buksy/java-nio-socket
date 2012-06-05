package org.nio.socket.test;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.nio.socket.ClientHandler;
import org.nio.socket.SocketClient;
import org.nio.socket.SocketServer;


public class TestServer implements ClientHandler{

	@Override
	public void handle(SocketClient cleint) {
		InputStream in = cleint.getInputStream();
		OutputStream out = cleint.getOutputStream();
		
		String str = Thread.currentThread().toString();
		
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
		String cli = ""; 
		try {
//			while(in.available() > 0) {
//				client += reader.readLine();
//			}
			while ((cli = reader.readLine())!=null) {
				writer.write("Server "+str+" "+cli);
				writer.newLine();
				writer.flush();
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		
	}

	
	public static void main(String args[]) {
		SocketServer server = new SocketServer(new InetSocketAddress("127.0.0.1", 8080));
		server.setClientHandler(new TestServer());
		server.setExecuterService(Executors.newFixedThreadPool(3));
		server.start();
		
	}
	
	
}
