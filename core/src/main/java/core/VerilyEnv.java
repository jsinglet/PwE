package core;

import verily.lang.VerilyTable;
import verily.lang.exceptions.MethodNotMappedException;

import java.nio.file.Path;

public class VerilyEnv {

    private Path home;
    private int numberOfThreads;
    private String appName;
    private String appVersion;
    private VerilyTable translationTable;
    private int port;
    private boolean reload;
    private boolean daemon;


    public Path getHome() {
        return home;
    }

    public void setHome(Path home) {
        this.home = home;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public VerilyTable getTranslationTable() {
        return translationTable;
    }

    public void setTranslationTable(VerilyTable translationTable) {
        this.translationTable = translationTable;
    }

    public void findMappedMethod(String context, String method) throws MethodNotMappedException {
        translationTable.methodAt(context, method);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public synchronized boolean isReload() {
        return reload;
    }

    public synchronized void setReload(boolean reload) {
        this.reload = reload;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public void setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }
}