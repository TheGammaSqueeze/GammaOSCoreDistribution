/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.nearby.common.bluetooth.util;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.bluetooth.BluetoothGatt;

import androidx.test.filters.SdkSuppress;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.testability.NonnullProvider;
import com.android.server.nearby.common.bluetooth.testability.TimeProvider;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.BluetoothOperationTimeoutException;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.Operation;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.SynchronousOperation;

import junit.framework.TestCase;

import org.mockito.Mock;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Unit tests for {@link BluetoothOperationExecutor}.
 */
public class BluetoothOperationExecutorTest extends TestCase {

    private static final String OPERATION_RESULT = "result";
    private static final String EXCEPTION_REASON = "exception";
    private static final long TIME = 1234;
    private static final long TIMEOUT = 121212;

    @Mock
    private NonnullProvider<BlockingQueue<Object>> mMockBlockingQueueProvider;
    @Mock
    private TimeProvider mMockTimeProvider;
    @Mock
    private BlockingQueue<Object> mMockBlockingQueue;
    @Mock
    private Semaphore mMockSemaphore;
    @Mock
    private Operation<String> mMockStringOperation;
    @Mock
    private Operation<Void> mMockVoidOperation;
    @Mock
    private Future<Object> mMockFuture;
    @Mock
    private Future<Object> mMockFuture2;

    private BluetoothOperationExecutor mBluetoothOperationExecutor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        initMocks(this);

        when(mMockBlockingQueueProvider.get()).thenReturn(mMockBlockingQueue);
        when(mMockSemaphore.tryAcquire()).thenReturn(true);
        when(mMockTimeProvider.getTimeMillis()).thenReturn(TIME);

