/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */
package network.misq.common.configuration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * TIPS
 * <p>
 * Set the Java system property -Dconfig.trace=loads to get output on stderr describing each file that is loaded.
 * (The output will show the library attempting to load misq.json, misq.properties, and reference.conf, but not fail.)
 * <p>
 * Use myConfig.root().render() to get a Config as a string with comments showing where each value came from.
 * This string can be printed out on console or logged to a file etc.
 * <p>
 * If you see errors like com.typesafe.config.ConfigException$Missing: No configuration setting found for key foo,
 * and you're sure that key is defined in your config file, they might appear e.g. when you're loading configuration
 * from a thread that's not the JVM's main thread. Try passing the ClassLoader in manually - e.g. with
 * ConfigFactory.load(getClass().getClassLoader()) or setting the context class loader. If you don't pass one,
 * Lightbend Config uses the calling thread's contextClassLoader, and in some cases, it may not have your configuration
 * files in its classpath, so loading the config on that thread can yield unexpected, erroneous results.
 * <p>
 * REFERENCES
 * <p>
 * https://github.com/lightbend/config
 * https://www.stubbornjava.com/posts/typesafe-config-features-and-example-usage
 * https://florentfo.rest/2019/01/07/configuring-spark-applications-with-typesafe-config.html
 */
@Slf4j
public class MisqConfig {

    public static final String NETWORK_CONFIG_PATH = "misq.networkConfig";
    public static final String NETWORK_IO_POOL_CONFIG_PATH = NETWORK_CONFIG_PATH + ".networkIOPool";

    private static final Config MISQ_CONFIG = ConfigFactory.load("misq");

    static {
        try {
            MISQ_CONFIG.checkValid(ConfigFactory.defaultReference(), "misq");
        } catch (Exception ex) {
            throw new IllegalStateException("misq.conf validation failed", ex);
        }
    }

    /**
     * Return the global Config object.
     *
     * @return Config
     */
    public static Config getGlobalConfig() {
        return MISQ_CONFIG;
    }

    /**
     * Return a Config for a given configuration path, e.g.,
     * "misq.networkConfig.torPeerGroupServiceConfig.peerExchangeConfig".
     *
     * @param path String representing a valid path in misq.conf
     * @return Config
     */
    public static Config getConfig(String path) {
        MISQ_CONFIG.checkValid(ConfigFactory.defaultReference(), path);
        return MISQ_CONFIG.getConfig(path);
    }

    public static void main(String[] args) {
        log.info("MISQ_CONFIG = {}", MISQ_CONFIG);
        log.info("misq.networkConfig.torPeerGroupServiceConfig = {}", getConfig("misq.networkConfig.torPeerGroupServiceConfig"));
        log.info("misq.networkConfig.i2pPeerGroupServiceConfig = {}", getConfig("misq.networkConfig.i2pPeerGroupServiceConfig"));
    }
}
