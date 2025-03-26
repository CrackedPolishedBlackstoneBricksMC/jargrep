package agency.highlysuspect.jargrep;

import org.teavm.jso.typedarrays.Int8Array;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

//Mostly copied from ByteArrayInputStream
public class Int8ArrayInputStream extends InputStream {
	public Int8ArrayInputStream(Int8Array buf) {
		this.buf = buf;
		this.count = buf.getLength();
	}
	
	private final Int8Array buf;
	private int pos = 0;
	private int mark = 0;
	private final int count;
	
	@Override
	public int read() {
		if(pos >= count) return -1;
		else return buf.get(pos++) & 0xFF;
	}
	
	@Override
	public int read(byte[] b, int off, int len) {
		Objects.checkFromIndexSize(off, len, b.length);
		
		//already at the end of the array
		if(pos >= count) return -1;
		
		//how much to actually read?
		int avail = count - pos;
		if(len > avail) len = avail;
		if(len <= 0) return 0; //nothing
		
		//we have System.arraycopy at home
		for(int i = 0; i < len; i++) {
			b[i + off] = buf.get(pos + i);
		}
		pos += len;
		return len;
	}
	
	@Override
	public byte[] readAllBytes() {
		//arraycopy
		byte[] result = new byte[available()];
		for(int i = pos; i < count; i++) {
			result[i] = buf.get(i);
		}
		pos = count;
		return result;
	}
	
	@Override
	public int readNBytes(byte[] b, int off, int len) throws IOException {
		int n = read(b, off, len);
		return n == -1 ? 0 : n;
	}
	
	@Override
	public long skip(long nLong) {
		//just a hunch, but i think working with ints
		//will be a little friendlier to teavm
		int n = (int) nLong;
		int k = count - pos;
		if(n < k) k = Math.max(n, 0);
		
		pos += k;
		return k;
	}
	
	@Override
	public int available() {
		return count - pos;
	}
	
	@Override
	public boolean markSupported() {
		return true;
	}
	
	@Override
	public void mark(int readlimit) {
		mark = pos;
	}
	
	@Override
	public void reset() {
		pos = mark;
	}
	
	@Override
	public void close() {
		//no
	}
}
