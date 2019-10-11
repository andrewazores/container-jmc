package com.redhat.rhjmc.containerjfr.net;

import java.nio.file.Path;

import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportsModule;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module(includes={
    ReportsModule.class,
})
public abstract class NetworkModule {
    @Binds
    @IntoSet
    abstract ConnectionListener bindWebServer(WebServer exporter);

    @Provides
    @Singleton
    static WebServer provideWebServer(NetworkConfiguration netConf, Environment env, @Named("RECORDINGS_PATH") Path recordingsPath, ReportGenerator reportGenerator, Logger logger) {
        return new WebServer(netConf, env, recordingsPath, reportGenerator, logger);
    }

    @Provides
    @Singleton
    static NetworkConfiguration provideNetworkConfiguration(Environment env, NetworkResolver resolver) {
        return new NetworkConfiguration(env, resolver);
    }

    @Provides
    @Singleton
    static NetworkResolver provideNetworkResolver() {
        return new NetworkResolver();
    }

    @Provides
    @Singleton
    static JFRConnectionToolkit provideJFRConnectionToolkit(ClientWriter cw) {
        return new JFRConnectionToolkit(cw);
    }

    @Provides
    static CloseableHttpClient provideHttpClient() {
        return HttpClients.createMinimal(new BasicHttpClientConnectionManager());
    }
}
