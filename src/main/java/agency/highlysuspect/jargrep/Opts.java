package agency.highlysuspect.jargrep;

import joptsimple.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

//looking at options
// yes: zipgrep -x (ignore filename pattern)
// yes: --help, -V

// no: -E --extended-regexp, -G --basic-regexp, -P --perl-regexp (strange regex engine selection)
// yes: -F --fixed-strings (plaintext regex engine; supported by java Pattern LITERAL)
// by-default: -e --regexp (regular expression)
// maybe: -f --file (pattern file)
// yes: -i --ignore-case (case insensitive - supported by java Pattern CASE_INSENSITIVE)
// no: -v --invert-match (invert match - not useful to jargrep i tink)
// no: -w --word-regexp (word character - put it in the regex)
// doubtful: -x --line-regexp (match whole line)
//
// yes: -c --count
// yes: -l --files-with-matches (print names of matched files only)
// maybe: -L --files-without-match
// maybe: -m, --max-count (limit per file)
// maybe: -o --only-matching (print only the matched part, not whole line)
// maybe: -q --quiet --silent
// maybe: -s --no-messages
//
// no: -b --byte-offset
// yes: -H --with-filename
// yes: -h --no-filename
// no: --label
// yes: -n --line-number (print line number of matches)
// no: -T --initial-tab
// no: -u --unix-byte-offsets
// no: -Z --null
//
// context options -A -B -C --after-context --before-context --context: probably not
//
// yes: -a --text (process binary files like text)
// yes: --binary-files=(binary | without-match | text), -I (same as without-match)
//   maybe want an analogous option for class-files
// no: -D --devices
// no: -d --directories (recursion control; should always be enabled)
// yes: --exclude (better than zipgrep -x option i think)
//   maybe exclude-dir is better though (?)
// maybe: --exclude-from
// yes: --include
//
// no: --line-buffered
// no: -U --binary (weird windows-only guessing games)
// no: -z --null-data
//
// additional options that jargrep will need:
// options about "what to search" - filenames, file contents, class files, various things inside class files
// something about unicode character classes? (looks like you can enable them from the regex though)

public class Opts {
	public Pattern grep;

	//Paths to look at
	public List<Path> targets = new ArrayList<>();

	//What to search
	public boolean searchFilenames = true;
	public boolean searchFileContents = true;
	public boolean searchClasses = true;
	public boolean searchFieldNames = true;
	public boolean searchFieldValues = true;
	public boolean searchMethodNames = true;
	public boolean searchLdc = true;

	//How to search
	public enum BinaryMode { BINARY, WITHOUT_MATCH, TEXT }
	public BinaryMode binaryMode = BinaryMode.BINARY;
	public boolean searchInsideSpecial = false;

	//Filters the files *inside* paths, not the paths themselves
	//just don't pass a jar as an option if you don't want to search it
	public FilenameFilter filenameFilter = new FilenameFilter();
	
	public boolean printFilename;
	
	public static Opts parse(String... args) {
		return new Opts().from(args);
	}
	
	private static final class BooleanConv implements ValueConverter<Boolean> {
		public static BooleanConv I = new BooleanConv();
		@Override
		public Boolean convert(String s) {
			return Boolean.valueOf(s);
		}
		
		@Override
		public String revert(Object value) {
			return value instanceof Boolean ? ((Boolean) value).toString() : null;
		}
		
		@Override
		public Class<? extends Boolean> valueType() {
			return Boolean.TYPE;
		}
		
		@Override
		public String valuePattern() {
			return null;
		}
	}

