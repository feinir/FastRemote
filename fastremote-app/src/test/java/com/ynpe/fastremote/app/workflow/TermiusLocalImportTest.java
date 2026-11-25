// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.storage.TermiusHostImporter;
import com.ynpe.fastremote.domain.host.HostImportSource;
import java.util.Arrays;
class TermiusLocalImportTest {
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
 @Test @EnabledIfEnvironmentVariable(named="FASTREMOTE_TERMIUS_PROBE",matches="true")
 void readsInstalledTermiusWithoutLoggingSecrets() throws Exception {
  byte[] key=TermiusLocalKey.read();
  try(var plan=new TermiusHostImporter().read(ThirdPartyLocalSources.defaultPath(HostImportSource.TERMIUS),key)) {
   assertFalse(plan.entries().isEmpty());
   int privateKeys=0;
   for(var host:plan.entries()) {
    assertFalse(host.host().startsWith("BAE"));
    assertTrue(host.port()>0 && host.port()<=65535);
    try(var secret=plan.copySecret(host.id())) {
     char[] material=secret.privateKey();char[] pass=secret.password();
     try {
      if(material.length>0) {
       byte[] encoded=new String(material).getBytes(java.nio.charset.StandardCharsets.UTF_8);
       try {com.ynpe.fastremote.transport.ssh.PrivateKeyImportValidator.validate(encoded,pass);
        if("true".equals(System.getenv("FASTREMOTE_IMPORT_LOGIN"))) {
         var store=new com.ynpe.fastremote.storage.YamlGlobalConfigStore(directory.resolve("global.yaml"));
         var vault=new com.ynpe.fastremote.storage.YamlSecretVault(store);
         char[] master=java.util.UUID.randomUUID().toString().toCharArray();
         try{vault.create(master);}finally{Arrays.fill(master,'\0');}
         var database=new com.ynpe.fastremote.storage.FastRemoteDatabase(directory.resolve("hosts.db"));
         var repository=new com.ynpe.fastremote.storage.SqliteHostCatalogRepository(database);
         try(var workflow=new SqliteHostCatalogWorkflow(database,repository,vault,new com.ynpe.fastremote.storage.YamlGlobalCredentialRepository(store))) {
          var imported=workflow.importThirdParty(plan,java.util.Set.of(host.id()),java.util.Map.of(),true).toCompletableFuture().get(10,java.util.concurrent.TimeUnit.SECONDS);
          assertEquals(1,imported.credentials());
          var saved=repository.listHosts().getFirst();
          var known=new com.ynpe.fastremote.storage.FileKnownHostRepository(java.nio.file.Path.of("../.fastremote-dev/known_hosts"));
          try(var connector=new com.ynpe.fastremote.transport.ssh.MinaSshSessionConnector(known);
              var resolved=workflow.loadCredential(saved).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS)) {
           byte[] storedKey=resolved.copyPrivateKey();char[] storedPass=resolved.copySecret();
           try(var credentials=new com.ynpe.fastremote.domain.connection.PrivateKeyCredentials(storedPass,storedKey)) {
            connector.testConnection(saved.profile(),credentials,ignored->com.ynpe.fastremote.domain.security.HostKeyDecision.REJECT,ignored->{},com.ynpe.fastremote.domain.settings.ConnectionSettings.defaults());
           }finally{Arrays.fill(storedKey,(byte)0);Arrays.fill(storedPass,'\0');}
          }
         }
         System.out.println("Termius actual SSH key authentication passed");
        }
        privateKeys++;}
       finally {Arrays.fill(encoded,(byte)0);}
      }
     }finally {Arrays.fill(material,'\0');Arrays.fill(pass,'\0');}
    }
   }
   assertTrue(privateKeys>0,"Expected the saved private-key session");
   System.out.println("Validated private keys: "+privateKeys);
   System.out.println("Local Termius: "+plan.entries().size()+" host records; encrypted credentials present: "+plan.hasSecrets());
  }finally{Arrays.fill(key,(byte)0);}
 }
}
