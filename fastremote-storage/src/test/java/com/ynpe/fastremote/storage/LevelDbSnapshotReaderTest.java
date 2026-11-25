// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.*;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32C;
import static org.junit.jupiter.api.Assertions.*;
class LevelDbSnapshotReaderTest {
 @TempDir Path dir;
 @Test void resolvesSequencesTombstonesAndManifestLogSelection()throws Exception {
  Files.writeString(dir.resolve("CURRENT"),"MANIFEST-000001\n");Files.write(dir.resolve("MANIFEST-000001"),log(new byte[]{2,3}));
  Files.write(dir.resolve("000002.log"),log(batch(100,1,"stale","ignored")));
  var out=new ByteArrayOutputStream();out.writeBytes(log(batch(1,1,"host","old")));out.writeBytes(log(batch(2,1,"host","new")));out.writeBytes(log(batch(3,1,"deleted","value")));out.writeBytes(log(batch(4,0,"deleted","")));Files.write(dir.resolve("000003.log"),out.toByteArray());
  var records=new LevelDbSnapshotReader().read(dir);assertEquals(1,records.size());assertArrayEquals("new".getBytes(),records.getFirst().value());
 }
 @Test void checksumFailureAndTruncatedRecordsFailClosed()throws Exception {
  byte[] bytes=log("test".getBytes());bytes[8]^=1;assertThrows(IOException.class,()->LevelDbSnapshotReader.log(bytes));
  assertThrows(IOException.class,()->LevelDbSnapshotReader.log(Arrays.copyOf(log("test".getBytes()),9)));
 }
 @Test void snappyLiteralAndOverlappingCopyAndInvalidOffset()throws Exception {
  assertArrayEquals("abcabcabc".getBytes(),LevelDbSnapshotReader.snappy(new byte[]{9,8,'a','b','c',22,3,0}));
  assertThrows(IOException.class,()->LevelDbSnapshotReader.snappy(new byte[]{9,22,0,0}));
 }
 @Test void v8NestedPropertiesAndInvalidReference()throws Exception {
  byte[] value={1,(byte)255,20,(byte)255,15,'o','"',4,'p','o','r','t','I',44,'"',4,'u','s','e','r','"',3,'b','o','b','{',2};
  var map=(Map<?,?>)ChromiumValueReader.read(value);assertEquals(22L,map.get("port"));assertEquals("bob",map.get("user"));
  assertThrows(IOException.class,()->ChromiumValueReader.read(new byte[]{1,(byte)255,20,(byte)255,15,'^',3}));
 }
 @Test void readsManifestSelectedSstAndLetsNewerWalDeleteItsRow()throws Exception {
  byte[] key=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).put("host".getBytes()).putLong((5L<<8)|1).array();
  byte[] data=block(key,"from-table".getBytes());
  var table=new ByteArrayOutputStream();table.writeBytes(blockWithCrc(data));
  var handle=new ByteArrayOutputStream();varint(handle,0);varint(handle,data.length);
  byte[] index=block(key,handle.toByteArray());int indexOffset=table.size();table.writeBytes(blockWithCrc(index));
  var footer=new ByteArrayOutputStream();varint(footer,0);varint(footer,0);varint(footer,indexOffset);varint(footer,index.length);
  byte[] padded=Arrays.copyOf(footer.toByteArray(),48);ByteBuffer.wrap(padded,40,8).order(ByteOrder.LITTLE_ENDIAN).putLong(0xdb4775248b80fb57L);table.writeBytes(padded);
  Files.write(dir.resolve("000004.ldb"),table.toByteArray());
  Files.writeString(dir.resolve("CURRENT"),"MANIFEST-000001\n");
  var manifest=new ByteArrayOutputStream();manifest.writeBytes(new byte[]{2,3,7,0,4});varint(manifest,table.size());manifest.writeBytes(new byte[]{0,0});
  Files.write(dir.resolve("MANIFEST-000001"),log(manifest.toByteArray()));
  assertArrayEquals("from-table".getBytes(),new LevelDbSnapshotReader().read(dir).getFirst().value());
  Files.write(dir.resolve("000003.log"),log(batch(6,0,"host","")));
  assertTrue(new LevelDbSnapshotReader().read(dir).isEmpty());
  // Obsolete tables are not scanned and must not resurrect deleted rows.
  Files.write(dir.resolve("000002.ldb"),table.toByteArray());
  assertTrue(new LevelDbSnapshotReader().read(dir).isEmpty());
 }
 static byte[] block(byte[] key,byte[] value){var out=new ByteArrayOutputStream();varint(out,0);varint(out,key.length);varint(out,value.length);out.writeBytes(key);out.writeBytes(value);out.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(1).array());return out.toByteArray();}
 static byte[] blockWithCrc(byte[] bytes){CRC32C crc=new CRC32C();crc.update(bytes);crc.update(0);int n=(int)crc.getValue();return ByteBuffer.allocate(bytes.length+5).order(ByteOrder.LITTLE_ENDIAN).put(bytes).put((byte)0).putInt(Integer.rotateRight(n,15)+0xa282ead8).array();}
 static void varint(ByteArrayOutputStream out,long n){while(n>127){out.write((int)n|128);n>>>=7;}out.write((int)n);}
 static byte[] log(byte[] payload){CRC32C crc=new CRC32C();crc.update(1);crc.update(payload);int n=(int)crc.getValue();return ByteBuffer.allocate(7+payload.length).order(ByteOrder.LITTLE_ENDIAN).putInt(Integer.rotateRight(n,15)+0xa282ead8).putShort((short)payload.length).put((byte)1).put(payload).array();}
 static byte[] batch(long seq,int type,String key,String value)throws IOException {var out=new ByteArrayOutputStream();out.writeBytes(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(seq).putInt(1).array());out.write(type);out.write(key.length());out.writeBytes(key.getBytes());if(type==1){out.write(value.length());out.writeBytes(value.getBytes());}return out.toByteArray();}
}
