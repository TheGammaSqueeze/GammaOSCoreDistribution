/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.NetworkReleasedException;
import android.net.QosCallbackException;
import android.net.SocketLocalAddressChangedException;
import android.net.SocketNotBoundException;
import android.os.Build;

import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DevSdkIgnoreRunner.class)
@IgnoreUpTo(Build.VERSION_CODES.R)
public class QosCallbackExceptionTest {
    private static final String ERROR_MESSAGE = "Test Error Message";
    private static final String ERROR_MSG_SOCK_NOT_BOUND = "The socket is unbound";
    private static final String ERROR_MSG_NET_RELEASED =
            "The network was released and is no longer available";
    private static final String ERROR_MSG_SOCK_ADDR_CHANGED =
            "The local address of the socket changed";


    @Test
    public void testQosCallbackException() throws Exception {
        final Throwable testcause = new Throwable(ERROR_MESSAGE);
        final QosCallbackException exception = new QosCallbackException(testcause);
        assertEquals(testcause, exception.getCause());

        final QosCallbackException exceptionMsg = new QosCallbackException(ERROR_MESSAGE);
        assertEquals(ERROR_MESSAGE, exceptionMsg.getMessage());
    }

    @Test
    public void testNetworkReleasedExceptions() throws Exception {
        final Throwable netReleasedException = new NetworkReleasedException();
        final QosCallbackException exception = new QosCallbackException(netReleasedException);

        assertTrue(exception.getCause() instanceof NetworkReleasedException);
        assertEquals(netReleasedException, exception.getCause());
        assertTrue(exception.getMessage().contains(ERROR_MSG_NET_RELEASED));
        assertThrowableMessageContains(exception, ERROR_MSG_NET_RELEASED);
    }

    @Test
    public void testSocketNotBoundExceptions() throws Exception {
        final Throwable sockNotBoundException = new SocketNotBoundException();
        final QosCallbackException exception = new QosCallbackException(sockNotBoundException);

        assertTrue(exception.getCause() instanceof SocketNotBoundException);
        assertEquals(sockNotBoundException, exception.getCause());
        assertTrue(exception.getMessage().contains(ERROR_MSG_SOCK_NOT_BOUND));
        assertThrowableMessageContains(exception, ERROR_MSG_SOCK_NOT_BOUND);
    }

    @Test
    public void testSocketLocalAddressChangedExceptions() throws  Exception {
        final Throwable localAddrChangedException = new SocketLocalAddressChangedException();
        final QosCallbackException exception = new QosCallbackException(localAddrChangedException);

        assertTrue(exception.getCause() instanceof SocketLocalAddressChangedException);
        assertEquals(localAddrChangedException, exception.getCause());
        assertTrue(exception.getMessage().contains(ERROR_MSG_SOCK_ADDR_CHANGED));
        assertThrowableMessageContains(exception, ERROR_MSG_SOCK_ADDR_CHANGED);
    }

    private void assertThrowableMessageContains(QosCallbackException exception, String errorMsg)
            throws Exception {
        try {
            triggerException(exception);
            fail("Expect exception");
        } catch (QosCallbackException e) {
            assertTrue(e.getMessage().contains(errorMsg));
        }
    }

    private void triggerException(QosCallbackException exception) throws Exception {
        throw new QosCallbackException(exception.getCause());
    }
}
