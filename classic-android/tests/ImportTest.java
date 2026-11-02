package com.dailyworklog.app;
import java.util.*;
public class ImportTest { public static void main(String[] a) throws Exception {
  List<List<String>> x = SheetImport.fromFile(new java.io.FileInputStream(a[0]));
  System.out.println("xlsx: " + x + " taskcol=" + SheetImport.likelyTaskColumn(x));
  System.out.println("csv: " + SheetImport.fromCsv("Task,Where\n\"Fix pump, north\",Zone 1\r\nClean bay,\n"));
  List<SheetImport.Box> b = new ArrayList<>();
  // simulated photo: 3 columns, slight vertical jitter
  b.add(new SheetImport.Box("Task",100,100,160,130)); b.add(new SheetImport.Box("Location",600,102,720,131)); b.add(new SheetImport.Box("Qty",900,99,950,128));
  b.add(new SheetImport.Box("Pack up sea can",102,150,380,180)); b.add(new SheetImport.Box("Zone 2",598,153,690,183)); b.add(new SheetImport.Box("4",930,151,945,181));
  b.add(new SheetImport.Box("Inspect torque",99,200,330,230)); b.add(new SheetImport.Box("wrenches",340,201,470,231)); b.add(new SheetImport.Box("Tool crib",601,198,730,228));
  List<List<String>> o = SheetImport.fromBoxes(b);
  System.out.println("photo: " + o + " taskcol=" + SheetImport.likelyTaskColumn(o));
} }
class CombineTest { public static void main(String[] a) {
  java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
  String[][] r = {{"Task","Where"},{"Pack up sea can","Zone 2"},{"and send out of","Zone 2"},{"zone 2 today",""},{"Inspect wrenches","Crib"}};
  for (String[] x : r) rows.add(new java.util.ArrayList<>(java.util.Arrays.asList(x)));
  SheetImport.combineRows(rows, java.util.Arrays.asList(1,2,3), 0, "Pack up sea can and send out of zone 2 today");
  System.out.println(rows);
  if (!rows.toString().equals("[[Task, Where], [Pack up sea can and send out of zone 2 today, Zone 2], [Inspect wrenches, Crib]]")) throw new AssertionError();
  System.out.println("COMBINE PASS"); } }
