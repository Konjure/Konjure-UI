/*
 * MIT License
 *
 * Copyright (c) 2018 Konjure
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.konjure.toolkit.plugins;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import com.yahoo.platform.yui.compressor.YUICompressor;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.konjure.toolkit.KonjureToolkit;
import org.konjure.toolkit.plugin.KonjurePlugin;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Connor Hollasch
 * @since 5/9/2018
 */
public class KonjureCompiler implements KonjurePlugin
{
    @Override
    public String cliName ()
    {
        return "compiler";
    }

    @Override
    public void populateOptionSpec (final Options options)
    {
        final Option srcDirectoryOption = new Option("s", "src", true, "Source directory");
        srcDirectoryOption.setArgName("directory");
        srcDirectoryOption.setRequired(true);

        final Option destDirectoryOption = new Option("d", "dest", true, "Destination directory");
        destDirectoryOption.setArgName("directory");
        destDirectoryOption.setRequired(true);

        final Option lineBreakOption = new Option("lb", "line-break", true, "Sets minified line break length");
        lineBreakOption.setArgName("length");
        lineBreakOption.setRequired(false);

        final Option versionOption = new Option("v", "version", true, "Compilation version");
        versionOption.setArgName("version");
        versionOption.setRequired(true);

        final Option minifyOption = new Option("m", "minify", false, "Will minify css and js contents");
        minifyOption.setRequired(false);

        final Option recursiveOption = new Option("r", "recursive", false, "Searches child directories recursively");
        recursiveOption.setRequired(false);

        options.addOption(srcDirectoryOption);
        options.addOption(destDirectoryOption);
        options.addOption(lineBreakOption);
        options.addOption(versionOption);
        options.addOption(minifyOption);
        options.addOption(recursiveOption);
    }

    @Override
    public void execute (final CommandLine commandLine)
    {
        final String srcArg = commandLine.getOptionValue("s");
        final String destArg = commandLine.getOptionValue("d");
        final String versionArg = commandLine.getOptionValue("v");

        final boolean minify = commandLine.hasOption("m");
        final boolean recursive = commandLine.hasOption("r");

        int lineBreakLength = -1;
        if (minify && commandLine.hasOption("lb")) {
            try {
                lineBreakLength = Integer.parseInt(commandLine.getOptionValue("lb"));
            } catch (final NumberFormatException e) {
                KonjureToolkit.getLogger().error("Could not parse integer for line break length", e);
            }
        }

        final File src = new File(srcArg);
        final File dest = new File(destArg);

        if (!src.exists()) {
            KonjureToolkit.getLogger().error("No such source directory " + src.getAbsolutePath());
            return;
        }

        if (!src.isDirectory()) {
            KonjureToolkit.getLogger().error("Source specified is not a directory");
            return;
        }

        if (!dest.exists()) {
            dest.mkdirs();
        }

        final CompilationScope scope;
        if (commandLine.getArgs().length > 0) {
            scope = CompilationScope.valueOf(commandLine.getArgs()[0].toUpperCase());
        } else {
            scope = CompilationScope.ALL;
        }

        if (scope == null) {
            KonjureToolkit.getLogger().error("No such compilation scope " + commandLine.getArgs()[0]);
            return;
        }

        compile(src, dest, versionArg, minify, recursive, lineBreakLength, scope);
    }

    private void compile (
            final File src,
            final File dest,
            final String version,
            final boolean minify,
            final boolean recursive,
            final int lineBreakLength,
            final CompilationScope scope)
    {
        final Collection<File> allFiles = new ArrayList<>();
        search(src, recursive, allFiles);

        KonjureToolkit.getLogger().info("(" + allFiles.size() + ") total files discovered.");

        if (scope.affects(CompileExtension.JS)) {
            compile(dest, version, minify, lineBreakLength, allFiles, CompileExtension.JS);
        }

        if (scope.affects(CompileExtension.CSS)) {
            compile(dest, version, minify, lineBreakLength, allFiles, CompileExtension.CSS);
        }
    }

    private void compile (
            final File dest,
            final String version,
            final boolean minify,
            final int lineBreakLength,
            final Collection<File> allFiles,
            final CompileExtension extension)
    {
        String combine;
        try {
            combine = combine(allFiles, extension);
        } catch (final Exception e) {
            KonjureToolkit.getLogger().error(
                    "An error occurred while processing " + extension.toString() + " files", e
            );
            return;
        }

        combine = postProcess(combine, minify, lineBreakLength, extension, version);

        try {
            Files.write(
                    Paths.get(new File(dest, "konjure-min" + extension.getExtension()).toURI()), combine.getBytes()
            );
        } catch (final IOException e) {
            KonjureToolkit.getLogger().error("An error occurred while writing output " + extension.toString(), e);
        }
    }

