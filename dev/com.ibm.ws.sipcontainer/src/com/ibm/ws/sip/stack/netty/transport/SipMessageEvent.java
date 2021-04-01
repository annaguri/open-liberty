package com.ibm.ws.sip.stack.netty.transport;

import java.net.InetSocketAddress;

import com.ibm.ws.sip.stack.transaction.transport.connections.SipMessageByteBuffer;

public class SipMessageEvent {
	private final SipMessageByteBuffer sipMsg;
	private final InetSocketAddress remoteAddr;

	public SipMessageEvent(final SipMessageByteBuffer data, final InetSocketAddress addr) {
		this.sipMsg = data;
		this.remoteAddr = addr;
	}

	public InetSocketAddress getRemoteAddress() {
		return remoteAddr;
	}

	public SipMessageByteBuffer getSipMsg() {
		return sipMsg;
	}
}