	public Opts from(String... args) {
		
		OptionParser parser = new OptionParser(true);
		OptionSpec<Void> help = parser.accepts("help",  "Print usage.").forHelp();
		
		//how to match
		OptionSpec<Void> fixedStrings = parser.acceptsAll(Arrays.asList("F", "fixed-strings"));
		OptionSpec<Void> caseInsensitive = parser.acceptsAll(Arrays.asList("i", "case-insensitive"));
		
		//how to search
		OptionSpec<Boolean> searchFilenames = parser.accepts("search-filenames", "Report matches in the names of files.")
			.withOptionalArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(true);
		OptionSpec<Boolean> searchFileContents = parser.accepts("search-contents", "Look inside text files.")
			.withOptionalArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(true);
		OptionSpec<Boolean> searchClasses = parser.accepts("search-classes", "Look inside class files.")
			.withOptionalArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(true);
		OptionSpec<Boolean> searchFieldNames = parser.accepts("field-names", "Search field names inside classes.")
			.availableIf(searchClasses)
			.withOptionalArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(true);
		OptionSpec<Boolean> searchFieldValues = parser.accepts("field-values", "Search constant field assignments inside classes.")
			.availableIf(searchClasses)
			.withOptionalArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(true);
		OptionSpec<Boolean> searchMethodNames = parser.accepts("method-names", "Search method names.")
			.availableIf(searchClasses)
			.withOptionalArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(true);
		OptionSpec<Boolean> searchLdc = parser.accepts("ldc", "Search LDC instructions inside methods (~string constants).")
			.availableIf(searchClasses)
			.withOptionalArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(true);
		
		//where to search
		OptionSpecBuilder includeB = parser.accepts("include", "Only search files whos names match this pattern.");
		OptionSpec<String> exclude = parser.accepts("exclude", "Don't search files whos names match this pattern.").availableUnless(includeB).withRequiredArg();
		OptionSpec<String> include = includeB.availableUnless(exclude).withRequiredArg();
		
		//output
		OptionSpec<Void> withFilename = parser.acceptsAll(Arrays.asList("with-filename", "H"));
		OptionSpec<Void> noFilename = parser.acceptsAll(Arrays.asList("no-filename", "h"));
		
		NonOptionArgumentSpec<String> nonopts = parser.nonOptions("PATTERN FILES*");
		
		OptionSet set = parser.parse(args);
		
		if(set.has(help)) {
			try {
				parser.printHelpOn(System.out);
			} catch (IOException e) {
				throw new RuntimeException(e); //really
			}
			System.exit(1);
		}
		
		String patternToCompile = null;
		
		for(Object o : set.nonOptionArguments()) {
			String s = o.toString();
			if(s.isEmpty()) continue;
			if(patternToCompile == null) patternToCompile = s;
			else targets.add(Paths.get(s));
		}
		
		int patternCompileOptions = 0;
		patternCompileOptions |= set.has(fixedStrings) ? Pattern.LITERAL : 0;
		patternCompileOptions |= set.has(caseInsensitive) ? Pattern.CASE_INSENSITIVE : 0;
		
		this.searchFilenames = set.valueOf(searchFilenames);
		this.searchFileContents = set.valueOf(searchFileContents);
		this.searchClasses = set.valueOf(searchClasses);
		this.searchFieldNames = set.valueOf(searchFieldNames);
		this.searchFieldValues = set.valueOf(searchFieldValues);
		this.searchMethodNames = set.valueOf(searchMethodNames);
		this.searchLdc = set.valueOf(searchLdc);

		//TODO:
		//if binary-files is set, set binarymode from it
		//else if '-a' or '--text' is set, set to text
		this.binaryMode = BinaryMode.BINARY;
		
		//TODO what's up with search-inside-special about
		this.searchInsideSpecial = true;
		
		if(set.has(exclude)) {
			this.filenameFilter.pattern = Pattern.compile(set.valueOf(exclude));
			this.filenameFilter.exclude = true;
		} else if(set.has(include)) {
			this.filenameFilter.pattern = Pattern.compile(set.valueOf(include));
			this.filenameFilter.exclude = false;
		}
		
		if(set.has(withFilename)) {
			printFilename = true;
		} else if(set.has(noFilename)) {
			printFilename = false;
		} else {
			printFilename = targets.size() > 1;
		}
		
		if(patternToCompile == null) {
			throw new IllegalArgumentException("No pattern");
		}
		grep = Pattern.compile(patternToCompile, patternCompileOptions);

		if(targets.isEmpty()) {
			//use all jars in current directory
			File[] cwdJars = new File(".").listFiles((f, name) ->
				name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".class"));
			if(cwdJars != null && cwdJars.length > 0) {
				Arrays.stream(cwdJars).map(File::toPath).forEach(targets::add);
			}

			if(targets.isEmpty()) {
				System.err.println("No files specified on command line, and no jars/zips/classes in current directory.");
				printUsageAndExit();
			}
		}

		return this;
	}

	public boolean matches(String s) {
		return grep.matcher(s).find();
	}

	private static void printUsageAndExit() {
		printy(System.err,
			"Usage: jargrep [OPTION]... PATTERN [FILES...]",
			"See jargrep --help for more information."
		);

		System.exit(1);
	}

	//TODO this is a much better help output than the joptsimple one LOL, i should use it
	private static void printHelpAndExit() {
		printy(System.out,
			"Usage: jargrep [OPTION]... PATTERN [FILES...]",
			"Recursively search for PATTERN in each FILE. Each FILE can be a .jar,",
			".zip, .class, or a plaintext file. PATTERN is a Java regular expression.",
			"Example: jargrep \"mixin\" aaa.jar bbb.jar",
			"",
			"The search will recurse into subdirectories, sub-zips, and sub-jars automatically.",
			"The pattern is always treated as a regular expression.",
			"",
			"Regexp selection and interpretation: ",
			"  -F, --fixed-strings   Enable Pattern.LITERAL mode, searching for the strings verbatim.",
			"  -i, --ignore-case     Enable case-insensitive mode.",
			"",
			"What to search: ",
			"  --search-filenames [TRUE|false]  Report matches in the names of files inside archives.",
			"  --search-contents  [TRUE|false]  Report matches in the contents of files.",
			"  --search-classes   [TRUE|false]  Parse .class files and report matches inside.",
			"If --search-classes is enabled:",
			"    --field-names    [TRUE|false]  Report matches in the names of fields.",
			"    --field-values   [TRUE|false]  Report matches in the values of static fields.",
			"    --method-names   [TRUE|false]  Report matches in the names of methods.",
			"    --ldc            [TRUE|false]  Report matches in LDC instructions inside methods.",
			"",
			"How to search:",
			"  --include [PATTERN]   Only recurse inside files matching the pattern.",
			"  --exclude [PATTERN]   Do not recurse inside files matching the pattern.",
			"",
			"  --binary-files binary            Report when binary files match, but don't print them.",
			"  --binary-files without-match     Do not search binary files.",
			"  -a, --binary-files text          Print matches inside binary files. (Might put crap in the terminal.)",
			"  --search-inside-special [true|FALSE]  Search binary files even when they're jars, zips etc.",
			""
		);

		System.exit(0);
	}

	private static void printy(PrintStream out, String... lines) {
		for(String line : lines) out.println(line);
	}
	
}
