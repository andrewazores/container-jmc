package com.redhat.rhjmc.containerjfr.net.web;

import java.nio.file.Path;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.NetworkModule;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module(includes = {NetworkModule.class})
public abstract class WebModule {
    @Binds
    @IntoSet
    abstract ConnectionListener bindWebServer(WebServer exporter);

    @Provides
    @Singleton
    static WebServer provideWebServer(
            HttpServer httpServer,
            NetworkConfiguration netConf,
            Environment env,
            @Named("RECORDINGS_PATH") Path recordingsPath,
            FileSystem fs,
            ReportGenerator reportGenerator,
            AuthManager authManager,
            Gson gson,
            Logger logger) {
        return new WebServer(
                httpServer,
                netConf,
                env,
                recordingsPath,
                fs,
                authManager,
                gson,
                reportGenerator,
                logger);
    }
}
