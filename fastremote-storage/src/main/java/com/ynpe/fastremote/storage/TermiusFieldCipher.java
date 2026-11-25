// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/** Reads version 04/01 NaCl secretbox fields, authenticating before returning plaintext. */
final class TermiusFieldCipher implements AutoCloseable {
    private final byte[] key;
    TermiusFieldCipher(byte[] key) throws IOException {
        if(key.length!=32)throw new IOException("Termius local key is unavailable");
        this.key=key.clone();
    }
    String decrypt(Object value) throws IOException {
        if(value==null)return "";
        if(!(value instanceof String text))throw new IOException("Invalid Termius text field");
        if(text.isEmpty())return text;
        byte[] bytes;
        try {bytes=Base64.getDecoder().decode(text);}catch(IllegalArgumentException plain){return text;}
        if(bytes.length<2 || bytes[0]!=4){Arrays.fill(bytes,(byte)0);return text;}
        if(bytes[1]!=1){Arrays.fill(bytes,(byte)0);throw new IOException("Unsupported Termius encryption version");}
        if(bytes.length<42)throw new IOException("Damaged Termius encrypted field");
        byte[] nonce=Arrays.copyOfRange(bytes,2,26),polyKey=new byte[32],zero=new byte[32],plain=new byte[bytes.length-42],tag=new byte[16];
        try {
            XSalsa20Engine cipher=new XSalsa20Engine();cipher.init(false,new ParametersWithIV(new KeyParameter(key),nonce));
            cipher.processBytes(zero,0,32,polyKey,0);
            Poly1305 mac=new Poly1305();mac.init(new KeyParameter(polyKey));mac.update(bytes,42,bytes.length-42);mac.doFinal(tag,0);
            if(!java.security.MessageDigest.isEqual(tag,Arrays.copyOfRange(bytes,26,42)))throw new IOException("Termius local key does not match this data");
            cipher.processBytes(bytes,42,plain.length,plain,0);
            return new String(plain,StandardCharsets.UTF_8);
        } finally {Arrays.fill(bytes,(byte)0);Arrays.fill(polyKey,(byte)0);Arrays.fill(plain,(byte)0);Arrays.fill(tag,(byte)0);}
    }
    @Override public void close(){Arrays.fill(key,(byte)0);}
}
