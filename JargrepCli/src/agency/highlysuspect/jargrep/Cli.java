package agency.highlysuspect.jargrep;

import joptsimple.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

public class Cli {
	public static void main(String[] args) throws Exception {
		new Cli().go(args);
	}
	
	public void go(String... args) throws Exception {
		parseOpts(args);
		
		JarGrep jg = new JarGrep(opts);
		
		try(Writer.FsWriter writer = new ConsoleWriter(System.out)) {
			for(Path target : targets) {
				if(Files.isRegularFile(target)) visitPath(jg, target, target.getFileName().toString(), writer);
				else if(Files.isDirectory(target)) {
					int recursionLimit = recurseIntoTargets ? Integer.MAX_VALUE : 1;
					Files.walkFileTree(target, Collections.emptySet(), recursionLimit, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							if(Files.isRegularFile(file)) visitPath(jg, file, target.relativize(file).toString(), writer);
							return FileVisitResult.CONTINUE;
						}
					});
				}
			}
		}
	}

	public void visitPath(JarGrep jg, Path path, String filename, Writer.FsWriter writer) throws IOException {
		try(InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
			jg.visitInputStream(writer, filename, in);
		}
	}

	public SearchOpts opts = new SearchOpts();
	//TODO formatting opts go here
	public List<Path> targets = new ArrayList<>();
	public boolean recurseIntoTargets = false;
	
	final OptionParser parser = new OptionParser(true);
	final OptionSpec<Void> help = accepts("help", "?").comment("Print this help message.").forHelp();
	final OptionSpec<Void> version = accepts("version", "v", "V").comment("Print version.");
	
	//how to match
	final OptionSpec<Void> fixedStrings = accepts("F", "fixed-string")
		.comment("Enable Pattern.LITERAL mode, searching for the string verbatim.");
	final OptionSpec<Void> caseInsensitive = accepts("i", "case-insensitive")
		.comment("Enable case-insensitive mode.");
	
	//how to search
	final OptionSpec<Void> recurseDirs = accepts("r", "recurse")
		.comment("Recursively search directories specified on the command line.");

	final OptionSpec<Boolean> searchFilename = trueflag(accepts("search-filenames")
		.comment("Report matches in the names of files."));
	final OptionSpec<Boolean> searchPlaintext = trueflag(accepts("search-text")
		.comment("Report matches inside text files."));
	final OptionSpec<Boolean> searchBinary = trueflag(accepts("search-binaries")
		.comment("Report matches inside binary files."));
	final OptionSpec<Boolean> searchArchive = trueflag(accepts("search-archives")
		.comment("Report matches inside nested archives."));
	final OptionSpec<Boolean> searchClass = trueflag(accepts("search-classes")
		.comment("Report matches inside class files."));
	final OptionSpec<Boolean> searchField = trueflag(accepts("search-fields")
		.comment("Report matches inside class field names."));
	final OptionSpec<Boolean> searchFieldValue = trueflag(accepts("search-field-values")
		.comment("Report matches inside some(!) final fields."));
	final OptionSpec<Boolean> searchMethod = trueflag(accepts("search-methods")
		.comment("Report matches inside class method names."));
	final OptionSpec<Boolean> searchLdc = trueflag(accepts("search-ldcs")
		.comment("Report matches in LDC constants inside methods."));
	final OptionSpec<Boolean> searchUsages = falseflag(accepts("search-usages")
		.comment("Attempt to report matches inside GET/PUT and INVOKE instructions inside methods."));
	final OptionSpec<Boolean> onlyFilename = falseflag(accepts("l", "files-with-matches", "classes-with-matches")
		.comment("Only show the names of matching files/classes.")); 
	
	final OptionSpec<Boolean> alwaysRawSearch = falseflag(accepts("alwaysRawSearch")
		.comment("Also perform a raw search over binaries even if they can be parsed as classes/zips."));
	
	private final OptionSpecBuilder includeB = accepts("include")
		.comment("When searching archives, only look in files matching this pattern.")
		.availableIf(searchArchive); //mutually exclusive option
	final OptionSpec<String> exclude = accepts("exclude")
		.comment("When searching archives, don't look in files matching this pattern.")
		.availableIf(searchArchive).availableUnless(includeB).withRequiredArg();
	final OptionSpec<String> include = includeB.availableUnless(exclude).withRequiredArg();
	//output
