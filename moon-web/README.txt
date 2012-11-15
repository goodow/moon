== Getting Dependencies ==

  1. Goodow Wave: https://github.com/goodow/wave/
  2. Goodow Walkaround: https://github.com/goodow/walkaround/


== Development And Running ==

-- Option A: Import your project into Eclipse --

Configure Eclipse following the instructions at 
http://code.google.com/p/google-web-toolkit/wiki/WorkingWithMaven#Using_Maven_with_Google_Plugin_for_Eclipse

In Eclipse, go to the File menu and choose:

  File -> Import... -> Existing Maven Projects into Workspace

  Select the directory containing this file.

  Click Finish.

You can now browse the project in Eclipse.

To launch your web app in GWT super development mode:

  TBD.


-- Option B: Build from the command line with Maven --

If you prefer to work from the command line, you can use Maven to
build your project (http://maven.apache.org/). You will also need Java
1.6 JDK. Maven uses the supplied 'pom.xml' file which describes
exactly how to build your project. This file has been tested to work
against Maven 2.2.1. The following assumes 'mvn' is on your command
line path.

To compile and run your project on a local Google AppEngine development web server:

  mvn clean gae:run

For a full listing of other goals, visit:
http://mojo.codehaus.org/gwt-maven-plugin/plugin-info.html

