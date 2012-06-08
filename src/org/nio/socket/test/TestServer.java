package org.nio.socket.test;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
	
	public static SSLContext getSSLContext() throws Exception{
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(new FileInputStream("testssl.ks"),"test123".toCharArray());
		 SSLContext sslContext = SSLContext.getInstance("TLS");
		 //System.out.println(ks.getCertificate("10.10.0.2").toString());
		 
		 KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		 kmf.init(ks, "test123".toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);
		 
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
	}

	
	public static void main(String args[]) {
		SocketServer server = new SocketServer(new InetSocketAddress("127.0.0.1", 8080));
		server.setClientHandler(new TestServer());
		try {
			server.setSSLContext(getSSLContext());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		server.setExecuterService(Executors.newFixedThreadPool(3));
		server.start();
		
	}
	
	
}
