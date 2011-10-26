package hudson.scm;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

@Extension
public final class SurroundSCM extends SCM {

	/*---------------------INNER CLASS------------------------------------------------------------*/

	public static class SurroundSCMDescriptor extends
			SCMDescriptor<SurroundSCM> {

		
		/**
		 * Constructs a new SurroundSCMDescriptor.
		 */
		protected SurroundSCMDescriptor() {
			super(SurroundSCM.class, null);
			load();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getDisplayName() {
			return "Surround SCM";
		}

		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			SurroundSCM scm = req.bindJSON(SurroundSCM.class, formData);
			return scm;
		}		

	}

	// if there are > changesThreshold changes, that it's build now -
	// incomparable
	// if there are < changesThreshold changes, but > 0 changes, then it's
	// significant
	final int changesThreshold = 5;

	// config options
	private String server;	
	private String serverPort;
	private String userName ;
	private String password ;
	private String branch ;
	private String repository;
	private String surroundSCMExecutable;
	
	
	//getters and setters
	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getServerPort() {
		return serverPort;
	}

	public void setServerPort(String serverPort) {
		this.serverPort = serverPort;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getBranch() {
		return branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	public String getRepository() {
		return repository;
	}

	public void setRepository(String repository) {
		this.repository = repository;
	}
	
	public String getSurroundSCMExecutable() {
		if (surroundSCMExecutable == null)
			return "sscm";
		else
			return surroundSCMExecutable;
	}

	public void setSurroundSCMExecutable(String surroundSCMExecutable) {
		this.surroundSCMExecutable = surroundSCMExecutable;
	}

	
	/**
	 * Singleton descriptor.
	 */
	@Extension
	public static final SurroundSCMDescriptor DESCRIPTOR = new SurroundSCMDescriptor();
	
	public static final SimpleDateFormat SURROUND_DATETIME_FORMATTER = new SimpleDateFormat("yyyyMMddHHmmss");

	@DataBoundConstructor
	public SurroundSCM(String server, String serverPort, String userName,
			String password, String branch, String repository, String surroundSCMExecutable) {
		this.server = server;
		this.serverPort = serverPort;
		this.userName = userName;
		this.password = password;
		this.branch = branch;
		this.repository = repository;
		this.surroundSCMExecutable = surroundSCMExecutable;
	}

	public SurroundSCM() {

	}
	
    @Override
    public SCMDescriptor<?> getDescriptor() {
        return DESCRIPTOR;
    }


	/*
	 * Calculates the SCMRevisionState that represents the state of the
	 * workspace of the given build. The returned object is then fed into the
	 * compareRemoteRevisionWith(AbstractProject, Launcher, FilePath,
	 * TaskListener, SCMRevisionState) method as the baseline SCMRevisionState
	 * to determine if the build is necessary.
	 */
    /* KD - We are going to use a command to get the changes from the last build to 
     * the current date, as such, we don't really need to do anything here
     */
	@Override
	public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build,
			Launcher launcher, TaskListener listener) throws IOException,
			InterruptedException {

		// this is what we'll return
		SurroundSCMRevisionState scmRevisionState = new SurroundSCMRevisionState();		
		final Date lastBuildDate = build.getTime();
		scmRevisionState.setDate(lastBuildDate);
				
		return scmRevisionState;
	}

	@Override
	/* 
	 */
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
		
		Date lastBuild = ((SurroundSCMRevisionState)baseline).getDate();
		Date now = new Date();
		File temporaryFile = File.createTempFile("changes", "txt");
		double countChanges = determineChangeCount(launcher, workspace, listener, lastBuild,now,temporaryFile);
				
		if (countChanges == 0)
			return PollingResult.NO_CHANGES;
		else if (countChanges < changesThreshold)
			return PollingResult.SIGNIFICANT;
			
		return PollingResult.BUILD_NOW;
	}

	// Obtains a fresh workspace of the module(s) into the specified directory
	// of the specified machine. We'll use sscm get
	@Override
	public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher,
			FilePath workspace, BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {
		 boolean returnValue = true;
		 
		 if (server != null )
			 listener.getLogger().println("server: "+server);
		
		String[] cmd = new String[10];
		cmd[0] = getSurroundSCMExecutable();//will default to sscm user can put in path
		cmd[1] = "get" ;
		cmd[2] = "/" ;
		cmd[3] = "-wreplace";
		cmd[4] = "-y".concat(userName).concat(":").concat(password);
		cmd[5] = "-z".concat(server).concat(":").concat(serverPort);
		cmd[6]= "-b".concat(branch);
		cmd[7]= "-p".concat(repository);
		cmd[8]="-d".concat(workspace.getRemote());
		cmd[9]="-r";
		int cmdResult = launcher.launch().cmds(cmd).envs(new String[0])
				.stdin(null).stdout(listener.getLogger()).pwd(workspace).join();
		if (cmdResult == 0)
		{
			final Run<?, ?> lastBuild = build.getPreviousBuild();
			final Date lastBuildDate;

			if (lastBuild == null) {
				lastBuildDate = new Date();
				lastBuildDate.setTime(0); // default to January 1, 1970
				listener.getLogger().print("Never been built.");				
			} else
				lastBuildDate = lastBuild.getTimestamp().getTime();
			
			Date now = new Date(); //defaults to current
			
			returnValue = captureChangeLog(launcher, workspace,listener, lastBuildDate, now, changelogFile);
		}
		else
			returnValue = false;
			
		listener.getLogger().println("Checkout completed.");	
		return returnValue;
	}

