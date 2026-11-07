package za.co.bacy.ui;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
/** Bounded stream reads compatible with Android 11 and later. */
public final class BoundedInput {
 private BoundedInput() {}
 public static byte[] read(InputStream input,int maximum)throws IOException{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
  while((n=input.read(buffer))!=-1){if(bytes.size()+n>maximum)throw new IOException("Response too large");bytes.write(buffer,0,n);}return bytes.toByteArray();
 }
}
