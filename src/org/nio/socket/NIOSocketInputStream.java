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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Vector;

public class NIOSocketInputStream extends InputStream {

	private ByteBuffer streamBuffer; 
	private ByteBuffer channelBuffer; 
	
	private SocketClient readClient;
	private boolean stremClosed = false;
	private Boolean isReadWait = false;
	
	public NIOSocketInputStream () {
		streamBuffer = ByteBuffer.allocate(1024);
		streamBuffer.flip();
		channelBuffer= ByteBuffer.allocate(1024);
	}
	
	@Override
	public int read() throws IOException {
		
		byte b[] = new byte [1];
		read(b,0,b.length);
		return  (0xFF & b[0]);
		
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read_size = 0;
		int read_length = 0;
		read_length = len;
		while (true) {
			
			if(stremClosed) {
				throw new IOException("Read stream closed");
			}
			
			if(streamBuffer.remaining() > 0) {
				int toread = Math.min(read_length, streamBuffer.remaining());
				streamBuffer.get(b, off, toread);
				read_size +=toread;
				if(read_size == len) {
					break;
				}
			}else {	
				int available = available();
				if(available > 0) {
					continue;
				}else if (available == 0 ) {
					synchronized (isReadWait) {
						isReadWait = true;
					}
						// Block the caller
					try {
						if(read_size == 0 ) {
							synchronized (this) {
								while(isReadWait()) {
									wait();
								}
							}
						}
						else {
							break;
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
					
		}
		
		return read_size;
	}
	
	@Override
	public synchronized int available() throws IOException {
		while (true) {
			
			if(stremClosed) {
				throw new IOException("Read stream closed");
			}
			
			int available = streamBuffer.remaining();
			if(available == 0) {
				streamBuffer.rewind();
				// Read it from the stream add it to the stream buffer
				channelBuffer.rewind();
				readClient.readToBuffer(channelBuffer);
				channelBuffer.flip();
				streamBuffer.put(channelBuffer);
			    streamBuffer.flip();
			    available = streamBuffer.remaining();
			}
			
			return available;
		}
	}
	
	@Override
	public void close() throws IOException {
		stremClosed = true;
		synchronized (this) {
			notifyRead();
		}
		
	}
	
	protected boolean isReadWait(){
		return isReadWait;
	}
	
	protected void notifyRead() {
		synchronized (isReadWait) {
			isReadWait = true;
		}
		notifyAll();
	}
	
}
