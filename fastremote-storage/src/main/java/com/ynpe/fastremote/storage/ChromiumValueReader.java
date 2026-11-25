// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict reader for the scalar/object/array subset of V8 structured clone used by host records. */
final class ChromiumValueReader {
    private final LevelDbSnapshotReader.Cursor c;
    private final List<Object> references=new ArrayList<>();
    private int values;
    private ChromiumValueReader(byte[] bytes){c=new LevelDbSnapshotReader.Cursor(bytes);}
    static Object read(byte[] bytes) throws IOException {
        var reader=new ChromiumValueReader(bytes);
        reader.c.var(); // IndexedDB record version
        if(reader.c.u8()!=255) throw LevelDbSnapshotReader.invalid();
        long blinkVersion=reader.c.var();
        if(blinkVersion<16 || blinkVersion>21) throw LevelDbSnapshotReader.invalid();
        // Blink v21 can include an out-of-line trailer descriptor.
        if(reader.c.remaining()>0 && (reader.c.bytes[reader.c.pos]&255)==0xfe) reader.c.take(13);
        if(reader.c.u8()!=255 || reader.c.var()>15) throw LevelDbSnapshotReader.invalid();
        return reader.value(0);
    }
    private Object value(int depth) throws IOException {
        if(depth>32 || ++values>100_000) throw LevelDbSnapshotReader.invalid();
        int tag=c.u8();while(tag==0)tag=c.u8();
        return switch(tag) {
            case '0','_','-' -> null;
            case 'T' -> true;
            case 'F' -> false;
            case 'I' -> {long n=c.var();yield (n>>>1)^-(n&1);}
            case 'U' -> c.var();
            case 'N' -> Double.longBitsToDouble(c.longLE());
            case '"' -> new String(c.blob(),StandardCharsets.ISO_8859_1);
            case 'S' -> new String(c.blob(),StandardCharsets.UTF_8);
            case 'c' -> new String(c.blob(),ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN?StandardCharsets.UTF_16LE:StandardCharsets.UTF_16BE);
            case '^' -> {int n=c.length();if(n>=references.size())throw LevelDbSnapshotReader.invalid();yield references.get(n);}
            case 'o' -> {
                Map<String,Object> object=new LinkedHashMap<>();references.add(object);int count=0;
                while(peek()!='{') {Object key=value(depth+1);if(!(key instanceof String || key instanceof Number))throw LevelDbSnapshotReader.invalid();object.put(key.toString(),value(depth+1));count++;}
                c.u8();if(c.var()!=count)throw LevelDbSnapshotReader.invalid();yield object;
            }
            case 'A' -> {
                int n=c.length();if(n>20_000)throw LevelDbSnapshotReader.invalid();
                List<Object> list=new ArrayList<>();references.add(list);
                for(int i=0;i<n;i++)list.add(value(depth+1));
                int properties=0;while(peek()!='$'){value(depth+1);value(depth+1);properties++;}
                c.u8();if(c.var()!=properties || c.var()!=n)throw LevelDbSnapshotReader.invalid();yield list;
            }
            case 'a' -> {
                int n=c.length();if(n>20_000)throw LevelDbSnapshotReader.invalid();
                Map<String,Object> object=new LinkedHashMap<>();references.add(object);int count=0;
                while(peek()!='@'){Object key=value(depth+1);object.put(String.valueOf(key),value(depth+1));count++;}
                c.u8();if(c.var()!=count || c.var()!=n)throw LevelDbSnapshotReader.invalid();yield object;
            }
            case 'D' -> {double date=Double.longBitsToDouble(c.longLE());references.add(date);yield date;}
            default -> throw LevelDbSnapshotReader.invalid();
        };
    }
    private int peek() throws IOException {if(c.remaining()==0)throw LevelDbSnapshotReader.invalid();return c.bytes[c.pos]&255;}
}
