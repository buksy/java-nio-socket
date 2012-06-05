/**
 * Copyright [2012] [Gihan Munasinghe ayeshka@gmail.com ]
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.nio.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;


public class SocketServer extends Thread{
	/**
	 * Holds the listening socket address. 
	 */
	private InetSocketAddress listener; 
	/**
	 * If the server needs to be SSL pass the context 
	 */
	private SSLContext sslContext;
	
	/**
	 * The executer to handled the incoming requests. 
	 */
	private ExecutorService executer; 

	private Selector selector;
	
	private ServerSocketChannel server;
	
	private ClientHandler handler = null;
	
	public SocketServer(InetSocketAddress sockAdd) {
		this.listener = sockAdd;
	}
	
	public void setSSLContext(SSLContext context){
		sslContext = context;
	}
	
	public void setClientHandler(ClientHandler handler) {
		this.handler = handler;
	}
	
	@Override
	public synchronized void start() {
		if(executer == null) {
			executer = Executors.newSingleThreadExecutor();
		}
		super.start();
	}
	
	@Override
	public void run() {
		try {
			server = ServerSocketChannel.open();
			server.configureBlocking( false );
			ServerSocket socket = server.socket();
			socket.bind( listener );
			selector = Selector.open();
			server.register( selector, server.validOps() );
			
			while (true) {
				selector.select();
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> ite = keys.iterator();
				while (ite.hasNext()) {
					ite.remove();
					SelectionKey key = ite.next();
					
					if(!key.isValid())
						continue;
					
					if(key.isAcceptable()) {
						SocketChannel channel = server.accept();
						channel.configureBlocking( false );
						SocketClient sc = newSocketClient(channel);
						if(sc!=null) {
							if(sslContext!=null) {
								sc.setSSLContext(sslContext);
							}
							channel.register(selector, SelectionKey.OP_READ, sc);
						}
						continue;
					}
					
					if(key.isReadable()) {
						key.interestOps(0);
						handleRead(key);
					}
					
					if(key.isWritable()) {
						key.interestOps(0);
						handleWrite(key);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * This method will be triggered by the server each time the there is a 
	 * new connection made by to the sever. 
	 * If you want to handled the connections and check the connections. 
	 * Override this method
	 * @param sc
	 * @return
	 */
	protected SocketClient newSocketClient(SocketChannel sc) {
		return new SocketClient(sc);
	}
	
	private void handleRead(SelectionKey key) {
		SocketClient sc = (SocketClient)key.attachment();
		if(sc.isReadBlocked()) { 
			// If there is block read going on, this means some thread is in a waiting state
			// So we should just unblock the thread
			sc.unblockRead();
		}else {
			// Get the executer to run the request
			ReadHandler rh = new ReadHandler(key);
			executer.execute(rh);
		}
	}
	
	/*
	 * You can either get your server thread to do the writing,
	 * if you are using the stream based approach. you can get the thread invoking the 
	 * flush to do the writing 
	 */
	private void handleWrite(SelectionKey key) throws IOException {
		SocketClient sc = (SocketClient)key.attachment();
		if(sc.isWriteBlocked()) {
			// A thread is interested in doing the write, some thread is using streams
			sc.unblockWrite();
		}else {
			// Just let server thread do the write
			sc.doWrite();
		}
		if(sc.isConnected()) 
			key.interestOps(SelectionKey.OP_READ);
		
	}
	
	private class ReadHandler implements Runnable {
		private SocketClient socketClient;
		private SelectionKey key = null;
		
		ReadHandler(final SelectionKey sc ) {
			socketClient = (SocketClient)sc.attachment();
			key = sc;
		}
		@Override
		public void run() {
			try {
				if(handler!=null)
					handler.handle(socketClient);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally {
				if(socketClient.isConnected()) {
					key.interestOps(SelectionKey.OP_READ);
				}
			}
			
		}

	}
}
