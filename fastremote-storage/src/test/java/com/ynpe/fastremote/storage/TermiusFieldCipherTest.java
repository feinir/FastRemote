// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.IOException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.*;

class TermiusFieldCipherTest {
 @Test void authenticatesFieldsAndRejectsWrongKeyTamperingAndFutureVersions()throws Exception {
  byte[] key=new byte[32],nonce=new byte[24],polyKey=new byte[32];
  byte[] plain="synthetic credential".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  byte[] envelope=new byte[42+plain.length];envelope[0]=4;envelope[1]=1;
  var stream=new XSalsa20Engine();stream.init(true,new ParametersWithIV(new KeyParameter(key),nonce));
  stream.processBytes(new byte[32],0,32,polyKey,0);stream.processBytes(plain,0,plain.length,envelope,42);
  var mac=new Poly1305();mac.init(new KeyParameter(polyKey));mac.update(envelope,42,plain.length);mac.doFinal(envelope,26);
  try(var cipher=new TermiusFieldCipher(key)) {
   assertEquals("synthetic credential",cipher.decrypt(Base64.getEncoder().encodeToString(envelope)));
   envelope[42]^=1;assertThrows(IOException.class,()->cipher.decrypt(Base64.getEncoder().encodeToString(envelope)));envelope[42]^=1;
   envelope[1]=2;assertThrows(IOException.class,()->cipher.decrypt(Base64.getEncoder().encodeToString(envelope)));envelope[1]=1;
   assertThrows(IOException.class,()->cipher.decrypt(java.util.Map.of("unexpected",true)));
  }
  key[0]=1;try(var wrong=new TermiusFieldCipher(key)){assertThrows(IOException.class,()->wrong.decrypt(Base64.getEncoder().encodeToString(envelope)));}
 }
}
