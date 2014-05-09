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
import hudson.util.ArgumentListBuilder;

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
  final int pluginVersion = 6;
  
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
  
  public static final String SURROUND_DATETIME_FORMAT_STR = "yyyyMMddHHmmss";
  public static final String SURROUND_DATETIME_FORMAT_STR_2 = "yyyyMMddHH:mm:ss";

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

    SimpleDateFormat scm_datetime_formatter = new SimpleDateFormat(SURROUND_DATETIME_FORMAT_STR);
    
    // this is what we'll return  
    final Date  lastBuildDate = build.getTime();
    final int   lastBuildNum  = build.getNumber();
    SurroundSCMRevisionState scmRevisionState = new SurroundSCMRevisionState(lastBuildDate, lastBuildNum);
    listener.getLogger().println("calcRevisionsFromBuild determined revision for build #" + scmRevisionState.getBuildNumber() + " built originally at " + scm_datetime_formatter.format(scmRevisionState.getDate()) + " pluginVer: " + pluginVersion);
    
    return scmRevisionState;
  }

  @Override
  /* 
   */
  protected PollingResult compareRemoteRevisionWith(
      AbstractProject<?, ?> project, Launcher launcher,
      FilePath workspace, TaskListener listener, SCMRevisionState baseline)
      throws IOException, InterruptedException {
    
    SimpleDateFormat scm_datetime_formatter = new SimpleDateFormat(SURROUND_DATETIME_FORMAT_STR);
    
    Date  lastBuild = ((SurroundSCMRevisionState)baseline).getDate();
    int   lastBuildNum = ((SurroundSCMRevisionState)baseline).getBuildNumber();
    
    Date now = new Date();
    File temporaryFile = File.createTempFile("changes", "txt");
    listener.getLogger().println("Calculating changes since build #" + lastBuildNum + " which happened at " + scm_datetime_formatter.format(lastBuild) + " pluginVer: " + pluginVersion);
    
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
    
    SimpleDateFormat scm_datetime_formatter = new SimpleDateFormat(SURROUND_DATETIME_FORMAT_STR_2);
    
    if (server != null )
      listener.getLogger().println("server: "+server);
    
    Date currentDate = new Date(); //defaults to current
    
    ArgumentListBuilder cmd = new ArgumentListBuilder();
    cmd.add(getSurroundSCMExecutable());//will default to sscm user can put in path
    cmd.add("get");
    cmd.add("/" );
    cmd.add("-wreplace");
    cmd.addMasked("-y".concat(userName).concat(":").concat(password));
    cmd.add("-z".concat(server).concat(":").concat(serverPort));
    cmd.add("-b".concat(branch));
    cmd.add("-p".concat(repository));
    cmd.add("-d".concat(workspace.getRemote()));
    cmd.add("-r");
    cmd.add("-s" + scm_datetime_formatter.format(currentDate));

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
      
      // Setup the revision state based on what we KNOW to be correct information.
      SurroundSCMRevisionState scmRevisionState = new SurroundSCMRevisionState(currentDate, build.getNumber());
      build.addAction(scmRevisionState);
      listener.getLogger().println("Checkout calculated ScmRevisionState for build #" + build.getNumber() + " to be the datetime " + scm_datetime_formatter.format(currentDate) + " pluginVer: " + pluginVersion);
      
      returnValue = captureChangeLog(launcher, workspace,listener, lastBuildDate, currentDate, changelogFile);
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
    
    SimpleDateFormat scm_datetime_formatter = new SimpleDateFormat(SURROUND_DATETIME_FORMAT_STR);
   
    String dateRange = scm_datetime_formatter.format(lastBuildDate);
    dateRange = dateRange.concat(":");
    dateRange = dateRange.concat(scm_datetime_formatter.format(currentDate));    
    
    ArgumentListBuilder cmd = new ArgumentListBuilder();
    cmd.add(getSurroundSCMExecutable());//will default to sscm user can put in path
    cmd.add("cc");
    cmd.add("/");
    cmd.add("-d".concat(dateRange));
    cmd.addMasked("-y".concat(userName).concat(":").concat(password));
    cmd.add("-z".concat(server).concat(":").concat(serverPort));
    cmd.add("-b".concat(branch));
    cmd.add("-p".concat(repository));    
    cmd.add("-r");    
    
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
    
    SimpleDateFormat scm_datetime_formatter = new SimpleDateFormat(SURROUND_DATETIME_FORMAT_STR);
    
    double changesCount = 0;
     if (server != null )
       listener.getLogger().println("in determine Change Count server: "+server);
    
    String dateRange = scm_datetime_formatter.format(lastBuildDate);
    dateRange = dateRange.concat(":");
    dateRange = dateRange.concat(scm_datetime_formatter.format(currentDate));
        
    ArgumentListBuilder cmd = new ArgumentListBuilder();
    cmd.add(getSurroundSCMExecutable());
    cmd.add("cc");
    cmd.add("/");
    cmd.add("-d".concat(dateRange));
    cmd.addMasked("-y".concat(userName).concat(":").concat(password));
    cmd.add("-z".concat(server).concat(":").concat(serverPort));
    cmd.add("-b".concat(branch));
    cmd.add("-p".concat(repository));
    cmd.add("-r");  

    listener.getLogger().println("determineChangeCount executing the command: " + cmd.toString() + " with date range: [ " + dateRange + " ]");
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
