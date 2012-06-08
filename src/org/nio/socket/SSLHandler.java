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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

public class SSLHandler {

	private SSLEngine sslEngine ; 
	private SocketChannel channel; 
	//private SelectionKey selectionKey ; 
	
	private ByteBuffer	netSendBuffer = null;

	private ByteBuffer	netRecvBuffer = null;
	
	private int unwrap_remain = 0;
	
	private boolean handShakeDone = false;
	
	public SSLHandler(SSLEngine engine , SocketChannel channle) throws SSLException {
		this.sslEngine = engine; 
		this.channel = channle;
		//this.selectionKey = selector;
		this.netSendBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		this.netRecvBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		engine.beginHandshake();
	}
	
	//Need to write the handler stop method;
	public void stop() throws IOException{
		
//		sslEngine.closeInbound();
		sslEngine.closeOutbound();
	}


	protected int doWrite(ByteBuffer buff) throws IOException{
		doHandshake();
		int out = buff.remaining();
		while(buff.remaining() > 0) {
			if(wrapAndWrite(buff) < 0)
				return -1;
		}
		return out;
	}
	
	protected int doRead(ByteBuffer buff) throws IOException{
	    	
		assert buff.position() == 0;
       	if(readAndUnwarp(buff) >= 0)
       		doHandshake();
       	else
       		return -1;
        return buff.position();
        
	}
	
	private int wrapAndWrite(ByteBuffer buff) throws IOException{
		 
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
             return flush();
         
	}
	
	private int flush() throws IOException{
		//System.out.println(netSendBuffer.position());
		netSendBuffer.flip();
		int	count = 0; 
		while (netSendBuffer.hasRemaining()) {
			int x = channel.write(netSendBuffer);
			if(x >= 0) { 
				count +=x; 
			}
			else  {
				count = x ;
				break;
			}
		}
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
        if (unwrap_remain > 0) {
        	netRecvBuffer.compact();
        	netRecvBuffer.flip();
        	needRead = false;
        } else {
        	netRecvBuffer.clear();
        	needRead = true;
        }

            int x=0;
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
                if(x == 0 && netRecvBuffer.position() == 0) {
                	netRecvBuffer.clear();
            	}
                if(x == 0 && handShakeDone) {
                	return 0;
                }
                //status = r.result.getStatus();
                if (status == Status.BUFFER_UNDERFLOW) {
                	// Not enogh data read from the channel need to read more from the socket
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
            
            unwrap_remain = netRecvBuffer.remaining();
        return buff.position();
    }
	
	protected void doHandshake () throws IOException {
       
		    handShakeDone = false;
            ByteBuffer tmpBuff = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
            HandshakeStatus hs_status = sslEngine.getHandshakeStatus();
            while (hs_status != HandshakeStatus.FINISHED &&
                   hs_status != HandshakeStatus.NOT_HANDSHAKING)
            {
                switch (hs_status) {
                    case NEED_TASK:
                       
                        Executor exec = 
      					  Executors.newSingleThreadExecutor();
                        Runnable task;
	      				  
                        while ((task=sslEngine.getDelegatedTask()) != null)
	      				  {
	      				    exec.execute(task);
	      				  }
	      				 break;
                        /* fall thru - call wrap again */
                    case NEED_WRAP:
                        tmpBuff.clear();
                        tmpBuff.flip();
                        if(wrapAndWrite(tmpBuff) < 0)
                        	throw new IOException("SSLHandshake failed");
                        break;

                    case NEED_UNWRAP:
                        tmpBuff.clear();
                        if(readAndUnwarp(tmpBuff) < 0)
                        	throw new IOException("SSLHandshake failed");
                        assert tmpBuff.position() == 0;
                        break;
                }
                hs_status = sslEngine.getHandshakeStatus();
            }
            handShakeDone = true;
    }
}
