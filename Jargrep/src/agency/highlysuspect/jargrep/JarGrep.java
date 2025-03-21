package agency.highlysuspect.jargrep;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static agency.highlysuspect.jargrep.SearchOpts.*;

public class JarGrep {
	public JarGrep(SearchOpts opts) {
		this.opts = opts;
	}
	
	public final SearchOpts opts;
	
	public void visitInputStream(Writer.FsWriter fsWriter, String filename, InputStream in) throws IOException {
		byte[] allBytes = readAll(in);
		boolean binary = looksBinary(allBytes);
		boolean filenameMatch = opts.get(SEARCH_FILENAMES) && opts.matches(filename);

		if(binary) {
			boolean visitedAsSpecial = false;
			
			//is it a jar or zip?
			//TODO check the first few bytes to catch renamed zips(?)
			if(opts.get(SEARCH_ARCHIVES) && (filename.endsWith(".jar") || filename.endsWith(".zip"))) {
				try(Writer.FsWriter archiveWriter = fsWriter.archiveWriter(filename)) {
					if(filenameMatch) archiveWriter.writeFileName(filename);
					visitZip(archiveWriter, allBytes);
					visitedAsSpecial = true;
				}
			}
			
			//is it a class file?
			if(opts.get(SEARCH_CLASSES) && filename.endsWith(".class")) {
				try(Writer.ClsWriter classWriter = fsWriter.classWriter(filename)) {
					visitClass(classWriter, allBytes);
					visitedAsSpecial = true;
				}
			}
			
			//TODO the whole "process all binary files as plain binary files" argument
			if(
				opts.get(SEARCH_BINARY_FILES) &&
				(!visitedAsSpecial || opts.get(ALWAYS_DO_RAW_SEARCH))
			) {
				try(Writer.BinWriter binWriter = fsWriter.binaryWriter(filename)) {
					if(filenameMatch) binWriter.writeFileName(filename);
					visitBin(binWriter, allBytes);
				}
			}
		} else {
			if(opts.get(SEARCH_PLAINTEXT_FILES)) {
				try(Writer.TxtWriter txtWriter = fsWriter.textWriter(filename)) {
					if(filenameMatch) txtWriter.writeFileName(filename);
					visitText(fsWriter.textWriter(filename), allBytes);
				}
			}
		}
	}
	
	public void visitZip(Writer.FsWriter fsWriter, byte[] in) throws IOException {
		//don't want to close the original input stream!
		ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(in));
		
