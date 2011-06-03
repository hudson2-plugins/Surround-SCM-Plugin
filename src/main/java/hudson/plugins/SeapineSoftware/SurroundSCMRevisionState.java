package hudson.plugins.SeapineSoftware;

import hudson.scm.SCMRevisionState;


import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SurroundSCMRevisionState extends SCMRevisionState {

	public Map<String, Long> revisions;

	public SurroundSCMRevisionState(Map<String, Long> revisions) {

		this.revisions = revisions;
	}

	public SurroundSCMRevisionState() {

		revisions = new HashMap<String, Long>();
		buildDate = new Date();
	}
	
	public void AddRevision(String key, long value){
		if (revisions == null)
			revisions = new HashMap<String, Long>();
		revisions.put(key, value);
	}

	public void setRevisions(Map<String, Long> revisions) {
		this.revisions = revisions;
	}
	
	public void setDate(Date date) {
		buildDate = date;
	}
	
	public Date getDate() {
		return buildDate;
	}
	
	
	public Date buildDate;

}
