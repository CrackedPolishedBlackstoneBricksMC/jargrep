package agency.highlysuspect.jargrep;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class JarGrep {
	public static void main(String[] args) throws Exception {
		Opts opts = Opts.parse(args);
		
		Writer.FsWriter result = new ConsoleWriter(System.out);
		
		for(Path target : opts.targets) {
			String filename = target.getFileName().toString();
			try(InputStream in = new BufferedInputStream(Files.newInputStream(target))) {
				visitInputStream(opts, result, filename, in);
			}
		}
		
		result.close();
	}
	
	static void visitInputStream(Opts opts, Writer.FsWriter fsWriter, String filename, InputStream in) throws Exception {
		byte[] allBytes = readAll(in);
		boolean binary = looksBinary(allBytes);
		boolean filenameMatch = opts.matches(filename);

		if(binary) {
			boolean visitedAsSpecial = false;
			
			//is it a jar or zip?
			//TODO check the first few bytes to catch renamed zips(?)
			if(filename.endsWith(".jar") || filename.endsWith(".zip")) {
				try(Writer.FsWriter archiveWriter = fsWriter.archiveWriter(filename)) {
					if(filenameMatch) archiveWriter.writeFileName(filename);
					visitZip(opts, archiveWriter, allBytes);
					visitedAsSpecial = true;
				}
			}
			
			//is it a class file?
			if(filename.endsWith(".class")) {
				try(Writer.ClsWriter classWriter = fsWriter.classWriter(filename)) {
					visitClass(opts, classWriter, allBytes);
					visitedAsSpecial = true;
				}
				
			}
			
			//TODO the whole "process all binary files as plain binary files" argument
			if(!visitedAsSpecial) {
				try(Writer.BinWriter binWriter = fsWriter.binaryWriter(filename)) {
					if(filenameMatch) binWriter.writeFileName(filename);
					visitBin(opts, binWriter, allBytes);
				}
			}
		} else {
			try(Writer.TxtWriter txtWriter = fsWriter.textWriter(filename)) {
				if(filenameMatch) txtWriter.writeFileName(filename);
				visitText(opts, fsWriter.textWriter(filename), allBytes);
			}
		}
	}
	
	static void visitZip(Opts opts, Writer.FsWriter fsvis, byte[] in) throws Exception {
		//don't want to close the original input stream!
		ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(in));
		
		ZipEntry entry;
		while((entry = zin.getNextEntry()) != null)
			if(!entry.isDirectory())
				visitInputStream(opts, fsvis, entry.getName(), zin);
	}
	
	static void visitBin(Opts opts, Writer.BinWriter result, byte[] bytes) throws Exception {
		//TODO don't line-by-line match for binary files
		// lol string matching over binary files is so broken anyway
		for(String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
			if(opts.matches(line)) {
				result.writeBinaryMatch();
				break;
			}
		}
	}
	
	static void visitText(Opts opts, Writer.TxtWriter result, byte[] bytes) throws Exception {
		for(String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
			if(opts.matches(line)) {
				result.writeTextMatch(line);
			}
		}
	}
	
	static void visitClass(Opts opts, Writer.ClsWriter cls, byte[] bytes) throws Exception {
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
						if(opts.matches(name)) fld.writeFieldName(name);
						
						String fieldValue = value == null ? null : value.toString();
						if(fieldValue != null && opts.matches(fieldValue)) {
							fld.writeFieldValue(name, fieldValue);
						}
					}
					return null;
				}
				
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					Writer.MthWriter mth = cls.methodWriter(name);
					if(opts.matches(name)) mth.writeMethodName(name);
					
					return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
						@Override
						public void visitLdcInsn(Object value) {
							if(value != null) {
								String ldc = value.toString();
								if(opts.matches(ldc)) mth.writeConstant(name, ldc);
							}
						}
					};
				}
			}, ClassReader.EXPAND_FRAMES);
		} catch (Exception e) {
			//TODO message
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
