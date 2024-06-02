package agency.highlysuspect.jargrep;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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
	public Filter filenameFilter = new Filter();

	//How to output
	public Output out = new Output();
	public boolean printFilename;

	public static Opts parse(String[] args) {
		Opts opts = new Opts();

		String patternToCompile = null;
		int patternCompileOptions = 0;
		boolean defaultHBehavior = true;

		OptLexer lexer = new OptLexer(args);
		for(Opt opt : lexer) {
			//unlabeled options
			if(opt.isMisc()) {
				String s = opt.getMisc();
				if(patternToCompile == null) {
					patternToCompile = s;
				} else {
					opts.targets.add(Paths.get(s));
				}
			}

			else if(opt.is("help")) {
				printHelpAndExit();
			}

			else if(opt.is("F", "fixed-strings")) {
				patternCompileOptions |= Pattern.LITERAL;
			} else if(opt.is("i", "case-insensitive")) {
				patternCompileOptions |= Pattern.CASE_INSENSITIVE;
			}

			//what to search (jargrep options)
			else if(opt.is("search-filenames")) {
				opts.searchFilenames = lexer.boolValue(true);
			} else if(opt.is("search-contents")) {
				opts.searchFileContents = lexer.boolValue(true);
			} else if(opt.is("search-classes")) {
				opts.searchClasses = lexer.boolValue(true);
			} else if(opt.is("field-names")) {
				opts.searchFieldNames = lexer.boolValue(true);
			} else if(opt.is("field-values")) {
				opts.searchFieldValues = lexer.boolValue(true);
			} else if(opt.is("method-names")) {
				opts.searchMethodNames = lexer.boolValue(true);
			} else if(opt.is("ldc")) {
				opts.searchLdc = lexer.boolValue(true);
			}

			//how to search
			else if(opt.is("binary-files")) {
				String binmode = lexer.value();
				if(binmode == null) throw new IllegalArgumentException("Expected binary-files");
				switch(binmode) {
					case "binary": opts.binaryMode = BinaryMode.BINARY; break;
					case "without-match": opts.binaryMode = BinaryMode.WITHOUT_MATCH; break;
					case "text": opts.binaryMode = BinaryMode.TEXT; break;
					default: throw new IllegalArgumentException("Unknown binary-files mode " + binmode);
				}
			} else if(opt.is("a", "text")) {
				opts.binaryMode = BinaryMode.TEXT;
			} else if(opt.is("search-inside-special")) {
				opts.searchInsideSpecial = lexer.boolValue(false);
			}

			else if(opt.is("exclude")) {
				opts.filenameFilter.pattern = Pattern.compile(lexer.value());
				opts.filenameFilter.exclude = true;
			} else if(opt.is("include")) {
				opts.filenameFilter.pattern = Pattern.compile(lexer.value());
				opts.filenameFilter.exclude = false;
			}

			//how to output
			else if(opt.is("H", "with-filename")) {
				defaultHBehavior = false;
				opts.printFilename = true;
			} else if(opt.is("h", "no-filename")) {
				defaultHBehavior = false;
				opts.printFilename = false;
			} else {
				System.err.println("Unrecognized option: " + opt);
				printUsageAndExit();
			}
		}

		if(patternToCompile == null) {
			throw new IllegalArgumentException("No pattern");
		}
		opts.grep = Pattern.compile(patternToCompile, patternCompileOptions);

		if(opts.targets.isEmpty()) {
			//use all jars in current directory
			File[] cwdJars = new File(".").listFiles((f, name) ->
				name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".class"));
			if(cwdJars != null && cwdJars.length > 0) {
				Arrays.stream(cwdJars).map(File::toPath).forEach(opts.targets::add);
			}

			if(opts.targets.isEmpty()) {
				System.err.println("No files specified on command line, and no jars/zips/classes in current directory.");
				printUsageAndExit();
			}
		}

		if(defaultHBehavior) {
			opts.printFilename = opts.targets.size() > 1;
		}

		return opts;
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

	private interface Opt {
		default boolean is(String... xs) {
			if(this instanceof Named) {
				for(String s : xs) {
					if(((Named) this).opt.equals(s)) return true;
				}
			}
			return false;
		}
		default boolean isMisc() {
			return this instanceof Misc;
		}
		default String getMisc() {
			return ((Misc) this).etc;
		}
	}
	private static class Named implements Opt {
		Named(String opt) { this.opt = opt; }
		String opt;
		@Override public String toString() { return "Opt " + opt; }
	}
	private static class Misc implements Opt {
		Misc(String etc) { this.etc = etc; }
		String etc;
		@Override public String toString() { return "Misc " + etc; }
	}

	private static class OptLexer implements Iterator<Opt>, Iterable<Opt> {
		public OptLexer(String[] args) {
			this.args = args;
		}

		private final String[] args;
		private int idx, subitem;

		private static final int MODE_READY = 0;
		private static final int MODE_SHORT = 1;
		private static final int MODE_MISCONLY = 2;

		private int mode = MODE_READY;

		@Override
		public boolean hasNext() {
			return idx < args.length;
		}

		@Override
		public Opt next() {
			while(true) {
				String currentArg = args[idx];
				switch(mode) {
					case MODE_READY:
						if(currentArg.equals("--")) { //separator between options and nonoptions
							mode = MODE_MISCONLY;
							idx++;
							continue;
						} else if(currentArg.startsWith("--")) { //long option
							idx++;
							return new Named(currentArg.substring(2));
						} else if(currentArg.startsWith("-")) { //short option
							mode = MODE_SHORT;
							subitem = 1;
							continue;
						} else {
							idx++;
							return new Misc(currentArg);
						}
					case MODE_SHORT:
						Named shortOpt = new Named(String.valueOf(currentArg.charAt(subitem)));
						subitem++;
						if(subitem == currentArg.length()) {
							//parsed all short options
							subitem = 0;
							mode = MODE_READY;
							idx++;
						}

						return shortOpt;
					case MODE_MISCONLY:
						idx++;
						return new Misc(currentArg);
				}
			}
		}

		public String value() {
			if(mode == MODE_SHORT) {
				if(subitem != args[idx].length()) {
					String rest = args[idx].substring(subitem);
					mode = MODE_READY;
					idx++;
					return rest;
				}
			} else {
				if(hasNext() && args[idx + 1].charAt(0) != '-') return args[idx++];
			}

			return null;
		}

		public boolean boolValue(boolean def) {
			if(mode == MODE_SHORT) {
				String value = value();
				return value == null ? def : flag(value);
			} else if(hasNext() && isFlag(args[idx])) {
				idx++;
				return flag(args[idx - 1]);
			} else return def;
		}

		private boolean isFlag(String s) {
			switch(s) {
				case "true": case "yes": case "on": case "false": case "no": case "off": return true;
				default: return false;
			}
		}

		private boolean flag(String s) {
			switch(s) {
				case "true":
				case "yes":
				case "on":
					return true;
				default:
					return false;
			}
		}

		@Override
		public Iterator<Opt> iterator() {
			return this;
		}
	}
}
