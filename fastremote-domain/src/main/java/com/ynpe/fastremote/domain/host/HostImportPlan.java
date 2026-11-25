// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.host;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** A read-only source snapshot. Its owner must close it on import, cancellation and failure. */
public final class HostImportPlan implements AutoCloseable {
    public enum Notice { PASSWORD_REQUIRED, KEY_REQUIRED, USERNAME_REQUIRED, UNSUPPORTED_ROUTE,
        UNSUPPORTED_PROTOCOL, INVALID_RECORD, GROUP_FLATTENED, SETTINGS_OMITTED }

    public record Entry(String id, String name, String group, String host, int port,
                        String username, AuthenticationType authentication, String jumpId,
                        List<Notice> notices, boolean importable, String keyReference) {
        public Entry(String id,String name,String group,String host,int port,String username,AuthenticationType authentication,String jumpId,List<Notice> notices,boolean importable) { this(id,name,group,host,port,username,authentication,jumpId,notices,importable,""); }
        public Entry { notices = List.copyOf(notices); }
    }

    /** Does not expose secret material through toString or equality. */
    public static final class Secret implements AutoCloseable {
        private final char[] password;
        private final char[] privateKey;
        public Secret(char[] password, char[] privateKey) {
            this.password = password.clone();
            this.privateKey = privateKey.clone();
        }
        public char[] password() { return password.clone(); }
        public char[] privateKey() { return privateKey.clone(); }
        @Override public void close() { Arrays.fill(password, '\0'); Arrays.fill(privateKey, '\0'); }
    }

    private final HostImportSource source;
    private final List<Entry> entries;
    private final java.util.Map<String, Secret> secrets;
    private boolean closed;

    public HostImportPlan(HostImportSource source, List<Entry> entries, java.util.Map<String, Secret> secrets) {
        this.source = Objects.requireNonNull(source);
        this.entries = new java.util.ArrayList<>(entries);
        this.secrets = new java.util.HashMap<>(secrets);
    }
    public HostImportSource source() { return source; }
    public synchronized List<Entry> entries() { return List.copyOf(entries); }
    public synchronized Secret copySecret(String id) {
        if (closed) throw new IllegalStateException("Import preview is closed");
        Secret secret = secrets.get(id);
        if (secret == null) return new Secret(new char[0], new char[0]);
        char[] password = secret.password();
        char[] key = secret.privateKey();
        try { return new Secret(password, key); }
        finally { Arrays.fill(password, '\0'); Arrays.fill(key, '\0'); }
    }
    public synchronized void attachSecret(String id, Secret material) {
        if (closed) throw new IllegalStateException("Import preview is closed");
        int index = -1;
        for (int i=0;i<entries.size();i++) if(entries.get(i).id().equals(id)){index=i;break;}
        if(index<0)throw new IllegalArgumentException("Unknown import host");
        char[] password=material.password(),key=material.privateKey();
        try {
            Secret previous=secrets.put(id,new Secret(password,key));
            if(previous!=null)previous.close();
            Entry e=entries.get(index);
            List<Notice> notices=e.notices().stream().filter(n->n!=Notice.KEY_REQUIRED&&n!=Notice.PASSWORD_REQUIRED).toList();
            entries.set(index,new Entry(e.id(),e.name(),e.group(),e.host(),e.port(),e.username(),e.authentication(),e.jumpId(),notices,e.importable(),e.keyReference()));
        } finally {Arrays.fill(password,'\0');Arrays.fill(key,'\0');}
    }
    public synchronized boolean hasSecrets() { return !secrets.isEmpty(); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        secrets.values().forEach(Secret::close);
    }
}