		ZipEntry entry;
		while((entry = zin.getNextEntry()) != null) {
			String name = entry.getName();
			if(opts.filenameFilter.test(name)) {
				//TODO kind of a hack
				if(entry.isDirectory() && opts.get(SEARCH_FILENAMES) && opts.matches(name))
					fsWriter.writeDirectoryName(name);
				
				else visitInputStream(fsWriter, name, zin);
			}
		}
	}
	
	@SuppressWarnings("CharsetObjectCanBeUsed") //teavm
	public void visitBin(Writer.BinWriter result, byte[] bytes) throws IOException {
		//TODO don't line-by-line match for binary files
		// lol string matching over binary files line-by-line is so broken anyway
		for(String line : new String(bytes, "UTF-8").split("\n")) {
			if(opts.matches(line)) {
				result.writeBinaryMatch();
				break;
			}
		}
	}
	
	@SuppressWarnings("CharsetObjectCanBeUsed") //teavm
	public void visitText(Writer.TxtWriter result, byte[] bytes) throws IOException {
		for(String line : new String(bytes, "UTF-8").split("\n")) {
			if(opts.matches(line)) result.writeTextMatch(line);
		}
	}

	private static final String[] immediates = new String[16];
	static {
		immediates[Opcodes.ACONST_NULL] = "null";
		immediates[Opcodes.ICONST_M1] = "-1";
		immediates[Opcodes.ICONST_0] = "0";
		immediates[Opcodes.ICONST_1] = "1";
		immediates[Opcodes.ICONST_2] = "2";
		immediates[Opcodes.ICONST_3] = "3";
		immediates[Opcodes.ICONST_4] = "4";
		immediates[Opcodes.ICONST_5] = "5";
		immediates[Opcodes.LCONST_0] = "0L";
		immediates[Opcodes.LCONST_1] = "1L";
		immediates[Opcodes.FCONST_0] = "0.0F";
		immediates[Opcodes.FCONST_1] = "1.0F";
		immediates[Opcodes.FCONST_2] = "2.0F";
		immediates[Opcodes.DCONST_0] = "0.0D";
		immediates[Opcodes.DCONST_1] = "1.0D";
	}
	
	public void visitClass(Writer.ClsWriter cls, byte[] bytes) {
		try {
			ClassReader cr = new ClassReader(bytes);
			
			cr.accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					if(opts.matches(name)) cls.writeClassName(name);
					super.visit(version, access, name, signature, superName, interfaces);
				}
				
				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					try(Writer.FldWriter fld = cls.fieldWriter(name)) {
						if(opts.get(SEARCH_CLASS_FIELD_NAMES) && opts.matches(name)) fld.writeFieldName(name);
						if(opts.get(SEARCH_CLASS_FIELD_VALUES) && opts.matches(value)) fld.writeFieldValue(name, value.toString());
					}
					return null;
				}
				
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					Writer.MthWriter mth = cls.methodWriter(name);
					if(opts.get(SEARCH_CLASS_METHOD_NAMES) && opts.matches(name)) mth.writeMethodName(name);
					
					return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
						@Override
						public void visitLdcInsn(Object value) {
							if(opts.get(SEARCH_CLASS_METHOD_VALUES)) matchConst(value);
						}

						@Override
						public void visitInsn(int opcode) {
							if(opts.get(SEARCH_CLASS_METHOD_VALUES) && opcode < immediates.length) matchConst(immediates[opcode]);
						}

						private void matchConst(Object it) {
							if(opts.matches(it)) mth.writeConstant(name, it.toString());
						}

						@Override
						public void visitFieldInsn(int opcode, String owner, String fieldName, String descriptor) {
							if(opts.get(SEARCH_CLASS_USAGES) && (opts.matches(owner) || opts.matches(fieldName) || opts.matches(descriptor)))
								mth.writeFieldAccess(name, Writer.MthWriter.FieldAccessType.fromOpcode(opcode), owner, fieldName, descriptor);
						}

						@Override
						public void visitMethodInsn(int opcode, String owner, String methodName, String descriptor, boolean isInterface) {
							if(opts.get(SEARCH_CLASS_USAGES) && (opts.matches(owner) || opts.matches(methodName) || opts.matches(descriptor)))
								mth.writeMethodAccess(name, Writer.MthWriter.MethodCallType.fromOpcode(opcode), owner, methodName, descriptor);
						}

						@Override
						public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
							//you know, probably not the best idea to just shove invokedynamics into the owner/method/desc trichotomy
							//also we don't search args
							if(opts.get(SEARCH_CLASS_USAGES) && (opts.matches(bootstrapMethodHandle.getOwner()) || opts.matches(bootstrapMethodHandle.getName()) || opts.matches(descriptor)))
								mth.writeMethodAccess(name, Writer.MthWriter.MethodCallType.DYNAMIC, bootstrapMethodHandle.getOwner(), bootstrapMethodHandle.getName(), descriptor);
						}
					};
				}
			}, ClassReader.EXPAND_FRAMES);
		} catch (Exception e) {
			e.printStackTrace(); //TODO better message
		}
	}

	protected static boolean looksBinary(byte[] bytes) {
		//quick check for class files:
		if(bytes.length >= 4 &&
			bytes[0] == (byte) 0xCA &&
			bytes[1] == (byte) 0xFE &&
			bytes[2] == (byte) 0xBA &&
			bytes[3] == (byte) 0xBE
		) {
			return true;
		}

		//I heard regular grep uses 32k for this?
		int max = Math.min(32767, bytes.length);
		for(int i = 0; i < max; i++) {
			if(bytes[i] == 0) {
				return true;
			}
		}
		return false;
	}


	protected static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] shuttle = new byte[4096];
		int read;
		while((read = in.read(shuttle)) != -1) out.write(shuttle, 0, read);
		return out.toByteArray();
	}
}
