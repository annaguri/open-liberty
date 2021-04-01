package com.ibm.ws.sip.stack.netty.transport;

import com.ibm.ws.sip.stack.transaction.transport.connections.SipMessageByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class SipMessageBufferToByteBufEncoder extends MessageToByteEncoder<SipMessageByteBuffer> {
	@Override
	public void encode(ChannelHandlerContext ctx, SipMessageByteBuffer msg, ByteBuf out) throws Exception {
		out.writeBytes(msg.getBytes(), 0, msg.getMarkedBytesNumber());
		msg.reset();
	}
}