        mBluetoothOperationExecutor =
                new BluetoothOperationExecutor(mMockSemaphore, mMockTimeProvider,
                        mMockBlockingQueueProvider);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testExecute() throws Exception {
        when(mMockBlockingQueue.take()).thenReturn(OPERATION_RESULT);

        String result = mBluetoothOperationExecutor.execute(mMockStringOperation);

        verify(mMockStringOperation).execute(mBluetoothOperationExecutor);
        assertThat(result).isEqualTo(OPERATION_RESULT);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testExecuteWithTimeout() throws Exception {
        when(mMockBlockingQueue.poll(TIMEOUT, TimeUnit.MILLISECONDS)).thenReturn(OPERATION_RESULT);

        String result = mBluetoothOperationExecutor.execute(mMockStringOperation, TIMEOUT);

        verify(mMockStringOperation).execute(mBluetoothOperationExecutor);
        assertThat(result).isEqualTo(OPERATION_RESULT);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSchedule() throws Exception {
        when(mMockBlockingQueue.poll(TIMEOUT, TimeUnit.MILLISECONDS)).thenReturn(OPERATION_RESULT);

        Future<String> result = mBluetoothOperationExecutor.schedule(mMockStringOperation);

        verify(mMockStringOperation).execute(mBluetoothOperationExecutor);
        assertThat(result.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testScheduleOtherOperationInProgress() throws Exception {
        when(mMockSemaphore.tryAcquire()).thenReturn(false);
        when(mMockBlockingQueue.poll(TIMEOUT, TimeUnit.MILLISECONDS)).thenReturn(OPERATION_RESULT);

        Future<String> result = mBluetoothOperationExecutor.schedule(mMockStringOperation);

        verify(mMockStringOperation, never()).run();

        when(mMockSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)).thenReturn(true);

        assertThat(result.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
        verify(mMockStringOperation).execute(mBluetoothOperationExecutor);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNotifySuccessWithResult() throws Exception {
        when(mMockBlockingQueueProvider.get()).thenReturn(new LinkedBlockingDeque<Object>());
        Future<String> future = mBluetoothOperationExecutor.schedule(mMockStringOperation);

        mBluetoothOperationExecutor.notifySuccess(mMockStringOperation, OPERATION_RESULT);

        assertThat(future.get(1, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNotifySuccessTwice() throws Exception {
        BlockingQueue<Object> resultQueue = new LinkedBlockingDeque<Object>();
        when(mMockBlockingQueueProvider.get()).thenReturn(resultQueue);
        Future<String> future = mBluetoothOperationExecutor.schedule(mMockStringOperation);

        mBluetoothOperationExecutor.notifySuccess(mMockStringOperation, OPERATION_RESULT);

        assertThat(future.get(1, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);

        // the second notification should be ignored
        mBluetoothOperationExecutor.notifySuccess(mMockStringOperation, OPERATION_RESULT);
        assertThat(resultQueue).isEmpty();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNotifySuccessWithNullResult() throws Exception {
        when(mMockBlockingQueueProvider.get()).thenReturn(new LinkedBlockingDeque<Object>());
        Future<String> future = mBluetoothOperationExecutor.schedule(mMockStringOperation);

        mBluetoothOperationExecutor.notifySuccess(mMockStringOperation, null);

        assertThat(future.get(1, TimeUnit.MILLISECONDS)).isNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNotifySuccess() throws Exception {
        when(mMockBlockingQueueProvider.get()).thenReturn(new LinkedBlockingDeque<Object>());
        Future<Void> future = mBluetoothOperationExecutor.schedule(mMockVoidOperation);

        mBluetoothOperationExecutor.notifySuccess(mMockVoidOperation);

        future.get(1, TimeUnit.MILLISECONDS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNotifyCompletionSuccess() throws Exception {
        when(mMockBlockingQueueProvider.get()).thenReturn(new LinkedBlockingDeque<Object>());
        Future<Void> future = mBluetoothOperationExecutor.schedule(mMockVoidOperation);

        mBluetoothOperationExecutor
                .notifyCompletion(mMockVoidOperation, BluetoothGatt.GATT_SUCCESS);

        future.get(1, TimeUnit.MILLISECONDS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNotifyCompletionFailure() throws Exception {
        when(mMockBlockingQueueProvider.get()).thenReturn(new LinkedBlockingDeque<Object>());
        Future<Void> future = mBluetoothOperationExecutor.schedule(mMockVoidOperation);

        mBluetoothOperationExecutor
                .notifyCompletion(mMockVoidOperation, BluetoothGatt.GATT_FAILURE);

        try {
            BluetoothOperationExecutor.getResult(future, 1);
            fail("Expected BluetoothException");
        } catch (BluetoothException e) {
            //expected
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNotifyFailure() throws Exception {
        when(mMockBlockingQueueProvider.get()).thenReturn(new LinkedBlockingDeque<Object>());
        Future<Void> future = mBluetoothOperationExecutor.schedule(mMockVoidOperation);

        mBluetoothOperationExecutor
                .notifyFailure(mMockVoidOperation, new BluetoothException("test"));

        try {
            BluetoothOperationExecutor.getResult(future, 1);
            fail("Expected BluetoothException");
        } catch (BluetoothException e) {
            //expected
        }
    }

    @SuppressWarnings("unchecked")
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWaitFor() throws Exception {
        mBluetoothOperationExecutor.waitFor(Arrays.asList(mMockFuture, mMockFuture2));

        verify(mMockFuture).get();
        verify(mMockFuture2).get();
    }

    @SuppressWarnings("unchecked")
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWaitForWithTimeout() throws Exception {
        mBluetoothOperationExecutor.waitFor(
                Arrays.asList(mMockFuture, mMockFuture2),
                TIMEOUT);

        verify(mMockFuture).get(TIMEOUT, TimeUnit.MILLISECONDS);
        verify(mMockFuture2).get(TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGetResult() throws Exception {
        when(mMockFuture.get()).thenReturn(OPERATION_RESULT);

        Object result = BluetoothOperationExecutor.getResult(mMockFuture);

        assertThat(result).isEqualTo(OPERATION_RESULT);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGetResultWithTimeout() throws Exception {
        when(mMockFuture.get(TIMEOUT, TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException());

        try {
            BluetoothOperationExecutor.getResult(mMockFuture, TIMEOUT);
            fail("Expected BluetoothOperationTimeoutException");
        } catch (BluetoothOperationTimeoutException e) {
            //expected
        }
        verify(mMockFuture).cancel(true);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_SynchronousOperation_execute() throws Exception {
        when(mMockBlockingQueueProvider.get()).thenReturn(mMockBlockingQueue);
        SynchronousOperation<String> synchronousOperation = new SynchronousOperation<String>() {
            @Override
            public String call() throws BluetoothException {
                return OPERATION_RESULT;
            }
        };

        @SuppressWarnings("unused") // future return.
        Future<?> possiblyIgnoredError = mBluetoothOperationExecutor.schedule(synchronousOperation);

        verify(mMockBlockingQueue).add(OPERATION_RESULT);
        verify(mMockSemaphore).release();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_SynchronousOperation_exception() throws Exception {
        final BluetoothException exception = new BluetoothException(EXCEPTION_REASON);
        when(mMockBlockingQueueProvider.get()).thenReturn(mMockBlockingQueue);
        SynchronousOperation<String> synchronousOperation = new SynchronousOperation<String>() {
            @Override
            public String call() throws BluetoothException {
                throw exception;
            }
        };

        @SuppressWarnings("unused") // future return.
        Future<?> possiblyIgnoredError = mBluetoothOperationExecutor.schedule(synchronousOperation);

        verify(mMockBlockingQueue).add(exception);
        verify(mMockSemaphore).release();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_AsynchronousOperation_exception() throws Exception {
        final BluetoothException exception = new BluetoothException(EXCEPTION_REASON);
        when(mMockBlockingQueueProvider.get()).thenReturn(mMockBlockingQueue);
        Operation<String> operation = new Operation<String>() {
            @Override
            public void run() throws BluetoothException {
                throw exception;
            }
        };

        @SuppressWarnings("unused") // future return.
        Future<?> possiblyIgnoredError = mBluetoothOperationExecutor.schedule(operation);

        verify(mMockBlockingQueue).add(exception);
        verify(mMockSemaphore).release();
    }
}
