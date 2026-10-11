package ru.stas.pupkov.preparations.jvm.code;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Один FQCN, загруженный двумя defining ClassLoader'ами — два разных типа в JVM.
 *
 * <p>Cast между ними падает, хотя байткод класса одинаковый.
 */
public class ClassLoaderIdentityDemo {

    private static final String PACKAGE_NAME = "cldemo";
    private static final String SIMPLE_NAME = "Payload";
    private static final String FQCN = PACKAGE_NAME + "." + SIMPLE_NAME;

    public static void main(String[] args) throws Exception {
        Path root = compilePayloadToTemp();
        URL[] urls = {root.toUri().toURL()};

        try (URLClassLoader loaderA = new URLClassLoader(urls, null);
             URLClassLoader loaderB = new URLClassLoader(urls, null)) {

            Class<?> classA = Class.forName(FQCN, true, loaderA);
            Class<?> classB = Class.forName(FQCN, true, loaderB);

            System.out.println("FQCN equal: " + classA.getName().equals(classB.getName()));
            System.out.println("Class objects identical: " + (classA == classB));
            System.out.println("loaderA defining: " + classA.getClassLoader());
            System.out.println("loaderB defining: " + classB.getClassLoader());

            Object instanceA = classA.getDeclaredConstructor().newInstance();
            Object instanceB = classB.getDeclaredConstructor().newInstance();

            Method idA = classA.getMethod("id");
            System.out.println("instanceA.id(): " + idA.invoke(instanceA));
            System.out.println("instanceB class: " + instanceB.getClass().getName());

            try {
                classA.cast(instanceB);
                System.out.println("ERROR: cast unexpectedly succeeded");
            } catch (ClassCastException expected) {
                System.out.println("cast instanceB -> type from loaderA: " + expected);
            }
        } finally {
            deleteRecursively(root);
        }
    }

    private static Path compilePayloadToTemp() throws IOException {
        Path root = Files.createTempDirectory("classloader-identity");
        Path packageDir = root.resolve(PACKAGE_NAME);
        Files.createDirectories(packageDir);

        Path sourceFile = packageDir.resolve(SIMPLE_NAME + ".java");
        String source = ""
                + "package " + PACKAGE_NAME + ";\n"
                + "public class " + SIMPLE_NAME + " {\n"
                + "    public String id() { return \"payload-v1\"; }\n"
                + "}\n";
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler unavailable (need JDK, not JRE-only runtime)");
        }
        int code = compiler.run(null, null, null, sourceFile.toAbsolutePath().toString());
        if (code != 0) {
            throw new IllegalStateException("compilation failed, exit=" + code);
        }
        return root;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort cleanup of temp dir
                        }
                    });
        }
    }
}