	@Override
	public ChangeLogParser createChangeLogParser() {		
		return new SurroundSCMChangeLogParser();
	}

	private boolean captureChangeLog(Launcher launcher, FilePath workspace,
			BuildListener listener, Date lastBuildDate, Date currentDate, File changelogFile) throws IOException, InterruptedException {
		
		boolean result = true;
		
		String dateRange = SURROUND_DATETIME_FORMATTER.format(lastBuildDate);
		dateRange = dateRange.concat(":");
		dateRange = dateRange.concat(SURROUND_DATETIME_FORMATTER.format(currentDate));		
		
		String[] cmd = new String[9];
		cmd[0] = getSurroundSCMExecutable();//will default to sscm user can put in path
		cmd[1] = "cc" ;
		cmd[2] = "/" ;
		cmd[3] = "-d".concat(dateRange);
		cmd[4] = "-y".concat(userName).concat(":").concat(password);
		cmd[5] = "-z".concat(server).concat(":").concat(serverPort);
		cmd[6]= "-b".concat(branch);
		cmd[7]= "-p".concat(repository);		
		cmd[8]="-r";		
		
		FileOutputStream os = new FileOutputStream(changelogFile);
		try {
            BufferedOutputStream bos = new BufferedOutputStream(os);
            PrintWriter writer = new PrintWriter(new FileWriter(changelogFile));
            try {            	
            	
            	
            	int cmdResult = launcher.launch().cmds(cmd).envs(new String[0]).stdin(null).stdout(bos).pwd(workspace).join();
            	if (cmdResult != 0)
            	{
            		listener.fatalError("Changelog failed with exit code " + cmdResult);
            		result = false;
            	}
            	
            	
            } finally {
            	writer.close();
                bos.close();
            }
        } finally {
            os.close();
        }

        listener.getLogger().println("Changelog calculated successfully.");
        listener.getLogger().println("Change log file: " + changelogFile.getAbsolutePath() );
        
        return result;
	}
	
	private double determineChangeCount(Launcher launcher, FilePath workspace,
			TaskListener listener, Date lastBuildDate, Date currentDate, File changelogFile) throws IOException, InterruptedException {
		
		double changesCount = 500;
		 if (server != null )
			 listener.getLogger().println("in determine Change Count server: "+server);
		
		String dateRange = SURROUND_DATETIME_FORMATTER.format(lastBuildDate);
		dateRange = dateRange.concat(":");
		dateRange = dateRange.concat(SURROUND_DATETIME_FORMATTER.format(currentDate));
				
		String[] cmd = new String[9];
		cmd[0] = getSurroundSCMExecutable();//will default to sscm user can put in path
		cmd[1] = "cc" ;
		cmd[2] = "/" ;
		cmd[3] = "-d".concat(dateRange);
		cmd[4] = "-y".concat(userName).concat(":").concat(password);
		cmd[5] = "-z".concat(server).concat(":").concat(serverPort);
		cmd[6]= "-b".concat(branch);
		cmd[7]= "-p".concat(repository);		
		cmd[8]="-r";	

		FileOutputStream os = new FileOutputStream(changelogFile);
		try {
            BufferedOutputStream bos = new BufferedOutputStream(os);
           
            try {            	
            	int cmdResult = launcher.launch().cmds(cmd).envs(new String[0]).stdin(null).stdout(bos).pwd(workspace).join();
            	if (cmdResult != 0)
            	{
            		listener.fatalError("Determine changes count failed with exit code " + cmdResult);            		
            	}            	
            } finally {
                bos.close();
            }
        } finally {
            os.close();
        }      
        
        BufferedReader br = null;
		String line = null;
		try{			
			br = new BufferedReader(new FileReader(changelogFile));
			line = br.readLine();
			if (line != null){
				listener.getLogger().println(line);
				String num = line.substring(6);
				 try {
					 changesCount = Double.valueOf(num.trim()).doubleValue();
			      } catch (NumberFormatException nfe) {
			         listener.fatalError("NumberFormatException: " + nfe.getMessage());
			      }

				
			}
			
		}catch (FileNotFoundException e) {
		      e.printStackTrace();
	    } 
		listener.getLogger().println("Number of changes determined to be: "+changesCount);
		return changesCount;
	}

}
