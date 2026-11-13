"""Verify serialized neural/defense learning against the public Robocode API."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PROBE = r'''
package equipe;
import java.io.*;import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import java.util.zip.CRC32;import java.awt.geom.Point2D;
public class memoryprobe {
 static class Dummy extends titanprime {public double getBattleFieldWidth(){return 800;}public double getBattleFieldHeight(){return 600;}public double getGunCoolingRate(){return .1;}}
 static class Other extends Dummy {public double getBattleFieldWidth(){return 900;}}
 static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
 static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Class<?> rec=Class.forName("equipe.titanprime$MemoryRecord"),pair=Class.forName("equipe.titanprime$TitanNeuralPair"),net=Class.forName("equipe.titanprime$TitanNeural");
  Constructor<?> rc=rec.getDeclaredConstructor(),pc=pair.getDeclaredConstructor();rc.setAccessible(true);pc.setAccessible(true);Object record=rc.newInstance(),neural=pc.newInstance();
  field(rec,"name").set(record,"fixture.MemoryOpponent 1.0");field(rec,"seen").set(record,7L);field(rec,"neural").set(record,neural);field(pair,"realSamples").set(neural,121L);
  Object slow=field(pair,"slow").get(neural);double[][] weights=(double[][])field(net,"first").get(slow);weights[0][0]=.123456789;
  Map<String,double[]> ratings=(Map<String,double[]>)field(rec,"guns").get(record);ratings.put("Titan Neural General",new double[]{12.5,64});
  float[] sample={1.95f,350,8,1,1.57f,0,.5f,.6f,10,12,64,130,250,.4f};
  ((List<float[]>)field(rec,"hits").get(record)).add(sample);((List<float[]>)field(rec,"visits").get(record)).add(sample);
  field(rec,"shots").set(record,32);field(rec,"timesHit").set(record,8);field(rec,"weightedHits").set(record,4.25);
  Method encode=titanprime.class.getDeclaredMethod("encodeMemory",List.class,long.class),read=titanprime.class.getDeclaredMethod("readMemory",File.class);encode.setAccessible(true);read.setAccessible(true);
  Dummy robot=new Dummy();byte[] data=(byte[])encode.invoke(robot,Arrays.asList(record),12L);Path path=Paths.get(args[0],"sample.dat");Files.write(path,data);Object snapshot=read.invoke(robot,path.toFile());require(snapshot!=null,"Roundtrip snapshot");
  Map<String,Object> decoded=(Map<String,Object>)field(snapshot.getClass(),"records").get(snapshot);Object restored=decoded.get("fixture.MemoryOpponent 1.0");require(restored!=null,"Opponent key");Object loaded=field(rec,"neural").get(restored);Object loadedNet=field(pair,"slow").get(loaded);
  Method predict=net.getDeclaredMethod("predict",double[].class);predict.setAccessible(true);double[] input=new double[13];Arrays.fill(input,.3);double[] a=(double[])predict.invoke(slow,(Object)input),b=(double[])predict.invoke(loadedNet,(Object)input);
  double delta=0;for(int i=0;i<a.length;i++)delta=Math.max(delta,Math.abs(a[i]-b[i]));require(delta<1e-6,"Float32 network prediction drift");
  require(field(pair,"realSamples").getLong(loaded)==121,"Learned sample count");
  Map<String,Object> store=(Map<String,Object>)field(titanprime.class,"MEMORY_RECORDS").get(null);store.putAll(decoded);
  titanprime.titan_utils_BattleField bf=new titanprime.titan_utils_BattleField(800,600);
  titanprime.titan_move_MoveEnemy enemy=new titanprime.titan_move_MoveEnemy("fixture.MemoryOpponent 1.0",350,100,new Point2D.Double(400,100),0,0,0,0,0,new ArrayList<titanprime.titan_gfx_RoboGraphic>(),bf,new titanprime.titan_utils_MovementPredictor(bf),System.out);
  require(enemy.memoryHits.size()==1&&enemy.memoryVisits.size()==1,"Defensive sample queues restored");require(enemy.raw1v1ShotsFired==32&&enemy.raw1v1ShotsHit==8&&enemy.raw1v1ShotsFiredThisRound==0,"Warm priors and fresh-round accounting");
  for(titanprime.titan_utils_KnnView<titanprime.titan_utils_TimestampedGuessFactor> view:enemy.views.values())require(view.size()>0,"KNN history rehydrated");
  require(read.invoke(new Other(),path.toFile())==null,"Different arena rejected");
  byte[] broken=data.clone();broken[broken.length-1]^=1;Files.write(path,broken);require(read.invoke(robot,path.toFile())==null,"CRC corruption rejected");
  broken=data.clone();broken[7]=99;CRC32 crc=new CRC32();crc.update(broken,0,broken.length-8);long value=crc.getValue();for(int i=0;i<8;i++)broken[broken.length-1-i]=(byte)(value>>>(8*i));Files.write(path,broken);require(read.invoke(robot,path.toFile())==null,"Unknown schema rejected");
  Files.write(path,new byte[10]);require(read.invoke(robot,path.toFile())==null,"Truncation rejected");Files.write(path,new byte[70001]);require(read.invoke(robot,path.toFile())==null,"Oversized snapshot rejected");
  List<Object> many=new ArrayList<Object>();
  for(int n=0;n<9;n++){Object extra=rc.newInstance();field(rec,"name").set(extra,"fixture.Opponent"+n);field(rec,"neural").set(extra,neural);many.add(extra);}
  byte[] multi=(byte[])encode.invoke(robot,many,Long.MAX_VALUE-1);require(multi.length<70000,"Compressed many-opponent quota");Files.write(path,multi);
  Object manySnapshot=read.invoke(robot,path.toFile());require(manySnapshot!=null,"Compressed many-opponent reload");
  require(((Map<?,?>)field(manySnapshot.getClass(),"records").get(manySnapshot)).size()==9,"No fixed four-opponent cap");
  ByteArrayOutputStream legacyBytes=new ByteArrayOutputStream();byte[] oldHeader=Arrays.copyOf(data,56);oldHeader[7]=1;legacyBytes.write(oldHeader);
  try(InputStream gz=new java.util.zip.GZIPInputStream(new ByteArrayInputStream(data,56,data.length-64))){byte[] buffer=new byte[2048];int amount;while((amount=gz.read(buffer))!=-1)legacyBytes.write(buffer,0,amount);}
  CRC32 legacyCrc=new CRC32();legacyCrc.update(legacyBytes.toByteArray());new DataOutputStream(legacyBytes).writeLong(legacyCrc.getValue());byte[] legacy=legacyBytes.toByteArray();
  Files.write(path,legacy);require(read.invoke(robot,path.toFile())!=null,"Schema1 migration");
  byte[] bombData;
  ByteArrayOutputStream bombBytes=new ByteArrayOutputStream();bombBytes.write(data,0,56);
  try(java.util.zip.GZIPOutputStream gz=new java.util.zip.GZIPOutputStream(bombBytes)){byte[] block=new byte[1024];for(int n=0;n<8193;n++)gz.write(block);}
  CRC32 bombCrc=new CRC32();bombCrc.update(bombBytes.toByteArray());new DataOutputStream(bombBytes).writeLong(bombCrc.getValue());
  bombData=bombBytes.toByteArray();require(bombData.length<70000,"Bomb compressed size");Files.write(path,bombData);require(read.invoke(robot,path.toFile())==null,"Decompression allocation bound");
  weights[0][0]=Double.NaN;boolean refused=false;try{encode.invoke(robot,Arrays.asList(record),13L);}catch(InvocationTargetException e){refused=e.getCause() instanceof IOException;}require(refused,"Nonfinite model refused");
  Files.write(path,data);
  System.out.println("MEMORY_CODEC_OK bytes="+data.length+" maxPredictionDrift="+delta+" restoredKNNViews="+enemy.views.size());
 }
}
'''

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', required=True, type=Path)
    parser.add_argument('--ecj', type=Path)
    parser.add_argument('--source', type=Path, default=ROOT/'robots/equipe/titanprime.java')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--fixture-output', type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory() as folder:
        directory = Path(folder)
        probe = directory/'memoryprobe.java'
        probe.write_text(PROBE)
        classes = directory/'classes'
        classes.mkdir()
        compiler = ['java', '-jar', str(args.ecj.resolve()), '-8', '-nowarn'] if args.ecj else ['javac', '--release', '8']
        build = subprocess.run(compiler + ['-encoding', 'UTF-8', '-cp', str(args.api.resolve()), '-d', str(classes), str(args.source.resolve()), str(probe)], capture_output=True, text=True, timeout=90)
        if build.returncode:
            raise RuntimeError(build.stdout + build.stderr)
        run = subprocess.run(['java', '-cp', str(classes)+os.pathsep+str(args.api.resolve()), 'equipe.memoryprobe', str(directory)], capture_output=True, text=True, timeout=30)
        if run.returncode:
            raise RuntimeError(run.stdout + run.stderr)
        if args.fixture_output:
            args.fixture_output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(directory/'sample.dat', args.fixture_output)
        report = dict(passed=True, source_sha256=hashlib.sha256(args.source.read_bytes()).hexdigest(),
                      checks=['Neural prediction float32 equivalence', 'Defensive KNN rehydration', 'Fresh round counters', 'CRC, schema, context, size, truncation and nonfinite weights', 'Nine opponents without fixed count cap', 'Schema1 migration', 'Decompression allocation bound'], output=run.stdout)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2)+'\n')
        print(json.dumps(report))

if __name__ == '__main__':
    main()
