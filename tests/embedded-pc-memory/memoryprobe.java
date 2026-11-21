package equipe;
import java.nio.file.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class memoryprobe {
 static int checks;
 static void ok(boolean yes,String msg){checks++;if(!yes)throw new AssertionError(msg);}
 static Field f(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 static Object call(Object r,String n,Class<?>[] t,Object...a)throws Exception{Method m=titanprime.class.getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(r,a);}
 static class Dummy extends titanprime {
  Path dir; double width=800,cooling=.1;
  Dummy(Path d){dir=d;out=System.out;}
  public double getBattleFieldWidth(){return width;}
  public double getBattleFieldHeight(){return 600;}
  public double getGunCoolingRate(){return cooling;}
  public int getOthers(){return 1;}
  public int getRoundNum(){return 0;}
  public long getTime(){return 0;}
  public double getEnergy(){return 100;}
  public File getDataFile(String n){return dir.resolve(n).toFile();}
 }
 static Object decode(Dummy d,byte[] b)throws Exception{return call(d,"decodeBattleMemory",new Class[]{byte[].class},(Object)b);}
 static Map records(Object s)throws Exception{return (Map)f(s.getClass(),"records").get(s);}
 static List sortedRecords(Object snapshot)throws Exception{List rows=new ArrayList(records(snapshot).values());Collections.sort(rows,new Comparator(){public int compare(Object a,Object b){try{return ((String)f(a.getClass(),"name").get(a)).compareTo((String)f(b.getClass(),"name").get(b));}catch(Exception e){throw new RuntimeException(e);}}});return rows;}
 static long generation(Object s)throws Exception{return f(s.getClass(),"generation").getLong(s);}
 static void reset(Dummy d)throws Exception{
  f(titanprime.class,"memoryLoaded").setBoolean(null,false);
  for(String n:new String[]{"MEMORY_RECORDS","MEMORY_SEEN","NEURAL_MODELS"})((Map)f(titanprime.class,n).get(null)).clear();
  f(titanprime.class,"_gun").set(null,new titanprime.titan_gun_Mira(d,false));
 }
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]);Dummy d=new Dummy(root.resolve("empty"));Files.createDirectories(d.dir);Files.deleteIfExists(d.getDataFile("memoria-a.dat").toPath());Files.deleteIfExists(d.getDataFile("memoria-b.dat").toPath());
  byte[] a=Files.readAllBytes(root.resolve("uploaded/memoria-a.dat")),b=Files.readAllBytes(root.resolve("uploaded/memoria-b.dat"));
  if(args.length>1){Object s=call(d,"readMemory",new Class[]{File.class},root.resolve("roundtrip.dat").toFile());ok(s!=null,"olddecoder accepts newsnapshot");ok(generation(s)==39,"olddecoder generation");ok(records(s).size()==4,"olddecoder profilecount");System.out.println("BASE_COMPATIBILITY_PASS checks="+checks);return;}
  Object seed=call(d,"readEmbeddedMemory",new Class[]{});ok(seed!=null&&generation(seed)==38,"embedded validgeneration38");ok(records(seed).size()==4,"fourprofiles embedded");
  ok(Arrays.equals(java.util.Base64.getDecoder().decode(String.join("",(String[])call(d,"embeddedMemoryChunks",new Class[]{}))),b),"all embedded bytes exactly equal upload");
  Object older=decode(d,a),fresh=decode(d,b);ok(generation(older)==37&&generation(fresh)==38,"uploaded generations");
  Object ob=records(fresh).get("obrabo.OBraboBot*");Object nn=f(ob.getClass(),"neural").get(ob);ok(f(nn.getClass(),"realSamples").getLong(nn)==553,"neural samples553");
  for(Object record:records(seed).values()){
   ok(((List)f(record.getClass(),"hits").get(record)).size()==24,"defensivehits retained");ok(((List)f(record.getClass(),"visits").get(record)).size()==24,"visits retained");ok(((Map)f(record.getClass(),"guns").get(record)).size()==4,"gunratings retained");
  }
  ok(decode(d,Arrays.copyOf(b,63))==null,"truncation rejected");byte[] bad=b.clone();bad[100]^=1;ok(decode(d,bad)==null,"CRC corruption rejected");ok(decode(d,new byte[70001])==null,"oversize rejected");d.width=900;ok(decode(d,b)==null&&call(d,"readEmbeddedMemory",new Class[]{})==null,"other arena ignored");d.width=800;d.cooling=.2;ok(decode(d,b)==null,"other cooling ignored");d.cooling=.1;
  f(older.getClass(),"generation").setLong(older,1);Map local=records(older);Object localRecord=local.get("obrabo.OBraboBot*");local.clear();local.put("obrabo.OBraboBot*",localRecord);
  Object merged=call(null,"combineMemory",new Class[]{seed.getClass(),older.getClass()},seed,older);ok(records(merged).size()==4,"missinglocal profiles retainedfromseed");ok(records(merged).get("obrabo.OBraboBot*")==localRecord,"local wins despite independentlowergeneration");ok(generation(merged)==38,"generation monotonic");
  byte[] encoded=(byte[])call(d,"encodeMemory",new Class[]{List.class,long.class},sortedRecords(merged),39L);Files.write(root.resolve("roundtrip.dat"),encoded);Object roundtrip=decode(d,encoded);ok(roundtrip!=null&&generation(roundtrip)==39,"future save decodes");
  byte[] again=(byte[])call(d,"encodeMemory",new Class[]{List.class,long.class},sortedRecords(roundtrip),39L);ok(Arrays.equals(encoded,again),"weights statistics andratings exactroundtrip");
  reset(d);call(d,"loadBattleMemory",new Class[]{});ok(((Map)f(titanprime.class,"MEMORY_RECORDS").get(null)).size()==4,"actual loader boots embedded withoutfiles");
  Files.write(d.getDataFile("memoria-a.dat").toPath(),a);Files.write(d.getDataFile("memoria-b.dat").toPath(),b);reset(d);call(d,"loadBattleMemory",new Class[]{});ok(f(titanprime.class,"memoryGeneration").getLong(null)==38,"actual loader chooses newerlocal slot");
  Files.write(d.getDataFile("memoria-b.dat").toPath(),bad);reset(d);call(d,"loadBattleMemory",new Class[]{});Map loaded=(Map)f(titanprime.class,"MEMORY_RECORDS").get(null);Object loadedNN=f(ob.getClass(),"neural").get(loaded.get("obrabo.OBraboBot*"));ok(f(nn.getClass(),"realSamples").getLong(loadedNN)==512,"corruptlatest fallsbackgoodlocalslot");
  Files.delete(d.getDataFile("memoria-a.dat").toPath());reset(d);call(d,"loadBattleMemory",new Class[]{});ok(((Map)f(titanprime.class,"MEMORY_RECORDS").get(null)).size()==4,"corruptonlyfilefallsbackembedded");
  titanprime.titan_utils_BattleField field=new titanprime.titan_utils_BattleField(800,600);
  titanprime.titan_move_MoveEnemy enemy=new titanprime.titan_move_MoveEnemy("voidious.Diamond 1.8.28",400,100,new java.awt.geom.Point2D.Double(750,300),0,8,Math.PI/2,0,1,new ArrayList<titanprime.titan_gfx_RoboGraphic>(),field,new titanprime.titan_utils_MovementPredictor(field),System.out);
  ok(enemy.memoryHits.size()==24&&enemy.memoryVisits.size()==24,"actualenemy reconstructs defensivehistory");ok(enemy.raw1v1ShotsFired==128&&enemy.raw1v1ShotsHit==12,"actualenemy restores defensecounters");
  int populated=0;for(titanprime.titan_utils_KnnView view:enemy.views.values())if(view.size()>0)populated++;ok(populated>0,"actualKNN trees populated");
  Object gun=f(titanprime.class,"_gun").get(null);titanprime.titan_gun_VirtualGunsManager manager=(titanprime.titan_gun_VirtualGunsManager)f(gun.getClass(),"_virtualGuns").get(gun);Map<String,double[]> ratings=manager.snapshotRatings("voidious.Diamond 1.8.28");ok(ratings.size()==4,"actualfour virtualguns restored");Map<String,double[]> expected=(Map)f(ob.getClass(),"guns").get(records(seed).get("voidious.Diamond 1.8.28"));for(String key:expected.keySet())ok(Arrays.equals(ratings.get(key),expected.get(key)),"exact gunrating "+key);
  System.out.println("EMBEDDED_MEMORY_PRODUCTION_PASS checks="+checks);
 }
}
