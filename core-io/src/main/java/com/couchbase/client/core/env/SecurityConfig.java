/*
 * Copyright (c) 2018 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.client.core.env;

import com.couchbase.client.core.annotation.Stability;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.couchbase.client.core.util.Validators.notNull;

public class SecurityConfig {

  private static final boolean DEFAULT_NATIVE_TLS_ENABLED = true;

  private final boolean nativeTlsEnabled;
  private final boolean tlsEnabled;
  private final List<X509Certificate> trustCertificates;
  private final TrustManagerFactory trustManagerFactory;


  public static Builder builder() {
    return new Builder();
  }

  public static SecurityConfig create() {
    return new SecurityConfig(builder());
  }

  public static Builder enableTls(boolean tlsEnabled) {
    return builder().enableTls(tlsEnabled);
  }

  public static Builder enableNativeTls(boolean nativeTlsEnabled) {
    return builder().enableNativeTls(nativeTlsEnabled);
  }

  public static Builder trustCertificates(final List<X509Certificate> certificates) {
    return builder().trustCertificates(certificates);
  }

  public static Builder trustCertificate(final Path certificatePath) {
    return builder().trustCertificate(certificatePath);
  }

  public static Builder trustManagerFactory(final TrustManagerFactory trustManagerFactory) {
    return builder().trustManagerFactory(trustManagerFactory);
  }

  private SecurityConfig(final Builder builder) {
    tlsEnabled = builder.tlsEnabled;
    nativeTlsEnabled = builder.nativeTlsEnabled;
    trustCertificates = builder.trustCertificates;
    trustManagerFactory = builder.trustManagerFactory;

    if (tlsEnabled) {
      if (trustCertificates != null && trustManagerFactory != null) {
        throw new IllegalArgumentException("Either trust certificates or a trust manager factory" +
          " can be provided, but not both!");
      }
      if ((trustCertificates == null || trustCertificates.isEmpty()) && trustManagerFactory == null) {
        throw new IllegalArgumentException("Either a trust certificate or a trust manager factory" +
          " must be provided when TLS is enabled!");
      }

    }
  }

  public boolean tlsEnabled() {
    return tlsEnabled;
  }

  public List<X509Certificate> trustCertificates() {
    return trustCertificates;
  }

  public TrustManagerFactory trustManagerFactory() {
    return trustManagerFactory;
  }

  public boolean nativeTlsEnabled() {
    return nativeTlsEnabled;
  }

  /**
   * Returns this config as a map so it can be exported into i.e. JSON for display.
   */
  @Stability.Volatile
  Map<String, Object> exportAsMap() {
    Map<String, Object> export = new LinkedHashMap<>();
    export.put("tlsEnabled", tlsEnabled);
    export.put("nativeTlsEnabled", nativeTlsEnabled);
    export.put("hasTrustCertificates", trustCertificates != null && !trustCertificates.isEmpty());
    export.put("trustManagerFactory", trustManagerFactory != null ? trustManagerFactory.getClass().getSimpleName() : null);
    return export;
  }

  public static class Builder {

    private boolean tlsEnabled = false;
    private boolean nativeTlsEnabled = DEFAULT_NATIVE_TLS_ENABLED;
    private List<X509Certificate> trustCertificates = null;
    private TrustManagerFactory trustManagerFactory = null;

    public SecurityConfig build() {
      return new SecurityConfig(this);
    }

    public Builder enableTls(boolean tlsEnabled) {
      this.tlsEnabled = tlsEnabled;
      return this;
    }

    public Builder enableNativeTls(boolean nativeTlsEnabled) {
      this.nativeTlsEnabled = nativeTlsEnabled;
      return this;
    }

    public Builder trustCertificates(final List<X509Certificate> certificates) {
      this.trustCertificates = certificates;
      return this;
    }

    public Builder trustCertificate(final Path certificatePath) {
      final StringBuilder contentBuilder = new StringBuilder();
      try {
        Files.lines(certificatePath, StandardCharsets.UTF_8).forEach(s -> contentBuilder.append(s).append("\n"));
      } catch (IOException ex) {
        throw new IllegalArgumentException("Could not read trust certificate from file \"" + certificatePath + "\"" , ex);
      }
      return trustCertificates(decodeCertificates(Collections.singletonList(contentBuilder.toString())));
    }

    public Builder trustManagerFactory(final TrustManagerFactory trustManagerFactory) {
      this.trustManagerFactory = trustManagerFactory;
      return this;
    }
  }

  /**
   * Helper method to decode string-encoded certificates into their x.509 format.
   *
   * @param certificates the string-encoded certificates.
   * @return the decoded certs in x.509 format.
   */
  public static List<X509Certificate> decodeCertificates(final List<String> certificates) {
    final CertificateFactory cf;
    try {
      cf = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      throw new IllegalStateException("Could not instantiate X.509 CertificateFactory", e);
    }

    return certificates.stream().map(c -> {
      try {
        return (X509Certificate) cf.generateCertificate(
          new ByteArrayInputStream(c.getBytes(StandardCharsets.UTF_8))
        );
      } catch (CertificateException e) {
        throw new IllegalArgumentException("Could not generate certificate from raw input: \"" + c + "\"", e);
      }
    }).collect(Collectors.toList());
  }

}
