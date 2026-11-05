import org.mozilla.javascript.*;
import com.planet_ink.coffee_mud.core.JSRuntime;
public class RuntimeBudgetProbe {
 public static void main(String[] args) {
  JSRuntime.install(); Context cx=JSRuntime.enter(1); long start=System.nanoTime();
  try {
   Object value=cx.evaluateString(cx.initStandardObjects(),"java.lang.Thread.sleep(1500); 42;","audit native-call boundary",1,null);
   System.out.println("result="+Context.toString(value));
   System.out.println("elapsed_ms="+(System.nanoTime()-start)/1000000L);
   System.out.println("budget_ms=1000");
  }catch(Throwable t){System.out.println("thrown="+t.getClass().getName());}
  finally {Context.exit();}
 }
}
