// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Reads only Termius/localKey after the user selects local Termius import. Never logs process output. */
final class TermiusLocalKey {
    static byte[] read() throws IOException, InterruptedException {
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(os.contains("win")) {
            byte[] raw=WindowsTermiusLocalKey.read();
            try{return decode(raw);}finally{Arrays.fill(raw,(byte)0);}
        }
        List<String> command;
        if(os.contains("mac"))command=List.of("/usr/bin/security","find-generic-password","-s","Termius","-a","localKey","-w");
        else if(os.contains("linux"))command=List.of("secret-tool","lookup","service","Termius","account","localKey");
        else throw new IOException("Termius local key access is not available on this platform");
        Process process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        byte[] raw=new byte[0];
        try {
            if(!process.waitFor(45,TimeUnit.SECONDS))throw new IOException("Termius local key access timed out");
            raw=process.getInputStream().readNBytes(4097);
            if(process.exitValue()!=0||raw.length>4096)throw new IOException("Allow access to the Termius local key and retry");
            return decode(raw);
        }finally{process.destroyForcibly();Arrays.fill(raw,(byte)0);process.getInputStream().close();}
    }
    private static byte[] decode(byte[] raw)throws IOException {
        byte[] result;
        try {result=Base64.getMimeDecoder().decode(raw);}catch(IllegalArgumentException bad){throw new IOException("Invalid Termius local key");}
        if(result.length!=32){Arrays.fill(result,(byte)0);throw new IOException("Invalid Termius local key");}
        return result;
    }
}
