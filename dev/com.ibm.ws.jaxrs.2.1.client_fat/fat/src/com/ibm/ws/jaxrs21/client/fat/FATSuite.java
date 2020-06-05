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
package com.ibm.ws.jaxrs21.client.fat;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ClientCXFRxInvokerTest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ClientCallbackTest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ClientCompletionStageRxInvokerTest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ClientJerseyRxInvokerTest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ClientLTPATest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ClientSSLProxyAuthTest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ClientSSLTest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ComplexClientTest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21ReactiveSampleTest;
import com.ibm.ws.jaxrs21.client.fat.test.JAXRS21TimeoutClientTest;

import componenttest.custom.junit.runner.AlwaysPassesTest;

@RunWith(Suite.class)
@SuiteClasses({ AlwaysPassesTest.class,
                JAXRS21ClientSSLProxyAuthTest.class,
                JAXRS21ClientSSLTest.class,
                JAXRS21ClientCallbackTest.class,
                JAXRS21ComplexClientTest.class,
                JAXRS21TimeoutClientTest.class,
                JAXRS21ClientLTPATest.class,
                JAXRS21ClientJerseyRxInvokerTest.class,
                JAXRS21ClientCXFRxInvokerTest.class,
                JAXRS21ClientCompletionStageRxInvokerTest.class,
                JAXRS21ReactiveSampleTest.class })
public class FATSuite {
}
