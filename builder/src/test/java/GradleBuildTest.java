import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class GradleBuildTest {    
  @Parameters
  public static Collection<File> data() {
      ArrayList<File> dirtyProjects = new ArrayList<File>(); 
      try {
	  Repository repository = new FileRepositoryBuilder()
	      .readEnvironment() // scan environment GIT_* variables
	      .findGitDir() // scan up the file system tree
	      .build();
	  RevCommit head = new Git(repository)
	      .log().call()
	      .iterator().next();
	  TreeWalk treeWalk = new TreeWalk(repository);
	  treeWalk.addTree(head.getTree());
	  for (RevCommit p : head.getParents()) {
	      treeWalk.addTree(p.getTree());
	  }
	  treeWalk.setRecursive(false);
	  treeWalk.setFilter(TreeFilter.ANY_DIFF);
	  while (treeWalk.next()) {
	      File f = new File("../" + treeWalk.getPathString());
	      System.err.println("dirty: " + f.getName());	      
	      if (f.getName().equals(".travis.yml")) {
		  System.err.println("travis modified");
		  return allProjects();
	      }
	      if (f.isDirectory() && f.getName().equals("builder")) {
		  System.err.println("builder modified");
		  return allProjects();
	      }
	      if (isProject(f)) {
		  System.err.println("added project: " + f.getName());		  
		  dirtyProjects.add(f);
	      } else {
		  System.err.println("not a project: " + f.getName());
	      }
	  }
      } catch (java.io.IOException e) {
	  System.err.println("error opening git repository: " + e);
	  return allProjects();
      } catch (GitAPIException e) {
	  System.err.println("error reading git repository log: " + e);
	  return allProjects();
      }
      if (dirtyProjects.size() > 0) {
	  System.err.println("building modified projects: " + dirtyProjects.toString());	  
	  return dirtyProjects;
      }
      System.err.println("no modified projects");
      return allProjects();
  }

  private static Collection<File> allProjects() {
      System.err.println("building all projects");
      return Arrays.asList(new File("..").listFiles(new FileFilter() {
	      public boolean accept(File f) { return isProject(f); }
	  }));
  }

  private static boolean isProject(File f) {
      return f.isDirectory() && Arrays.asList(f.list()).containsAll(Arrays.asList("build.gradle", "app"));
  }
    
  private File gradleProject;
  public GradleBuildTest(File gradleProject) {
      this.gradleProject = gradleProject;
  }

  @Test
  public void test() {
      GradleConnector connector = GradleConnector.newConnector();
      connector.forProjectDirectory(gradleProject);
      ProjectConnection connection = connector.connect();
      try {
	  BuildLauncher launcher = connection.newBuild();
	  launcher.setStandardOutput(System.out);
	  launcher.setStandardError(System.err);	  

	  launcher.forTasks("clean");
	  launcher.run();
	  
	  launcher.forTasks("app:lint");
	  launcher.run();

	  launcher.forTasks("build");
	  launcher.run();	  
      } finally {
	  // Clean up
	  connection.close();
      }
  }
}
