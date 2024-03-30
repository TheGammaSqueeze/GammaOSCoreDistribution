/* Copyright (c) 2021 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.ims;

import android.content.Context;
import android.os.RemoteException;
import org.codeaurora.ims.internal.ICrsCrbtListener;
import org.codeaurora.ims.internal.ICrsCrbtController;

public abstract class CrsCrbtControllerBase {
    public final class CrsCrbtBinder extends ICrsCrbtController.Stub {

        @Override
        public void setCrsCrbtListener(ICrsCrbtListener listener)
            throws RemoteException{
            CrsCrbtControllerBase.this.onSetCrsCrbtListener(listener);
        }

        @Override
        public void removeCrsCrbtListener(ICrsCrbtListener listener)
            throws RemoteException{
            CrsCrbtControllerBase.this.onRemoveCrsCrbtListener(listener);
        }

        @Override
        public boolean isPreparatorySession(String callId)
            throws RemoteException {
            return CrsCrbtControllerBase.this.onIsPreparatorySession(callId);
        }

        @Override
        public void sendSipDtmf(String requestCode)
            throws RemoteException {
            CrsCrbtControllerBase.this.onSendSipDtmf(requestCode);
        }
    }

    private ICrsCrbtController mBinder;

    public ICrsCrbtController getBinder() {
        if (mBinder == null) {
            mBinder = new CrsCrbtBinder();
        }
        return mBinder;
    }

    protected void onSetCrsCrbtListener(ICrsCrbtListener listener)
        throws RemoteException {
        //no-op
    }

    protected void onRemoveCrsCrbtListener(ICrsCrbtListener listener)
        throws RemoteException {
        //no-op
    }

    protected boolean onIsPreparatorySession(String callId)
        throws RemoteException {
        //no-op
        return false;
    }

    protected void onSendSipDtmf(String requestCode)
        throws RemoteException {
        //no-op
    }
}
