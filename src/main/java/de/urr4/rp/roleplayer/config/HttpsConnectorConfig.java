package de.urr4.rp.roleplayer.config;

import java.io.File;
import java.nio.file.Path;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Adds an additional HTTPS connector alongside the main plain-HTTP one
 * (server.port), using a self-signed PEM certificate generated on the host
 * (see setup.sh, same approach as the sibling "calories" project).
 *
 * <p>Browsers only expose microphone access (getUserMedia, used for the
 * in-browser recording feature) in a "secure context" — plain
 * {@code http://<lan-host>:PORT} doesn't satisfy that, but
 * {@code https://<lan-host>:HTTPS_PORT} does, even with a self-signed
 * certificate (after accepting the browser's one-time warning).
 *
 * <p>If no certificate/key files exist at the configured paths, HTTPS is
 * simply left disabled and the app keeps working as before over HTTP only.
 */
@Component
public class HttpsConnectorConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final Logger log = LoggerFactory.getLogger(HttpsConnectorConfig.class);

    @Value("${DB_PATH:./data/roleplayer.db}")
    private String dbPath;

    @Value("${HTTPS_PORT:3502}")
    private int httpsPort;

    @Value("${TLS_CERT_PATH:}")
    private String tlsCertPathOverride;

    @Value("${TLS_KEY_PATH:}")
    private String tlsKeyPathOverride;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        File certFile = resolvePath(tlsCertPathOverride, "cert.pem");
        File keyFile = resolvePath(tlsKeyPathOverride, "key.pem");

        if (!certFile.exists() || !keyFile.exists()) {
            log.info(
                    "No TLS certificate found at {} - HTTPS disabled (microphone recording needs HTTPS or localhost).",
                    certFile);
            return;
        }

        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(httpsPort);
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setProperty("SSLEnabled", "true");

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate =
                new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        certificate.setCertificateFile(certFile.getAbsolutePath());
        certificate.setCertificateKeyFile(keyFile.getAbsolutePath());
        sslHostConfig.addCertificate(certificate);
        connector.addSslHostConfig(sslHostConfig);

        factory.addAdditionalTomcatConnectors(connector);
        log.info("HTTPS connector enabled on port {} (cert: {})", httpsPort, certFile);
    }

    private File resolvePath(String override, String fileName) {
        if (override != null && !override.isBlank()) {
            return new File(override);
        }
        Path tlsDir = Path.of(dbPath).toAbsolutePath().getParent().resolve("tls");
        return tlsDir.resolve(fileName).toFile();
    }
}
