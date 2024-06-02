package agency.highlysuspect.jargrep;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class Output {

	Deque<String> segments = new ArrayDeque<>();

	public void pushFilename(String filename) {
		segments.addLast("file " + filename);
	}

	public void pushClassName(String classname) {
		segments.addLast("class " + classname);
	}

	public void pushFieldName(String fieldName) {
		segments.addLast("field " + fieldName);
	}

	public void pushMethodName(String methodName) {
		segments.addLast("method " + methodName);
	}

	public void pushFieldValue() {
		segments.addLast("field value");
	}

	public void pushMethodBody() {
		segments.addLast("method body");
	}

	public void pop() {
		segments.removeLast();
	}

	public boolean topLevel() {
		return segments.isEmpty();
	}

	public void printCurrent() {
		printInner();
		System.out.println();
	}

	public void print(String message) {
		System.out.print(indentation(printInner()));
		System.out.println(message);
		System.out.println();
	}

	private int printInner() {
		int indent = 0;

		for(String s : segments) {
			System.out.print(indentation(indent));
			System.out.println(s);

			indent++;
		}
		return indent;
	}

	private final Map<Integer, String> indentStrs = new HashMap<>();
	public String indentation(int level) {
		return indentStrs.computeIfAbsent(level, this::mkIndentation);
	}

	private String mkIndentation(int level) {
		if(level == 0) {
			return "";
		}

		StringBuilder indentation = new StringBuilder();
		for(int i = 0; i < level * 3; i++) {
			indentation.append(' ');
		}

		indentation.setCharAt(indentation.length() - 3, '\\');
		indentation.setCharAt(indentation.length() - 2, '-');

		return indentation.toString();
	}
}
