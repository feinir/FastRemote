// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32C;

/** Bounded, read-only LevelDB snapshot reader. Resolves the manifest, sequence numbers and tombstones.
 * Does not open the source database for writing, recover it, copy it or acquire its writer lock. */
final class LevelDbSnapshotReader {
    private static final int LIMIT = 64 * 1024 * 1024;
    private final Map<String, Version> versions = new HashMap<>();
    private final Map<Path, Stamp> stamps = new HashMap<>();
    private int total;
    private long expanded;
    record Version(long sequence, byte[] key, byte[] value) {}
    record Stamp(long size, java.nio.file.attribute.FileTime modified) {}

    List<Version> read(Path directory) throws IOException {
        byte[] current = bytes(directory.resolve("CURRENT"));
        String manifest = new String(current, java.nio.charset.StandardCharsets.US_ASCII).strip();
        if (!manifest.matches("MANIFEST-\\d+")) throw invalid();
        Set<Long> tables = new HashSet<>();
        long logNumber = 0, previousLog = 0;
        for (byte[] record : log(bytes(directory.resolve(manifest)))) {
            Cursor c = new Cursor(record);
            while (c.remaining() > 0) {
                int tag = (int)c.var();
                switch(tag) {
                    case 1 -> c.blob();
                    case 2 -> logNumber = c.var();
                    case 3, 4 -> c.var();
                    case 5 -> { c.var(); c.blob(); }
                    case 6 -> { c.var(); tables.remove(c.var()); }
                    case 7 -> { c.var(); tables.add(c.var()); c.var(); c.blob(); c.blob(); }
                    case 9 -> previousLog = c.var();
                    default -> throw invalid();
                }
            }
        }
        if (tables.size() > 1024) throw invalid();
        for (long number : tables) {
            Path file = directory.resolve(String.format(Locale.ROOT,"%06d.ldb",number));
            if (!Files.exists(file)) file=directory.resolve(String.format(Locale.ROOT,"%06d.sst",number));
            table(bytes(file));
        }
        List<Path> logs;
        try(var stream=Files.list(directory)) { logs=stream.filter(p->p.getFileName().toString().matches("\\d+\\.log")).limit(1025).toList(); }
        if(logs.size()>1024) throw invalid();
        for(Path file:logs) {
            long n=Long.parseLong(file.getFileName().toString().replace(".log",""));
            if(n<logNumber && n!=previousLog) continue;
            for(byte[] record:log(bytes(file))) batch(record);
        }
        for(var entry:stamps.entrySet()) if(!entry.getValue().equals(stamp(entry.getKey())))
            throw new IOException("Termius data changed during reading; close Termius and retry");
        return versions.values().stream().filter(v->v.value()!=null).toList();
    }
    private static Stamp stamp(Path p) throws IOException {
        var a=Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if(!a.isRegularFile()) throw invalid();
        return new Stamp(a.size(),a.lastModifiedTime());
    }
    private byte[] bytes(Path path) throws IOException {
        Stamp stamp=stamp(path);
        if(stamp.size()>LIMIT-total) throw new IOException("Termius data exceeds import limit");
        stamps.put(path,stamp);
        byte[] result;
        try(var in=Files.newInputStream(path)) { result=in.readNBytes(LIMIT-total+1); }
        total+=result.length;
        if(total>LIMIT) throw invalid();
        return result;
    }
    private void put(long seq,byte[] key,byte[] value) throws IOException {
        if(versions.size()>200_000) throw invalid();
        String id=Base64.getEncoder().encodeToString(key);
        Version prev=versions.get(id);
        if(prev==null || seq>prev.sequence()) versions.put(id,new Version(seq,key,value));
    }
    private void batch(byte[] record) throws IOException {
        Cursor c=new Cursor(record);
        long sequence=c.longLE();
        long count=c.intLE() & 0xffffffffL;
        if(count>200_000) throw invalid();
        for(int i=0;i<count;i++) {
            int type=c.u8();byte[] key=c.blob();
            if(type!=0 && type!=1) throw invalid();
            put(sequence+i,key,type==1?c.blob():null);
        }
        if(c.remaining()!=0) throw invalid();
    }
    static List<byte[]> log(byte[] data) throws IOException {
        List<byte[]> records=new ArrayList<>();
        java.io.ByteArrayOutputStream fragments=null;
        for(int block=0;block<data.length;block+=32768) {
            int end=Math.min(block+32768,data.length),p=block;
            while(p+7<=end) {
                int crc=ByteBuffer.wrap(data,p,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int length=(data[p+4]&255)|((data[p+5]&255)<<8),type=data[p+6]&255;p+=7;
                if(type==0 && length==0) break;
                if(p+length>end) throw invalid();
                checksum(data,p-1,length+1,crc);
                byte[] piece=Arrays.copyOfRange(data,p,p+length);p+=length;
                switch(type) {
                    case 1 -> { if(fragments!=null) throw invalid(); records.add(piece); }
                    case 2 -> { if(fragments!=null) throw invalid(); fragments=new java.io.ByteArrayOutputStream(); fragments.writeBytes(piece); }
                    case 3,4 -> {
                        if(fragments==null || fragments.size()+length>LIMIT) throw invalid();
                        fragments.writeBytes(piece);
                        if(type==4) { records.add(fragments.toByteArray()); fragments=null; }
                    }
                    default -> throw invalid();
                }
                if(records.size()>200_000) throw invalid();
            }
        }
        if(fragments!=null) throw new IOException("Incomplete Termius write; close Termius and retry");
        return records;
    }
    private void table(byte[] data) throws IOException {
        if(data.length<48) throw invalid();
        Cursor footer=new Cursor(Arrays.copyOfRange(data,data.length-48,data.length));
        footer.var();footer.var();long offset=footer.var(),size=footer.var();
        long magic=ByteBuffer.wrap(data,data.length-8,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        if(magic!=0xdb4775248b80fb57L) throw invalid();
        for(var index:blockEntries(block(data,offset,size))) {
            Cursor handle=new Cursor(index.getValue());
            for(var entry:blockEntries(block(data,handle.var(),handle.var()))) {
                byte[] key=entry.getKey();if(key.length<8) throw invalid();
                long trailer=ByteBuffer.wrap(key,key.length-8,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                int kind=(int)(trailer&255);
                if(kind!=0 && kind!=1) throw invalid();
                put(trailer>>>8,Arrays.copyOf(key,key.length-8),kind==0?null:entry.getValue());
            }
        }
    }
    private byte[] block(byte[] file,long offset,long size) throws IOException {
        if(offset<0 || size<0 || size>LIMIT || offset>file.length-size-5) throw invalid();
        int start=(int)offset,n=(int)size,type=file[start+n]&255;
        int crc=ByteBuffer.wrap(file,start+n+1,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        checksum(file,start,n+1,crc);
        byte[] bytes=Arrays.copyOfRange(file,start,start+n);
        byte[] decoded=switch(type) {case 0->bytes;case 1->snappy(bytes);default->throw invalid();};
        expanded+=decoded.length;
        if(expanded>LIMIT)throw new IOException("Expanded Termius data exceeds import limit");
        return decoded;
    }
    private static List<Map.Entry<byte[],byte[]>> blockEntries(byte[] block) throws IOException {
        if(block.length<4) throw invalid();
        int restarts=ByteBuffer.wrap(block,block.length-4,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if(restarts<0 || restarts>(block.length-4)/4) throw invalid();
        int end=block.length-4-4*restarts;
        Cursor c=new Cursor(Arrays.copyOf(block,end));
        List<Map.Entry<byte[],byte[]>> result=new ArrayList<>();byte[] previous=new byte[0];
        while(c.remaining()>0) {
            int shared=c.length(),suffix=c.length(),valueLength=c.length();
            if(shared>previous.length || shared+suffix>LIMIT) throw invalid();
            byte[] key=Arrays.copyOf(previous,shared+suffix);
            System.arraycopy(c.take(suffix),0,key,shared,suffix);
            result.add(Map.entry(key,c.take(valueLength)));previous=key;
            if(result.size()>200_000) throw invalid();
        }
        return result;
    }
    static byte[] snappy(byte[] encoded) throws IOException {
        Cursor c=new Cursor(encoded);byte[] out=new byte[c.length()];int p=0;
        while(c.remaining()>0) {
            int tag=c.u8(),kind=tag&3,n,offset;
            if(kind==0) {
                n=tag>>>2;
                if(n<60)n++;else {int count=n-59;n=0;for(int i=0;i<count;i++)n|=c.u8()<<(8*i);n++;}
                if(n<0 || n>out.length-p) throw invalid();
                System.arraycopy(c.take(n),0,out,p,n);p+=n;continue;
            }
            if(kind==1){n=4+((tag>>>2)&7);offset=((tag&224)<<3)|c.u8();}
            else {n=1+(tag>>>2);offset=c.u8()|(c.u8()<<8);if(kind==3)offset|=c.u8()<<16|c.u8()<<24;}
            if(offset<=0 || offset>p || n>out.length-p) throw invalid();
            for(int i=0;i<n;i++) {out[p]=out[p-offset];p++;}
        }
        if(p!=out.length) throw invalid();return out;
    }
    private static void checksum(byte[] data,int start,int length,int expected) throws IOException {
        CRC32C crc=new CRC32C();crc.update(data,start,length);int n=(int)crc.getValue();
        if(Integer.rotateRight(n,15)+0xa282ead8!=expected) throw invalid();
    }
    static IOException invalid() {return new IOException("Unsupported or damaged Termius database");}
    static final class Cursor {
        final byte[] bytes;int pos;
        Cursor(byte[] bytes){this.bytes=bytes;}
        int remaining(){return bytes.length-pos;}
        int u8() throws IOException {if(pos>=bytes.length)throw invalid();return bytes[pos++]&255;}
        long var() throws IOException {long v=0;for(int s=0;s<64;s+=7){int b=u8();if(s==63 && b>1)throw invalid();v|=(long)(b&127)<<s;if(b<128)return v;}throw invalid();}
        int length() throws IOException {long n=var();if(n<0||n>LIMIT)throw invalid();return(int)n;}
        byte[] take(int n) throws IOException {if(n<0||n>remaining())throw invalid();byte[] b=Arrays.copyOfRange(bytes,pos,pos+n);pos+=n;return b;}
        byte[] blob() throws IOException {return take(length());}
        int intLE() throws IOException {return ByteBuffer.wrap(take(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();}
        long longLE() throws IOException {return ByteBuffer.wrap(take(8)).order(ByteOrder.LITTLE_ENDIAN).getLong();}
    }
}
