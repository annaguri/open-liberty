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
import java.util.concurrent.atomic.AtomicInteger;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import com.ibm.sip.util.log.Log;
import com.ibm.sip.util.log.LogMgr;
import com.ibm.websphere.ras.annotation.Trivial;
import com.ibm.ws.sip.stack.netty.transport.LibertyNettyBundle;
import com.ibm.wsspi.channelfw.ChainEventListener;
import com.ibm.wsspi.kernel.service.utils.FrameworkState;

/**
 * Encapsulation of steps for starting/stopping an SIP chain in a controlled/predictable
 * manner with a minimum of synchronization.
 */
abstract public class GenericChain {
    
	/**
     * Class Logger. 
     */
    private static final LogMgr c_logger = Log.get(GenericChain.class);

	/** "localhost" string  */
	protected static final String LOCALHOST = "localhost";
	
	/** "id" string  */
    protected static final String ID = "id";

    /**   SipChannel prefix name */
    private static String SIP_Channel = "SIPChannel_";
    
    /**   Chain prefix name */
    private static String CHAIN = "Chain";
    
	/** this member is increased for each new chain when it is created. */
    protected static int s_chains = 0;
    
	/** ENUM which holds the current state for ths particular chain */
    enum ChainState {
        UNINITIALIZED(0, "UNINITIALIZED"),
        DESTROYED(1, "DESTROYED"),
        INITIALIZED(2, "INITIALIZED"),
        STOPPED(3, "STOPPED"),
        QUIESCED(4, "QUIESCED"),
        STARTED(5, "STARTED");

        final int val;
        final String name;

        @Trivial
        /**
         * Ctor
         * @param val
         * @param name
         */
        ChainState(int val, String name) {
            this.val = val;
            this.name = "name";
        }

        @Trivial
        /**
         * Prints the state
         * @param state
         * @return
         */
        public static final String printState(int state) {
            switch (state) {
                case 0:
                    return "UNINITIALIZED";
                case 1:
                    return "DESTROYED";
                case 2:
                    return "INITIALIZED";
                case 3:
                    return "STOPPED";
                case 4:
                    return "QUIESCED";
                case 5:
                    return "STARTED";
            }
            return "UNKNOWN";
        }
    }
    
    public enum Type {tcp, tls, udp};

    
    /** Owner of this Chain - Endpoint Impl	 */
    protected final GenericEndpointImpl owner;

    /** Endpoint name	 */
	private String endpointName;
	
	/** SipChannel name	 */
    protected String sipChannelName;
    
    /** Chain name	 */
    private String chainName;
    
    /** Reference to Netty bundle*/
    private LibertyNettyBundle nettyBundle;


	/**
     * The state of the chain according to values from {@link ChainState}.
     * Aside from the initial value assignment, new values are only assigned from
     * within {@link ChainEventListener} methods.
     */
    private final AtomicInteger chainState = new AtomicInteger(ChainState.UNINITIALIZED.val);

    /**
     * Toggled by enable/disable methods. This serves only to block activity
     * of some operations (start/update on disabled chain should no-op).
     */
    private volatile boolean enabled = false;

    /**
     * A snapshot of the configuration (collection of properties objects) last used
     * for a start/update operation.
     */
    private volatile ActiveConfiguration currentConfig = null;

    /**
     * Create the new chain with it's parent endpoint
     * 
     * @param sipEndpointImpl the owning endpoint: used for notifications
     * @param isTls true if this is to be an TLS chain.
     */
    public GenericChain(GenericEndpointImpl owner) {
        this.owner = owner;
    }
    
    
    /**
     * Return current configuration
     * @return
     */
	protected ActiveConfiguration getCurrentConfig() {
		return currentConfig;
	}


	/**
	 * Sets the current configuration
	 * @param currentConfig
	 */
	protected void setCurrentConfig(ActiveConfiguration currentConfig) {
		this.currentConfig = currentConfig;
	}


	/**
	 * Returns the Endpoint Name 
	 * @return
	 */
	protected String getEndpointName() {
		return endpointName;
	}


