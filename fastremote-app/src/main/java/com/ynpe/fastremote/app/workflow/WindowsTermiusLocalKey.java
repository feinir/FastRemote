// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;

/** Reads only the exact keytar generic credential used by local Termius. */
final class WindowsTermiusLocalKey {
    private WindowsTermiusLocalKey() { }

    static byte[] read() throws IOException {
        var api=Native.load("Advapi32",CredentialsApi.class);
        var reference=new PointerByReference();
        if(!api.CredReadW(new WString("Termius/localKey"),1,0,reference))
            throw new IOException("Termius local key is unavailable in Windows Credential Manager");
        Pointer allocation=reference.getValue();
        try {
            var credential=new Credential(allocation);
            if(credential.type!=1 || credential.blob==null || credential.blobSize<=0 || credential.blobSize>4096)
                throw new IOException("Invalid Termius local key credential");
            return credential.blob.getByteArray(0,credential.blobSize);
        } finally {api.CredFree(allocation);}
    }
    interface CredentialsApi extends StdCallLibrary {
        boolean CredReadW(WString target,int type,int flags,PointerByReference credential);
        void CredFree(Pointer credential);
    }
    @Structure.FieldOrder({"flags","type","target","comment","lastWrittenLow","lastWrittenHigh","blobSize","blob","persist","attributeCount","attributes","alias","username"})
    public static final class Credential extends Structure {
        public int flags,type;
        public Pointer target,comment;
        public int lastWrittenLow,lastWrittenHigh,blobSize;
        public Pointer blob;
        public int persist,attributeCount;
        public Pointer attributes,alias,username;
        public Credential(Pointer pointer){super(pointer);read();}
    }
}
