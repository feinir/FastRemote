// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.host.*;
import com.ynpe.fastremote.domain.catalog.*;
import com.ynpe.fastremote.domain.credential.*;
import com.ynpe.fastremote.domain.connection.*;
import com.ynpe.fastremote.domain.vault.*;
import com.ynpe.fastremote.ui.ThirdPartyImportResult;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Applies a selected preview without re-reading a mutable external source. */
final class ThirdPartyCatalogImport {
    private final HostCatalogRepository repository;
    private final ManagedSecretVault vault;
    private final GlobalCredentialRepository credentials;
    ThirdPartyCatalogImport(HostCatalogRepository repository,ManagedSecretVault vault,GlobalCredentialRepository credentials){this.repository=repository;this.vault=vault;this.credentials=credentials;}
    ThirdPartyImportResult apply(HostImportPlan plan,Set<String> selected,Map<String,String> usernames,boolean skipDuplicates) throws Exception {
        List<SavedHost> existing=new ArrayList<>(repository.listHosts());
        List<HostGroup> groups=new ArrayList<>(repository.listGroups());
        List<HostId> inserted=new ArrayList<>();List<GroupId> createdGroups=new ArrayList<>();List<SecretId> createdSecrets=new ArrayList<>();
        Map<String,GroupId> targetGroups=new HashMap<>();int skipped=0;
        try {
            for(var entry:plan.entries()) {
                if(!selected.contains(entry.id()))continue;
                if(!entry.importable() || !entry.jumpId().isBlank())throw new IllegalArgumentException("Selected host cannot be imported");
                String user=usernames.getOrDefault(entry.id(),entry.username()).strip();
                var profile=new ConnectionProfile(new Host(entry.host()),new Port(entry.port()),new Username(user),entry.authentication(),Optional.empty());
                if(skipDuplicates && existing.stream().anyMatch(h->h.name().equals(entry.name()) && h.profile().equals(profile))) {skipped++;continue;}
                GroupId root=targetGroups.get("");
                if(root==null){
                    root=group(plan.source().label(),Optional.empty(),groups,createdGroups);targetGroups.put("",root);
                }
                GroupId parent=root;
                if(!entry.group().isBlank()){
                    parent=targetGroups.get(entry.group());
                    if(parent==null){parent=group(entry.group(),Optional.of(root),groups,createdGroups);targetGroups.put(entry.group(),parent);}
                }
                final GroupId target=parent;
                Set<String> names=new HashSet<>();existing.stream().filter(h->h.groupId().equals(Optional.of(target))).forEach(h->names.add(h.name().toLowerCase(Locale.ROOT)));
                String name=unique(entry.name(),names);
                SavedHost host=SavedHost.create(name,Optional.of(parent),profile,existing.stream().filter(h->h.groupId().equals(Optional.of(target))).mapToInt(SavedHost::sortOrder).max().orElse(-10)+10);
                repository.saveHost(host);inserted.add(host.id());
                try(var material=plan.copySecret(entry.id())) {
                    char[] password=material.password(),privateKey=material.privateKey();
                    byte[] keyBytes=new byte[0],secretBytes=new byte[0],payload=new byte[0];
                    try {
                        if(password.length>0 || privateKey.length>0){
                            keyBytes=encode(privateKey);secretBytes=encode(password);
                            boolean key=entry.authentication()==AuthenticationType.PRIVATE_KEY;
                            if(key && keyBytes.length==0)throw new IllegalArgumentException("Private key is missing");
                            if(key)com.ynpe.fastremote.transport.ssh.PrivateKeyImportValidator.validate(keyBytes,password);
                            payload=key?ByteBuffer.allocate(12+keyBytes.length+secretBytes.length).putInt(0x46524331).putInt(keyBytes.length).put(keyBytes).putInt(secretBytes.length).put(secretBytes).array():secretBytes.clone();
                            SecretId secret=vault.put(key?SecretType.PRIVATE_KEY:SecretType.PASSWORD,payload);createdSecrets.add(secret);
                            credentials.save(new GlobalCredential(secret,name,user,entry.authentication(),CredentialScope.HOST_BOUND,Optional.of(host.id()),Optional.empty(),0));
                            host=host.withCredential(Optional.of(secret));repository.saveHost(host);
                        }
                    }finally{Arrays.fill(password,'\0');Arrays.fill(privateKey,'\0');Arrays.fill(keyBytes,(byte)0);Arrays.fill(secretBytes,(byte)0);Arrays.fill(payload,(byte)0);}
                }
                existing.add(host);
            }
            return new ThirdPartyImportResult(inserted.size(),createdGroups.size(),createdSecrets.size(),skipped);
        }catch(Exception failure){
            for(int i=inserted.size()-1;i>=0;i--)try{repository.deleteHost(inserted.get(i));}catch(Exception e){failure.addSuppressed(e);}
            for(int i=createdSecrets.size()-1;i>=0;i--)try{vault.delete(createdSecrets.get(i));}catch(Exception e){failure.addSuppressed(e);}
            for(int i=createdGroups.size()-1;i>=0;i--)try{repository.deleteGroup(createdGroups.get(i));}catch(Exception e){failure.addSuppressed(e);}
            throw failure;
        }
    }
    private GroupId group(String name,Optional<GroupId> parent,List<HostGroup> groups,List<GroupId> created)throws Exception {
        var same=groups.stream().filter(g->g.parentId().equals(parent)&&g.name().equalsIgnoreCase(name)).findFirst();
        if(same.isPresent())return same.get().id();
        HostGroup group=new HostGroup(GroupId.create(),parent,name,groups.stream().filter(g->g.parentId().equals(parent)).mapToInt(HostGroup::sortOrder).max().orElse(-10)+10,GroupColor.BLUE);
        repository.saveGroup(group);groups.add(group);created.add(group.id());return group.id();
    }
    private static String unique(String name,Set<String> existing){if(!existing.contains(name.toLowerCase(Locale.ROOT)))return name;for(int i=1;i<100_000;i++){String suffix="-"+i;String n=name.substring(0,Math.min(name.length(),255-suffix.length()))+suffix;if(!existing.contains(n.toLowerCase(Locale.ROOT)))return n;}throw new IllegalStateException("Too many duplicate host names");}
    private static byte[] encode(char[] chars){ByteBuffer b=StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));byte[] result=new byte[b.remaining()];b.get(result);if(b.hasArray())Arrays.fill(b.array(),(byte)0);return result;}
}
