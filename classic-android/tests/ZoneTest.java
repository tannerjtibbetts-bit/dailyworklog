package com.dailyworklog.app;
import java.util.*;
public class ZoneTest { public static void main(String[] a) throws Exception {
  String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[0])), "UTF-8");
  java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\"t\": \"((?:[^\"\\\\]|\\\\.)*)\", \"l\": (\\d+), \"t0\": (\\d+), \"r\": (\\d+), \"b\": (\\d+)").matcher(json);
  List<SheetImport.Box> boxes = new ArrayList<>();
  while (m.find()) boxes.add(new SheetImport.Box(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))));
  java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.File(a[1]));
  ZoneSheet.Pixels px = new ZoneSheet.Pixels() {
    public int width() { return img.getWidth(); } public int height() { return img.getHeight(); }
    public int rgb(int x, int y) { return img.getRGB(x, y) & 0xFFFFFF; } };
  List<ZoneSheet.Task> tasks = ZoneSheet.parse(boxes, px);
  System.out.println("boxes=" + boxes.size() + " tasks=" + (tasks == null ? "null" : tasks.size()));
  for (ZoneSheet.Task t : tasks) System.out.println("[" + t.colour + "] " + t.side + " | " + t.title() + "\n    " + t.notes().replace("\n", " / "));
} }
