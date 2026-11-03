package tech.alius.rc.license.internal;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Issuer-controlled SDK version window: inclusive from, exclusive until (* = no upper bound). */
public final class TeleautoKeyPolicy {
    public static int compare(String a,String b){int[] x=version(a),y=version(b);for(int i=0;i<3;i++){int c=Integer.compare(x[i],y[i]);if(c!=0)return c;}return 0;}
    private static int[] version(String s){if(!s.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))throw new IllegalArgumentException("SDK version must be X.Y.Z");String[] p=s.split("\\.");return new int[]{Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])};}
    public static boolean active(String from,String until,String sdk){version(from);version(sdk);if(!until.equals("*")&&compare(from,until)>=0)throw new IllegalArgumentException("Invalid SDK key window");return compare(sdk,from)>=0&&(until.equals("*")||compare(sdk,until)<0);}
    public static Map<String,PublicKey> resolve(Map<String,String> records,String sdk,TeleautoLicenseVerifier.Codec codec)throws GeneralSecurityException{
        Map<String,PublicKey> keys=new HashMap<>();
        for(Map.Entry<String,String> e:records.entrySet()){
            if(!e.getKey().matches("[A-Za-z0-9_.-]{1,64}"))throw new IllegalArgumentException("Invalid key ID");String[] p=e.getValue().split("\\|",-1);if(p.length!=3)throw new IllegalArgumentException("Invalid public-key record");
            PublicKey key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(codec.decode(p[2])));if(!TeleautoLicenseVerifier.p256(key))throw new IllegalArgumentException("Only P-256 keys are supported");
            if(active(p[0],p[1],sdk))keys.put(e.getKey(),key);
        }
        return Collections.unmodifiableMap(keys);
    }
}
