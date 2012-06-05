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
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

public class SSLHandler {

	private SSLEngine sslEngine ; 
	private SocketChannel channel; 
	private SelectionKey selectionKey ; 
	
	private ByteBuffer	netSendBuffer = null;

	private ByteBuffer	netRecvBuffer = null;
	
	public SSLHandler(SSLEngine engine , SocketChannel channle, SelectionKey selector) throws SSLException {
		this.sslEngine = engine; 
		this.channel = channle;
		this.selectionKey = selector;
		this.netSendBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		this.netRecvBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		engine.beginHandshake();
	}
	
	//Need to write the handler stop method;
	public void stop() throws IOException{
		sslEngine.closeInbound();
		sslEngine.closeOutbound();
	}


	protected void doWrite(ByteBuffer buff) throws IOException{
		while(buff.remaining() > 0) {
			wrapAndWrite(buff);
			doHandshake();
		}
	}
	
	protected int doRead(ByteBuffer buff) throws IOException{
	    	
		assert buff.position() == 0;
        while (buff.position() == 0) {
        	readAndUnwarp(buff);
        	doHandshake();
        }
        int out_size = buff.position();
        return out_size;
	}
	
	private void wrapAndWrite(ByteBuffer buff) throws IOException{
		 
         Status status;
        
         netSendBuffer.clear();
             do {
            	 status = sslEngine.wrap (buff, netSendBuffer).getStatus();
                 if (status == Status.BUFFER_OVERFLOW) {
                    // There data in the net buffer therefore need to send out the data 
                	 flush();
                 }
             } while (status == Status.BUFFER_OVERFLOW);
             if (status == Status.CLOSED ) {
            	 throw new IOException("SSLEngine Closed");
                 
             }
             flush();
         
	}
	
	private int flush() throws IOException{
		//System.out.println(netSendBuffer.position());
		netSendBuffer.flip();
		int	count = 0; 
		while (netSendBuffer.hasRemaining()) 
			count += channel.write(netSendBuffer);
		netSendBuffer.compact();
		//System.out.println(count);
		return count;
	}
	
	private int readAndUnwarp(ByteBuffer buff) throws IOException {
		Status status = Status.OK;
        if (!channel.isOpen()) {
        	return -1;
            //throw new IOException ("Engine is closed");
        }
        boolean needRead;
        if (netRecvBuffer.hasRemaining()) {
        	netRecvBuffer.compact();
        	netRecvBuffer.flip();
        	needRead = false;
        } else {
        	netRecvBuffer.clear();
        	needRead = true;
        }

            int x,y;
            do {
                if (needRead) {

                    x = channel.read(netRecvBuffer);
                    if (x == -1) {
                    	return x;
                        //throw new IOException ("connection closed for reading");
                    }
                    netRecvBuffer.flip();
                }
                status = sslEngine.unwrap (netRecvBuffer, buff).getStatus();
                //status = r.result.getStatus();
                if (status == Status.BUFFER_UNDERFLOW) {
                	// Not enoght data read from the channel need to read more from the socket
                    needRead = true;
                } else if (status == Status.BUFFER_OVERFLOW) {
                   // Buffer over flow, the caller does not have enough buffer space in the buff 
                   // re do the call after freeing the current data in the buffer
                
                	break;
                } else if (status == Status.CLOSED) {
                	buff.flip();
                	return -1;
                    //throw new IOException("SSLEngine Closed");
                    
                }
            } while (status != Status.OK);
            
        return buff.position();
    }
	
	void doHandshake () throws IOException {
       
		
            ByteBuffer tmpBuff = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
            HandshakeStatus hs_status = sslEngine.getHandshakeStatus();
            while (hs_status != HandshakeStatus.FINISHED &&
                   hs_status != HandshakeStatus.NOT_HANDSHAKING)
            {
                switch (hs_status) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = sslEngine.getDelegatedTask()) != null) {
                            /* run in current thread, because we are already
                             * There is high chance that we are running in a different thread;
                             */
                            task.run();
                        }
                        /* fall thru - call wrap again */
                    case NEED_WRAP:
                        tmpBuff.clear();
                        tmpBuff.flip();
                        wrapAndWrite(tmpBuff);
                        break;

                    case NEED_UNWRAP:
                        tmpBuff.clear();
                        readAndUnwarp(tmpBuff);
                        assert tmpBuff.position() == 0;
                        break;
                }
                hs_status = sslEngine.getHandshakeStatus();
            }
    }
}
