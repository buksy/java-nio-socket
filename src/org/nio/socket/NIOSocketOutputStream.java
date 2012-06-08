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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NIOSocketOutputStream extends OutputStream{

	private ByteArrayOutputStream holder = null;
	private SocketClient client = null; 
	private boolean stremClosed = false;
	private Boolean isWriteWait = new Boolean(false);
	
	private Lock writeLock = new ReentrantLock();
	private Condition cond = writeLock.newCondition();
	
	private static final int MAX_BUFF_SIZE = 1024;
	
	NIOSocketOutputStream(SocketClient cli) {
		holder = new ByteArrayOutputStream();
		client = cli;
	}
	
	@Override
	public void write(int b) throws IOException {
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		synchronized (holder) {
			holder.write(b);
			checkAndFlush();
		}
		
	}

	@Override
	public void write(byte[] arg0) throws IOException {
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		synchronized (holder) {
			holder.write(arg0);
			checkAndFlush();
		}
		
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		synchronized (holder) {
			holder.write(b, off, len);
			checkAndFlush();
		}
		
	}
	
	@Override
	public void flush() throws IOException {		
		
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		try{
			writeLock.lock();
			synchronized (isWriteWait) {
				isWriteWait = true;
			}
			client.triggerWrite();
			while(isWriteWait) {
				cond.await();
			}
			
//			synchronized (this) {
//				try {
//					while(isWriteWait) {
//						wait();
//						if(stremClosed) {
//							throw new IOException("Write stream closed");
//						}
//					}
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
			client.doWrite();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			writeLock.unlock();
		}
		
	}
	
	protected void notifyWrite() {
		try {
			writeLock.lock();
			synchronized (isWriteWait) {
				isWriteWait = false;
			}
			cond.signal();
		}finally{
			writeLock.unlock();
		}
		
	}
	
	
	protected ByteBuffer getByteBuffer() {
		ByteBuffer buff = null;
		synchronized (holder) {
			buff = ByteBuffer.wrap(holder.toByteArray());
			holder.reset();
		}
		return buff;
	}
	
	@Override
	public void close() throws IOException {
		if(!stremClosed) {
			stremClosed = true;
		}
		
	}
	
	private void checkAndFlush() throws IOException{
		if (holder.size() > MAX_BUFF_SIZE)
			flush();
	}

	protected boolean isWriteWait() {
		return isWriteWait;
	}
}
