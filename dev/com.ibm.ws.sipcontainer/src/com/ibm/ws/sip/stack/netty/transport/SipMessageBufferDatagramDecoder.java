package com.ibm.ws.sip.stack.netty.transport;

import java.util.List;

import com.ibm.ws.sip.stack.transaction.transport.connections.SipMessageByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

public final class SipMessageBufferDatagramDecoder extends MessageToMessageDecoder<DatagramPacket> {

	@Override
	protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
		final ByteBuf content = packet.content();

		if (content.readableBytes() < 20) {
			return;
		}

		//System.out.println("SipMessageBufferDatagramDecoder.decode: length [" + content.readableBytes() + "].");

		final byte[] b = new byte[content.readableBytes()];
		content.getBytes(0, b);

		SipMessageByteBuffer data = SipMessageByteBuffer.fromPool();
		data.put(b, 0, b.length);

		// with UDP we don't get the remote address in
		// SimpleChannelInboundHandler.channelActive()
		// so this is our chance to get it
		SipMessageEvent sipEvent = new SipMessageEvent(data, packet.sender());
		out.add(sipEvent);
	}

}