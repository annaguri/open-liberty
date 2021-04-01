/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.sip.stack.netty.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.*;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;
import com.ibm.ws.kernel.feature.ServerStarted;
import com.ibm.ws.sip.stack.transaction.transport.connections.SipMessageByteBuffer;
import com.ibm.ws.sip.stack.transaction.util.SIPStackUtil;
import com.ibm.ws.sip.stack.transport.chfw.GenericChain;
import com.ibm.ws.sip.stack.transport.sip.*;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import jain.protocol.ip.sip.ListeningPoint;

@Component(configurationPid = "io.openliberty.netty.channel", immediate = true, service = LibertyNettyBundle.class, property = {
		"service.vendor=IBM" })
public class LibertyNettyBundle {

	private static final TraceComponent tc = Tr.register(LibertyNettyBundle.class);

	/**
	 * The TCP based bootstrap.
	 */
	private ServerBootstrap serverBootstrap;
	/**
	 * UDP based bootstrap.
	 */
	private Bootstrap bootstrap;

	private EventLoopGroup parentGroup;
	private EventLoopGroup workerGroup;
	private EventLoopGroup udpGroup;

	@Reference(name = "cfwBundle")
	//private CHFWBundle cfwBundle;
	
	private ExecutorService executorService;
	
	private SipInboundChannelFactoryWs sipInboundChannelFactory = new SipInboundChannelFactoryWs();

	private static Queue<Callable<?>> serverStartedTasks = new LinkedBlockingQueue<>();
	private static AtomicBoolean serverCompletelyStarted = new AtomicBoolean(false);
	private static Object syncStarted = new Object() {
	}; // use brackets/inner class to make lock appear

	@Activate
	protected void activate(Map<String, Object> properties) {
		// use the executor service provided by Liberty for spawning threads
		parentGroup = new NioEventLoopGroup(0, executorService);
		workerGroup = new NioEventLoopGroup(0, executorService);
		udpGroup = new NioEventLoopGroup(0, executorService);

		serverBootstrap = createTCPBoostrap();
		bootstrap = createUDPBootstrap();
	}

	private Bootstrap createUDPBootstrap() {
		final Bootstrap b = new Bootstrap();
		b.group(udpGroup).channel(NioDatagramChannel.class).handler(new ChannelInitializer<DatagramChannel>() {
			@Override
			protected void initChannel(final DatagramChannel ch) throws Exception {
				final ChannelPipeline pipeline = ch.pipeline();
				pipeline.addLast("decoder", new SipMessageBufferDatagramDecoder());
				pipeline.addLast("handler", new SipDatagramHandler());
			}
		});
		return b;
	}

