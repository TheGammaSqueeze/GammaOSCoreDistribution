/* Copyright (c) 2018 The Linux Foundation. All rights reserved.
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

import android.Manifest;
import android.content.Context;
import android.os.RemoteException;
import org.codeaurora.ims.MultiIdentityLineInfo;
import org.codeaurora.ims.internal.IImsMultiIdentityListener;
import org.codeaurora.ims.internal.IImsMultiIdentityInterface;
import java.util.List;
import java.util.ArrayList;

public abstract class ImsMultiIdentityControllerBase {
    public final class MultiIdentityBinder extends IImsMultiIdentityInterface.Stub {

        @Override
        public void setMultiIdentityListener(
                IImsMultiIdentityListener listener) throws RemoteException{
            ImsMultiIdentityControllerBase.this.
                setMultiIdentityListener(listener);
        }

        @Override
        public void updateRegistrationStatus(List<MultiIdentityLineInfo> linesInfo) {
            ImsMultiIdentityControllerBase.this.
                updateRegistrationStatus(linesInfo);
        }

        @Override
        public void queryVirtualLineInfo(String msisdn) throws RemoteException{
           ImsMultiIdentityControllerBase.this.
               queryVirtualLineInfo(msisdn);
        }
    }

    private IImsMultiIdentityInterface mBinder;

    public IImsMultiIdentityInterface getBinder() {
        if (mBinder == null) {
            mBinder = new MultiIdentityBinder();
        }
        return mBinder;
    }

    protected void setMultiIdentityListener(
            IImsMultiIdentityListener listener) throws RemoteException{
        //no-op
    }

    protected void updateRegistrationStatus(
            List<MultiIdentityLineInfo> linesInfo) {
        //no-op
    }

    protected void queryVirtualLineInfo(String msisdn) throws RemoteException{
        //no-op
    }
}
