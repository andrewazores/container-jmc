/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.web.handlers;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Provider;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.web.HttpMimeType;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;

class HealthGetHandler implements RequestHandler {

    static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";
    static final String GRAFANA_DASHBOARD_ENV = "GRAFANA_DASHBOARD_URL";

    private final Provider<WebClient> webClientProvider;
    private final Environment env;
    private final Gson gson;
    private final Logger logger;

    @Inject
    HealthGetHandler(
            Provider<WebClient> webClientProvider, Environment env, Gson gson, Logger logger) {
        this.webClientProvider = webClientProvider;
        this.env = env;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    public String path() {
        return "/health";
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    // try-with-resources generates a "redundant" nullcheck in bytecode
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    @Override
    public void handle(RoutingContext ctx) {
        CompletableFuture<Boolean> datasourceAvailable = new CompletableFuture<>();
        CompletableFuture<Boolean> dashboardAvailable = new CompletableFuture<>();

        WebClient client = webClientProvider.get();
        try {
            checkUri(client, GRAFANA_DATASOURCE_ENV, "", datasourceAvailable);
            checkUri(client, GRAFANA_DASHBOARD_ENV, "/api/health", dashboardAvailable);

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime())
                    .end(
                            gson.toJson(
                                    Map.of(
                                            "dashboardAvailable",
                                            dashboardAvailable.join(),
                                            "datasourceAvailable",
                                            datasourceAvailable.join())));
        } finally {
            client.close();
        }
    }

    private void checkUri(
            WebClient client, String envName, String path, CompletableFuture<Boolean> future) {
        if (this.env.hasEnv(envName)) {
            String uri = this.env.getEnv(envName) + path;
            client.getAbs(uri)
                    .timeout(5000)
                    .send(
                            handler -> {
                                if (handler.failed()) {
                                    future.complete(false);
                                    this.logger.info(new IOException(handler.cause()));
                                    return;
                                }
                                future.complete(handler.result().statusCode() == 200);
                            });
        } else {
            future.complete(false);
        }
    }
}
