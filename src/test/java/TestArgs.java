import agency.highlysuspect.jargrep.Cli;
import agency.highlysuspect.jargrep.SearchOpts;
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
		Cli cli = new Cli();
		cli.parseOpts("hey", "./foo");
		
		assertEquals("hey", cli.opts.grep.toString());
		assertEquals(of(Paths.get("./foo")), cli.targets);
	}
	
	@Test
	public void blankLeading() {
		Cli cli = new Cli();
		cli.parseOpts("", "hey", "./foo");
		
		assertEquals("hey", cli.opts.grep.toString());
		assertEquals(of(Paths.get("./foo")), cli.targets);
	}
	
	@Test
	public void blankTrailing() {
		Cli cli = new Cli();
		cli.parseOpts("hey", "./foo", "");
		
		assertEquals("hey", cli.opts.grep.toString());
		assertEquals(of(Paths.get("./foo")), cli.targets);
	}
	
}