	private ServerBootstrap createTCPBoostrap() {
		final ServerBootstrap b = new ServerBootstrap();

		b.group(this.parentGroup, this.workerGroup).channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(final SocketChannel ch) throws Exception {
						final ChannelPipeline pipeline = ch.pipeline();

						pipeline.addLast("decoder", new SipMessageBufferStreamDecoder());
						pipeline.addLast("handler", new SipStreamHandler());
					}
				}).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).childOption(ChannelOption.SO_KEEPALIVE, true);
		return b;
	}

	public void addChain(GenericChain chain) {
		if (tc.isDebugEnabled()) {
			Tr.debug(tc, "addChain. " + chain.getChainName());
		}
		sipInboundChannelFactory.initChannel(chain);
		/*
		 * try { runWhenServerStarted(new Callable<Void>() {
		 * 
		 * @Override public Void call() { try { finishInitServerSocket(chainName); }
		 * catch (Exception x) { if (TraceComponent.isAnyTracingEnabled() &&
		 * tc.isDebugEnabled()) { Tr.debug(tc,
		 * "caught exception from finishInitServerSocket(): " + x); } } return null; }
		 * }); } catch (Exception x) { if (TraceComponent.isAnyTracingEnabled() &&
		 * tc.isDebugEnabled()) { Tr.debug(tc, "caught exception:: " + x); } }
		 */
	}

	/**
	 * DS method for setting the required dynamic executor service reference.
	 * 
	 * @param bundle
	 */
	@Reference(policy = ReferencePolicy.STATIC, cardinality = ReferenceCardinality.MANDATORY)
	protected void setExecutorService(ExecutorService service) {
		this.executorService = service;
		if (tc.isDebugEnabled()) {
			Tr.debug(tc, "setExecutorService", executorService);
		}
	}
	
	/**
	 * Declarative services method that is invoked once the server is started. Only
	 * after this method is invoked is the initial polling for persistent tasks
	 * performed.
	 *
	 * @param ref reference to the ServerStarted service
	 */
	@Reference(service = ServerStarted.class, policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
	protected void setServerStarted(ServiceReference<ServerStarted> ref) {
		// This is a signal that
		// the server is fully started, but before the "smarter planet" message has been
		// output. Use this signal to run tasks, mostly likely tasks that will
		// finish the port listening logic, that need to run at the end of server
		// startup

		if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
			Tr.debug(tc, "Server Completely Started signal received. "
					+ SIPConnectionFactoryImplWs.instance().getInboundChannels().size());
		}

		for (ListeningPoint lp : SIPConnectionFactoryImplWs.instance().getInboundChannels().keySet()) {
			executorService.submit(new Callable<Void>() {
				@Override
				public Void call() {
					try {
						finishInitServerSocket(lp);
					} catch (Exception x) {
						if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
							Tr.debug(tc, "setServerStarted", "caught exception from finishInitServerSocket(): " + x);
						}
					}
					return null;
				}
			});
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(tc, "setServerStarted", lp);
			}
		}

		/*
		  Callable<?> task; 
		  while ((task = serverStartedTasks.poll()) != null) { 
		  	try {
		  		cfwBundle.getExecutorService().submit(task); 
		  		// task.call(); 
		  	} catch (Exception e) { 
		  		if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())  { 
		  			Tr.debug(tc, "caught exception performing late cycle server startup task: " + e); 
		  	} 
		  } }
		 */

		synchronized (syncStarted) {
			serverCompletelyStarted.set(true);
			syncStarted.notifyAll();
		}
	}

	public synchronized void finishInitServerSocket(ListeningPoint lp) throws IOException {
		if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
			Tr.debug(tc, "finishInitServerSocket", lp);
		}
		try {
			if (lp.getTransport().equalsIgnoreCase(ListeningPoint.TRANSPORT_TCP)) {
				ChannelFuture future = serverBootstrap.bind(new InetSocketAddress(lp.getPort())).sync();
				future.channel().closeFuture().sync();
			} else if (lp.getTransport().equalsIgnoreCase(ListeningPoint.TRANSPORT_UDP)) {
				bootstrap.bind(lp.getPort()).sync().channel();
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(tc, "finishInitServerSocket",
						"caught exception performing late cycle server startup task: " + e);
			}
		}
	}

	/**
	 * Method is called to run a task if the server has already started, if the
	 * server has not started that task is queued to be run when the server start
	 * signal has been received.
	 *
	 * @param callable - task to run
	 * @return <code>null</code> if the task was not ran, but queued, else return
	 *         the task to denote it has ran.
	 * @throws Exception
	 */
	public static <T> T runWhenServerStarted(Callable<T> callable) throws Exception {
		synchronized (syncStarted) {
			if (!serverCompletelyStarted.get()) {
				serverStartedTasks.add(callable);
				return null;
			}
		}
		return callable.call();
	}

	/**
	 * non-blocking method to return the state of server startup with respect to the
	 * server being completely started. The server will be "completely" stated when
	 * the server start signal has been received and any task waiting on that signal
	 * before running have now been run.
	 */
	@FFDCIgnore({ InterruptedException.class })
	public static boolean isServerCompletelyStarted() {
		return serverCompletelyStarted.get();
	}

	/**
	 * Declarative Services method for unsetting the ServerStarted service
	 *
	 * @param ref reference to the service
	 */
	protected synchronized void unsetServerStarted(ServiceReference<ServerStarted> ref) {
		// server is shutting down
	}

	/**
	 * If the server has not completely started, then wait until it has been. The
	 * server will be "completely" stated when the server start signal has been
	 * received and any task waiting on that signal before running have now been
	 * run.
	 */
	@FFDCIgnore({ InterruptedException.class })
	public static void waitServerCompletelyStarted() {
		synchronized (syncStarted) {
			if (serverCompletelyStarted.get() == false) {
				try {
					syncStarted.wait();
				} catch (InterruptedException x) {
					// assume we can go one then
				}
			}
		}
		return;
	}

	@Deactivate
	protected void deactivate(Map<String, Object> properties, int reason) {
		shutdown();
	}

	@Modified
	protected void modified(Map<String, Object> config) {
	}

	public void shutdown() {
		parentGroup.shutdownGracefully();
		workerGroup.shutdownGracefully();
		udpGroup.shutdownGracefully();
	}

	/**
	 * 
	 * @param addr
	 * @param transport
	 * @return the listening point matching the given address and transport
	 */
	private ListeningPoint getListeningPoint(SocketAddress addr, String transport) {
		ListeningPoint lp = null, defaultLp = null;

		if (addr == null || !(addr instanceof InetSocketAddress)) {
			return null;
		}

		InetSocketAddress isa = (InetSocketAddress) addr;

		for (ListeningPoint lPoint : SIPConnectionFactoryImplWs.instance().getInboundChannels().keySet()) {
			if (lPoint.getPort() != isa.getPort()) {
				continue;
			}
			if (!lPoint.getTransport().equalsIgnoreCase(transport)) {
				continue;
			}
			String addHost = SIPStackUtil.getHostAddress(isa.getHostName());
			String lpHost = SIPStackUtil.getHostAddress(lPoint.getHost());

			if (SIPStackUtil.isSameHost(addHost, lpHost)) {
				lp = lPoint;
				break;
			} else {
				defaultLp = lPoint;
			}
		}

		if (lp == null) {
			lp = defaultLp;
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "getListeningPoint", "a listening point was not found for: " + addr + ","
						+ transport + ". returning the default value: " + lp);
			}
		}
		return lp;
	}

	private class SipStreamHandler extends SimpleChannelInboundHandler<SipMessageByteBuffer> {

		final private AttributeKey<SipTcpInboundConnLink> attrKey = AttributeKey.valueOf("SipTcpInboundConnLink");

		/**
		 * Called when a new connection is established
		 */
		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "channelActive", ctx.channel().remoteAddress() + " connected");
			}

			ListeningPoint lp = getListeningPoint(ctx.channel().localAddress(), ListeningPoint.TRANSPORT_TCP);
			SipInboundChannel inboundChannel = null;

			if (lp != null) {
				inboundChannel = SIPConnectionFactoryImplWs.instance().getInboundChannels().get(lp);
			}

			if (inboundChannel != null) {
				Attribute<SipTcpInboundConnLink> attr = ctx.channel().attr(attrKey);
				attr.setIfAbsent(new SipTcpInboundConnLink(inboundChannel, ctx.channel()));
			} else {
				if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
					Tr.debug(this, tc, "channelActive",
							"could not associate an incoming connection with a SIP channel");
				}
			}
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, SipMessageByteBuffer msg) throws Exception {
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "channelRead0",
						ctx.channel() + ". [" + msg.getMarkedBytesNumber() + "] bytes received");
			}
			Attribute<SipTcpInboundConnLink> attr = ctx.channel().attr(attrKey);
			SipTcpInboundConnLink connLink = attr.get();

			if (connLink != null) {
				connLink.complete(msg);
			} else {
				if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
					Tr.debug(this, tc, "channelRead0", "could not associate an incoming messafe with a SIP channel");
				}
			}

		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "channelInactive", ctx.channel().remoteAddress() + " has been disonnected");
			}

			Attribute<SipTcpInboundConnLink> attr = ctx.channel().attr(attrKey);
			SipTcpInboundConnLink connLink = attr.get();
			// clean up from connections table
			if (connLink != null) {
				connLink.destroy(null);
			} else {
				if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
					Tr.debug(this, tc, "channelInactive", "could not find a SIP channel");
				}
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "exceptionCaught. " + cause.getClass());
			}
			Attribute<SipTcpInboundConnLink> attr = ctx.channel().attr(attrKey);
			if (cause instanceof Exception) {
				SipTcpInboundConnLink connLink = attr.get();
				if (connLink != null) {
					connLink.destroy((Exception) cause);
				} else {
					if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
						Tr.debug(this, tc, "exceptionCaught", "could not find a SIP channel");
					}
				}
			}
			ctx.close();
		}
	}

	private class SipDatagramHandler extends SimpleChannelInboundHandler<SipMessageEvent> {

		final private AttributeKey<SipUdpConnLink> attrKey = AttributeKey.valueOf("SipUdpConnLink");

		/**
		 * Called when a new connection is established
		 */
		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "channelActive. " + ctx.channel());
			}

			SipUdpInboundChannel inboundChannel = null;
			ListeningPoint lp = getListeningPoint(ctx.channel().localAddress(), ListeningPoint.TRANSPORT_UDP);

			if (lp != null) {
				inboundChannel = (SipUdpInboundChannel) SIPConnectionFactoryImplWs.instance().getInboundChannels()
						.get(lp);
			}

			if (inboundChannel != null) {
				inboundChannel.setChannel(ctx.channel());
				Attribute<SipUdpConnLink> attr = ctx.channel().attr(attrKey);
				attr.setIfAbsent((SipUdpConnLink) inboundChannel.getConnectionLink());
			} else {
				if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
					Tr.debug(this, tc, "channelActive",
							"could not associate an incoming connection with a SIP channel");
				}
			}
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "channelInactive", ctx.channel().remoteAddress() + " has been disonnected");
			}

			Attribute<SipUdpConnLink> attr = ctx.channel().attr(attrKey);
			SipUdpConnLink connLink = attr.get();
			// clean up from connections table
			if (connLink != null) {
				connLink.close(null);
			} else {
				if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
					Tr.debug(this, tc, "channelInactive", "could not find a SIP channel");
				}
			}
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, SipMessageEvent msg) throws Exception {
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "channelRead0. " + ctx.channel() + ". [" + msg.getSipMsg().getMarkedBytesNumber()
						+ "] bytes received");
			}
			Attribute<SipUdpConnLink> attr = ctx.channel().attr(attrKey);
			SipUdpConnLink connLink = attr.get();
			if (connLink != null) {
				connLink.complete(msg.getSipMsg(), msg.getRemoteAddress());
			} else {
				if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
					Tr.debug(this, tc, "channelRead0", "could not find a SIP channel");
				}
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
				Tr.debug(this, tc, "exceptionCaught. " + cause.getClass());
			}
			Attribute<SipUdpConnLink> attr = ctx.channel().attr(attrKey);
			if (cause instanceof Exception) {
				SipUdpConnLink connLink = attr.get();
				if (connLink != null) {
					connLink.close(cause);
				} else {
					if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
						Tr.debug(this, tc, "exceptionCaught", "could not find a SIP channel");
					}
				}
			}
			ctx.close();
		}
	}
}
