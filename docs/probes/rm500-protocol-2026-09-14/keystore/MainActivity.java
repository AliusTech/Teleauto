package tech.alius.rc.keystoreprobe;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.security.keystore.*;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
 private static final String TAG="TeleautoKeyProbe";
 private TextView view;
 private String stage="start";
 void report(String s){Log.i(TAG,s);runOnUiThread(()->view.append(s+"\n"));}
 @Override public void onCreate(Bundle b){super.onCreate(b);view=new TextView(this);view.setTextSize(20);setContentView(view);final String mode=getIntent().getStringExtra("mode");new Thread(()->runTest(mode)).start();}
 void runTest(String mode){
  final String alias="teleauto.diagnostic."+System.nanoTime();
  KeyStore store=null;
  try {
   stage="software GCM control";
   byte[] input="temporary diagnostic data".getBytes("UTF-8");
   KeyGenerator software=KeyGenerator.getInstance("AES");software.init(256);SecretKey soft=software.generateKey();
   Cipher control=Cipher.getInstance("AES/GCM/NoPadding");control.init(Cipher.ENCRYPT_MODE,soft);byte[] c=control.doFinal(input);
   Cipher verify=Cipher.getInstance("AES/GCM/NoPadding");verify.init(Cipher.DECRYPT_MODE,soft,new GCMParameterSpec(128,control.getIV()));
   report("software AES-GCM roundtrip="+Arrays.equals(input,verify.doFinal(c)));
   stage="keystore load";store=KeyStore.getInstance("AndroidKeyStore");store.load(null);report("keystore load OK; mode="+mode);
   if("rsa".equals(mode)){
    stage="RSA key generation";
    KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
    gen.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).build());
    KeyPair pair=gen.generateKeyPair();report("RSA generation OK");
    stage="RSA signing";Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(pair.getPrivate());signer.update(input);byte[] sig=signer.sign();
    Signature check=Signature.getInstance("SHA256withRSA");check.initVerify(pair.getPublic());check.update(input);report("keystore RSA signature="+check.verify(sig));
   } else {
    stage="AES-128 key generation";
    KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(128).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
    SecretKey generated=gen.generateKey();report("AES-128 generation OK");
    stage="AES-128 key reload";SecretKey key=(SecretKey)store.getKey(alias,null);report("AES-128 reload OK type="+key.getClass().getName());
    stage="AES-128 cipher init";Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);report("AES-128 cipher init OK");
    stage="AES-128 encrypt";cipher.doFinal(input);report("AES-128 encrypt OK");
   }
  }catch(Throwable e){Log.e(TAG,"FAILED stage="+stage,e);report("FAILED "+stage+": "+e.getClass().getSimpleName());}
  finally {if(store!=null)try{store.deleteEntry(alias);report("temporary alias deleted");}catch(Exception e){report("temporary alias cleanup: "+e.getClass().getSimpleName());}report("DONE");}
 }
}