//	final OptionSpec<Void> withFilename = accepts("with-filename", "H").noComment();
//	final OptionSpec<Void> noFilename = accepts("no-filename", "h").noComment();
	
	{
		//more width
		parser.formatHelpWith(new TweakedHelpFormatter(
			help, version, fixedStrings, caseInsensitive,
			include, exclude,
			recurseDirs,
			searchFilename, searchPlaintext, searchBinary, searchArchive,
			searchClass, searchField, searchFieldValue, searchMethod, searchLdc, searchUsages,
			onlyFilename,
			alwaysRawSearch,
			parser.nonOptions() //required for the joptsimple internals im abusing
		));
	}
	
	//papering over joptsimple's awkward api
	interface HasComment {
		OptionSpecBuilder comment(String... commentLines);
	}
	HasComment accepts(String... options) {
		return commentLines -> {
			String comment = String.join("\n", commentLines); //TODO doesnt actually work lol
			if(options.length == 1) return parser.accepts(options[0], comment);
			else return parser.acceptsAll(Arrays.asList(options), comment);
		};
	}
	OptionSpec<Boolean> trueflag(OptionSpecBuilder builder) {
		return builder.withOptionalArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(true);
	}
	OptionSpec<Boolean> falseflag(OptionSpecBuilder builder) {
		//if it defaults to false, you gotta pass the argument
		//TODO this is just because i can't tell the difference between --flag and --flag=false in joptsimple api
		// if --flag's default value is false
		return builder.withRequiredArg().withValuesConvertedBy(BooleanConv.I).defaultsTo(false);
	}
	
	String getInvocation() {
		String trueName;
		try {
			//lol
			trueName = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
		} catch (Exception e) {
			trueName = "jargrep.jar";
		}
		return "Usage: java -jar " + trueName + " [OPTION]... PATTERN [FILE]...";
	}

	String getVersion() {
		String hmm = getClass().getPackage().getImplementationVersion();
		return hmm == null ? "Unknown Version" : hmm;
	}
	
	RuntimeException usage() {
		for(String s : new String[] {
			getInvocation(),
			"",
			"jargrep is a recursive Java archive searching tool.",
			"For example, to search for 'needle' inside 'haystack.jar', try",
			"",
			"  jargrep \"needle\" haystack.jar",
			"",
			"If you don't specify any files to search, jargrep will search",
			"all .jar, .zip, and .class files in the current directory.",
			"",
			"This is jargrep " + getVersion() +
				". Pass --help for information about all options.",
		}) System.out.println(s);
		
		
		return exit(1);
	}
	
	RuntimeException halp() {
		System.out.println(getInvocation());
		System.out.println();
		//really guys
		try {
			parser.printHelpOn(System.out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return exit(1);
	}
	
	RuntimeException version() {
		System.out.println("jargrep version " + getVersion());
		return exit(1);
	}
	
	RuntimeException exit(int code) {
		//TODO don't actually exit in testing
		System.exit(code);
		return new IllegalStateException("exited");
	}
	
	@SuppressWarnings("unchecked")
	public void parseOpts(String... args) {
		OptionSet set = parser.parse(args);
		
		if(!set.hasOptions() && set.nonOptionArguments().isEmpty()) {
			throw usage();
		}
		
		if(set.has(help)) {
			throw halp();
		}
		
		if(set.has(version)) {
			throw version();
		}
		
		//first "non-option" is the pattern to compile,
		//everything else is a filename to search
		String patternToCompile = null;
		for(Object o : set.nonOptionArguments()) {
			String s = o.toString();
			if(s.isEmpty()) continue;
			if(patternToCompile == null) patternToCompile = s;
			else targets.add(Paths.get(s));
		}
		
		if(patternToCompile == null) {
			System.err.println("No pattern");
			throw usage();
		}
		
		int patternCompileOptions = 0;
		patternCompileOptions |= set.has(fixedStrings) ? Pattern.LITERAL : 0;
		patternCompileOptions |= set.has(caseInsensitive) ? Pattern.CASE_INSENSITIVE : 0;
		opts.grep = Pattern.compile(patternToCompile, patternCompileOptions);

		this.recurseIntoTargets = set.has(recurseDirs);
		
		//default to archives in the current directory
		if(targets.isEmpty()) {
			File[] cwdJars = new File(".").listFiles((f, name) ->
				name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".class"));
			if(cwdJars != null && cwdJars.length > 0) {
				Arrays.stream(cwdJars).map(File::toPath).forEach(targets::add);
			}
			
			if(targets.isEmpty()) {
				System.err.println("No files specified on command line, and no jars/zips/classes in current directory.");
				throw usage();
			}
		}
		
		//// search control ////
		
		int[] flags = {
			SearchOpts.SEARCH_FILENAMES,
			SearchOpts.SEARCH_PLAINTEXT_FILES,
			SearchOpts.SEARCH_BINARY_FILES,
			SearchOpts.SEARCH_ARCHIVES,
			SearchOpts.SEARCH_CLASSES,
			SearchOpts.SEARCH_CLASS_FIELD_NAMES,
			SearchOpts.SEARCH_CLASS_FIELD_VALUES,
			SearchOpts.SEARCH_CLASS_METHOD_NAMES,
			SearchOpts.SEARCH_CLASS_METHOD_VALUES,
			SearchOpts.SEARCH_CLASS_USAGES,
			SearchOpts.ALWAYS_DO_RAW_SEARCH,
			SearchOpts.ONLY_SHOW_FILENAME
		};
		OptionSpec<?>[] specs = {
			searchFilename,
			searchPlaintext,
			searchBinary,
			searchArchive,
			searchClass,
			searchField,
			searchFieldValue,
			searchMethod,
			searchLdc,
			searchUsages,
			alwaysRawSearch,
			onlyFilename
		};
		for(int i = 0; i < flags.length; i++) {
			OptionSpec<Boolean> spec = (OptionSpec<Boolean>) specs[i];
			opts.set(flags[i], set.valueOf(spec));
		}
		
		if(set.has(exclude)) {
			opts.filenameFilter.pattern = Pattern.compile(set.valueOf(exclude));
			opts.filenameFilter.exclude = true;
		} else if(set.has(include)) {
			opts.filenameFilter.pattern = Pattern.compile(set.valueOf(include));
			opts.filenameFilter.exclude = false;
		}
		
		//// output formatting options ////
		
		//TODO:
		//if binary-files is set, set binarymode from it
		//else if '-a' or '--text' is set, set to text
		opts.binaryMode = SearchOpts.BinaryMode.BINARY;

//		if(set.has(withFilename)) {
//			opts.printFilename = true;
//		} else if(set.has(noFilename)) {
//			opts.printFilename = false;
//		} else {
//			opts.printFilename = targets.size() > 1;
//		}
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
	
	public static class TweakedHelpFormatter extends BuiltinHelpFormatter {
		@SuppressWarnings("unchecked")
		public TweakedHelpFormatter(OptionSpec<?>... order) {
			super(120, 2);
			
			//all joptsimple classes that implement one implement the other
			//also it needs to be mutable
			this.order = (List<OptionDescriptor>) (Object) new ArrayList<>(Arrays.asList(order));
		}
		
		private final List<OptionDescriptor> order;
		
		@Override
		public String format(Map<String, ? extends OptionDescriptor> options) {
//			this.optionRows.reset(); //private access... really dude
//			this.nonOptionRows.reset();

//			Comparator<OptionDescriptor> comparator = (first, second) -> {
//				return ((String)first.options().iterator().next()).compareTo((String)second.options().iterator().next());
//			};
//			Set<OptionDescriptor> sorted = new TreeSet(comparator);
//			sorted.addAll(options.values());
			this.addRows(order);
			return this.formattedHelpOutput();
		}
		
		@Override
		protected void addHeaders(Collection<? extends OptionDescriptor> options) {
			//no
		}
	}
}
