// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.util.*;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImportedPrivateKeyTest {
    @TempDir Path directory;

    @Test void convertsNssshV6AndChecksPublicPrivateConsistency() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        var pair=generator.generateKeyPair();var key=(RSAPrivateCrtKey)pair.getPrivate();
        String container=container(key,key.getPublicExponent().toByteArray());
        Path path=directory.resolve("key.pri");Files.writeString(path,container);
        char[] pem=ImportedPrivateKey.read(path);
        try {
            assertFalse(ImportedPrivateKey.encrypted(pem));
            byte[] der=Base64.getDecoder().decode(new String(pem).replaceAll("-----[^\\r\\n]+-----","").replaceAll("\\s",""));
            try {
                var decoded=RSAPrivateKey.getInstance(der);
                assertEquals(key.getModulus(),decoded.getModulus());
                assertEquals(key.getPrivateExponent(),decoded.getPrivateExponent());
                assertEquals(key.getPrimeP(),decoded.getPrime1());
            }finally{Arrays.fill(der,(byte)0);}
        }finally{Arrays.fill(pem,'\0');}
        Files.writeString(path,container(key,new byte[]{2}));
        assertThrows(IOException.class,()->ImportedPrivateKey.read(path));
        Files.writeString(path,container.replace("PRIVATE KEY","PUBLIC KEY"));
        assertThrows(IOException.class,()->ImportedPrivateKey.read(path));
    }
    @Test void detectsEncryptedKeysAndRejectsNonKeyFiles()throws Exception {
        assertTrue(ImportedPrivateKey.encrypted("-----BEGIN ENCRYPTED PRIVATE KEY-----".toCharArray()));
        assertTrue(ImportedPrivateKey.encrypted("PuTTY-User-Key-File-3: ssh-rsa\nEncryption: aes256-cbc".toCharArray()));
        assertFalse(ImportedPrivateKey.encrypted("PuTTY-User-Key-File-3: ssh-rsa\nEncryption: none".toCharArray()));
        Path path=directory.resolve("other.txt");Files.writeString(path,"not a private key");
        assertThrows(IOException.class,()->ImportedPrivateKey.read(path));
    }
    static String container(RSAPrivateCrtKey key,byte[] exponent)throws IOException {
        var pub=new ByteArrayOutputStream();try(var out=new DataOutputStream(pub)) {
            field(out,"ssh-rsa".getBytes(StandardCharsets.US_ASCII));field(out,exponent);field(out,key.getModulus().toByteArray());
        }
        var body=new ByteArrayOutputStream();try(var out=new DataOutputStream(body)) {
            out.writeInt(1234);out.writeInt(1234);
            for(var value:List.of(key.getPrimeQ(),key.getPrimeP(),key.getPrivateExponent(),key.getCrtCoefficient()))field(out,value.toByteArray());
            field(out,new byte[0]);out.write(new byte[]{1,2,3,4});
        }
        var envelope=new ByteArrayOutputStream();try(var out=new DataOutputStream(envelope)) {
            out.write("nsssh-key-v6\0".getBytes(StandardCharsets.US_ASCII));field(out,"none".getBytes());field(out,"none".getBytes());field(out,new byte[0]);out.writeInt(1);field(out,body.toByteArray());
        }
        // The two base64 blocks may or may not end in padding.
        return "---- BEGIN NSSSH PRIVATE KEY ----\nComment: generated fixture\nKey: ssh-rsa\n"+
                Base64.getEncoder().encodeToString(pub.toByteArray())+"\n"+
                Base64.getEncoder().encodeToString(envelope.toByteArray())+"\n---- END NSSSH PRIVATE KEY ----\n";
    }
    static void field(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
}
