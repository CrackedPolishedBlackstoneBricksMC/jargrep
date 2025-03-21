package agency.highlysuspect.jargrep;

import org.objectweb.asm.Opcodes;

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
	@SuppressWarnings("EnhancedSwitchMigration")
	interface MthWriter extends Writer {
		void writeMethodName(String name);
		void writeConstant(String name, String constant);
		void writeFieldAccess(String name, FieldAccessType type, String fieldOwner, String fieldName, String fieldDesc);
		void writeMethodAccess(String name, MethodCallType type, String methodOwner, String methodName, String methodDesc);

		enum FieldAccessType {
			GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD;

			public static FieldAccessType fromOpcode(int opcode) {
				switch(opcode) {
					case Opcodes.GETSTATIC: return GETSTATIC;
					case Opcodes.PUTSTATIC: return PUTSTATIC;
					case Opcodes.GETFIELD: return GETFIELD;
					case Opcodes.PUTFIELD: return PUTFIELD;
					default: throw new IllegalArgumentException();
				}
			}
		}

		enum MethodCallType {
			VIRTUAL, SPECIAL, STATIC, INTERFACE, DYNAMIC;

			public static MethodCallType fromOpcode(int opcode) {
				switch(opcode) {
					case Opcodes.INVOKEVIRTUAL: return VIRTUAL;
					case Opcodes.INVOKESPECIAL: return SPECIAL;
					case Opcodes.INVOKESTATIC: return STATIC;
					case Opcodes.INVOKEINTERFACE: return INTERFACE;
					case Opcodes.INVOKEDYNAMIC: return DYNAMIC;
					default: throw new IllegalArgumentException();
				}
			}
		}
	}
	
	interface Everything extends FsWriter, TxtWriter, BinWriter, ClsWriter, FldWriter, MthWriter {}
}
