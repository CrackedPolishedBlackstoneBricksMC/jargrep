package agency.highlysuspect.jargrep;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Objects;

import static agency.highlysuspect.jargrep.Writer.*;

public class ConsoleWriter implements FsWriter, TxtWriter, BinWriter, ClsWriter, FldWriter, MthWriter {
	//root writer
	public ConsoleWriter(PrintStream out) {
		this.out = out;
		this.indent = -1;
		this.parent = null;
		this.header = null;
	}
	
	//child writer
	protected ConsoleWriter(ConsoleWriter parent, @NotNull String header) {
		this.out = parent.out;
		this.indent = parent.indent + 1;
		this.parent = parent;
		this.header = Objects.requireNonNull(header);
	}
	
	protected final PrintStream out;
	protected final int indent;
	protected final @Nullable ConsoleWriter parent;
	protected final @Nullable String header;
	
	protected boolean headerShown = false;
	
	private void println(int indent, String s) {
		if(indent > 0) {
			for(int i = 0; i < indent - 1; i++) out.print("  |");
			out.print("-> ");
		}
		out.println(s);
	}
	
	protected void printHeader() {
		if(!headerShown) {
			if(parent != null) parent.printHeader();
			if(header != null) println(indent, header);
			headerShown = true;
		}
	}
	
	protected void child(String s) {
		printHeader();
		println(indent + 1, s);
	}
	
	@Override
	public void writeEnd() {
		//no-op
	}
	
	@Override
	public FsWriter archiveWriter(String filename) {
		return new ConsoleWriter(this, "archive " + filename);
	}
	
	@Override
	public TxtWriter textWriter(String filename) {
		return new ConsoleWriter(this, "text file " + filename);
	}
	
	@Override
	public BinWriter binaryWriter(String filename) {
		return new ConsoleWriter(this, "binary file " + filename);
	}
	
	@Override
	public ClsWriter classWriter(String filename) {
		return new ConsoleWriter(this, "class " + filename);
	}
	
	@Override
	public FldWriter fieldWriter(String fieldName) {
		return new ConsoleWriter(this, "field " + fieldName);
	}
	
	@Override
	public MthWriter methodWriter(String methodName) {
		return new ConsoleWriter(this, "method " + methodName);
	}
	
	@Override
	public void writeBinaryMatch() {
		child("(binary file matches)");
	}
	
	@Override
	public void writeClassName(String className) {
		child("class: " + className);
	}
	
	@Override
	public void writeTextMatch(String match) {
		child("match: " + match);
	}
	
	@Override
	public void writeFileName(String filename) {
		printHeader();
	}
	
	@Override
	public void writeDirectoryName(String dirName) {
		//TODO kind of a hack
		child("directory " + dirName);
	}
	
	@Override
	public void writeFieldName(String name) {
		printHeader();
	}
	
	@Override
	public void writeFieldValue(String name, String value) {
		child("value: " + value);
	}
	
	@Override
	public void writeMethodName(String name) {
		printHeader();
	}
	
	@Override
	public void writeConstant(String name, String constant) {
		child("constant: " + constant);
	}

	@Override
	public void writeFieldAccess(String name, FieldAccessType type, String fieldOwner, String fieldName, String fieldDesc) {
		child(type.name() + ": " + fieldOwner + "#" + fieldName + " " + fieldDesc);
	}

	@Override
	public void writeMethodAccess(String name, MethodCallType type, String methodOwner, String methodName, String methodDesc) {
		child(type.name() + ": " + methodOwner + "#" + methodName + " " + methodDesc);
	}
}