	/**
     * Initialize this chain manager: Channel and chain names shouldn't fluctuate as config changes,
     * so come up with names associated with this set of channels/chains that will be reused regardless
     * of start/stop/enable/disable/modify
     * 
     * @param endpointId The id of the sipEndpoint
     * @param componentId The DS component id
     * @param cfw Channel framework
     */
   public void init(String endpointId, Object componentId, LibertyNettyBundle nettyBundle, String name) {

	   String chainNumber = String.valueOf(s_chains++);
	   
        this.nettyBundle = nettyBundle;

        endpointName = endpointId;
        
        sipChannelName = SIP_Channel + getName() + "_" + endpointId + "_" + chainNumber;
        
        chainName = CHAIN + endpointId + "_" + chainNumber;
    }
   
   /**
    * returns ChainName
    * @return
    */
   public String getChainName() {
		return chainName;
	}


    /**
     * Enable this chain: this happens automatically for the sip chain,
     * but is delayed on the ssl chain until ssl support becomes available.
     * This does not change the chain's state. The caller should
     * make subsequent calls to perform actions on the chain.
     */
    public void enable() {
        if (c_logger.isTraceDebugEnabled()) {
            c_logger.traceDebug("enable chain " + this);
        }
        enabled = true;
    }

    /**
     * Disable this chain. This does not change the chain's state. The caller should
     * make subsequent calls to perform actions on the chain.
     */
    public void disable() {
        if (c_logger.isTraceDebugEnabled()) {
            c_logger.traceDebug("disable chain " + this);
        }
        enabled = false;
    }

    
    /**
     * retuns if this chain is enabled
     * @return
     */
    public boolean isEnabled() {
		return enabled;
	}


	/**
     * Returns the Endpoint owner of this Chain
     * @return
     */
    protected GenericEndpointImpl getOwner() {
		return owner;
	}

    /**
     * 
     * @param e
     * @param cfg
     */
    private void handleStartupError(Exception e, ActiveConfiguration cfg) {
        if (c_logger.isTraceDebugEnabled()) {
            c_logger.traceDebug("Error starting chain " + chainName, this, e);
        }

        // Post an endpoint failed to start event to anyone listening
        String topic = owner.getEventTopic() + GenericServiceConstants.ENDPOINT_FAILED;
        postEvent(topic, cfg, e);

        // schedule a task to try again later.. 
    }

    /**
     * 
     * @return
     */
    public int getActivePort() {
        ActiveConfiguration cfg = currentConfig;
        if (cfg != null)
            return cfg.getActivePort();
        return -1;
    }

    /**
     * 
     * @return
     */
    public String getActiveHost() {
        ActiveConfiguration cfg = currentConfig;
        if (cfg != null){
            return cfg.configHost;
        }
        return null;
    }

    /**
   	 * @return name of this chain
   	 */
   	abstract protected String getName();
   
   	abstract public Type getType();

   	abstract public String getTransport();

   	/**
     * Setup event propertied - OSGI
     * @param eventProps
     */
    
   	abstract protected void setupEventProps(Map<String, Object> eventProps) ;
   	
   	/**
     * Create active configuration for this chain
     * @param cfg
     */
   	abstract protected ActiveConfiguration createActiveConfiguration();

   	/**
   	 * Create channels for this Chain
   	 * @param newConfig
   	 */
   	abstract protected void createChannels(ActiveConfiguration newConfig);
   	
   	/**
   	 * Rebuild all channels that related to this specific chain
   	 * @param oldConfig
   	 * @param newConfig
   	 */
   	abstract protected void rebuildTheChannel(ActiveConfiguration oldConfig, ActiveConfiguration newConfig) ;


