package agency.highlysuspect.jargrep;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class Filter implements Predicate<String> {
	public Pattern pattern = null;
	public boolean exclude = false;

	@Override
	public boolean test(String s) {
		return (pattern == null || pattern.matcher(s).find()) ^ exclude;
	}
}
