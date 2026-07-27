package top.rootu.lampa.net;

import android.os.Build;

import com.btr.proxy.selector.pac.PacProxySelector;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import top.rootu.lampa.helpers.Helpers;

/**
 * OkHttp client factory used by {@link Http} and TMDB.
 *
 * The former Apache {@code DefaultHttpClient} path (createStandardHttpClient,
 * TlsSniSocketFactory, gzip/deflate/brotli entities) was dead code and has been
 * removed together with the {@code org.apache.http.legacy} library.
 */
public class HttpHelper {

    public static final int DEFAULT_CONNECTION_TIMEOUT = 15000;

    /**
     * The 'User-Agent' name to send to the server
     */
    public static String userAgent = "Mozilla/5.0";

    public static OkHttpClient getOkHttpClient(int timeout) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (Http.isDisableH2()) {
            Http.disableH2(false);
            builder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
        }
        try {
            // Use Conscrypt for TLS 1.3 on Android < 10. Certificates are validated
            // against the system CA store (system trust manager); the default OkHttp
            // hostname verifier is kept — no trust-all, no MITM bypass.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && (!Helpers.isBrokenTCL() && !Helpers.isWisdomShare())
            ) {
                builder.sslSocketFactory(
                        new TlsSocketFactory(TlsSocketFactory.TLS_MODERN),
                        TlsSocketFactory.getSystemTrustManager()
                );
            }
            // https://github.com/square/okhttp/issues/3894
            // The default OkHttp configuration does not support older versions of TLS,
            // or all cipher suites.  Make our support as reasonably broad as possible.
            builder.connectionSpecs(Arrays.asList(ConnectionSpec.CLEARTEXT,
                    new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .allEnabledTlsVersions()
                            .allEnabledCipherSuites()
                            .build()));
        } catch (NoSuchAlgorithmException | KeyManagementException ignore) {
        }
        if (timeout > 0) {
            builder.connectTimeout(timeout / 2L, TimeUnit.MILLISECONDS);
            builder.readTimeout(timeout, TimeUnit.MILLISECONDS);
            builder.writeTimeout(timeout, TimeUnit.MILLISECONDS);
            builder.callTimeout(timeout, TimeUnit.MILLISECONDS);
        } else {
            builder.connectTimeout(DEFAULT_CONNECTION_TIMEOUT / 2L, TimeUnit.MILLISECONDS);
            builder.readTimeout(DEFAULT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
            builder.writeTimeout(DEFAULT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
            builder.callTimeout(DEFAULT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
        }
        PacProxySelector ps = Http.getProxySelector();
        if (ps != null) {
            builder.proxySelector(ps);
        }
        return builder.build();
    }
}
