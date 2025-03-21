package agency.highlysuspect.jargrep;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
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

public class SearchOpts {
	public Pattern grep;

	//What to search
	public static final int SEARCH_FILENAMES = 1;
	public static final int SEARCH_PLAINTEXT_FILES = 2;
	public static final int SEARCH_BINARY_FILES = 4;
	public static final int SEARCH_ARCHIVES = 8;
	public static final int SEARCH_CLASSES = 16;
	public static final int SEARCH_CLASS_FIELD_NAMES = 32;
	public static final int SEARCH_CLASS_FIELD_VALUES = 64;
	public static final int SEARCH_CLASS_METHOD_NAMES = 128;
	public static final int SEARCH_CLASS_METHOD_VALUES = 256;
	public static final int SEARCH_CLASS_USAGES = 1024; //yes theres a gap in the numbering
	
	public static final int ALWAYS_DO_RAW_SEARCH = 512;
	
	public FilenameFilter filenameFilter = new FilenameFilter();
	public int searchFlags = SEARCH_FILENAMES | SEARCH_PLAINTEXT_FILES | SEARCH_BINARY_FILES | SEARCH_ARCHIVES | SEARCH_CLASSES | SEARCH_CLASS_FIELD_NAMES | SEARCH_CLASS_FIELD_VALUES | SEARCH_CLASS_METHOD_NAMES | SEARCH_CLASS_METHOD_VALUES | SEARCH_CLASS_USAGES;
	
	public void set(int flag, boolean value) {
		if(value) searchFlags |= flag;
		else searchFlags &= ~flag;
	}
	
	public boolean get(int flag) {
		return (this.searchFlags & flag) != 0;
	}
	
	//TODO move format options to another place
	public boolean printFilename;
	public enum BinaryMode { BINARY, WITHOUT_MATCH, TEXT }
	public BinaryMode binaryMode = BinaryMode.BINARY;
	
	@Contract("null -> false")
	public boolean matches(@Nullable Object o) {
		if(o == null) return false;
		else return grep.matcher(o.toString()).find();
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
