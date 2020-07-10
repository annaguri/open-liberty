/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.grpc.internal.monitor;

import java.time.Clock;
import java.time.Instant;

import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.Status;

public class GrpcMonitoringClientCallListener<RespT> extends ForwardingClientCallListener<RespT> {
	private static final long MILLIS_PER_SECOND = 1000L;
	
	private final ClientCall.Listener<RespT> delegate;
	private final GrpcClientMetrics clientMetrics;
	private final GrpcMethod grpcMethod;
	private final Clock clock;
	private final Instant startInstant;

	GrpcMonitoringClientCallListener(ClientCall.Listener<RespT> delegate, GrpcClientMetrics clientMetrics,
			GrpcMethod grpcMethod, Clock clock) {
		this.delegate = delegate;
		this.clientMetrics = clientMetrics;
		this.grpcMethod = grpcMethod;
		this.clock = clock;
		this.startInstant = clock.instant();
	}

	@Override
	protected Listener<RespT> delegate() {
		return delegate;
	}

	@Override
	public void onClose(Status status, Metadata metadata) {
		clientMetrics.recordClientHandled();// status.getCode());
		double latencySec = (clock.millis() - startInstant.toEpochMilli()) / (double) MILLIS_PER_SECOND;
		clientMetrics.recordLatency(latencySec);
		super.onClose(status, metadata);
	}

	@Override
	public void onMessage(RespT responseMessage) {
		if (grpcMethod.streamsResponses()) {
			clientMetrics.incrementReceivedMsgCountBy(1);
		}
		super.onMessage(responseMessage);
	}
}
