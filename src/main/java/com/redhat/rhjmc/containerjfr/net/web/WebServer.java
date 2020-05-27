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
package com.redhat.rhjmc.containerjfr.net.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.utils.URIBuilder;

import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.web.handlers.RequestHandler;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class WebServer {

    private static final String WEB_CLIENT_ASSETS_BASE =
            WebServer.class.getPackageName().replaceAll("\\.", "/");

    private static final String ENABLE_CORS_ENV = "CONTAINER_JFR_ENABLE_CORS";
    private static final String USE_LOW_MEM_PRESSURE_STREAMING_ENV =
            "USE_LOW_MEM_PRESSURE_STREAMING";

    private static final String MIME_TYPE_JSON = "application/json";
    public static final String MIME_TYPE_HTML = "text/html";
    private static final String MIME_TYPE_PLAINTEXT = "text/plain";
    private static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";

    // Use X- prefix so as to not trigger web-browser auth dialogs
    private static final String AUTH_SCHEME_HEADER = "X-WWW-Authenticate";

    private static final Pattern RECORDING_FILENAME_PATTERN =
            Pattern.compile("([A-Za-z\\d-]*)_([A-Za-z\\d-_]*)_([\\d]*T[\\d]*Z)(.[\\d]+)?");

    private final HttpServer server;
    private final NetworkConfiguration netConf;
    private final Environment env;
    private final Path savedRecordingsPath;
    private final FileSystem fs;
    private final Set<RequestHandler> requestHandlers;
    private final Gson gson;
    private final AuthManager auth;
    private final Logger logger;

    WebServer(
            HttpServer server,
            NetworkConfiguration netConf,
            Environment env,
            Path savedRecordingsPath,
            FileSystem fs,
            Set<RequestHandler> requestHandlers,
            Gson gson,
            AuthManager auth,
            Logger logger) {
        this.server = server;
        this.netConf = netConf;
        this.env = env;
        this.savedRecordingsPath = savedRecordingsPath;
        this.fs = fs;
        this.requestHandlers = requestHandlers;
        this.gson = gson;
        this.auth = auth;
        this.logger = logger;
    }

    public void start() throws FlightRecorderException, SocketException, UnknownHostException {
        Router router =
                Router.router(server.getVertx()); // a vertx is only available after server started

        // error page handler
        Handler<RoutingContext> failureHandler =
                ctx -> {
                    HttpStatusException exception;
                    if (ctx.failure() instanceof HttpStatusException) {
                        exception = (HttpStatusException) ctx.failure();
                    } else {
                        exception = new HttpStatusException(500, ctx.failure());
                    }

                    if (exception.getStatusCode() < 500) {
                        logger.warn(exception);
                    } else {
                        logger.error(exception);
                    }

                    if (exception.getStatusCode() == 401) {
                        ctx.response().putHeader(AUTH_SCHEME_HEADER, auth.getScheme().toString());
                    }

                    String payload =
                            exception.getPayload() != null
                                    ? exception.getPayload()
                                    : exception.getMessage();

                    ctx.response()
                            .setStatusCode(exception.getStatusCode())
                            .setStatusMessage(exception.getMessage());

                    String accept = ctx.request().getHeader(HttpHeaders.ACCEPT);
                    if (accept.contains(MIME_TYPE_JSON)
                            && accept.indexOf(MIME_TYPE_JSON)
                                    < accept.indexOf(MIME_TYPE_PLAINTEXT)) {
                        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
                        endWithJsonKeyValue("message", payload, ctx.response());
                        return;
                    }

                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_PLAINTEXT)
                            .end(payload);
                };

        requestHandlers.forEach(
                handler -> {
                    if (!handler.isAvailable()) {
                        return;
                    }
                    Route route = getHandlerRoute(router, handler);
                    if (handler.isAsync()) {
                        route = route.handler(handler);
                    } else {
                        route = route.blockingHandler(handler, handler.isOrdered());
                    }
                    route.failureHandler(failureHandler);
                });

        router.post("/api/v1/auth")
                .blockingHandler(this::handleAuthRequest, false)
                .failureHandler(failureHandler);

        if (isCorsEnabled()) {
            router.options("/*")
                    .blockingHandler(
                            ctx -> {
                                enableCors(ctx.response());
                                ctx.response().end();
                            },
                            false)
                    .failureHandler(failureHandler);
        }

        router.post("/api/v1/recordings")
                .handler(BodyHandler.create(true))
                .handler(this::handleRecordingUploadRequest)
                .failureHandler(failureHandler);

        router.get("/*")
                .handler(StaticHandler.create(WEB_CLIENT_ASSETS_BASE))
                .handler(this::handleWebClientIndexRequest)
                .failureHandler(failureHandler);

        this.server.requestHandler(
                req -> {
                    Instant start = Instant.now();
                    req.response()
                            .endHandler(
                                    (res) ->
                                            logger.info(
                                                    String.format(
                                                            "(%s): %s %s %d %dms",
                                                            req.remoteAddress().toString(),
                                                            req.method().toString(),
                                                            req.path(),
                                                            req.response().getStatusCode(),
                                                            Duration.between(start, Instant.now())
                                                                    .toMillis())));
                    enableCors(req.response());
                    router.handle(req);
                });
    }

    Route getHandlerRoute(Router router, RequestHandler handler) {
        switch (handler.httpMethod()) {
            case CONNECT:
                return router.connect(handler.path());
            case DELETE:
                return router.delete(handler.path());
            case GET:
                return router.get(handler.path());
            case HEAD:
                return router.head(handler.path());
            case OPTIONS:
                return router.options(handler.path());
            case PATCH:
                return router.patch(handler.path());
            case POST:
                return router.post(handler.path());
            case PUT:
                return router.put(handler.path());
            case TRACE:
                return router.trace(handler.path());
            default:
                throw new IllegalArgumentException(handler.httpMethod().toString());
        }
    }

    public void stop() {
        this.server.requestHandler(null);
    }

    public URL getHostUrl()
            throws MalformedURLException, SocketException, UnknownHostException,
                    URISyntaxException {
        return getHostUri().toURL();
    }

    URI getHostUri() throws SocketException, UnknownHostException, URISyntaxException {
        return new URIBuilder()
                .setScheme(server.isSsl() ? "https" : "http")
                .setHost(netConf.getWebServerHost())
                .setPort(netConf.getExternalWebServerPort())
                .build()
                .normalize();
    }

    public String getArchivedDownloadURL(String recordingName)
            throws UnknownHostException, URISyntaxException, SocketException {
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments("api", "v1", "recordings", recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getDownloadURL(JFRConnection connection, String recordingName)
            throws URISyntaxException, IOException {
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments(
                        "api",
                        "v1",
                        "targets",
                        getTargetId(connection),
                        "recordings",
                        recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getArchivedReportURL(String recordingName)
            throws SocketException, UnknownHostException, URISyntaxException {
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments("api", "v1", "reports", recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getReportURL(JFRConnection connection, String recordingName)
            throws URISyntaxException, IOException {
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments(
                        "api", "v1", "targets", getTargetId(connection), "reports", recordingName)
                .build()
                .normalize()
                .toString();
    }

    private String getTargetId(JFRConnection conn) throws IOException {
        return conn.getJMXURL().toString();
    }

    private <T> void endWithJsonKeyValue(String key, T value, HttpServerResponse response) {
        response.end(String.format("{\"%s\":%s}", key, gson.toJson(value)));
    }

    void handleAuthRequest(RoutingContext ctx) {
        boolean authd = false;
        try {
            authd = validateRequestAuthorization(ctx.request()).get();
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }
        if (authd) {
            ctx.response().setStatusCode(200);
            ctx.response().end();
        } else {
            throw new HttpStatusException(401);
        }
    }

    void handleWebClientIndexRequest(RoutingContext ctx) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_HTML);
        ctx.response().sendFile(WEB_CLIENT_ASSETS_BASE + "/index.html");
    }

    void handleRecordingUploadRequest(RoutingContext ctx) {
        try {
            if (!validateRequestAuthorization(ctx.request()).get()) {
                throw new HttpStatusException(401);
            }
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }

        if (!fs.isDirectory(savedRecordingsPath)) {
            throw new HttpStatusException(503, "Recording saving not available");
        }

        FileUpload upload = null;
        for (FileUpload fu : ctx.fileUploads()) {
            // ignore unrecognized form fields
            if ("recording".equals(fu.name())) {
                upload = fu;
                break;
            }
        }

        if (upload == null) {
            throw new HttpStatusException(400, "No recording submission");
        }

        String fileName = upload.fileName();
        if (fileName == null || fileName.isEmpty()) {
            throw new HttpStatusException(400, "Recording name must not be empty");
        }

        if (fileName.endsWith(".jfr")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        Matcher m = RECORDING_FILENAME_PATTERN.matcher(fileName);
        if (!m.matches()) {
            throw new HttpStatusException(400, "Incorrect recording file name pattern");
        }

        String targetName = m.group(1);
        String recordingName = m.group(2);
        String timestamp = m.group(3);
        int count =
                m.group(4) == null || m.group(4).isEmpty()
                        ? 0
                        : Integer.parseInt(m.group(4).substring(1));

        final String basename = String.format("%s_%s_%s", targetName, recordingName, timestamp);
        final String uploadedFileName = upload.uploadedFileName();
        validateRecording(
                upload.uploadedFileName(),
                (res) ->
                        saveRecording(
                                basename,
                                uploadedFileName,
                                count,
                                (res2) -> {
                                    if (res2.failed()) {
                                        ctx.fail(res2.cause());
                                        return;
                                    }

                                    ctx.response()
                                            .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
                                    endWithJsonKeyValue("name", res2.result(), ctx.response());

                                    logger.info(
                                            String.format("Recording saved as %s", res2.result()));
                                }));
    }

    private Future<Boolean> validateRequestAuthorization(HttpServerRequest req) throws Exception {
        return auth.validateHttpHeader(() -> req.getHeader(HttpHeaders.AUTHORIZATION));
    }

    private boolean isCorsEnabled() {
        return this.env.hasEnv(ENABLE_CORS_ENV);
    }

    private void enableCors(HttpServerResponse response) {
        if (isCorsEnabled()) {
            response.putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:9000");
            response.putHeader("Vary", "Origin");
            response.putHeader(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS, HEAD");
            response.putHeader(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    String.join(", ", Arrays.asList("authorization", "Authorization")));
            response.putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            response.putHeader(
                    HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                    String.join(", ", Arrays.asList(AUTH_SCHEME_HEADER)));
        }
    }

    private <T> AsyncResult<T> makeAsyncResult(T result) {
        return new AsyncResult<>() {
            @Override
            public T result() {
                return result;
            }

            @Override
            public Throwable cause() {
                return null;
            }

            @Override
            public boolean succeeded() {
                return true;
            }

            @Override
            public boolean failed() {
                return false;
            }
        };
    }

    private <T> AsyncResult<T> makeFailedAsyncResult(Throwable cause) {
        return new AsyncResult<>() {
            @Override
            public T result() {
                return null;
            }

            @Override
            public Throwable cause() {
                return cause;
            }

            @Override
            public boolean succeeded() {
                return false;
            }

            @Override
            public boolean failed() {
                return true;
            }
        };
    }

    private void validateRecording(String recordingFile, Handler<AsyncResult<Void>> handler) {
        server.getVertx()
                .executeBlocking(
                        event -> {
                            try {
                                JfrLoaderToolkit.loadEvents(
                                        new File(recordingFile)); // try loading events to see if
                                // it's a valid file
                                event.complete();
                            } catch (CouldNotLoadRecordingException | IOException e) {
                                event.fail(e);
                            }
                        },
                        res -> {
                            if (res.failed()) {
                                Throwable t;
                                if (res.cause() instanceof CouldNotLoadRecordingException) {
                                    t =
                                            new HttpStatusException(
                                                    400,
                                                    "Not a valid JFR recording file",
                                                    res.cause());
                                } else {
                                    t = res.cause();
                                }

                                handler.handle(makeFailedAsyncResult(t));
                                return;
                            }

                            handler.handle(makeAsyncResult(null));
                        });
    }

    private void saveRecording(
            String basename, String tmpFile, int counter, Handler<AsyncResult<String>> handler) {
        // TODO byte-sized rename limit is arbitrary. Probably plenty since recordings
        // are also differentiated by second-resolution timestamp
        if (counter >= Byte.MAX_VALUE) {
            handler.handle(
                    makeFailedAsyncResult(
                            new IOException(
                                    "Recording could not be saved. File already exists and rename attempts were exhausted.")));
            return;
        }

        String filename = counter > 1 ? basename + "." + counter + ".jfr" : basename + ".jfr";

        server.getVertx()
                .fileSystem()
                .exists(
                        savedRecordingsPath.resolve(filename).toString(),
                        (res) -> {
                            if (res.failed()) {
                                handler.handle(makeFailedAsyncResult(res.cause()));
                                return;
                            }

                            if (res.result()) {
                                saveRecording(basename, tmpFile, counter + 1, handler);
                                return;
                            }

                            // verified no name clash at this time
                            server.getVertx()
                                    .fileSystem()
                                    .move(
                                            tmpFile,
                                            savedRecordingsPath.resolve(filename).toString(),
                                            (res2) -> {
                                                if (res2.failed()) {
                                                    handler.handle(
                                                            makeFailedAsyncResult(res2.cause()));
                                                    return;
                                                }

                                                handler.handle(makeAsyncResult(filename));
                                            });
                        });
    }

    public static class DownloadDescriptor {
        public final InputStream stream;
        public final Optional<Long> bytes;
        public final Optional<AutoCloseable> resource;

        public DownloadDescriptor(InputStream stream, Long bytes, AutoCloseable resource) {
            this.stream = Objects.requireNonNull(stream);
            this.bytes = Optional.ofNullable(bytes);
            this.resource = Optional.ofNullable(resource);
        }
    }
}
