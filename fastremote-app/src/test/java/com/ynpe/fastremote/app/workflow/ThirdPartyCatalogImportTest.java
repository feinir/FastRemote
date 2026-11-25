// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.host.*;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ThirdPartyCatalogImportTest {
 @TempDir Path dir;
 @Test void persistsEncryptedCredentialsReopensAndSkipsDuplicates()throws Exception {
  Path yaml=dir.resolve("global.yaml"),db=dir.resolve("catalog.db");
  var store=new YamlGlobalConfigStore(yaml);var vault=new YamlSecretVault(store);vault.create("test-master-123".toCharArray());
  var metadata=new YamlGlobalCredentialRepository(store);var database=new FastRemoteDatabase(db);var repository=new SqliteHostCatalogRepository(database);
  var entry=entry("one","deploy");
  try(var plan=new HostImportPlan(HostImportSource.TERMIUS,List.of(entry),Map.of("one",new HostImportPlan.Secret("import-test-secret".toCharArray(),new char[0])));
      var workflow=new SqliteHostCatalogWorkflow(database,repository,vault,metadata)) {
   var result=workflow.importThirdParty(plan,Set.of("one"),Map.of(),true).toCompletableFuture().get(5,TimeUnit.SECONDS);
   assertEquals(1,result.hosts());assertEquals(1,result.credentials());assertEquals(2,result.groups());
   var again=workflow.importThirdParty(plan,Set.of("one"),Map.of(),true).toCompletableFuture().get(5,TimeUnit.SECONDS);
   assertEquals(0,again.hosts());assertEquals(1,again.skipped());assertEquals(1,repository.listHosts().size());
   assertFalse(Files.readString(yaml).contains("import-test-secret"));
  }
  var reopenedStore=new YamlGlobalConfigStore(yaml);var reopenedVault=new YamlSecretVault(reopenedStore);reopenedVault.unlock("test-master-123".toCharArray());
  var reopenedDb=new FastRemoteDatabase(db);var reopenedRepository=new SqliteHostCatalogRepository(reopenedDb);
  try(var workflow=new SqliteHostCatalogWorkflow(reopenedDb,reopenedRepository,reopenedVault,new YamlGlobalCredentialRepository(reopenedStore))){
   var host=reopenedRepository.listHosts().getFirst();assertEquals("deploy",host.profile().username().value());
   try(var resolved=workflow.loadCredential(host).toCompletableFuture().get(5,TimeUnit.SECONDS)){assertArrayEquals("import-test-secret".toCharArray(),resolved.copySecret());}
  }
 }
 @Test void rollsBackEarlierHostsGroupsAndSecretsWhenLaterHostFails()throws Exception {
  var store=new YamlGlobalConfigStore(dir.resolve("global.yaml"));var vault=new YamlSecretVault(store);vault.create("test-master-123".toCharArray());
  var metadata=new YamlGlobalCredentialRepository(store);var database=new FastRemoteDatabase(dir.resolve("catalog.db"));var repository=new SqliteHostCatalogRepository(database);
  try(var workflow=new SqliteHostCatalogWorkflow(database,repository,vault,metadata);
      var plan=new HostImportPlan(HostImportSource.TERMIUS,List.of(entry("one","deploy"),entry("two","")),Map.of("one",new HostImportPlan.Secret("test-secret".toCharArray(),new char[0])))){
   assertThrows(java.util.concurrent.ExecutionException.class,()->workflow.importThirdParty(plan,Set.of("one","two"),Map.of(),true).toCompletableFuture().get(5,TimeUnit.SECONDS));
   assertTrue(repository.listHosts().isEmpty());assertTrue(repository.listGroups().isEmpty());assertTrue(metadata.list().isEmpty());
   var imported=workflow.importThirdParty(plan,Set.of("two"),Map.of("two","filled"),true).toCompletableFuture().get(5,TimeUnit.SECONDS);
   assertEquals(1,imported.hosts());assertEquals("filled",repository.listHosts().getFirst().profile().username().value());
  }
 }
 @Test void duplicateKeepBothRenamesAndNeverOverwritesExisting()throws Exception {
  var store=new YamlGlobalConfigStore(dir.resolve("global.yaml"));var vault=new YamlSecretVault(store);
  var metadata=new YamlGlobalCredentialRepository(store);var db=new FastRemoteDatabase(dir.resolve("catalog.db"));var repository=new SqliteHostCatalogRepository(db);
  try(var workflow=new SqliteHostCatalogWorkflow(db,repository,vault,metadata);var plan=new HostImportPlan(HostImportSource.XSHELL,List.of(entry("one","deploy")),Map.of())){
   workflow.importThirdParty(plan,Set.of("one"),Map.of(),false).toCompletableFuture().get(5,TimeUnit.SECONDS);
   var original=repository.listHosts().getFirst();
   workflow.importThirdParty(plan,Set.of("one"),Map.of(),false).toCompletableFuture().get(5,TimeUnit.SECONDS);
   assertEquals(2,repository.listHosts().size());assertTrue(repository.listHosts().contains(original));assertTrue(repository.listHosts().stream().anyMatch(h->h.name().equals("Host one-1")));
  }
 }
 @Test void attachesPortablePrivateKeyAndReloadsItFromEncryptedVault()throws Exception {
  var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
  byte[] der=generator.generateKeyPair().getPrivate().getEncoded();
  Path keyFile=dir.resolve("generated.pem");
  Files.writeString(keyFile,"-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(der)+"\n-----END PRIVATE KEY-----\n");
  var store=new YamlGlobalConfigStore(dir.resolve("global.yaml"));var vault=new YamlSecretVault(store);vault.create("test-master-123".toCharArray());
  var db=new FastRemoteDatabase(dir.resolve("hosts.db"));var repository=new SqliteHostCatalogRepository(db);
  var keyEntry=new HostImportPlan.Entry("key","Key host","","example.test",22,"deploy",AuthenticationType.PRIVATE_KEY,"",List.of(HostImportPlan.Notice.KEY_REQUIRED),true);
  try(var workflow=new SqliteHostCatalogWorkflow(db,repository,vault,new YamlGlobalCredentialRepository(store));
      var plan=new HostImportPlan(HostImportSource.BITVISE,List.of(keyEntry),Map.of())) {
   workflow.attachImportKey(plan,"key",keyFile,new char[0]).toCompletableFuture().get(5,TimeUnit.SECONDS);
   assertFalse(plan.entries().getFirst().notices().contains(HostImportPlan.Notice.KEY_REQUIRED));
   workflow.importThirdParty(plan,Set.of("key"),Map.of(),true).toCompletableFuture().get(5,TimeUnit.SECONDS);
   try(var restored=workflow.loadCredential(repository.listHosts().getFirst()).toCompletableFuture().get(5,TimeUnit.SECONDS)) {
    byte[] encoded=restored.copyPrivateKey();
    try {com.ynpe.fastremote.transport.ssh.PrivateKeyImportValidator.validate(encoded,new char[0]);}
    finally{Arrays.fill(encoded,(byte)0);}
   }
   assertFalse(Files.readString(dir.resolve("global.yaml")).contains("BEGIN PRIVATE KEY"));
   Files.writeString(keyFile,"not a key");
   assertThrows(java.util.concurrent.ExecutionException.class,()->workflow.attachImportKey(plan,"key",keyFile,new char[0]).toCompletableFuture().get(5,TimeUnit.SECONDS));
   assertTrue(plan.hasSecrets(),"Failed replacement preserves the previous valid key");
  }finally{Arrays.fill(der,(byte)0);}
 }
 private static HostImportPlan.Entry entry(String id,String user){return new HostImportPlan.Entry(id,"Host "+id,"group / subgroup","example.test",2222,user,AuthenticationType.PASSWORD,"",List.of(),true);}
}
