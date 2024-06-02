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

		for(Path target : opts.targets) {
			InputStream in;

			try {
				in = Files.newInputStream(target);
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}

			in = new BufferedInputStream(in);

			try {
				processFile(opts, target.normalize().toString(), in);
			} finally {
				in.close();
			}
		}

		if(!opts.out.topLevel()) {
			opts.out.print("{jargrep bug} Seems like I pushed more than I popped...!");
		}
	}

	static void processFile(Opts opts, String filename, InputStream in) throws Exception {
		if(!opts.out.topLevel() && !opts.filenameFilter.test(filename))
			return;

		opts.out.pushFilename(filename);

		if(opts.searchFilenames && opts.matches(filename)) opts.out.print("filename");

		byte[] allBytes = readAll(in);
		boolean binary = looksBinary(allBytes);
		boolean wasSpecial = false;

		//if it's a jar or zip file, recurse inside that file
		if(filename.endsWith(".jar") || filename.endsWith(".zip")) {
			wasSpecial = processZip(opts, allBytes);
		}

		//if it's a class file, search inside the class
		if(opts.searchClasses && filename.endsWith(".class")) {
			wasSpecial = processClass(opts, allBytes);
		}

		//search the file?
		boolean doSearch;
		if(!binary) {
			doSearch = true;
		} else {
			if(opts.binaryMode == Opts.BinaryMode.WITHOUT_MATCH) {
				doSearch = false;
			} else if(!opts.searchInsideSpecial && wasSpecial) {
				doSearch = false;
			} else {
				doSearch = true;
			}
		}

		//search the file
		if(doSearch) {
			for(String line : new String(allBytes, StandardCharsets.UTF_8).split("\n")) {
				if(opts.matches(line)) {
					if(binary && opts.binaryMode == Opts.BinaryMode.BINARY) {
						opts.out.print("Binary file matches");
						break;
					} else {
						opts.out.print(line);
					}
				}
			}
		}

		opts.out.pop();
	}

	public static boolean looksBinary(byte[] bytes) {
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

	static boolean processZip(Opts opts, byte[] zipBytes) throws Exception {
		try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while((entry = zip.getNextEntry()) != null) {
				if(!entry.isDirectory() && opts.filenameFilter.test(entry.getName()))
					processFile(opts, entry.getName(), zip);
			}
			return true;
		} catch (IOException e) {
			opts.out.print("corrupt zip");
			return false;
		}
	}

	static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] shuttle = new byte[4096];
		int read;
		while((read = in.read(shuttle)) != -1) out.write(shuttle, 0, read);
		return out.toByteArray();
	}

	static boolean processClass(Opts opts, byte[] bytes) throws IOException {
		try {
			ClassReader cr = new ClassReader(bytes);

			cr.accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					opts.out.pushClassName(name);

					super.visit(version, access, name, signature, superName, interfaces);
				}

				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					opts.out.pushFieldName(name);

					if(opts.searchFieldNames && opts.matches(name)) {
						opts.out.printCurrent();
					}

					String fieldValue = value == null ? null : value.toString();
					if(fieldValue != null && opts.searchFieldValues && opts.matches(fieldValue)) {
						opts.out.pushFieldValue();
						opts.out.print(fieldValue);
						opts.out.pop();
					}

					opts.out.pop();

					return super.visitField(access, name, descriptor, signature, value);
				}

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					opts.out.pushMethodName(name);

					if(opts.searchMethodNames && opts.matches(name)) {
						opts.out.printCurrent();
					}

					if(opts.searchLdc) {
						opts.out.pushMethodBody();

						return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitLdcInsn(Object value) {
								String valueStr = value == null ? null : value.toString();
								if(valueStr != null && opts.matches(valueStr))
									opts.out.print("ldc: " + valueStr);
							}

							@Override
							public void visitEnd() {
								super.visitEnd();
								opts.out.pop(); //method body
								opts.out.pop(); //method
							}
						};
					} else {
						opts.out.pop();
						return super.visitMethod(access, name, descriptor, signature, exceptions);
					}
				}

				@Override
				public void visitEnd() {
					opts.out.pop();
				}
			}, ClassReader.EXPAND_FRAMES);
		} catch (Exception e) {
			opts.out.print("corrupt class");
			return false;
		}

		return true;
	}
}