    private String postProcess (
            String combine,
            final boolean minify,
            final int lineBreakLength,
            final CompileExtension compileExtension,
            final String version)
    {
        if (minify) {
            try {
                if (compileExtension.equals(CompileExtension.JS)) {
                    final JavaScriptCompressor jsCompressor = new JavaScriptCompressor(
                            new StringReader(combine),
                            new KonjureYuiErrorReporter());

                    final StringWriter jsWriter = new StringWriter();
                    jsCompressor.compress(jsWriter, lineBreakLength, true, true, false, false);
                    KonjureToolkit.getLogger().info("All JS sources minified...");

                    combine = jsWriter.getBuffer().toString();
                } else {

                    final CssCompressor cssCompressor = new CssCompressor(new StringReader(combine));

                    final StringWriter cssWriter = new StringWriter();
                    cssCompressor.compress(cssWriter, lineBreakLength);
                    KonjureToolkit.getLogger().info("All CSS sources minified...");

                    combine = cssWriter.getBuffer().toString();
                }
            } catch (final IOException e) {
                KonjureToolkit.getLogger().error("An error occurred while minifying JS/CSS files", e);
                e.printStackTrace();
            }
        }

        final String header = buildHeader(compileExtension, version);
        return header + System.lineSeparator() + System.lineSeparator() + combine;
    }

    private String buildHeader (final CompileExtension extension, final String version)
    {
        final String nLine = System.lineSeparator();

        final String header = "/*" + nLine + nLine +
                "\t* Konjure UI " + extension.toString() + " Library v" + version + nLine +
                "\t* https://konjure.org/ui" + nLine + nLine +
                "\t* Copyright (c) " + Calendar.getInstance().get(Calendar.YEAR) + " Konjure and other contributors" + nLine +
                "\t* Released under the MIT license" + nLine +
                "\t* https://opensource.org/licenses/MIT" + nLine + nLine +
                "*/";

        return header;
    }

    private String combine (
            final Collection<File> files,
            final CompileExtension compileExtension) throws FileNotFoundException
    {
        final StringBuilder sb = new StringBuilder();
        int count = 0;

        for (final File file : files) {
            if (file.getName().endsWith(compileExtension.getExtension())) {
                final BufferedReader br = new BufferedReader(new FileReader(file));
                br.lines().forEach(line -> sb.append(line).append(System.lineSeparator()));
                ++count;
            }
        }

        KonjureToolkit.getLogger().info("Processing " + count + " " + compileExtension.getExtension() + " files.");

        return sb.toString();
    }

    private void search (final File root, final boolean recursive, final Collection<File> files)
    {
        final File[] rawFiles = root.listFiles();

        if (rawFiles == null) {
            return;
        }

        for (final File file : rawFiles) {
            if (file.isDirectory() && recursive) {
                search(file, true, files);
            }

            files.add(file);
        }
    }

    private class KonjureYuiErrorReporter implements ErrorReporter
    {
        final Logger log = KonjureToolkit.getLogger();

        @Override
        public void warning (
                final String message,
                final String source,
                final int line,
                final String lineSource,
                final int lineOffset)
        {
            if (line < 0) {
                log.warn(message);
            } else {
                log.warn(line + ":" + lineOffset + ": " + message);
            }
        }

        @Override
        public void error (
                final String message,
                final String source,
                final int line,
                final String lineSource,
                final int lineOffset)
        {
            if (line < 0) {
                log.error(message);
            } else {
                log.error(line + ":" + lineOffset + ": " + message);
            }
        }

        @Override
        public EvaluatorException runtimeError (
                final String message,
                final String source,
                final int line,
                final String lineSource,
                final int lineOffset)
        {
            error(message, source, line, lineSource, lineOffset);
            return new EvaluatorException(message);
        }
    }

    private enum CompileExtension
    {
        JS(".js"),
        CSS(".css");

        private String extension;

        CompileExtension (final String extension)
        {
            this.extension = extension;
        }

        public String getExtension ()
        {
            return this.extension;
        }
    }

    private enum CompilationScope
    {
        JS,
        CSS,
        ALL;

        public boolean affects (final CompileExtension compileExtension)
        {
            if (this.equals(ALL)) {
                return true;
            }

            return compileExtension.equals(CompileExtension.JS) ? this.equals(JS) : this.equals(CSS);
        }
    }
}
