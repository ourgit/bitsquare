/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app;

import ch.qos.logback.classic.Level;
import io.bitsquare.BitsquareException;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.btc.BtcOptionKeys;
import io.bitsquare.btc.UserAgent;
import io.bitsquare.common.CommonOptionKeys;
import io.bitsquare.common.crypto.KeyStorage;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.network.NetworkOptionKeys;
import io.bitsquare.storage.Storage;
import io.bitsquare.util.spring.JOptCommandLinePropertySource;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

public class BitsquareEnvironment extends StandardEnvironment {
    private static final Logger log = LoggerFactory.getLogger(BitsquareEnvironment.class);

    private static final String BITCOIN_NETWORK_PROP = "bitcoinNetwork.properties";

    public static void setDefaultAppName(String defaultAppName) {
        DEFAULT_APP_NAME = defaultAppName;
    }

    public static String DEFAULT_APP_NAME = "Bitsquare";

    public static final String DEFAULT_USER_DATA_DIR = defaultUserDataDir();
    public static final String DEFAULT_APP_DATA_DIR = appDataDir(DEFAULT_USER_DATA_DIR, DEFAULT_APP_NAME);

    public static final String LOG_LEVEL_DEFAULT = (DevFlags.STRESS_TEST_MODE || DevFlags.DEV_MODE) ? Level.TRACE.levelStr : Level.INFO.levelStr;

    static final String BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME = "bitsquareCommandLineProperties";
    static final String BITSQUARE_APP_DIR_PROPERTY_SOURCE_NAME = "bitsquareAppDirProperties";
    static final String BITSQUARE_HOME_DIR_PROPERTY_SOURCE_NAME = "bitsquareHomeDirProperties";
    public static final String BITSQUARE_CLASSPATH_PROPERTY_SOURCE_NAME = "bitsquareClasspathProperties";
    static final String BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME = "bitsquareDefaultProperties";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    private final String appName;
    private final String userDataDir;
    private final String appDataDir;
    private final String btcNetworkDir;
    private final String logLevel;
    private BitcoinNetwork bitcoinNetwork;
    private final String btcSeedNodes, seedNodes, ignoreDevMsg, useTorForBtc, useTorForHttp,
            myAddress, banList, dumpStatistics, maxMemory, socks5ProxyBtcAddress, socks5ProxyHttpAddress;

    public BitsquareEnvironment(OptionSet options) {
        this(new JOptCommandLinePropertySource(BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(
                options)));
    }

    public BitcoinNetwork getBitcoinNetwork() {
        return bitcoinNetwork;
    }

    public void saveBitcoinNetwork(BitcoinNetwork bitcoinNetwork) {
        try {
            Resource resource = getAppDirPropertiesResource();
            File file = resource.getFile();
            Properties properties = new Properties();
            if (file.exists()) {
                Object propertiesObject = appDirProperties().getSource();
                if (propertiesObject instanceof Properties) {
                    properties = (Properties) propertiesObject;
                } else {
                    log.warn("propertiesObject not instance of Properties");
                }
            }
            properties.setProperty(BtcOptionKeys.BTC_NETWORK, bitcoinNetwork.name());

            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                properties.store(fileOutputStream, null);
            } catch (IOException e1) {
                log.error(e1.getMessage());
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public String getAppDataDir() {
        return appDataDir;
    }

    protected BitsquareEnvironment(PropertySource commandLineProperties) {
        logLevel = commandLineProperties.containsProperty(CommonOptionKeys.LOG_LEVEL_KEY) ?
                (String) commandLineProperties.getProperty(CommonOptionKeys.LOG_LEVEL_KEY) :
                LOG_LEVEL_DEFAULT;

        userDataDir = commandLineProperties.containsProperty(CoreOptionKeys.USER_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(CoreOptionKeys.USER_DATA_DIR_KEY) :
                DEFAULT_USER_DATA_DIR;

        appName = commandLineProperties.containsProperty(CoreOptionKeys.APP_NAME_KEY) ?
                (String) commandLineProperties.getProperty(CoreOptionKeys.APP_NAME_KEY) :
                DEFAULT_APP_NAME;

        appDataDir = commandLineProperties.containsProperty(CoreOptionKeys.APP_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(CoreOptionKeys.APP_DATA_DIR_KEY) :
                appDataDir(userDataDir, appName);
        ignoreDevMsg = commandLineProperties.containsProperty(CoreOptionKeys.IGNORE_DEV_MSG_KEY) ?
                (String) commandLineProperties.getProperty(CoreOptionKeys.IGNORE_DEV_MSG_KEY) :
                "";
        dumpStatistics = commandLineProperties.containsProperty(CoreOptionKeys.DUMP_STATISTICS) ?
                (String) commandLineProperties.getProperty(CoreOptionKeys.DUMP_STATISTICS) :
                "";
        maxMemory = commandLineProperties.containsProperty(CoreOptionKeys.MAX_MEMORY) ?
                (String) commandLineProperties.getProperty(CoreOptionKeys.MAX_MEMORY) :
                "";

        seedNodes = commandLineProperties.containsProperty(NetworkOptionKeys.SEED_NODES_KEY) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SEED_NODES_KEY) :
                "";

        myAddress = commandLineProperties.containsProperty(NetworkOptionKeys.MY_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.MY_ADDRESS) :
                "";
        banList = commandLineProperties.containsProperty(NetworkOptionKeys.BAN_LIST) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.BAN_LIST) :
                "";
        socks5ProxyBtcAddress = commandLineProperties.containsProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS) :
                "";
        socks5ProxyHttpAddress = commandLineProperties.containsProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS) :
                "";
        useTorForHttp = commandLineProperties.containsProperty(NetworkOptionKeys.USE_TOR_FOR_HTTP) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.USE_TOR_FOR_HTTP) :
                "";

