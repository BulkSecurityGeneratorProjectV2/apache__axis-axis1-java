/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.axis.maven;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.axis.transport.http.SimpleAxisServer;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Start a {@link SimpleAxisServer} instance in a separate JVM.
 * 
 * @goal start-server
 * @phase pre-integration-test
 * @requiresDependencyResolution test
 */
public class StartServerMojo extends AbstractServerMojo {
    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    /**
     * The current build session instance. This is used for toolchain manager API calls.
     * 
     * @parameter default-value="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;
    
    /**
     * @component
     */
    private ToolchainManager toolchainManager;
    
    /**
     * Directory with WSDD files for services to deploy.
     * 
     * @parameter
     */
    private File wsddDir;
    
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        
        // Locate java executable to use
        String executable;
        Toolchain tc = toolchainManager.getToolchainFromBuildContext("jdk", session);
        if (tc != null) {
            executable = tc.findTool("java");
        } else {
            executable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        }
        if (log.isDebugEnabled()) {
            log.debug("Java executable: " + executable);
        }
        
        // Get class path
        List classPathElements;
        try {
            classPathElements = project.getTestClasspathElements();
        } catch (DependencyResolutionRequiredException ex) {
            throw new MojoExecutionException("Unexpected exception", ex);
        }
        if (log.isDebugEnabled()) {
            log.debug("Class path elements: " + classPathElements);
        }
        
        // Select WSDD files
        String[] wsddFiles;
        if (wsddDir != null) {
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(wsddDir);
            scanner.setIncludes(new String[] { "**/deploy.wsdd" });
            scanner.scan();
            String[] includedFiles = scanner.getIncludedFiles();
            wsddFiles = new String[includedFiles.length];
            for (int i=0; i<includedFiles.length; i++) {
                wsddFiles[i] = new File(wsddDir, includedFiles[i]).getPath();
            }
            if (log.isDebugEnabled()) {
                log.debug("WSDD files: " + Arrays.asList(wsddFiles));
            }
        } else {
            wsddFiles = null;
        }
        
        // Start the server
        try {
            getServerManager().startServer(executable, (String[])classPathElements.toArray(new String[classPathElements.size()]), getPort(), wsddFiles);
        } catch (Exception ex) {
            throw new MojoFailureException("Failed to start server", ex);
        }
    }
}
