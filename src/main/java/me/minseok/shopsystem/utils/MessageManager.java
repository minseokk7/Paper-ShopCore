package me.minseok.shopsystem.utils;

import me.minseok.shopsystem.ShopCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

public class MessageManager {

    private final ShopCore plugin;
    private FileConfiguration messages;
    private File messageFile;
    private String prefix;

    // MiniMessage instance for parsing
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageManager(ShopCore plugin) {
        this.plugin = plugin;
        reloadMessages();
    }

    public void reloadMessages() {
        if (messageFile == null) {
            messageFile = new File(plugin.getDataFolder(), "messages.yml");
        }

        if (!messageFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(messageFile);

        // Also load defaults from the jar
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(defaultStream));
            messages.setDefaults(defaultMessages);
        }

        this.prefix = messages.getString("prefix", "<gray>[<gradient:gold:yellow>ShopCore</gradient><gray>] <reset>");
    }

    /**
     * Parse a simple MiniMessage formatted string into a Component
     */
    public Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return miniMessage.deserialize(message);
    }

    /**
     * Get a raw string from messages.yml
     */
    public String getRaw(String path) {
        String message = messages.getString(path);
        if (message == null) {
            plugin.getLogger().log(Level.WARNING, "Message path not found: " + path);
            return "<red>Missing message: " + path;
        }
        return message;
    }

    /**
     * Get parsed component from messages.yml without prefix
     */
    public Component get(String path) {
        return parse(getRaw(path));
    }

    /**
     * Sends a prefixed message to the sender
     */
    public void send(CommandSender sender, String path) {
        Component comp = parse(prefix + getRaw(path));
        sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(comp));
    }

    /**
     * Sends a custom string as prefixed message
     */
    public void sendCustom(CommandSender sender, String customMessage) {
        Component comp = parse(prefix + customMessage);
        sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(comp));
    }
}