        btcSeedNodes = commandLineProperties.containsProperty(BtcOptionKeys.BTC_SEED_NODES) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.BTC_SEED_NODES) :
                "";

        useTorForBtc = commandLineProperties.containsProperty(BtcOptionKeys.USE_TOR_FOR_BTC) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.USE_TOR_FOR_BTC) :
                "";

        MutablePropertySources propertySources = this.getPropertySources();
        propertySources.addFirst(commandLineProperties);
        try {
            propertySources.addLast(appDirProperties());
            propertySources.addLast(homeDirProperties());
            propertySources.addLast(classpathProperties());

            bitcoinNetwork = BitcoinNetwork.valueOf(getProperty(BtcOptionKeys.BTC_NETWORK, BitcoinNetwork.DEFAULT.name()).toUpperCase());
            btcNetworkDir = Paths.get(appDataDir, bitcoinNetwork.name().toLowerCase()).toString();
            File btcNetworkDirFile = new File(btcNetworkDir);
            if (!btcNetworkDirFile.exists())
                btcNetworkDirFile.mkdir();

            // btcNetworkDir used in defaultProperties
            propertySources.addLast(defaultProperties());
        } catch (Exception ex) {
            throw new BitsquareException(ex);
        }
    }

    private Resource getAppDirPropertiesResource() {
        String location = String.format("file:%s/bitsquare.properties", appDataDir);
        return resourceLoader.getResource(location);
    }

    PropertySource<?> appDirProperties() throws Exception {
        Resource resource = getAppDirPropertiesResource();

        if (!resource.exists())
            return new PropertySource.StubPropertySource(BITSQUARE_APP_DIR_PROPERTY_SOURCE_NAME);

        return new ResourcePropertySource(BITSQUARE_APP_DIR_PROPERTY_SOURCE_NAME, resource);
    }

    private PropertySource<?> homeDirProperties() throws Exception {
        String location = String.format("file:%s/.bitsquare/bitsquare.properties", getProperty("user.home"));
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists())
            return new PropertySource.StubPropertySource(BITSQUARE_HOME_DIR_PROPERTY_SOURCE_NAME);

        return new ResourcePropertySource(BITSQUARE_HOME_DIR_PROPERTY_SOURCE_NAME, resource);
    }

    private PropertySource<?> classpathProperties() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:bitsquare.properties");
        return new ResourcePropertySource(BITSQUARE_CLASSPATH_PROPERTY_SOURCE_NAME, resource);
    }

    private PropertySource<?> defaultProperties() {
        return new PropertiesPropertySource(BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {
            private static final long serialVersionUID = -8478089705207326165L;

            {
                setProperty(CommonOptionKeys.LOG_LEVEL_KEY, logLevel);

                setProperty(NetworkOptionKeys.SEED_NODES_KEY, seedNodes);
                setProperty(NetworkOptionKeys.MY_ADDRESS, myAddress);
                setProperty(NetworkOptionKeys.BAN_LIST, banList);
                setProperty(NetworkOptionKeys.TOR_DIR, Paths.get(btcNetworkDir, "tor").toString());
                setProperty(NetworkOptionKeys.NETWORK_ID, String.valueOf(bitcoinNetwork.ordinal()));
                setProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS, socks5ProxyBtcAddress);
                setProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS, socks5ProxyHttpAddress);
                setProperty(NetworkOptionKeys.USE_TOR_FOR_HTTP, useTorForHttp);

                setProperty(CoreOptionKeys.APP_DATA_DIR_KEY, appDataDir);
                setProperty(CoreOptionKeys.IGNORE_DEV_MSG_KEY, ignoreDevMsg);
                setProperty(CoreOptionKeys.DUMP_STATISTICS, dumpStatistics);
                setProperty(CoreOptionKeys.APP_NAME_KEY, appName);
                setProperty(CoreOptionKeys.MAX_MEMORY, maxMemory);
                setProperty(CoreOptionKeys.USER_DATA_DIR_KEY, userDataDir);

                setProperty(BtcOptionKeys.BTC_SEED_NODES, btcSeedNodes);
                setProperty(BtcOptionKeys.USE_TOR_FOR_BTC, useTorForBtc);

                setProperty(UserAgent.NAME_KEY, appName);
                setProperty(UserAgent.VERSION_KEY, Version.VERSION);
                setProperty(BtcOptionKeys.WALLET_DIR, btcNetworkDir);
                setProperty(Storage.DIR_KEY, Paths.get(btcNetworkDir, "db").toString());
                setProperty(KeyStorage.DIR_KEY, Paths.get(btcNetworkDir, "keys").toString());
            }
        });
    }

    public static String defaultUserDataDir() {
        if (Utilities.isWindows())
            return System.getenv("APPDATA");
        else if (Utilities.isOSX())
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support").toString();
        else // *nix
            return Paths.get(System.getProperty("user.home"), ".local", "share").toString();
    }

    private static String appDataDir(String userDataDir, String appName) {
        return Paths.get(userDataDir, appName).toString();
    }
}