    /**
     * Update/start the chain configuration.
     */
    // TODO Liberty - if we are not going to support configuration runtime change - remove unnecessary code. 
    public synchronized void update() {
        if (c_logger.isEventEnabled()) {
            c_logger.event("update chain " + this);
        }

        // Don't update or start the chain if it is disabled.
        if (!isEnabled() || FrameworkState.isStopping())
            return;

        final ActiveConfiguration oldConfig = getCurrentConfig();

        // The old configuration was "valid" if it existed, and if it was correctly configured
        final boolean validOldConfig = oldConfig == null ? false : oldConfig.validConfiguration;
        final ActiveConfiguration newConfig = createActiveConfiguration();

        if (newConfig.configPort < 0 || !newConfig.isReady()) {
            if (c_logger.isTraceDebugEnabled()) {
                c_logger.traceDebug("Stopping chain due to configuration " + newConfig);
            }

            // save the new/changed configuration before we start setting up the new chain
            setCurrentConfig(newConfig);

            return;
        } 
        
        if (validOldConfig && newConfig.unchanged(oldConfig)) {
            // If the old config was valid & the new one is the same.. 
            if (c_logger.isTraceDebugEnabled()) {
                c_logger.traceDebug("Configuration is unchanged " + newConfig);
            }
            // If configurations are identical, see if the listening port is also the same
            // which would indicate that the chain is running with the unchanged configuration
            int port = newConfig.getActivePort();
            if (port == oldConfig.getActivePort() && port != -1) {
                if (c_logger.isTraceDebugEnabled()) {
                    c_logger.traceDebug("Chain is already started " + oldConfig);
                }
                return;
            }

            if (c_logger.isTraceDebugEnabled()) {
                c_logger.traceDebug("Existing config must be started " + newConfig);
            }
        } else {
			if (c_logger.isTraceDebugEnabled()) {
				c_logger.traceDebug("New/changed chain configuration "
						+ newConfig);
			}
		}

		if (validOldConfig) {
			rebuildTheChannel(oldConfig, newConfig);
		}

		createChannels(newConfig);
		// save the new/changed configuration before we start setting up the
		// new chain
		setCurrentConfig(newConfig);

		if (newConfig.validConfiguration) {
			// ANNA
			// still using the CH f/w code, but made a change so the chain is not started
			//startChain(newConfig);  
			initChain(chainName);
		}
    }
    
    
	/**
     * ChainEventListener method.
     * This method can not be synchronized (deadlock with update/stop).
     * Rely on CFW synchronization of chain operations.
     */
    public synchronized void chainStarted() {
    	
    	if (c_logger.isTraceDebugEnabled()) {
            c_logger.traceDebug("Chain " + toString() + " is started");
        }
    	
        chainState.set(ChainState.STARTED.val);

        final ActiveConfiguration cfg = currentConfig;
        final int port = cfg.getActivePort();

        if (port > 0) {
            if (c_logger.isTraceDebugEnabled()) {
                c_logger.traceDebug("New configuration started " + cfg);
            }

            // Post an endpoint started event to anyone listening
            String topic = owner.getEventTopic() + GenericServiceConstants.ENDPOINT_STARTED;
            postEvent(topic, cfg, null);
        }
    }

    /**
     * Publish an event relating to a chain starting/stopping with the
     * given properties set about the chain.
     */
   protected void postEvent(String t, ActiveConfiguration c, Exception e) {
        Map<String, Object> eventProps = new HashMap<String, Object>(4);

        if (c.activeHost != null) {
            eventProps.put(GenericServiceConstants.ENDPOINT_ACTIVE_HOST, c.activeHost);
        }

        eventProps.put(GenericServiceConstants.ENDPOINT_ACTIVE_PORT, c.configHost);
        eventProps.put(GenericServiceConstants.ENDPOINT_CONFIG_HOST, c.configHost);
        eventProps.put(GenericServiceConstants.ENDPOINT_CONFIG_PORT, c.configPort);
        
        setupEventProps(eventProps);

        if (e != null) {
            eventProps.put(GenericServiceConstants.ENDPOINT_EXCEPTION, e.toString());
        }

        EventAdmin engine = GenericEndpointImpl.getEventAdmin();
        if (engine != null) {
            Event event = new Event(t, eventProps);
            engine.postEvent(event);
        }
    }


   /**
    * @see java.lang.Object#toString()
    */
   public String toString() {
        return this.getClass().getSimpleName()
               + "[@=" + System.identityHashCode(this)
               + ",enabled=" + enabled
               + ",state=" + ChainState.printState(chainState.get())
               + ",chainName=" + chainName
               + ",config=" + currentConfig + "]";
    }


    /**
     * Adds chain to the ChannelFramework
     * @param chanList
     * @param cd
     * @param newConfig 
     */
    protected void addChain(String[] chanList, ActiveConfiguration newConfig) {
        // We configured the chain successfully
        newConfig.validConfiguration = true;
	}

    private void initChain(String chainName) {
    	// ANNA
        if (c_logger.isTraceDebugEnabled()) {
            c_logger.traceDebug("initChain. " + chainName);
        }
        
        nettyBundle.addChain(this);
    }
}
