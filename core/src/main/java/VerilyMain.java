/*
 * VerilyMain.java
 *
 * Main entrypoint into Verily.
 *
 */

import content.TemplateFactory;
import core.Verily;
import core.VerilyContainer;
import exceptions.InitException;
import exceptions.VerilyCompileFailedException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.cli.*;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;
import verily.lang.exceptions.TableHomomorphismException;
import utils.VerilyUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class VerilyMain {

    public static final Options argList = new Options();

    // Main Verily Modes:
    // -run        - run the application (the default)
    // -init <dir> - create a new Verily application in the named directory
    // -new Name   - Create a new Method/Controller pair
    final Logger logger = LoggerFactory.getLogger(VerilyMain.class);

    static {

        Option port = OptionBuilder.withArgName(Verily.ARG_PORTNUMBER)
                .hasArg()
                .withDescription("port number to bind to (default 8000)")
                .create(Verily.ARG_PORT);

        Option init = OptionBuilder.withArgName(Verily.ARG_INIT_DIR)
                .hasArg()
                .withDescription("create a new Verily application in the specified directory")
                .create(Verily.ARG_INIT);

        Option run = new Option(Verily.ARG_RUN, "run the application");
        Option oNew = OptionBuilder.withArgName(Verily.ARG_NEW_CLASS)
                .hasArg()
                .withDescription("create a new Verily Method+Router pair")
                .create(Verily.ARG_NEW);

        Option help = new Option(Verily.ARG_HELP, "display this help");
        Option nocompile = new Option(Verily.ARG_NOCOMPILE, "do not do internal recompile (used for development only)");

        Option fast = new Option(Verily.ARG_FAST, "do not recalculate dependencies before running");


        Option watch = new Option(Verily.ARG_WATCH, "try to dynamically reload classes and templates (not for production use)");

        Option test = new Option(Verily.ARG_TEST, "run the unit tests for this application");

        Option daemon = new Option(Verily.ARG_DAEMON, "run this application in the background");
        Option threads = OptionBuilder.withArgName(Verily.ARG_NUM_THREADS)
                .hasArg()
                .withDescription("the number of threads to create for handling requests.")
                .create(Verily.ARG_THREADS);


        argList.addOption(port);
        argList.addOption(help);
        argList.addOption(init);
        argList.addOption(run);
        argList.addOption(oNew);
        argList.addOption(nocompile);
        argList.addOption(fast);
        argList.addOption(watch);
        argList.addOption(test);
        argList.addOption(daemon);
        argList.addOption(threads);

        System.setProperty(SimpleLogger.LEVEL_IN_BRACKETS_KEY, "true");
        System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "false");
        System.setProperty(SimpleLogger.LOG_FILE_KEY, "System.out");
        System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");

    }

    public static void main(String args[]) {


        CommandLineParser parser = new PosixParser();
        int EXIT = 0;

        try {
            CommandLine line = parser.parse(argList, args);

            sanityCheckCommandLine(line);

            VerilyMain m = new VerilyMain();

            // this code assumes the command line has been sanity checked already
            if (line.hasOption(Verily.ARG_RUN)) {

                long ts1 = System.currentTimeMillis();
                m.bootstrap(line);

                // compile the project
                if (line.hasOption(Verily.ARG_NOCOMPILE) == false) {
                    VerilyUtil.reloadProject();
                }

                long ts2 = System.currentTimeMillis();

                m.ready(ts2 - ts1);


            } else if (line.hasOption(Verily.ARG_HELP)) {
                VerilyMain.usage();
            } else if (line.hasOption(Verily.ARG_INIT)) {
                m.init(line);
            } else if (line.hasOption(Verily.ARG_NEW)) {
                m.newPair(line);
            } else if (line.hasOption(Verily.ARG_TEST)) {
                VerilyUtil.test();
            }


        } catch (ParseException e) {
            // we aren't really interested in moving forward if this fails
            System.err.println(VerilyUtil.getMessage("MsgParsingFailed") + e.getMessage());
            VerilyMain.usage();
            EXIT = 1;

        } catch (InitException e) {
            // we aren't really interested in moving forward if this fails
            System.err.println(VerilyUtil.getMessage("MsgInitFailed") + e.getMessage());
            EXIT = 1;

        } catch (NoSuchFileException e) {
            System.err.println(VerilyUtil.getMessage("MsgInvalidDirectoryFormat"));
            EXIT = 1;
        } catch (IOException e) {
            System.err.println(VerilyUtil.getMessage("MsgContainerInitFailed") + e.getMessage());
            EXIT = 1;
        } catch (NumberFormatException e) {
            System.err.println(VerilyUtil.getMessage("MsgInvalidPort"));
            EXIT = 1;

        } catch (TableHomomorphismException e) {
            System.err.println(e.getMessage());
        } catch (InterruptedException e) {
            // this is a little bit of an unexpected exception so we are going to bail ungracefully
            e.printStackTrace();
            EXIT = 1;
        } catch (VerilyCompileFailedException e) {
            System.err.println(VerilyUtil.getMessage("MsgCompileFailed"));
            EXIT = 1;
        }

        // only make an explicit call to exit if we have an abnormal exit condition
        if (EXIT != 0) {
            System.exit(EXIT);
        }
    }

    public static void sanityCheckCommandLine(CommandLine l) throws ParseException {

        // make sure only one of -run, -init, or -new are specified
        boolean orun = l.hasOption(Verily.ARG_RUN);
        boolean oinit = l.hasOption(Verily.ARG_INIT);
        boolean onew = l.hasOption(Verily.ARG_NEW);
        boolean otest = l.hasOption(Verily.ARG_TEST);


        if (orun ^ oinit ^ onew ^ otest == false) {
            throw new ParseException("Exactly one of -init, -run, -test, or -new must be specified");
        }
    }

    public static void usage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("verily", argList);
    }

    public void init(CommandLine cl) throws InitException {

        String newProject = cl.getOptionValue(Verily.ARG_INIT);
        String newProjectName = null;

        Path here = Paths.get("");

        // special case for IDE project creation.
        if(newProject.startsWith("./")==false){
            if (Files.exists(here.resolve(newProject))) {
                throw new InitException(String.format("Directory %s already exists.", newProject));
            }
            newProjectName = newProject;
        }else{
            newProjectName = newProject.substring(newProject.indexOf("/")+1);
            newProject     = ".";
        }

        // Step 1 - Create the directory hierarchy
        try {

            logger.info("Creating directory hierarchy...");
            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("java").resolve("routers"));
            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("java").resolve("methods"));

            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("resources").resolve("css"));
            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("resources").resolve("images"));
            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("resources").resolve("js"));

            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("test").resolve("java"));

            logger.info("Done.");

        } catch (IOException e) {
            throw new InitException(String.format("Error creating project directories. Message: %s", e.getMessage()));

        }

        logger.info("Initializing Maven POM...");

        // Step 2 - Fill in the pom.xml template and copy it over.
        try {
            Writer body = new OutputStreamWriter(new FileOutputStream(here.resolve(newProject).resolve("pom.xml").toFile()));
            Map<String, String> vars = new HashMap<String, String>();

            vars.put("version", Verily.VERSION);
            vars.put("projectName", newProjectName);

            Template t = TemplateFactory.getBootstrapInstance().getPOMTemplate();

            t.process(vars, body);

            body.close();

        } catch (FileNotFoundException e) {
            throw new InitException(String.format("FileNotFoundException. Message: %s", e.getMessage()));
        } catch (TemplateException e) {
            throw new InitException(String.format("Error during template initialization. Message: %s", e.getMessage()));
        } catch (IOException e) {
            throw new InitException(String.format("Error creating POM file. Message: %s", e.getMessage()));
        }

        logger.info("Done. Execute \"verily -run\" from inside your new project directory to run this project.");
    }

    public void newPair(CommandLine cl) throws InitException {

        String newPairName = cl.getOptionValue(Verily.ARG_NEW);

        Path here = Paths.get("");

        Path method = here.resolve("src").resolve("main").resolve("java").resolve("methods").resolve(newPairName + ".java");
        Path controller = here.resolve("src").resolve("main").resolve("java").resolve("routers").resolve(newPairName + ".java");
        Path test = here.resolve("src").resolve("test").resolve("java").resolve(newPairName + "Test.java");


        if (Files.exists(method) || Files.exists(controller) || Files.exists(test)) {
            throw new InitException(String.format("At least one element of the pair \"%s\" already exists.", newPairName));
        }


        // Step 1 - Create a Method/Controller pair (and a unit test.)
        logger.info("Creating a new Method/Router pair...");

        // Step 2 - Fill in the templates and copy it over.
        try {
            Writer methodBody = new OutputStreamWriter(new FileOutputStream(method.toFile()));
            Writer controllerBody = new OutputStreamWriter(new FileOutputStream(controller.toFile()));
            Writer testBody = new OutputStreamWriter(new FileOutputStream(test.toFile()));

            Map<String, String> vars = new HashMap<String, String>();

            vars.put("version", Verily.VERSION);
            vars.put("NAME", newPairName);

            Template t1 = TemplateFactory.getBootstrapInstance().getMethodTemplate();
            Template t2 = TemplateFactory.getBootstrapInstance().getRouterTemplate();
            Template t3 = TemplateFactory.getBootstrapInstance().getTestTemplate();

            t1.process(vars, methodBody);
            t2.process(vars, controllerBody);
            t3.process(vars, testBody);

            methodBody.close();
            controllerBody.close();
            testBody.close();

        } catch (FileNotFoundException e) {
            throw new InitException(String.format("FileNotFoundException. Message: %s", e.getMessage()));
        } catch (TemplateException e) {
            throw new InitException(String.format("Error during template initialization. Message: %s", e.getMessage()));
        } catch (IOException e) {
            throw new InitException(String.format("Error creating Pair file. Message: %s", e.getMessage()));
        }


        logger.info("Method/Router Pair Created. You can find the files created in the following locations:");
        logger.info("M: {}", method.toString());
        logger.info("R: {}", controller.toString());
        logger.info("T: {}", test.toString());

    }

    public void bootstrap(CommandLine cl) throws IOException, NumberFormatException, TableHomomorphismException {

        int port = Verily.DEFAULT_PORT;
        int numThreads = Verily.DEFAULT_THREADS;

        if (cl.getOptionValue(Verily.ARG_PORT) != null) {
            port = Integer.parseInt(cl.getOptionValue(Verily.ARG_PORT));
        }

        if(cl.getOptionValue(Verily.ARG_THREADS)!=null){
            numThreads = Integer.parseInt(cl.getOptionValue(Verily.ARG_THREADS));
        }

        logger.info("Bootstrapping Verily on port {}...", port);


        Container container = VerilyContainer.getContainer(numThreads);

        Server server = new ContainerServer(container);
        Connection connection = new SocketConnection(server);
        SocketAddress address = new InetSocketAddress(port);

        connection.connect(address);

        VerilyContainer.getContainer().getEnv().setPort(port);

        if (cl.hasOption(Verily.ARG_WATCH)) {
            VerilyContainer.getContainer().getEnv().setReload(true);
        } else {
            VerilyContainer.getContainer().getEnv().setReload(false);
        }


        logger.info("Starting services...");

        VerilyContainer.getContainer().startServices();

        logger.info("------------------------------------------------------------------------");
        logger.info("Verily STARTUP COMPLETE");
        logger.info("------------------------------------------------------------------------");


    }

    public void ready(long timeInMs) throws IOException, TableHomomorphismException {
        logger.info("Bootstrapping complete in {} seconds. Verily ready to serve requests at http://localhost:{}/", (double) timeInMs / 1000, VerilyContainer.getContainer().getEnv().getPort());
    }
}