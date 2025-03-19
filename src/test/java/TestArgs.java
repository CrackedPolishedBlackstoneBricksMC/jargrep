import agency.highlysuspect.jargrep.Opts;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class TestArgs {
	@SafeVarargs
	private final <T> List<T> of(T... things) {
		return Arrays.asList(things);
	}
	
	@Test
	public void doesntCrashLol() {
		Opts opts = Opts.parse("hey", "./foo");
		assertEquals("hey", opts.grep.toString());
		assertEquals(of(Paths.get("./foo")), opts.targets);
	}
	
	@Test
	public void blankLeading() {
		Opts opts = Opts.parse("", "hey", "./foo");
		assertEquals("hey", opts.grep.toString());
		assertEquals(of(Paths.get("./foo")), opts.targets);
	}
	
	@Test
	public void blankTrailing() {
		Opts opts = Opts.parse("hey", "./foo", "");
		assertEquals("hey", opts.grep.toString());
		assertEquals(of(Paths.get("./foo")), opts.targets);
	}
	
}
