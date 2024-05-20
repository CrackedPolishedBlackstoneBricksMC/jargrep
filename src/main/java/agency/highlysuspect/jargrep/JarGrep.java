package agency.highlysuspect.jargrep;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class JarGrep {
	public static void main(String[] args) throws Exception {
		OptionParser opt = new OptionParser();

		OptionSet set = opt.parse(args);

		if(set.nonOptionArguments().size() < 2) {
			System.err.println("Usage: java -jar jargrep.jar -- [PATTERN] [JARS]");
			System.exit(1);
		}

		Iterator<Object> nonOptionArguments = (Iterator<Object>) set.nonOptionArguments().iterator();

		Pattern pattern = Pattern.compile(nonOptionArguments.next().toString());

		List<Path> jars = new ArrayList<>();
		nonOptionArguments.forEachRemaining(p -> jars.add(Paths.get(p.toString())));

		for(Path jar : jars) {
			try(
				Context f = new Context().push(jar.toString());
				JarInputStream jis = new JarInputStream(new BufferedInputStream(Files.newInputStream(jar)))
			) {
				scan(f, jis, pattern);
			}
		}
	}

	public static class Context implements AutoCloseable {
		Deque<String> context = new ArrayDeque<>();

		public Context push(String ctx) {
			context.addLast(ctx);
			return this;
		}

		@Override
		public void close() {
			context.removeLast();
		}

		@Override
		public String toString() {
			return String.join(":", context);
		}

		public Context print(String message) {
			System.out.println(this + " " + message);
			return this;
		}
	}

	static void scan(Context context, JarInputStream jis, Pattern pattern) throws IOException {
		ZipEntry entry;
		while((entry = jis.getNextEntry()) != null) {
			String name = entry.getName();

			try(Context ctx = context.push(name)) {
				if(pattern.matcher(name).find()) {
					ctx.push("(filename)").print("").close();
				}

				byte[] allBytes = wow(jis);
				String allBytesAsString = new String(allBytes, StandardCharsets.UTF_8);
				for(String line : allBytesAsString.split("\n")) {
					if(pattern.matcher(name).find()) {
						ctx.print(line);
					}
				}

				if(name.endsWith(".class")) {
					scanClass(ctx, allBytes, pattern);
				}
			}
		}
	}

	static byte[] wow(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] shuttle = new byte[4096];
		int read;
		while((read = in.read(shuttle)) != -1) out.write(shuttle, 0, read);
		return out.toByteArray();
	}

	static void scanClass(Context ctx, byte[] bytes, Pattern pattern) throws IOException {
		ClassReader cr = new ClassReader(bytes);

		cr.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				ctx.push("class " + name);
				super.visit(version, access, name, signature, superName, interfaces);
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				ctx.push("field " + name);

				if(pattern.matcher(name).find())
					ctx.print("");

				if(value != null && pattern.matcher(value.toString()).find())
					ctx.push("(field value)").print(value.toString()).close();

				ctx.close();

				return super.visitField(access, name, descriptor, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				ctx.push("method " + name);

				if(pattern.matcher(name).find())
					ctx.print("");

				return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
					@Override
					public void visitLdcInsn(Object value) {
						if(value != null && pattern.matcher(value.toString()).find())
							ctx.push("(ldc)").print(value.toString()).close();
					}

					@Override
					public void visitEnd() {
						super.visitEnd();
						ctx.close();
					}
				};
			}

			@Override
			public void visitEnd() {
				ctx.close();
			}
		}, ClassReader.EXPAND_FRAMES);
	}
}
