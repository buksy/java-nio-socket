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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;


public class SocketClient {
	
	
	//private Selector selector = null;
	private SocketChannel client = null;
	private Selector selector = null;
	private InetSocketAddress address = null;

	private SSLContext sslContext; 
	private SSLEngine sslEngine = null;
	private SSLHandler sslHandler = null;
	
	private boolean initConnDone = false;
	private boolean isClosed = false;
	
	private NIOSocketInputStream socketInputStream;
	private NIOSocketOutputStream socketOutputStream = null; 
	
	public SocketClient(InetSocketAddress address) {	
		this.address = address;
		socketInputStream = new NIOSocketInputStream(this);
		socketOutputStream = new NIOSocketOutputStream(this);
	}
	
	
	protected SocketClient(SocketChannel client,Selector key) {
		this.client = client;
		initConnDone = true;
		selector = key;
		socketInputStream = new NIOSocketInputStream(this);
		socketOutputStream = new NIOSocketOutputStream(this);
	}
	
	public void setSSLContext( SSLContext context) {
		this.sslContext = context;
	}
	
	protected void buildSSLHandler(SSLContext context, boolean clientmode) throws IOException {
		if(context!=null && client!=null  ) {
			setSSLContext(context);
			sslEngine = sslContext.createSSLEngine(client.socket().getInetAddress().getHostName(), client.socket().getPort());
			sslEngine.setUseClientMode(clientmode);
			sslHandler = new SSLHandler(sslEngine, client);
			//sslHandler.doHandshake();
			//System.out.println(((!clientmode)? "Server ": "Client ") + "Handshake done");
		}
	}
	
	public void connect() throws IOException {
		
		if(initConnDone)
			throw new IOException("Socket Already connected");
		
		client = SocketChannel.open();
		client.configureBlocking( false );
		client.connect( address );
		
		new Thread() {
			
			public void run() {
				try {
					selector = Selector.open();
					client.register(selector, SelectionKey.OP_CONNECT);
					
					while (!isClosed) {
						int nsel = selector.select();
						if (nsel == 0)
							continue;
						Set<SelectionKey> selectedKeys = selector.selectedKeys();
						Iterator<SelectionKey>	it = selectedKeys.iterator();
						while (it.hasNext())
						{
							SelectionKey key = it.next();
							it.remove();
							if (!key.isValid()) 
								continue;
							if(key.isValid() && key.isConnectable()) {
								if(client.finishConnect()) {
									if(sslContext!=null) { 
										// Do the SSL handshake stuff ;
										buildSSLHandler(sslContext,true);
									}
									client.register(selector,SelectionKey.OP_READ);
									initConnDone = true;
									
								}
							}
							if(key.isValid() && key.isReadable()) {
								unblockRead();
								if(client.isOpen())
									client.register(selector,SelectionKey.OP_READ);
							}
							if(key.isValid() && key.isWritable()) {
								unblockWrite();
								if(client.isOpen())
									client.register(selector,SelectionKey.OP_READ);
							}
						}
					}
				
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}finally {
					try {
						selector.close();
						client.close();
						socketInputStream.close();
						socketOutputStream.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			}
			
		}.start();
		
	}

	protected SocketChannel getSocketChannel() {
		return client;
	}
	
	protected boolean isReadBlocked() {
		return socketInputStream.isReadWait();
	}
	
	protected void unblockRead() {
		socketInputStream.notifyRead();
	}
	
	
	protected boolean isWriteBlocked() {
		return socketOutputStream.isWriteWait();
	}
	
	protected void unblockWrite() {
		System.out.println("SocketClient.unblockWrite()");
		socketOutputStream.notifyWrite();
	}
	
	protected int  doWrite() throws IOException{
		System.out.println("SocketClient.doWrite()");
		if(sslHandler!=null) {
			return sslHandler.doWrite(socketOutputStream.getByteBuffer());
		}else {
			//Write the non SSL bit of the transfer
			ByteBuffer buff = socketOutputStream.getByteBuffer();
			int out = buff.remaining();
			while(buff.hasRemaining()) {
				int x = client.write(buff);
				if(x < 0)
					return x;
			}
			return out;
		}
	}
	
	
	public boolean isConnected() {
		return (initConnDone && client!=null && client.isConnected()) ;
	}
	
	public OutputStream getOutputStream(){
		return socketOutputStream;
	}
	
	public InputStream getInputStream() {
		return socketInputStream;
	}
	
	public void close() throws IOException{
		if(!isClosed) {
			isClosed = true;
			if(sslHandler != null ) {
				sslHandler.stop();
			}
			client.close();
			socketInputStream.close();
			socketOutputStream.close();
			initConnDone = false;
			if(selector!=null) {
				selector.wakeup();
			}
		}
	}
	
	
	protected void triggerWrite() throws IOException {
		if (client != null && client.isOpen()) {
			System.out.println("SocketClient.triggerWrite()");
			try {
				client.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ, this);
				selector.wakeup();
			} catch (ClosedChannelException e) {
				throw new IOException ("Connection Closed ");
			}
		}
	}
	
	public InetSocketAddress getRemoteAddress() {
		if(client!=null)
			return (InetSocketAddress)(client.socket().getRemoteSocketAddress());
		return null;
	}
	
	public InetSocketAddress getLocalAddress() {
		if(client!=null)
			return (InetSocketAddress)(client.socket().getLocalSocketAddress());
		return null;
	}


	protected int readToBuffer(ByteBuffer buffer) throws IOException{
		int out = 0;
		if(sslHandler!=null) {
			out = sslHandler.doRead(buffer);
		}else {
			out = client.read(buffer);
		}
		if(out < 0) {
			close();
		}else {
			client.register(selector, SelectionKey.OP_READ,this);
		}
		return out;
	}

}
