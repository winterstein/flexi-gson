package com.winterwell.bob.wwjobs;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import com.winterwell.bob.BuildTask;
import com.winterwell.utils.io.FileUtils;

/**
 * A copy of BuildFlexiGson in the bob project itself. This can be used from the command line.
 * @author daniel
 *
 */
public class BuildFlexiGson2 extends BuildWinterwellProject {

	public BuildFlexiGson2() {
		super(new File(FileUtils.getWinterwellDir(), "flexi-gson"));
		setIncSrc(true);
		setScpToWW(true);
	}

	@Override
	public List<BuildTask> getDependencies() {
		return Arrays.asList(new BuildUtils());
	}
	
}