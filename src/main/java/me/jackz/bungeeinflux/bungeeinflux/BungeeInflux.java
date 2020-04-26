package me.jackz.bungeeinflux.bungeeinflux;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
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
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

public final class BungeeInflux extends Plugin {

    private static InfluxDB influxDB;
    private static String measurement_name;

    @Override
    public void onEnable() {
        // Plugin startup logic
        try {
            saveDefaultResource("config.yml", false);
            Configuration config = loadConfig();
            String url = config.getString("connection.url", "http://localhost:8086");
            String username = config.getString("connection.username", "");
            String password = config.getString("connection.password", "");
            String database = config.getString("connection.database", "bungeecoord");
            String measurement_name = config.getString("measurement_name","bungeecoord");
            int update_interval = config.getInt("update_interval_seconds",300);
            getLogger().info(String.format("Connecting to InfluxDB server %s on %s", url, database));

            influxDB = InfluxDBFactory.connect(url, username, password);
            influxDB.query(new Query("CREATE DATABASE " + database));
            influxDB.setDatabase(database);

            if (useGzip) {
                influxDB.enableGzip();
            } else {
                influxDB.disableGzip();
            }

            getProxy().getScheduler().schedule(this, this::updateProxyStatuses, 0L, update_interval, TimeUnit.SECONDS);
        }catch(Exception ex) {
            getLogger().severe("An exception occurred while initializing. " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        influxDB.close();
        getProxy().getScheduler().cancel(this);
    }

    private void saveDefaultResource(String filename, boolean force) throws IOException {
        Path file = Paths.get(getDataFolder().getAbsolutePath(),filename);
        if (force || !Files.exists(file)) {
            try (InputStream in = getResourceAsStream(filename)) {
                //won't replace existing because of File.exists, unless force
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
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
                if(error != null) {
                    influxDB.write(Point.measurement(measurement_name)
                        .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                        .tag("server", info.getName())
                        .tag("version", result.getVersion().getName())
                        .addField("players", info.getPlayers().size())
                        .build());
                }
            });
        }
    }

    public static InfluxDB getInfluxDB() {
        return influxDB;
    }
}
