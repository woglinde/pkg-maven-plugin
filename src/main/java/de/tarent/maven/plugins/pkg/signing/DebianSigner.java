/*
 * Maven Packaging Plugin,
 * Maven plugin to package a Project (deb, ipk, izpack)
 * Copyright (C) 2000-2008 tarent GmbH
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License,version 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 *
 * tarent GmbH., hereby disclaims all copyright
 * interest in the program 'Maven Packaging Plugin'
 * Signature of Elmar Geese, 11 March 2008
 * Elmar Geese, CEO tarent GmbH.
 */

/**
 * 
 */
package de.tarent.maven.plugins.pkg.signing;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import de.tarent.maven.plugins.pkg.AbstractPackagingMojo;
import de.tarent.maven.plugins.pkg.TargetConfiguration;
import de.tarent.maven.plugins.pkg.Utils;
import de.tarent.maven.plugins.pkg.generator.ChangelogFileGenerator;
import de.tarent.maven.plugins.pkg.generator.SourceControlFileGenerator;
import de.tarent.maven.plugins.pkg.map.PackageMap;


/**
 * Generates a changes-file for the package generated by the pkg-goal and signs it.
 * 
 * @author Fabian K&ouml;ster (f.koester@tarent.de) tarent GmbH Bonn
 * @execute goal="pkg"
 * @goal sign
 *
 */
public class DebianSigner extends AbstractPackagingMojo {

	/**
	 * The command to use for generating a files-list.
	 */
	protected final String filesGenCmd = "dpkg-distaddfile";

	/**
	 * The command to use for actually signing the Debian-Packages.
	 */
	protected final String signCmd = "debsign";

	/**
	 * The command to use for generating a .changes-file.
	 */
	protected final String changesGenCmd = "dpkg-genchanges";
	
	/**
	 * The command to use for generating a date-string which complies to the RFC-2822 specification.
	 */
	protected final String rfc2822DateCmd = "date";

	protected PackageMap packageMap;
	protected TargetConfiguration distroConfiguration;

	protected File tempRoot;
	protected File basePkgDir;
	protected String packagingType;
	protected String packageVersion;
	protected String packageName;
	protected String architecture;

	/**
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		if(!getPackagingType().equals("deb"))
			throw new MojoFailureException("Signing packaging-type '"+ getPackagingType() + "' is currently not supported.");

		if(getDistroConfiguration().getMaintainer() == null)
			throw new MojoFailureException("Maintainer has to be defined.");
		
		packageVersion = (String) getPluginContext().get("packageVersion");

		generateFileList(getLog(), buildDir);
		
		generateSourceControlFile(getLog(), buildDir);
			
		generateChangelogFile(getLog(), buildDir);

		generateChangesFile(getLog(), buildDir);

		signPackage(getLog(), buildDir);
		
	}

	/**
	 * Actually signs the Debian-package using the command defined in the <code>signCmd</code>-variable
	 * 
	 * @param l
	 * @param base
	 * @throws MojoExecutionException
	 */
	protected void signPackage(Log l, File base) throws MojoExecutionException  {
		l.info("calling " + signCmd + " to sign package");

		File pathToChangesFile = new File(base, getPackageFileNameBase() + ".changes");

		Utils.exec(new String[] {signCmd,
				pathToChangesFile.getAbsolutePath()} ,
				base,
				"Signing the changes-file failed.",
		"Error signing the changes-file.");
		
		l.info("changes-file signed successfully");
	}

	/**
	 * Executes the command defined in the <code>filesGenCmd</code>-variable to generate a files-list.
	 * 
	 * @param l
	 * @param base
	 * @throws MojoExecutionException
	 */
	protected void generateFileList(Log l, File base) throws MojoExecutionException  {
		l.info("calling " + filesGenCmd + " to generate file-list");

		File pathToFileListFile = new File(getTempRoot(), "files");

		Utils.exec(new String[] {filesGenCmd,
				"-f" + pathToFileListFile.getAbsolutePath(),
				getDebFileName(),
				getDistroConfiguration().getSection(),
		"optional"} ,
		base,
		"Generating file-list failed.",
		"Error creating the file-list.");
	}
	
	/**
	 * Excecutes the command defined in the <code>rfc2822DateCmd</code>-variable to generate a date-string
	 * complying to the RFC-2822 specification.
	 * 
	 * @param l
	 * @param base
	 * @return
	 * @throws MojoExecutionException
	 */
	protected String getRFC2822Date(Log l, File base) throws MojoExecutionException {
		l.info("calling 'date -R' to get RFC‐2822 date");
		
		InputStream processOutput = Utils.exec(new String[] { rfc2822DateCmd, "-R" } ,
		base,
		"Generating RFC-2822 date failed",
		"Error generating RFC-2822 date.");
		
		if(processOutput != null) { 
			try {
				return new BufferedReader(new InputStreamReader(processOutput)).readLine();
			} catch (IOException e) {
				throw new MojoExecutionException("Generating RFC-2822 date failed. ", e);
			}
		}
		else
			throw new MojoExecutionException("Generating RFC-2822 date failed (No output from "+rfc2822DateCmd+").");
	}
	
