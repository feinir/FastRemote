// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.host.*;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.nio.file.Path;
import java.util.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class ThirdPartyLocalSources {
    static Path defaultPath(HostImportSource source) {
        Path home=Path.of(System.getProperty("user.home"));
        String os=System.getProperty("os.name","");
        return switch(source){
            case TERMIUS -> os.startsWith("Mac")?home.resolve("Library/Application Support/Termius"):
                    os.startsWith("Windows")?Path.of(System.getenv().getOrDefault("APPDATA",home.toString()),"Termius"):home.resolve(".config/Termius");
            case PUTTY -> home.resolve(".putty/sessions");
            default -> home;
        };
    }
    static HostImportPlan readPutty() {
        String root="Software\\SimonTatham\\PuTTY\\Sessions";
        if(!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER,root))return new HostImportPlan(HostImportSource.PUTTY,List.of(),Map.of());
        List<HostImportPlan.Entry> entries=new ArrayList<>();
        for(String name:Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER,root)){
            String path=root+"\\"+name;
            String host=string(path,"HostName");if(host.isBlank())continue;
            String username=string(path,"UserName"),protocol=string(path,"Protocol"),key=string(path,"PublicKeyFile");
            int port=number(path,"PortNumber",22),proxy=number(path,"ProxyMethod",0);
            List<HostImportPlan.Notice> notices=new ArrayList<>();
            if(!protocol.equalsIgnoreCase("ssh"))notices.add(HostImportPlan.Notice.UNSUPPORTED_PROTOCOL);
            if(proxy!=0)notices.add(HostImportPlan.Notice.UNSUPPORTED_ROUTE);
            if(username.isBlank())notices.add(HostImportPlan.Notice.USERNAME_REQUIRED);
            notices.add(key.isBlank()?HostImportPlan.Notice.PASSWORD_REQUIRED:HostImportPlan.Notice.KEY_REQUIRED);
            entries.add(new HostImportPlan.Entry(name,URLDecoder.decode(name.replace("+","%2B"),StandardCharsets.UTF_8),"",host,port,username,
                    key.isBlank()?AuthenticationType.PASSWORD:AuthenticationType.PRIVATE_KEY,"",notices,proxy==0&&protocol.equalsIgnoreCase("ssh")));
            if(entries.size()>20_000)throw new IllegalArgumentException("Too many PuTTY sessions");
        }
        return new HostImportPlan(HostImportSource.PUTTY,entries,Map.of());
    }
    private static String string(String path,String name){return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER,path,name)?Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER,path,name):"";}
    private static int number(String path,String name,int fallback){return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER,path,name)?Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER,path,name):fallback;}
}
