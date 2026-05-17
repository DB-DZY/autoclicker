package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("autoclicker.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    // 配置数据
    int minIntervalTicks = 20;
    int maxIntervalTicks = 30;
    boolean randomMode = true;



    // 单例实例
    private static ConfigManager instance;

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static ConfigManager load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                return GSON.fromJson(reader, ConfigManager.class);
            } catch (IOException e) {
                AutoClicker.LOGGER.error("加载配置文件失败", e);
            }
        }
        return new ConfigManager();
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            AutoClicker.LOGGER.error("保存配置文件失败", e);
        }
    }
}