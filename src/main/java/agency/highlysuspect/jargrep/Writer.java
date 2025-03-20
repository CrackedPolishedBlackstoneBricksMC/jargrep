package agency.highlysuspect.jargrep;

public interface Writer extends AutoCloseable {
	/**
	 * abuse of java syntax that lets you use try-with-resources
	 */
	@Override
	default void close() {
		writeEnd();
	}
	
	void writeEnd();
	
	interface FileWriter extends Writer {
		void writeFileName(String filename);
		void writeDirectoryName(String dirName);
	}
	
	interface FsWriter extends FileWriter {
		FsWriter archiveWriter(String filename);
		TxtWriter textWriter(String filename);
		BinWriter binaryWriter(String filename);
		ClsWriter classWriter(String filename);
	}
	
	interface TxtWriter extends FileWriter {
		void writeTextMatch(String match);
	}
	interface BinWriter extends FileWriter {
		void writeBinaryMatch();
	}
	
	interface ClsWriter extends Writer {
		void writeClassName(String className);
		FldWriter fieldWriter(String fieldName);
		MthWriter methodWriter(String methodName);
	}
	interface FldWriter extends Writer {
		void writeFieldName(String name);
		void writeFieldValue(String name, String value);
	}
	interface MthWriter extends Writer {
		void writeMethodName(String name);
		void writeConstant(String name, String constant);
	}
	
	interface Everything extends FsWriter, TxtWriter, BinWriter, ClsWriter, FldWriter, MthWriter {}
}
