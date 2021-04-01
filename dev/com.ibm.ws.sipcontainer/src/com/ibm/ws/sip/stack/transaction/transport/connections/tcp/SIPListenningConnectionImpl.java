/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.sip.stack.transaction.transport.connections.tcp;

import java.io.IOException;
import java.net.*;

import com.ibm.sip.util.log.Log;
import com.ibm.sip.util.log.LogMgr;
import com.ibm.ws.jain.protocol.ip.sip.ListeningPointImpl;
import com.ibm.ws.sip.parser.util.InetAddressCache;
import com.ibm.ws.sip.stack.dispatch.Dispatcher;
import com.ibm.ws.sip.stack.transaction.transport.Hop;
import com.ibm.ws.sip.stack.transaction.transport.connections.SIPConnection;
import com.ibm.ws.sip.stack.transaction.transport.connections.SIPListenningConnection;

import jain.protocol.ip.sip.ListeningPoint;

public class SIPListenningConnectionImpl implements SIPListenningConnection {

	/**
	 * Class Logger.
	 */
	private static final LogMgr c_logger = Log.get(SIPListenningConnectionImpl.class);

	/** the listening socket */
	private ServerSocket m_sock;

	/** the local listening point to listen on */
	private ListeningPointImpl m_lp;

	SIPListenningConnectionImpl(ListeningPointImpl lp) {
		m_lp = lp;
	}

	public SIPConnection createConnection(InetAddress remoteAdress, int remotePort) throws IOException {
		return new SIPConnectionImpl(this, remoteAdress, remotePort);
	}

	public synchronized void listen() throws IOException {
		try {
			InetAddress address = InetAddressCache.getByName(m_lp.getHost());
			m_sock = new ServerSocket(m_lp.getPort(), 0, address);

			// if the port was 0 , we will get a listening port by default.
			// then we should set back the port to the listening Point Object.
			// if the port was not 0 , it will just set the same port
			m_lp.setPort(m_sock.getLocalPort());
		} catch (IOException ex) {
			if (c_logger.isTraceDebugEnabled()) {
				c_logger.traceDebug(this, "listen", ex.getMessage());
			}
			throw ex;
		}
	}

	public synchronized void stopListen() {
		close();
	}

	public synchronized void close() {
		try {
			// ANNA
			// m_sock.close();
			notifyClosed();
		} catch (Exception e) {
			if (c_logger.isTraceDebugEnabled()) {
				c_logger.traceDebug(this, "close", e.getMessage());
			}
		}
	}

	public void notifyConnectionCreated(InetSocketAddress remoteAddress) {
		InetAddress address = remoteAddress.getAddress();
		int port = remoteAddress.getPort();
		Hop key = new Hop("TCP", InetAddressCache.getHostAddress(address), port);
		SIPConnectionImpl connection = new SIPConnectionImpl(this, address, port);
		connection.setKey(key);
		notifyConnectionCreated(connection);

	}

	private void notifyConnectionCreated(SIPConnection connection) {
		Dispatcher.instance().queueConnectionAcceptedEvent(this, connection);
	}

	protected void notifyClosed() {
		// todo
	}

	public ListeningPoint getListeningPoint() {
		return m_lp;
	}

	public String toString() {
		return m_lp.toString();
	}
}
