/*
 * The MIT License
 * 
 * Copyright (c) 2013, eSailors IT Solutions GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.esailors.jenkins.teststability;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.MavenTestDataPublisher;
import hudson.maven.reporters.SurefireReport;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.util.DescribableList;

/**
 * Fixed {@link MavenTestDataPublisher}.
 * The standard {@link MavenTestDataPublisher} from Jenkins (at least up to 1.520) just doesn't work.
 * <p>
 * See also https://github.com/jenkinsci/jenkins/pull/810
 * 
 * @author ckutz
 */
public class FixedMavenTestDataPublisher extends MavenTestDataPublisher {
	
	public FixedMavenTestDataPublisher(
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
		super(testDataPublishers);
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {

		debug("FixedMavenTestDataPublisher perform", listener);
		
		MavenModuleSetBuild msb = (MavenModuleSetBuild) build;
		
		Map<MavenModule, MavenBuild> moduleLastBuilds = msb.getModuleLastBuilds();
		
		debug("Found " + moduleLastBuilds.size() + " module builds", listener);
		
		for (MavenBuild moduleBuild : moduleLastBuilds.values()) {
		
			SurefireReport report = moduleBuild.getAction(SurefireReport.class);
			if (report == null) {
				return true;
			}
			
			List<Data> data = new ArrayList<Data>();
			if (getTestDataPublishers() != null) {
				for (TestDataPublisher tdp : getTestDataPublishers()) {
					Data d = tdp.getTestData(build, launcher, listener, report.getResult());
					if (d != null) {
						data.add(d);
					}
				}
			}
			
			report.setData(data);
			moduleBuild.save();
		}

		return true;
	}
	
	private void debug(String msg, BuildListener listener) {
		if (StabilityTestDataPublisher.DEBUG) {
			listener.getLogger().println(msg);
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public String getDisplayName() {
			return "Additional test report features (fixed)";
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return MavenModuleSet.class.isAssignableFrom(jobType) && !TestDataPublisher.all().isEmpty();
		}
		
		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers
                    = new DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>>(Saveable.NOOP);
            try {
                testDataPublishers.rebuild(req, formData, TestDataPublisher.all());
            } catch (IOException e) {
                throw new FormException(e,null);
            }

            return new FixedMavenTestDataPublisher(testDataPublishers);
		}
		
	}
	

}
