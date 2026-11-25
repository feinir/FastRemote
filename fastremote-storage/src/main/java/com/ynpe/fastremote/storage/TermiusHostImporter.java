// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.host.HostImportPlan;
import com.ynpe.fastremote.domain.host.HostImportSource;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import static com.ynpe.fastremote.domain.host.HostImportPlan.Notice.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads only migration-related object stores; account tokens, history and logs are not decoded. */
public final class TermiusHostImporter {
    private static final Set<String> TABLES=Set.of("hosts","groups","ssh_configs","ssh_identities","keys","host_chains","proxies");
    public HostImportPlan read(Path source, byte[] localKey) throws IOException {
        Map<String,List<Map<String,Object>>> tables=readTables(locate(source));
        Map<String,HostImportPlan.Secret> secrets=new HashMap<>();
        List<HostImportPlan.Entry> hosts=new ArrayList<>();
        try(var cipher=new TermiusFieldCipher(localKey)) {
            for(var host:tables.getOrDefault("hosts",List.of())) {
                if(deleted(host))continue;
                String id=id(host),address=cipher.decrypt(host.get("address")),name=cipher.decrypt(host.get("label"));
                if(name.isBlank())name=address;
                List<HostImportPlan.Notice> notices=new ArrayList<>();
                var config=find(tables,"ssh_configs",host.get("ssh_config"));
                if(config.isEmpty())notices.add(UNSUPPORTED_PROTOCOL);
                List<Map<String,Object>> parents=parents(tables,host.get("group"));
                String group=groupName(parents,cipher);
                if(parents.size()>1)notices.add(GROUP_FLATTENED);
                Object identityRef=config.get("identity");
                if(identityRef==null)for(var parent:parents){var inherited=find(tables,"ssh_configs",parent.get("ssh_config"));if(inherited.get("identity")!=null){identityRef=inherited.get("identity");break;}}
                var identity=find(tables,"ssh_identities",identityRef);
                String user=cipher.decrypt(identity.get("username"));
                String password=cipher.decrypt(identity.get("password"));
                var keyRecord=find(tables,"keys",identity.get("ssh_key"));
                String privateKey=cipher.decrypt(keyRecord.get("private_key"));
                String keyPassword=cipher.decrypt(keyRecord.get("passphrase"));
                boolean key=identity.get("ssh_key")!=null || identity.get("hardware_key")!=null || Boolean.TRUE.equals(config.get("use_ssh_key"));
                if(user.isBlank())notices.add(USERNAME_REQUIRED);
                if(key && privateKey.isBlank())notices.add(KEY_REQUIRED);
                if(!key && password.isBlank())notices.add(PASSWORD_REQUIRED);
                boolean routed=nonEmpty(config.get("proxycommand")) || nonEmpty(config.get("proxy")) || nonEmpty(host.get("proxy"));
                for(var chain:tables.getOrDefault("host_chains",List.of())) if(!deleted(chain) && (idRef(chain.get("host")).equals(id) || idRef(chain.get("child")).equals(id)))routed=true;
                for(var parent:parents){var inherited=find(tables,"ssh_configs",parent.get("ssh_config"));routed|=nonEmpty(inherited.get("proxycommand")) || nonEmpty(parent.get("proxy"));}
                if(routed)notices.add(UNSUPPORTED_ROUTE);
                int port=config.get("port") instanceof Number n?n.intValue():22;
                try {new com.ynpe.fastremote.domain.connection.Host(address);new com.ynpe.fastremote.domain.connection.Port(port);if(name.isBlank()||name.length()>255||group.length()>255)throw new IllegalArgumentException();}
                catch(IllegalArgumentException invalid){notices.add(INVALID_RECORD);}
                boolean valid=!notices.contains(INVALID_RECORD)&&!notices.contains(UNSUPPORTED_PROTOCOL)&&!routed;
                if(valid && (key ? !privateKey.isEmpty() : !password.isEmpty())) {
                    char[] secret=(key?keyPassword:password).toCharArray(),material=privateKey.toCharArray();
                    try {secrets.put(id,new HostImportPlan.Secret(secret,material));}
                    finally {Arrays.fill(secret,'\0');Arrays.fill(material,'\0');}
                }
                hosts.add(new HostImportPlan.Entry(id,name,group,address,port,user,key?AuthenticationType.PRIVATE_KEY:AuthenticationType.PASSWORD,"",notices,valid));
            }
            return new HostImportPlan(HostImportSource.TERMIUS,hosts,secrets);
        }catch(Throwable failure){secrets.values().forEach(HostImportPlan.Secret::close);throw failure;}
    }
    private static boolean nonEmpty(Object value){return value!=null && (!(value instanceof String text)||!text.isBlank());}
    private static boolean deleted(Map<String,Object> row){return Boolean.TRUE.equals(row.get("deleted")) || (row.get("status") instanceof String status && status.toUpperCase(Locale.ROOT).contains("DELETE"));}
    private static String id(Map<String,Object> row){return row.get("local_id")!=null?"local:"+row.get("local_id"):"id:"+row.get("id");}
    private static String idRef(Object ref){return ref instanceof Map<?,?> m?m.get("local_id")!=null?"local:"+m.get("local_id"):"id:"+m.get("id"):String.valueOf(ref);}
    private static Map<String,Object> find(Map<String,List<Map<String,Object>>> tables,String table,Object ref){
        if(ref==null)return Map.of();String id=idRef(ref);
        return tables.getOrDefault(table,List.of()).stream().filter(r->!deleted(r)&&(id(r).equals(id) || (ref instanceof Map<?,?> m && m.get("id")!=null && Objects.equals(m.get("id"),r.get("id"))))).findFirst().orElse(Map.of());
    }
    private static List<Map<String,Object>> parents(Map<String,List<Map<String,Object>>> tables,Object ref) throws IOException {
        List<Map<String,Object>> parents=new ArrayList<>();Set<String> seen=new HashSet<>();
        while(ref!=null){var group=find(tables,"groups",ref);if(group.isEmpty())throw new IOException("Termius group reference is missing");if(!seen.add(id(group))||parents.size()>32)throw new IOException("Termius group cycle");parents.add(group);ref=group.getOrDefault("parent_group",group.get("parent"));}
        return parents;
    }
    private static String groupName(List<Map<String,Object>> parents,TermiusFieldCipher cipher)throws IOException {
        List<String> names=new ArrayList<>();for(int i=parents.size()-1;i>=0;i--)names.add(cipher.decrypt(parents.get(i).get("label")));return String.join(" / ",names);
    }
    static Path locate(Path path)throws IOException {
        for(Path candidate:List.of(path,path.resolve("file__0.indexeddb.leveldb"),path.resolve("IndexedDB/file__0.indexeddb.leveldb")))
            if(Files.isRegularFile(candidate.resolve("CURRENT")))return candidate;
        throw new IOException("Select the Termius data folder containing IndexedDB");
    }
    @SuppressWarnings("unchecked")
    static Map<String,List<Map<String,Object>>> readTables(Path path)throws IOException {
        var records=new LevelDbSnapshotReader().read(path);Map<Long,String> databaseNames=new HashMap<>();
        for(var row:records){byte[] key=row.key();if(key.length<=5||key[0]!=0||key[1]!=0||key[2]!=0||key[3]!=0||(key[4]&255)!=201)continue;
            var c=new LevelDbSnapshotReader.Cursor(Arrays.copyOfRange(key,5,key.length));
            int origin=c.length();c.take(Math.multiplyExact(origin,2));int n=c.length();String name=new String(c.take(Math.multiplyExact(n,2)),StandardCharsets.UTF_16BE);
            if(TABLES.contains(name))databaseNames.put(new LevelDbSnapshotReader.Cursor(row.value()).var(),name);
        }
        Map<String,List<Map<String,Object>>> result=new HashMap<>();
        for(var row:records){var c=new LevelDbSnapshotReader.Cursor(row.key());int prefix=c.u8();
            long db=le(c,((prefix>>>5)&7)+1),store=le(c,((prefix>>>2)&7)+1),index=le(c,(prefix&3)+1);
            String name=databaseNames.get(db);if(name==null||store==0||index!=1)continue;
            Object decoded=ChromiumValueReader.read(row.value());
            if(!(decoded instanceof Map<?,?> map))throw LevelDbSnapshotReader.invalid();
            Map<String,Object> object = (Map<String,Object>)map;
            if (!object.containsKey("local_id") && c.remaining() > 0 && c.u8() == 3) {
                double primary = Double.longBitsToDouble(c.longLE());
                if (Double.isFinite(primary) && primary == Math.rint(primary)) object.put("local_id", (long)primary);
            }
            result.computeIfAbsent(name,k->new ArrayList<>()).add(object);
        }
        return result;
    }
    private static long le(LevelDbSnapshotReader.Cursor c,int length)throws IOException {long value=0;for(int i=0;i<length;i++)value|=(long)c.u8()<<(i*8);return value;}
}
