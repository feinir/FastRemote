// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.host.*;
import com.ynpe.fastremote.domain.connection.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Connection-prefix reader for the 9.66 profile layout, shared by .tlp and .bscp.
 * Versions/layouts outside the verified family fail explicitly instead of scanning arbitrary strings. */
public final class BitviseProfileImporter {
    public HostImportPlan read(Path source)throws IOException {
        List<Path> files;
        if(Files.isDirectory(source))try(var stream=Files.walk(source,8)){files=stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).filter(p->p.toString().toLowerCase(Locale.ROOT).matches(".*\\.(tlp|bscp)$")).sorted().limit(1025).toList();}
        else files=List.of(source);
        if(files.size()>1024)throw new IOException("Too many Bitvise profiles");
        List<HostImportPlan.Entry> entries=new ArrayList<>();long total=0;
        for(Path file:files){total+=Files.size(file);if(total>16*1024*1024)throw new IOException("Bitvise profiles exceed size limit");
            try(var in=new DataInputStream(Files.newInputStream(file))){entries.add(parse(in,file.getFileName().toString(),file.toString()));}
        }
        return new HostImportPlan(HostImportSource.BITVISE,entries,Map.of());
    }
    static HostImportPlan.Entry parse(DataInputStream in,String filename,String id)throws IOException {
        String version=string(in);
        if(!version.equals("Tunnelier 9.66"))throw new IOException("Unsupported Bitvise profile version");
        if(in.readInt()!=7 || in.readInt()!=2)throw new IOException("Unsupported Bitvise profile layout");
        int[] lengths=new int[6];long total=0;
        for(int i=0;i<lengths.length;i++) {
            lengths[i]=in.readInt();
            if(lengths[i]<0 || lengths[i]>16*1024*1024)throw new IOException("Invalid Bitvise section length");
            total+=lengths[i];
        }
        if(total>16*1024*1024 || lengths[0]<40)throw new IOException("Invalid Bitvise section lengths");
        if(in.readUnsignedByte()!=2 || in.readUnsignedByte()!=0)throw new IOException("Unsupported Bitvise connection type");
        byte[] sections=in.readNBytes((int)total);
        if(sections.length!=total || in.read()!=-1)throw new IOException("Truncated or inconsistent Bitvise profile");
        // Only the connection-settings section is parsed; credentials in protected key sections
        // require an explicit portable key selection in the import preview.
        in=new DataInputStream(new ByteArrayInputStream(sections,0,lengths[0]));
        String host=string(in);int port=in.readInt();String spn=string(in);
        int kerberos=in.readUnsignedByte(),delegation=in.readUnsignedByte(),obfuscation=in.readUnsignedByte();
        String keyword=string(in),user=string(in);int auth=in.readUnsignedByte();
        List<HostImportPlan.Notice> notices=new ArrayList<>();
        // Other profile sections are deliberately not applied. The preview requires explicit acknowledgment.
        notices.add(HostImportPlan.Notice.SETTINGS_OMITTED);
        if(auth==1)notices.add(HostImportPlan.Notice.PASSWORD_REQUIRED);else notices.add(HostImportPlan.Notice.KEY_REQUIRED);
        if(user.isBlank())notices.add(HostImportPlan.Notice.USERNAME_REQUIRED);
        boolean valid=(auth==1||auth==2)&&kerberos==0&&delegation==0&&obfuscation==0&&spn.isEmpty()&&keyword.isEmpty();
        if(!valid)notices.add(HostImportPlan.Notice.UNSUPPORTED_ROUTE);
        try{new Host(host);new Port(port);}catch(IllegalArgumentException invalid){valid=false;notices.add(HostImportPlan.Notice.INVALID_RECORD);}
        return new HostImportPlan.Entry(id,filename.replaceFirst("(?i)\\.(tlp|bscp)$",""),"",host,port,user,
                auth==1?AuthenticationType.PASSWORD:AuthenticationType.PRIVATE_KEY,"",notices,valid);
    }
    private static String string(DataInputStream in)throws IOException {
        int n=in.readInt();if(n<0||n>65536)throw new IOException("Invalid Bitvise field length");
        byte[] value=in.readNBytes(n);if(value.length!=n)throw new EOFException("Truncated Bitvise profile");
        if(n>0 && value[n-1]==0)n--;
        String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(value,0,n)).toString();
        if(text.indexOf('\0')>=0)throw new IOException("Invalid Bitvise text field");return text;
    }
}
