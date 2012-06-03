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


public class SocketClient {
	
	
	//private Selector selector = null;
	private SocketChannel client = null;
	private SelectionKey selctionKey = null;
	private InetSocketAddress address = null;

	private SSLContext sslContext; 
	private SSLEngine sslEngine = null;
	private SSLHandler sslHandler = null;
	
	private boolean initConnDone = false;
	
	private NIOSocketInputStream socketInputStream;
	private NIOSocketOutputStream socketOutputStream = null; 
	
	public SocketClient(InetSocketAddress address) throws IOException {	
		this.address = address;
		socketInputStream = new NIOSocketInputStream();
		socketOutputStream = new NIOSocketOutputStream(this);
	}
	
	
	protected SocketClient(SocketChannel client) {
		this.client = client;
		initConnDone = true;
	}
	
	public void setSSLContext( SSLContext context) {
		this.sslContext = context;
	}
	
	public void connect() throws IOException {
		
		if(initConnDone)
			throw new IOException("Socket Already connected");
		
		client = SocketChannel.open();
		client.configureBlocking( false );
		client.connect( address );
		
		new Thread() {
			
			public void run() {
				Selector selector = null;
				try {
					selector = Selector.open();
					client.register(selector, SelectionKey.OP_CONNECT);
					
					while (true) {
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
							if(key.isConnectable()) {
								if(client.finishConnect()) {
									if(sslContext!=null) { 
										// Do the SSL handshake stuff ;
										sslEngine = sslContext.createSSLEngine(client.socket().getInetAddress().getHostName(), client.socket().getPort());
										sslEngine.setUseClientMode(true);
										sslHandler = new SSLHandler(sslEngine, client,key);
									}
									client.register(selector,SelectionKey.OP_READ);
									initConnDone = true;
									selctionKey = key;
								}
							}
							if(key.isReadable()) {
								doRead();
								client.register(selector,SelectionKey.OP_READ);
							}
							if(key.isWritable()) {
								doWrite();
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

	protected void doRead() throws IOException {
		socketInputStream.notifyAll();
	}
	
	protected void doWrite() throws IOException{
		if(sslHandler!=null) {
			sslHandler.doWrite(socketOutputStream.getByteBuffer());
		}else {
			//Write the non SSL bit of the transfer
			ByteBuffer buff = socketOutputStream.getByteBuffer();
			while(buff.hasRemaining()) {
				client.write(buff);
			}
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
		if(sslHandler != null ) {
			sslHandler.stop();
		}
		client.close();
	}
	
	
	protected void sendNow() throws IOException {
		if (client != null && client.isOpen()) {
			try {
				client.register(selctionKey.selector(), SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				selctionKey.selector().wakeup();
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
		if(sslHandler!=null) {
			return sslHandler.doRead(buffer);
		}else {
			return client.read(buffer);
		}
		
	}

}