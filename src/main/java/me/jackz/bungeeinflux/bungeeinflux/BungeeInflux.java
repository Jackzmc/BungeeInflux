package me.jackz.bungeeinflux.bungeeinflux;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.bstats.bungeecord.MetricsLite;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BungeeInflux extends Plugin {

    private static InfluxDB influxDB;
    private static String measurement_name;
    private static Map<String, String> tagsMap = new HashMap<>();

    private final int bStatsID = 7327;

    @Override
    public void onEnable() {
        // Plugin startup logic
        try {
            if(!getDataFolder().exists()) {
                //noinspection ResultOfMethodCallIgnored
                getDataFolder().mkdir();
            }
            saveDefaultResource("config.yml", false);
        }catch(IOException ex) {
            getLogger().severe("Could not copy config.yml to plugins folder: ");
            ex.printStackTrace();
        }
        try {
            Configuration config = loadConfig();
            String url = config.getString("connection.url", "http://localhost:8086");
            String username = config.getString("connection.username", "bungeeinflux-dummyvalue");
            String password = config.getString("connection.password", "bungeeinflux-dummyvalue");
            String database = config.getString("connection.database", "bungeecoord");
            //provide dummy values if empty/null incase influxdb is setup with no authentication
            if(username.equals("")) username = "bungeeinflux-dummyuser";
            if(password.equals("")) username = "bungeeinflux-dummypass";

            boolean useGzip = config.getBoolean("gzip",false);
            measurement_name = config.getString("measurement_name","bungeecoord");
            int update_interval = config.getInt("update_interval_seconds",300);
            Configuration tags = config.getSection("tags");
            for (String key : tags.getKeys()) {
                //don't include example tag
                if(key.equals("example")) continue;
                tagsMap.put(key, tags.getString(key));
            }

            //init metrics
            if(config.getBoolean("metrics",true)) {
                MetricsLite metrics = new MetricsLite(this, bStatsID);
                if(metrics.isEnabled()) getLogger().info("bStats metrics is enabled");
            }
            //finally connect to influx

            connectInflux(url, username, password, database);

            if (useGzip) {
                influxDB.enableGzip();
            } else {
                influxDB.disableGzip();
            }
            getLogger().info("Updating stats every " + update_interval + " seconds");
            //register command & scheduler
            getProxy().getPluginManager().registerCommand(this, new MainCommand(this));
            getProxy().getScheduler().schedule(this, this::updateProxyStatuses, 0L, update_interval, TimeUnit.SECONDS);
            //getProxy().registerChannel("bungeecoord");
        }catch(IOException ex) {
            getLogger().severe("An exception occurred while initializing: ");
            ex.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if(influxDB != null) influxDB.close();
        getProxy().getScheduler().cancel(this);
    }

    public void connectInflux(String url, String username, String password, String database) {
        influxDB = InfluxDBFactory.connect(url, username, password);
        getLogger().info("Connected to InfluxDB server " + url + " on database " + database);

        influxDB.query(new Query("CREATE DATABASE " + database));
        influxDB.setDatabase(database);

        influxDB.enableBatch(BatchOptions.DEFAULTS.exceptionHandler(
                (failedPoints, throwable) -> {
                    getLogger().warning("Influx Error: " + throwable.getMessage());
                })
        );
    }

    public void initialize() {
        influxDB.close();
        getProxy().getScheduler().cancel(this);

        onEnable();
    }

    private void saveDefaultResource(String filename, boolean force) throws IOException {
        Path file = Paths.get(getDataFolder().getAbsolutePath(),filename);
        if (force || !Files.exists(file)) {
            try (InputStream in = getResourceAsStream(filename)) {
                //won't replace existing because of File.exists, unless force
                Files.copy(in, file);
            }
        }
    }

    private Configuration loadConfig() throws IOException {
        File config_file = new File(getDataFolder(),"config.yml");
        if(config_file.exists()) {
            try {
                return ConfigurationProvider.getProvider(YamlConfiguration.class).load(config_file);
            } catch (IOException e) {
                getLogger().warning("Could not load config.yml, using default config");
            }
        }
        saveDefaultResource("config.yml",false);
        return ConfigurationProvider.getProvider(YamlConfiguration.class).load(config_file);
    }

    private void updateProxyStatuses() {
        for (ServerInfo info : getProxy().getServers().values()) {
            info.ping((result, error) -> {
                if(error == null) {
                    influxDB.write(Point.measurement(measurement_name)
                        .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                        .tag("server", info.getName())
                        .tag("version", result.getVersion().getName())
                        .tag(tagsMap)
                        .addField("players", info.getPlayers().size())
                        .build());
                }
                //server is offline if error, probably
            });
        }
    }

    public static InfluxDB getInfluxDB() {
        return influxDB;
    }
}
