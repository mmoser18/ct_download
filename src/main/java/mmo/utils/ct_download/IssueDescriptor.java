package mmo.utils.ct_download;

import org.openqa.selenium.WebElement;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueDescriptor 
{
	final WebElement button;
	final String jahrgang;
	final String issueNr;
	String filename;

	String targetFullPath;

	public IssueDescriptor(final WebElement button, final String jahrgang, final String issueNr) {
		this.button = button;
		this.jahrgang = jahrgang;
		this.issueNr = issueNr;
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[jahrgang:" + jahrgang + "/issueNr:" + issueNr + "]";
	}
}
