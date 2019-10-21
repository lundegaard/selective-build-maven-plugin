/*
 * Copyright (C) 2019 Lundegaard a.s., All Rights Reserved
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; version 3.0 of the License.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * https://www.gnu.org/licenses/lgpl-3.0.html
 */
package eu.lundegaard.maven.selectivebuild;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.asList;

/**
 * @author Ales Rybak(ales.rybak@lundegaard.eu)
 */
@Mojo(
        name = "changes")
public class ChangesMojo extends AbstractMojo {

    private static final String ENCODING_UTF8 = "UTF-8";
    private static final Logger LOG = LoggerFactory.getLogger(ChangesMojo.class);

    @Parameter(property = "localChanges", defaultValue = "false")
    private boolean localChanges;

    /**
     * If true tell maven to rebuild also modules which are dependent on changed
     * modules
     */
    @Parameter(property = "dependents", defaultValue = "true")
    private boolean dependents;

    /**
     * If true changes in ./ directory or in non-maven directories causes whole
     * rebuild
     */
    @Parameter(property = "strictMode", defaultValue = "false")
    private boolean strictMode;

    /**
     * Changes in these files cause whole rebuild
     */
    @Parameter
    private List<String> wholeRebuildFiles = asList("pom.xml");

    @Parameter(property = "branch", required = true, defaultValue = "HEAD")
    private String branch;

    @Parameter(property = "target", required = true, defaultValue = "origin/develop")
    private String target;

    @Parameter(property = "projectRootDir", required = true, defaultValue = "${project.basedir}")
    private Path projectRootDir;

    public void setProjectRootDir(File file) {
        this.projectRootDir = file.toPath();
    }

    @Parameter(property = "tooManyCommitsLimit", required = true, defaultValue = "20")
    private int tooManyCommitsLimit;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {

            doExecute();

        } catch (IOException | InterruptedException e) {
            throw new MojoFailureException("Error while running plugin.", e);
        }
    }

    private void doExecute() throws IOException, InterruptedException {

        // Skip if there are too many commits
        if (!localChanges) {
            LOG.debug("Getting number of commits between {} and {}", target, branch);
            int numberOfCommits = parseInt(exec("git rev-list --count " + target + "..." + branch).trim());
            if (numberOfCommits > tooManyCommitsLimit) {
                LOG.debug("Too many commits between branches ({}) -> full rebuild", numberOfCommits);
                printOutFullRebuildBuildMavenParams();
                return;
            }
        }

        // Retrieve all changed files
        String changedFilesCommand;
        if (localChanges) {
            LOG.debug("Getting local changes...");
            changedFilesCommand = "git diff --name-only";
        } else {
            LOG.debug("Getting changed files between {} and {}", target, branch);
            changedFilesCommand = "git whatchanged --name-only --pretty= " + target + "..." + branch;
        }
        List<Path> changedPaths = execToList(changedFilesCommand).stream()
                .map(projectRootDir::resolve)
                .collect(Collectors.toList());

        if (changedPaths.isEmpty()) {
            LOG.debug("No changed files detected, recommended non-recursive build.");
            printOutNonRecursiveBuildMavenParams();
            return;
        } else {
            LOG.debug("Detected changed files:");
            changedPaths.forEach(path -> LOG.debug("  - " + path.toString()));
        }

        for (String file : wholeRebuildFiles) {
            if (changedPaths.contains(projectRootDir.resolve(file))) {
                LOG.debug("File '{}' is changed -> full rebuild.", file);
                printOutFullRebuildBuildMavenParams();
                return;
            }
        }

        // Associate every changed file with corresponding maven module, otherwise assign '.'
        List<String> changedModules = changedPaths.stream()
                .map(path -> findInAncestorDirs(path, "pom.xml"))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(projectRootDir::relativize)
                .map(Path::toString)
                .map(module -> module.equals("") ? "." : module)
                .collect(Collectors.toList());

        if (!changedModules.isEmpty()) {
            LOG.debug("Changes detected in the following modules:");
            changedModules.forEach(module -> LOG.debug("  - " + module));
        }

        // Create unique list of changedModules and optionally remove '.' for faster (and less safe) build
        if (strictMode && changedModules.contains(".")) {
            LOG.debug("Changes in the root dir and strict mode -> rebuild all");
            printOutFullRebuildBuildMavenParams();
            return;
        } else {
            // Not in the strict mode means we can ignore changes in the root dir
            changedModules.remove(".");
        }

        // No changed changedModules detected, rebuild all
        if (changedModules.isEmpty()) {
            LOG.debug("No module changes detected -> non-recursive build");
            printOutNonRecursiveBuildMavenParams();
            return;
        }

        List<String> mavenOpts = new ArrayList<>();

        if (dependents) {
            mavenOpts.add("-amd");
        }

        printOutSelectiveBuildMavenParams(mavenOpts, changedModules);
    }

    private void printOutNonRecursiveBuildMavenParams() {
        System.out.println("-N");
    }

    private void printOutFullRebuildBuildMavenParams() {
        // for full rebuild no params should be used (it's default behavior)
        System.out.println();
    }

    private void printOutSelectiveBuildMavenParams(List<String> mavenOpts, List<String> changedModules) {
        System.out.print(String.join(" ", mavenOpts));
        System.out.print(" -pl ");
        System.out.print(String.join(",", changedModules));
        System.out.println();
    }

    private Path findInAncestorDirs(final Path path, final String file) {
        Path currentPath = path;
        Path rootPath = path.getRoot();
        while (!currentPath.equals(rootPath)) {
            if (Files.exists(currentPath.resolve(file))) {
                return currentPath;
            } else {
                currentPath = currentPath.getParent();
            }
        }
        return null;
    }

    private String exec(final String command) throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(command);
        process.waitFor();

        String stdErr = IOUtils.toString(process.getErrorStream(), ENCODING_UTF8);
        if (stdErr != null && !stdErr.isEmpty()) {
            LOG.error(stdErr);
        }

        String stdOut = IOUtils.toString(process.getInputStream(), ENCODING_UTF8);
        return stdOut;
    }

    private List<String> execToList(final String command) throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(command);
        process.waitFor();

        String stdErr = IOUtils.toString(process.getErrorStream(), ENCODING_UTF8);
        if (stdErr != null && !stdErr.isEmpty()) {
            LOG.error(stdErr);
        }

        List<String> stdOutLines = IOUtils.readLines(process.getInputStream(), ENCODING_UTF8);
        return stdOutLines;
    }

}
