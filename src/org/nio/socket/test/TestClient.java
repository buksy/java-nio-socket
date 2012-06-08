package org.nio.socket.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import org.nio.socket.SocketClient;

public class TestClient {

	public static void main(String args[]) {
		for (int i = 0 ; i< 10 ; i++) {
			new Thread() {
				public void run() {
					SocketClient client = new SocketClient(new InetSocketAddress("127.0.0.1", 8080));
					try {
						client.setSSLContext(TestServer.getSSLContext());
						client.connect();
						while(!client.isConnected()) {
							System.out.println("Cleint not connected");
							sleep(1000);
						}
						OutputStream out = client.getOutputStream();
						out.write(("Hello from Therad "+Thread.currentThread().getId()).getBytes());
						out.write("\r\n".getBytes());
						out.flush();
						
						System.out.println("send hello");
						
						BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
						System.out.println(reader.readLine());
						client.close();
						
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					
				}
			}.start();
		}
	}
	
}
