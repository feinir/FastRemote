// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;

/** Converts unencrypted NetSarang NSSSH RSA containers to portable PKCS#1 in memory. */
public final class ImportedPrivateKey {
    private ImportedPrivateKey() { }
    public static char[] read(Path path)throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>1024*1024)throw new IOException("Private key must be a readable file no larger than 1 MiB");
        byte[] bytes;
        try(var stream=Files.newInputStream(path)){bytes=stream.readNBytes(1024*1024+1);}
        if(bytes.length>1024*1024)throw new IOException("Private key exceeds size limit");
        try {
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(text.contains("BEGIN NSSSH PRIVATE KEY"))return nsssh(text);
            if(!text.contains("PRIVATE KEY-----")&&!text.startsWith("PuTTY-User-Key-File-"))throw new IOException("Unsupported private key format; select an OpenSSH or PEM key");
            return text.toCharArray();
        }finally{Arrays.fill(bytes,(byte)0);}
    }
    public static boolean encrypted(char[] key) throws IOException {
        String text=new String(key);
        if(text.contains("ENCRYPTED PRIVATE KEY") || text.contains("Proc-Type: 4,ENCRYPTED"))return true;
        if(text.startsWith("PuTTY-User-Key-File-"))return !text.contains("Encryption: none");
        if(text.contains("BEGIN OPENSSH PRIVATE KEY")) {
            byte[] raw;
            try {raw=Base64.getDecoder().decode(text.replaceAll("-----[^\\r\\n]+-----","").replaceAll("\\s", ""));}
            catch(IllegalArgumentException invalid){throw new IOException("Invalid OpenSSH private key");}
            try(var in=new DataInputStream(new ByteArrayInputStream(raw))){
                if(!Arrays.equals(in.readNBytes(15),"openssh-key-v1\0".getBytes(StandardCharsets.US_ASCII)))throw new IOException("Invalid OpenSSH private key");
                return !new String(field(in),StandardCharsets.US_ASCII).equals("none");
            } finally {Arrays.fill(raw,(byte)0);}
        }
        return false;
    }
    static char[] nsssh(String text)throws IOException {
        List<byte[]> chunks=new ArrayList<>();StringBuilder part=new StringBuilder();
        try {
            for(String line:text.split("\\R")){
                line=line.strip();if(!line.matches("[A-Za-z0-9+/]+={0,2}"))continue;
                part.append(line);if(line.endsWith("=")){chunks.add(Base64.getDecoder().decode(part.toString()));part.setLength(0);}
            }
            if(!part.isEmpty())chunks.add(Base64.getDecoder().decode(part.toString()));
            if(chunks.isEmpty())throw new IOException("Unsupported NSSSH key layout");
            int size=chunks.stream().mapToInt(b->b.length).sum();
            if(size>1024*1024)throw new IOException("NSSSH key exceeds size limit");
            byte[] combined=new byte[size];int offset=0;
            for(byte[] chunk:chunks){System.arraycopy(chunk,0,combined,offset,chunk.length);offset+=chunk.length;}
            chunks.add(combined);
            try(var pub=new DataInputStream(new ByteArrayInputStream(combined))){
                var envelope=pub;
                if(!new String(field(pub),StandardCharsets.US_ASCII).equals("ssh-rsa"))throw new IOException("Unsupported NSSSH key algorithm");
                BigInteger e=integer(pub),n=integer(pub);
                byte[] magic=envelope.readNBytes(13);
                if(!Arrays.equals(magic,"nsssh-key-v6\0".getBytes(StandardCharsets.US_ASCII)))throw new IOException("Unsupported NSSSH private envelope");
                if(!new String(field(envelope),StandardCharsets.US_ASCII).equals("none") || !new String(field(envelope),StandardCharsets.US_ASCII).equals("none"))throw new IOException("Encrypted NSSSH key requires an OpenSSH export");
                if(field(envelope).length!=0 || envelope.readInt()!=1)throw new IOException("Unsupported NSSSH private envelope");
                byte[] body=field(envelope);
                try(var data=new DataInputStream(new ByteArrayInputStream(body))){
                    if(data.readInt()!=data.readInt())throw new IOException("Invalid NSSSH key check");
                    BigInteger q=integer(data),p=integer(data),d=integer(data),inverse=integer(data);
                    BigInteger pm=p.subtract(BigInteger.ONE),qm=q.subtract(BigInteger.ONE),lcm=pm.divide(pm.gcd(qm)).multiply(qm);
                    if(!p.multiply(q).equals(n)||!q.modInverse(p).equals(inverse)||!e.multiply(d).mod(lcm).equals(BigInteger.ONE))throw new IOException("NSSSH public and private keys do not match");
                    byte[] der=new RSAPrivateKey(n,e,d,p,q,d.mod(pm),d.mod(qm),inverse).getEncoded();
                    try{return ("-----BEGIN RSA PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(der)+"\n-----END RSA PRIVATE KEY-----\n").toCharArray();}
                    finally{Arrays.fill(der,(byte)0);}
                }finally{Arrays.fill(body,(byte)0);}
            }
        }catch(IllegalArgumentException|ArithmeticException malformed){throw new IOException("Invalid NSSSH private key");}
        finally{chunks.forEach(b->Arrays.fill(b,(byte)0));}
    }
    private static byte[] field(DataInputStream in)throws IOException {int n=in.readInt();if(n<0||n>1024*1024)throw new IOException("Invalid private key field length");byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException("Truncated private key");return b;}
    private static BigInteger integer(DataInputStream in)throws IOException {byte[] b=field(in);try{if(b.length==0||b.length>2049)throw new IOException("Unsupported RSA key size");return new BigInteger(1,b);}finally{Arrays.fill(b,(byte)0);}}
}