	/**
	 * Generates a changelog file which complies to the Debian-Policy.
	 * 
	 * @param l
	 * @param base
	 * @throws MojoExecutionException
	 */
	protected void generateChangelogFile(Log l, File base) throws MojoExecutionException {
		l.info("generating changelog-file");
		
		File pathToChangelogFile = new File(base, "changelog");
		
		// name, version, repoName, change, author, date
		ChangelogFileGenerator cgen = new ChangelogFileGenerator();
		cgen.setPackageName(getPackageName());
		cgen.setVersion(getPackageVersion());
		cgen.setMaintainer(getDistroConfiguration().getMaintainer());
		cgen.setDate(getRFC2822Date(l, base));
		cgen.setRepositoryName(getPackageMap().getRepositoryName());
		cgen.setChanges(promptForChanges());
				
		try {
			cgen.generate(pathToChangelogFile);
		} catch (IOException e) {
			throw new MojoExecutionException("IOException while creating changelog file.",
					e);
		}
	}
	
	/**
	 * Prompts the user for a list of changes since last deployment of the package.
	 * 
	 * @return a String-array containing the changes. The size of the array corresponds to the amount of changes.
	 * @throws MojoExecutionException
	 */
	protected List<String> promptForChanges() throws MojoExecutionException {
		List<String> changes = new ArrayList<String>();
		
		System.out.println("Please type in your changes since last deployment of this package. One change per line. If you are ready just make an empty line.");
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		try {
			
			String line = reader.readLine();
			
			while(line != null && line.trim().length() != 0) {
				
				changes.add(new String(line));
				line = reader.readLine();
			}
			
		} catch(IOException excp) {
			throw new MojoExecutionException("Error getting user-input ", excp);
		}
		
		return changes;
	}

	protected void generateChangesFile(Log l, File base) throws MojoExecutionException  {
		l.info("calling " + changesGenCmd + " to generate changes-file");

		File pathToControlFile = new File(getTempRoot(), "control");
		File pathToChangelogFile = new File(base, "changelog");
		File pathToFileListFile = new File(getTempRoot(), "files");

		String maintainer = getDistroConfiguration().getMaintainer();

		File pathToChangesFile = new File(base, getPackageFileNameBase() + ".changes");

		InputStream processOutput = Utils.exec(new String[] {changesGenCmd,
				"-b",
				"-c" + pathToControlFile.getAbsolutePath(),
				"-l" + pathToChangelogFile.getAbsolutePath(),
				"-f" + pathToFileListFile.getAbsolutePath(),
				"-m" + maintainer,
				"-e" + maintainer} ,
				getTempRoot(),
				"Generating changes-file failed",
		"Error creating the changes-file.");
		
		// Store output of the executed process into a .changes-file
		if(processOutput != null)
			Utils.storeInputStream(processOutput, pathToChangesFile, "Error when storing the changes-file.");
		else
			throw new MojoExecutionException("Storing the changes-file to \""+pathToChangesFile.getAbsolutePath()+"\"failed (No output from "+changesGenCmd+").");
	}
	
	/**
	 * Generates a control-file containing a 'Source' line which is needed for generating the .changes-file.
	 * 
	 * @param l
	 * @param base
	 * @throws MojoExecutionException
	 */
	protected void generateSourceControlFile(Log l, File base) throws MojoExecutionException {
		File pathToControlFile = new File(getTempRoot(), "control");
		
		l.info("creating source-control file: " + pathToControlFile.getAbsolutePath());

		SourceControlFileGenerator cgen = new SourceControlFileGenerator();
		cgen.setSource(getPackageName());
		cgen.setPackageName(getPackageName());
		cgen.setVersion(getPackageVersion());
		cgen.setSection(getDistroConfiguration().getSection());
		cgen.setMaintainer(getDistroConfiguration().getMaintainer());
		cgen.setArchitecture(getDistroConfiguration().getArchitecture());

		l.info("creating control file: " + pathToControlFile.getAbsolutePath());
		Utils.createFile(pathToControlFile, "control");

		try	{
			cgen.generate(pathToControlFile);
		}
		catch (IOException ioe)	{
			throw new MojoExecutionException("IOException while creating control file.",
					ioe);
		}
	}

	public File getTempRoot() {
		
		if (tempRoot == null)
			tempRoot = new File(buildDir, getPackagingType() + "-tmp");

		return tempRoot;
	}

	public File getBasePkgDir()	{
		
		if (basePkgDir == null)
			basePkgDir = new File(getTempRoot(), getPackageName() + "-"
					+ getPackageVersion());

		return basePkgDir;
	}

	public String getPackagingType() {
		if(packagingType == null)
			packagingType = getPackageMap().getPackaging();

		return packagingType;
	}

	public PackageMap getPackageMap() {
		if(packageMap == null)
			packageMap = ((PackageMap)getPluginContext().get("pm"));

		return packageMap;
	}

	public TargetConfiguration getDistroConfiguration() {
		if(distroConfiguration == null)
			distroConfiguration = ((TargetConfiguration)getPluginContext().get("dc"));

		return distroConfiguration;
	}

	public String getPackageVersion() {
		if (packageVersion == null)
			packageVersion = Utils.fixVersion(version) + "-0" + Utils.sanitizePackageVersion(getDistroConfiguration().getTarget())
			+ (getDistroConfiguration().getRevision().length() == 0 ? "" : "-" + getDistroConfiguration().getRevision());

		return packageVersion;
	}

	public String getPackageName() {
		if (packageName == null)
			packageName = Utils.createPackageName(artifactId, getDistroConfiguration().getPackageNameSuffix(), getDistroConfiguration().getSection(), 
					getPackageMap().isDebianNaming());

		return packageName;
	}

	public String getArchitecture() {
		if(architecture == null)
			architecture = getDistroConfiguration().getArchitecture();

		return architecture;
	}
	
	public String getPackageFileNameBase() {
		return getPackageName() + "_" + getPackageVersion() + "_" + getArchitecture();
	}

	public String getDebFileName() {
		return getPackageFileNameBase() + ".deb";
	}
}
