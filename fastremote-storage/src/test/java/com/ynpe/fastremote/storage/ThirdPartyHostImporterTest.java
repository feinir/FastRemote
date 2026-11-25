// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.host.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ThirdPartyHostImporterTest {
 @TempDir Path dir;
 @Test void importsMobaBookmarksAndRejectsOtherProtocolsAndProxies()throws Exception {
  Path p=dir.resolve("MobaXterm.ini");
  String[] fields=new String[39];Arrays.fill(fields,"");fields[0]="0";fields[1]="example.test";fields[2]="2202";fields[3]="deploy";
  String direct=String.join("%",fields);fields[20]="proxy.example";
  Files.writeString(p,"[Bookmarks]\nSubRep=生产/应用/服务\nImgNum=42\nMy Host=#109#"+direct+"#\nProxy=#109#"+String.join("%",fields)+"#\nWSL=#149#14%2%\n");
  try(var plan=new ThirdPartyHostImporter().read(HostImportSource.MOBAXTERM,p)) {
   assertEquals(3,plan.entries().size());var host=plan.entries().getFirst();
   assertEquals("My Host",host.name());assertEquals(2202,host.port());assertEquals("deploy",host.username());assertTrue(host.importable());
   assertFalse(plan.entries().get(1).importable());assertFalse(plan.entries().get(2).importable());
   assertEquals("生产/应用/服务",host.group());assertFalse(plan.hasSecrets());
  }
 }
 @Test void preservesPuttyCaseUnicodePlusDefaultSettingsAndMissingUsername()throws Exception {
  Path p=dir.resolve("putty.reg");Files.writeString(p,"\ufeffWindows Registry Editor Version 5.00\n\n[HKEY_CURRENT_USER\\Software\\SimonTatham\\PuTTY\\Sessions\\My%20Host%2B%E4%B8%AD]\n\"HostName\"=\"example.test\"\n\"PortNumber\"=dword:000008ae\n\"UserName\"=\"\"\n\"Protocol\"=\"ssh\"\n\n[HKEY_CURRENT_USER\\Software\\SimonTatham\\PuTTY\\Sessions\\Default%20Settings]\n\"HostName\"=\"default.example\"\n",StandardCharsets.UTF_16LE);
  try(var plan=new ThirdPartyHostImporter().read(HostImportSource.PUTTY,p)) {
   assertEquals(2,plan.entries().size());assertEquals("My Host+中",plan.entries().getFirst().name());assertEquals(2222,plan.entries().getFirst().port());
   assertTrue(plan.entries().getFirst().notices().contains(HostImportPlan.Notice.USERNAME_REQUIRED));assertEquals("Default Settings",plan.entries().getLast().name());
  }
 }
 @Test void importsXShellFoldersUtf16AndXftpSftpButNotFtp()throws Exception {
  Path sub=Files.createDirectories(dir.resolve("组/子组"));
  Files.writeString(sub.resolve("主机.xsh"),"\ufeff[CONNECTION]\nHost=example.test\nPort=22\nProtocol=SSH\n[CONNECTION:AUTHENTICATION]\nUserName=deploy\nPassword=opaque\n",StandardCharsets.UTF_16LE);
  Files.writeString(dir.resolve("sftp.xfp"),"[Connection]\nHost=files.example\nPort=2222\nProtocol=SFTP\n[Authentication]\nUserName=files\n");
  Files.writeString(dir.resolve("ftp.xfp"),"[Connection]\nHost=files.example\nPort=21\nProtocol=FTP\n");
  try(var plan=new ThirdPartyHostImporter().read(HostImportSource.XSHELL,dir)) {
   assertEquals(3,plan.entries().size());assertEquals(2,plan.entries().stream().filter(HostImportPlan.Entry::importable).count());assertFalse(plan.hasSecrets());
   assertTrue(plan.entries().stream().anyMatch(e->e.group().equals("组/子组")&&e.username().equals("deploy")));
  }
 }
 @Test void readsArchiveWithoutExtractingAndRejectsTraversal()throws Exception {
  Path zip=dir.resolve("sessions.xts");
  try(var out=new ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new ZipEntry("group/server.xsh"));out.write("[CONNECTION]\nHost=example.test\nProtocol=SSH\n".getBytes(StandardCharsets.UTF_8));out.closeEntry();}
  try(var plan=new ThirdPartyHostImporter().read(HostImportSource.XSHELL,zip)){assertEquals(1,plan.entries().size());assertEquals("group",plan.entries().getFirst().group());}
  try(var out=new ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new ZipEntry("../server.xsh"));out.write(new byte[]{1});out.closeEntry();}
  assertThrows(IOException.class,()->new ThirdPartyHostImporter().read(HostImportSource.XSHELL,zip));assertFalse(Files.exists(dir.resolve("server.xsh")));
 }
 @Test void importsBothBitviseExtensionsAndRejectsUnknownLayouts()throws Exception {
  for(String ext:List.of("tlp","bscp")){
   Path p=dir.resolve("test."+ext);Files.write(p,bitvise("Tunnelier 9.66"));
   try(var plan=new BitviseProfileImporter().read(p)){assertEquals("example.test",plan.entries().getFirst().host());assertEquals("deploy",plan.entries().getFirst().username());assertTrue(plan.entries().getFirst().importable());}
  }
  Path bad=dir.resolve("bad.tlp");Files.write(bad,bitvise("Tunnelier 99.0"));assertThrows(IOException.class,()->new BitviseProfileImporter().read(bad));
  Files.write(bad,new byte[]{0,0,0,14,1});assertThrows(IOException.class,()->new BitviseProfileImporter().read(bad));
 }
 static byte[] bitvise(String version)throws IOException {
  var body=new ByteArrayOutputStream();try(var out=new DataOutputStream(body)){
   string(out,"example.test");out.writeInt(22);string(out,"");out.write(new byte[3]);string(out,"");string(out,"deploy");out.writeByte(1);
  }
  var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
   string(out,version);for(int n:new int[]{7,2,body.size(),8,31,4,4,0})out.writeInt(n);out.writeByte(2);out.writeByte(0);out.write(body.toByteArray());out.write(new byte[47]);
  }return bytes.toByteArray();
 }
 static void string(DataOutputStream out,String s)throws IOException {byte[] b=s.getBytes(StandardCharsets.UTF_8);out.writeInt(b.length+1);out.write(b);out.writeByte(0);}
 @Test void rejectsInvalidPortAndKeepsSecretsOutOfObjectRepresentation()throws Exception {
  Path p=dir.resolve("bad.xsh");Files.writeString(p,"[CONNECTION]\nHost=example.test\nPort=70000\nProtocol=SSH\n");
  try(var plan=new ThirdPartyHostImporter().read(HostImportSource.XSHELL,p)){assertFalse(plan.entries().getFirst().importable());}
  var s=new HostImportPlan.Secret("private-test".toCharArray(),new char[0]);assertFalse(s.toString().contains("private-test"));s.close();assertEquals(0,s.password()[0]);
 }
}
