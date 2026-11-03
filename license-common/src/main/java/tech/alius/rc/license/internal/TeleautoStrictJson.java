package tech.alius.rc.license.internal;

import java.util.*;

/** Small bounded JSON reader for the fixed license schema. Rejects duplicates and non-integer numbers. */
public final class TeleautoStrictJson {
    private final String source; private int pos;
    private TeleautoStrictJson(String source) { if (source.length() > 32768) throw new IllegalArgumentException(); this.source=source; }
    public static Map<String,Object> object(String source) {
        TeleautoStrictJson p=new TeleautoStrictJson(source); Object value=p.value(0); p.space();
        if (p.pos!=source.length() || !(value instanceof Map)) throw new IllegalArgumentException();
        return (Map<String,Object>)value;
    }
    private void space(){while(pos<source.length() && " \r\n\t".indexOf(source.charAt(pos))>=0)pos++;}
    private char take(){if(pos>=source.length())throw new IllegalArgumentException();return source.charAt(pos++);}
    private Object value(int depth){
        if(depth>8)throw new IllegalArgumentException();space();char c=take();
        if(c=='"')return string();
        if(c=='{'){
            Map<String,Object> out=new LinkedHashMap<>();space();if(pos<source.length()&&source.charAt(pos)=='}'){pos++;return out;}
            while(true){space();if(take()!='"')throw new IllegalArgumentException();String key=string();space();if(take()!=':')throw new IllegalArgumentException();
                if(out.containsKey(key)||out.size()>=32)throw new IllegalArgumentException();out.put(key,value(depth+1));space();c=take();if(c=='}')return out;if(c!=',')throw new IllegalArgumentException();}
        }
        if(c=='['){List<Object> out=new ArrayList<>();space();if(pos<source.length()&&source.charAt(pos)==']'){pos++;return out;}
            while(true){if(out.size()>=32)throw new IllegalArgumentException();out.add(value(depth+1));space();c=take();if(c==']')return out;if(c!=',')throw new IllegalArgumentException();}}
        if(c=='-'||(c>='0'&&c<='9')){int start=pos-1;while(pos<source.length()&&Character.isDigit(source.charAt(pos)))pos++;String n=source.substring(start,pos);if(!n.matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException();return Long.parseLong(n);}
        throw new IllegalArgumentException();
    }
    private String string(){StringBuilder out=new StringBuilder();while(true){char c=take();if(c=='"')break;if(c<32)throw new IllegalArgumentException();
        if(c=='\\'){c=take();switch(c){case '"':case '\\':case '/':break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;case 'u':if(pos+4>source.length())throw new IllegalArgumentException();c=(char)Integer.parseInt(source.substring(pos,pos+4),16);pos+=4;break;default:throw new IllegalArgumentException();}}
        out.append(c);if(out.length()>24000)throw new IllegalArgumentException();}
        for(int i=0;i<out.length();i++){char c=out.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=out.length()||!Character.isLowSurrogate(out.charAt(i)))throw new IllegalArgumentException();}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException();}
        return out.toString();}
    public static String encode(Object v){
        if(v instanceof String){StringBuilder b=new StringBuilder("\"");for(char c:((String)v).toCharArray()){if(c=='"'||c=='\\')b.append('\\').append(c);else if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}return b.append('"').toString();}
        if(v instanceof Long||v instanceof Integer)return v.toString();
        if(v instanceof Map){List<String> parts=new ArrayList<>();((Map<?,?>)v).forEach((k,x)->parts.add(encode(k)+":"+encode(x)));return "{"+String.join(",",parts)+"}";}
        if(v instanceof List){List<String> parts=new ArrayList<>();for(Object x:(List<?>)v)parts.add(encode(x));return "["+String.join(",",parts)+"]";}
        throw new IllegalArgumentException();
    }
}
