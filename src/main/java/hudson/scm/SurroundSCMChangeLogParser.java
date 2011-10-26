package hudson.scm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.kohsuke.stapler.export.Exported;
import org.xml.sax.SAXException;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SurroundSCMChangeLogSet;
import hudson.scm.SurroundSCMChangeLogSet.SurroundSCMChangeLogSetEntry;
import hudson.scm.EditType;

public class SurroundSCMChangeLogParser extends ChangeLogParser {

	@Override
	public ChangeLogSet<? extends Entry> parse(AbstractBuild build,
			File changelogFile) throws IOException, SAXException {

		//open the changelog File
		SurroundSCMChangeLogSet cls = new SurroundSCMChangeLogSet(build);		
		String line = null;
		BufferedReader br = null;
		
		boolean foundAnItem=false;
		try{			
			br = new BufferedReader(new FileReader(changelogFile));
			while ((line = br.readLine())!=null)
			{
				//skip the total line
				if (foundAnItem == false){
					foundAnItem=true;
					continue;
				}
					                   	            			
				//check for count first
				if (line.startsWith("total-0"))
					break; //there are none, abandon ship
				
				//get the path
				int end = line.indexOf(">");
				
				//sanity check
				if (end<=0)
					break;
				
				String path = line.substring(1,end);
				line = line.substring(end+1);
				
				//get the name
				end = line.indexOf(">");
				//sanity check
				if (end<=0)
					break;
				String name = line.substring(1,end);
				line = line.substring(end+1);
				name = path.concat("/").concat(name);
				
				//get the version
				end = line.indexOf(">");
				//sanity check
				if (end<=0)
					break;
				String version = line.substring(1,end);
				line = line.substring(end+1);
				
				//get the action
				end = line.indexOf(">");
				//sanity check
				if (end<=0)
					break;
				String action = line.substring(1,end);
				line = line.substring(end+1);
				
				//get the date
				end = line.indexOf(">");
				//sanity check
				if (end<=0)
					break;
				String date = line.substring(1,end);
				line = line.substring(end+1);
				
				//get the comment
				end = line.indexOf(">");
				//sanity check
				if (end<=0)
					break;
				String comment = line.substring(1,end);
				line = line.substring(end+1);
										
				//get the user
				end = line.indexOf(">");
				//sanity check
				if (end<=0)
					break;
				String userName = line.substring(1,end);
				line = line.substring(end+1);
				
				SurroundSCMChangeLogSetEntry next = new SurroundSCMChangeLogSetEntry(name,comment,version,action,date,cls ,userName);
				if (!cls.addEntry(next)) //terminate on error
					break;
				
			}	
			
		}catch (FileNotFoundException e) {
		      e.printStackTrace();
	    } 

		br.close();
		
		return cls;
	}

	

}

