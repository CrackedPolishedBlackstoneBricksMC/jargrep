package agency.highlysuspect.jargrep;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
			try(JarInputStream jis = new JarInputStream(new BufferedInputStream(Files.newInputStream(jar)))) {
				scan(jar, jis, pattern);
			}
		}
	}

	static void scan(Path jar, JarInputStream jis, Pattern pattern) throws IOException {
		ZipEntry entry;
		while((entry = jis.getNextEntry()) != null) {

			String name = entry.getName();
			if(pattern.matcher(name).find()) {
				System.out.println("in filename: " + jar + " " + name);
			}

			byte[] allBytes = wow(jis);
			if(pattern.matcher(new String(allBytes, StandardCharsets.UTF_8)).find()) {
				System.out.println("in file: " + jar + " " + name);
			}

			if(name.endsWith(".class")) {
				scanClass(jar, allBytes, pattern);
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

	static void scanClass(Path jar, byte[] bytes, Pattern pattern) throws IOException {
		ClassReader cr = new ClassReader(bytes);

		cr.accept(new ClassVisitor(Opcodes.ASM9) {
			String currentClass;

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				if(pattern.matcher(name).find())
					System.out.println("in class name: " + jar + " " + name);

				currentClass = name;
				super.visit(version, access, name, signature, superName, interfaces);
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				if(pattern.matcher(name).find())
					System.out.println("in field name: " + jar + " " + currentClass + " " + name);

				if(value != null) {
					if(pattern.matcher(value.toString()).find())
						System.out.println("in field value: " + jar + " " + currentClass + " " + value);
				}

				return super.visitField(access, name, descriptor, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if(pattern.matcher(name).find())
					System.out.println("in method name: " + jar + " " + currentClass + " " + name);

				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}, ClassReader.EXPAND_FRAMES);
	}
}
