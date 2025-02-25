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

package com.couchbase.client.core.endpoint;

import com.couchbase.client.core.annotation.Stability;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Allows configuring a {@link CircuitBreaker}.
 *
 * @since 2.0.0
 */
public class CircuitBreakerConfig {

  public static final boolean DEFAULT_ENABLED = true;
  public static final int DEFAULT_VOLUME_THRESHOLD = 20;
  public static final int DEFAULT_ERROR_THRESHOLD_PERCENTAGE = 50;
  public static final Duration DEFAULT_SLEEP_WINDOW = Duration.ofSeconds(5);
  public static final Duration DEFAULT_ROLLING_WINDOW = Duration.ofMinutes(1);

  private final boolean enabled;
  private final int volumeThreshold;
  private final int errorThresholdPercentage;
  private final Duration sleepWindow;
  private final Duration rollingWindow;

  /**
   * Creates a new builder to customize the configuration properties.
   *
   * @return a {@link Builder} to customize.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a new {@link CircuitBreakerConfig} with the default settings applied.
   *
   * @return a new {@link CircuitBreakerConfig} with defaults.
   */
  public static CircuitBreakerConfig create() {
    return builder().build();
  }

  /**
   * Creates a new {@link CircuitBreakerConfig} which disables the circuit breaker.
   *
   * @return a new disabled {@link CircuitBreakerConfig}.
   */
  public static CircuitBreakerConfig disabled() {
    return builder().enabled(false).build();
  }

  private CircuitBreakerConfig(final Builder builder) {
    this.enabled = builder.enabled;
    this.volumeThreshold = builder.volumeThreshold;
    this.errorThresholdPercentage = builder.errorThresholdPercentage;
    this.sleepWindow = builder.sleepWindow;
    this.rollingWindow = builder.rollingWindow;
  }

  /**
   * Returns true if this circuit breaker is enabled.
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Returns the volume threshold at which point the circuit will decide if it opens.
   */
  public int volumeThreshold() {
    return volumeThreshold;
  }

  /**
   * Returns the configured error threshold percentage after which the circuit possibly opens.
   */
  public int errorThresholdPercentage() {
    return errorThresholdPercentage;
  }

  /**
   * Returns the configured sleep window after which a canary is allowed to go through.
   */
  public Duration sleepWindow() {
    return sleepWindow;
  }

  /**
   * Returns the configured rolling window duration which is considered to track the failed ops.
   */
  public Duration rollingWindow() {
    return rollingWindow;
  }

  @Stability.Volatile
  public Map<String, Object> exportAsMap() {
    Map<String, Object> export = new LinkedHashMap<>();
    export.put("enabled", enabled);
    export.put("volumeThreshold", volumeThreshold);
    export.put("errorThresholdPercentage", errorThresholdPercentage);
    export.put("sleepWindowMs", sleepWindow.toMillis());
    export.put("rollingWindowMs", rollingWindow.toMillis());
    return export;
  }

  public static class Builder {

    private boolean enabled = DEFAULT_ENABLED;
    private int volumeThreshold = DEFAULT_VOLUME_THRESHOLD;
    private int errorThresholdPercentage = DEFAULT_ERROR_THRESHOLD_PERCENTAGE;
    private Duration sleepWindow = DEFAULT_SLEEP_WINDOW;
    private Duration rollingWindow = DEFAULT_ROLLING_WINDOW;

    /**
     * Enables or disables this circuit breaker.
     *
     * <p>If this property is set to false, then all other properties are not looked at.</p>
     *
     * @param enabled if true enables it, if false disables it.
     * @return this {@link Builder} for chaining purposes.
     */
    public Builder enabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * The volume threshold defines how many operations need to be in the window at least so that
     * the threshold percentage can be meaningfully calculated.
     *
     * <p>The default is 20.</p>
     *
     * @param volumeThreshold the volume threshold in the interval.
     * @return this {@link Builder} for chaining purposes.
     */
    public Builder volumeThreshold(final int volumeThreshold) {
      this.volumeThreshold = volumeThreshold;
      return this;
    }

    /**
     * The percentage of operations that need to fail in a window until the circuit is opened.
     *
     * <p>The default is 50.</p>
     *
     * @param errorThresholdPercentage the percent of ops that need to fail.
     * @return this {@link Builder} for chaining purposes.
     */
    public Builder errorThresholdPercentage(final int errorThresholdPercentage) {
      this.errorThresholdPercentage = errorThresholdPercentage;
      return this;
    }

    /**
     * The sleep window that is waited from when the circuit opens to when the canary is tried.
     *
     * <p>The default is 5 seconds.</p>
     *
     * @param sleepWindow the sleep window as a duration.
     * @return this {@link Builder} for chaining purposes.
     */
    public Builder sleepWindow(final Duration sleepWindow) {
      this.sleepWindow = sleepWindow;
      return this;
    }

    /**
     * How long the window is in which the number of failed ops are tracked in a rolling fashion.
     *
     * <p>The default is 1 minute.</p>
     *
     * @param rollingWindow the rolling window duration.
     * @return this {@link Builder} for chaining purposes.
     */
    public Builder rollingWindow(final Duration rollingWindow) {
      this.rollingWindow = rollingWindow;
      return this;
    }

    /**
     * Creates a new {@link CircuitBreakerConfig} out of the configured properties.
     *
     * @return the new {@link CircuitBreakerConfig}.
     */
    public CircuitBreakerConfig build() {
      return new CircuitBreakerConfig(this);
    }
  }

}
