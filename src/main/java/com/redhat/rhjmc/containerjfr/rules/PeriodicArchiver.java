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
package com.redhat.rhjmc.containerjfr.rules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.utils.URLEncodedUtils;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.Credentials;
import com.redhat.rhjmc.containerjfr.net.web.http.AbstractAuthenticatedRequestHandler;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;
import com.redhat.rhjmc.containerjfr.util.HttpStatusCodeIdentifier;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

class PeriodicArchiver implements Runnable {

    private final WebClient webClient;
    private final ServiceRef serviceRef;
    private final Credentials credentials;
    private final String recordingName;
    private final boolean keepOldArchives;
    private final Logger logger;

    private final List<String> previousRecordings;

    PeriodicArchiver(
            WebClient webClient,
            ServiceRef serviceRef,
            Credentials credentials,
            String recordingName,
            boolean keepOldSnapshots,
            Logger logger) {
        this.webClient = webClient;
        this.serviceRef = serviceRef;
        this.credentials = credentials;
        this.recordingName = recordingName;
        this.keepOldArchives = keepOldSnapshots;
        this.logger = logger;

        this.previousRecordings = new ArrayList<>(1);
    }

    @Override
    public void run() {
        logger.info(String.format("PeriodicArchiver for %s running", recordingName));

        try {
            performArchival();
            if (!keepOldArchives && this.previousRecordings.size() > 1) {
                pruneArchives();
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error(e);
        }
    }

    void performArchival() throws InterruptedException, ExecutionException {
        // FIXME using an HTTP request to localhost here works well enough, but is needlessly
        // complex. The API handler targeted here should be refactored to extract the logic that
        // creates the recording from the logic that simply figures out the recording parameters
        // from the POST form, path param, and headers. Then the handler should consume the API
        // exposed by this refactored chunk, and this refactored chunk can also be consumed here
        // rather than firing HTTP requests to ourselves

        // FIXME don't hardcode this path
        String path =
                "/api/v1/targets/"
                        + URLEncodedUtils.formatSegments(serviceRef.getJMXServiceUrl().toString())
                        + "/recordings/"
                        + URLEncodedUtils.formatSegments(recordingName);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        if (credentials != null) {
            headers.add(
                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER,
                    String.format(
                            "Basic %s",
                            Base64.encodeBase64String(
                                    String.format(
                                                    "%s:%s",
                                                    credentials.getUsername(),
                                                    credentials.getPassword())
                                            .getBytes())));
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        this.webClient
                .patch(path)
                .timeout(30_000L)
                .putHeaders(headers)
                .sendBuffer(
                        Buffer.buffer("save"),
                        ar -> {
                            if (ar.failed()) {
                                this.logger.error(
                                        new IOException("Periodic archival failed", ar.cause()));
                                future.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> resp = ar.result();
                            if (!HttpStatusCodeIdentifier.isSuccessCode(resp.statusCode())) {
                                this.logger.error(resp.bodyAsString());
                                future.completeExceptionally(new IOException(resp.bodyAsString()));
                                return;
                            }
                            future.complete(resp.bodyAsString());
                        });
        this.previousRecordings.add(future.get());
    }

    void pruneArchives() {
        logger.info("Pruning old archived recordings");

        List<CompletableFuture<Boolean>> futures = new ArrayList<>(previousRecordings.size());
        this.previousRecordings
                .subList(1, previousRecordings.size())
                .forEach(
                        recordingName -> {
                            String path =
                                    "/api/v1/recordings/"
                                            + URLEncodedUtils.formatSegments(recordingName);
                            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                            if (credentials != null) {
                                headers.add(
                                        AbstractAuthenticatedRequestHandler
                                                .JMX_AUTHORIZATION_HEADER,
                                        String.format(
                                                "Basic %s",
                                                Base64.encodeBase64String(
                                                        String.format(
                                                                        "%s:%s",
                                                                        credentials.getUsername(),
                                                                        credentials.getPassword())
                                                                .getBytes())));
                            }

                            CompletableFuture<Boolean> future = new CompletableFuture<>();
                            futures.add(future);
                            this.webClient
                                    .delete(path)
                                    .timeout(30_000L)
                                    .putHeaders(headers)
                                    .send(
                                            ar -> {
                                                if (ar.failed()) {
                                                    this.logger.error(
                                                            new IOException(
                                                                    "Periodic archival failed",
                                                                    ar.cause()));
                                                    future.completeExceptionally(ar.cause());
                                                    return;
                                                }
                                                HttpResponse<Buffer> resp = ar.result();
                                                if (!HttpStatusCodeIdentifier.isSuccessCode(
                                                        resp.statusCode())) {
                                                    this.logger.error(resp.bodyAsString());
                                                    future.completeExceptionally(
                                                            new IOException(resp.bodyAsString()));
                                                    return;
                                                }
                                                future.complete(true);
                                            });
                        });
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
    }
}
