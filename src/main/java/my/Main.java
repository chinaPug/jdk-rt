package my;

import java.util.HashMap;
import java.util.Map;

public class Main {
    static ThreadLocal<String> threadLocal=new ThreadLocal<>();
    static InheritableThreadLocal<String> inheritableThreadLocal = new InheritableThreadLocal<>();
    private static String a="aaaaaaaaaaa";
    public static void main(String[] args) throws InterruptedException {
        HashMap<String,String> map=new HashMap<>();
    }
}
