package com.ibm.ws.sip.stack.netty.transport;

import java.util.List;

import com.ibm.ws.sip.stack.transaction.transport.connections.SipMessageByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class SipMessageBufferStreamDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		// System.out.println("decode. " + in.toString(Charset.forName("utf-8")));
		SipMessageByteBuffer data = SipMessageByteBuffer.fromPool();

		while (in.isReadable()) {
			final byte b = in.readByte();
			data.put(b);
		}

		out.add(data);
		// System.out.println("decode. length [" + data.getMarkedBytesNumber() + "].");
	}
}
