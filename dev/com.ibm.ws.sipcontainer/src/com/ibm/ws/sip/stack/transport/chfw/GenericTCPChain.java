/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.sip.stack.transport.chfw;

import java.util.HashMap;
import java.util.Map;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.sip.stack.netty.transport.LibertyNettyBundle;

import jain.protocol.ip.sip.ListeningPoint;

/**
 * Encapsulation of steps for starting/stopping an http chain in a
 * controlled/predictable manner with a minimum of synchronization.
 */
public class GenericTCPChain extends GenericChain {
	private static final TraceComponent tc = Tr.register(GenericTCPChain.class);

	private final boolean isTLS;

	private String tcpName;
	private String tlsName;

	/**
	 * Create the new chain with it's parent endpoint
	 * 
	 * @param sipEndpointImpl the owning endpoint: used for notifications
	 * @param isTls           true if this is to be an TLS chain.
	 */
	public GenericTCPChain(GenericEndpointImpl owner, boolean isTls) {
		super(owner);
		this.isTLS = isTls;
	}

	/**
	 * Initialize this chain manager: Channel and chain names shouldn't fluctuate as
	 * config changes, so come up with names associated with this set of
	 * channels/chains that will be reused regardless of
	 * start/stop/enable/disable/modify
	 * 
	 * @param endpointId  The id of the sipEndpoint
	 * @param componentId The DS component id
	 * @param cfw         Channel framework
	 */
	public void init(String endpointId, Object componentId, LibertyNettyBundle nettyBundle, String name) {
		final String root = "TCP" + (isTLS ? "-ssl" : "");

		String commmonName = root + "_" + name + "_" + endpointId;

		tcpName = commmonName;
		tlsName = "TLS-" + commmonName;

		super.init(endpointId, componentId, nettyBundle, name);

	}

	/**
	 * @see com.ibm.ws.sip.stack.transport.chfw.GenericChain#createActiveConfiguration()
	 */
	protected ActiveConfiguration createActiveConfiguration() {
		Map<String, Object> tcpOptions = getOwner().getTcpOptions();
		Map<String, Object> sslOptions = (isTLS) ? getOwner().getSslOptions() : null;
		Map<String, Object> endpointOptions = getOwner().getEndpointOptions();
		return new ActiveConfiguration(isTLS, tcpOptions, sslOptions, endpointOptions, this);
	}

	/**
	 * @see com.ibm.ws.sip.stack.transport.chfw.GenericChain#createChannels(com.ibm.ws.sip.stack.transport.chfw.ActiveConfiguration)
	 */
	protected void createChannels(ActiveConfiguration newConfig) {

		Map<Object, Object> chanProps;
		Map<String, Object> tcpOptions = getOwner().getTcpOptions();

		// Endpoint
		// define is a simple replace of the old value known to the endpointMgr

		chanProps = new HashMap<Object, Object>(tcpOptions);
		chanProps.put("endPointName", getEndpointName());
		chanProps.put("hostname", newConfig.configHost);
		chanProps.put("port", String.valueOf(newConfig.configPort));

		// SIP Channel
		chanProps = new HashMap<Object, Object>();
		chanProps.put("endPointName", newConfig.endpointOptions.get(ID));
		if (isTLS) {
			chanProps.put("channelChainProtocolType", "tls");
		} else {
			chanProps.put("channelChainProtocolType", "tcp");
		}

		final String[] chanList;
		if (isTLS) {
			chanList = new String[] { getName(), tlsName, sipChannelName };
		} else {
			chanList = new String[] { getName(), sipChannelName };
		}
		addChain(chanList, newConfig);
	}

	/**
	 * This method is used when configuration of the Endpoint was changed and
	 * channels should be rebuilded.
	 */
	protected void rebuildTheChannel(ActiveConfiguration oldConfig, ActiveConfiguration newConfig) {

		// We've been through channel configuration before...
		// We have to destroy/rebuild the chains because the channels don't
		// really support dynamic updates. *sigh*
	}

	/**
	 * 
	 * @return
	 */
	public String getName() {
		return tcpName;
	}

	/**
	 * Publish an event relating to a chain starting/stopping with the given
	 * properties set about the chain.
	 */
	public void setupEventProps(Map<String, Object> eventProps) {

		eventProps.put(GenericServiceConstants.ENDPOINT_IS_TLS, isTLS);
	}

	@Override
	public Type getType() {
		return isTLS ? Type.tls : Type.tcp;
	}

	@Override
	public String getTransport() {
		return ListeningPoint.TRANSPORT_TCP;
	}

}
