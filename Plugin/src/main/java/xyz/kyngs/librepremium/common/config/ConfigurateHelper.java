package xyz.kyngs.librepremium.common.config;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import xyz.kyngs.easydb.scheduler.ThrowableFunction;
import xyz.kyngs.librepremium.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librepremium.common.config.key.ConfigurationKey;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public record ConfigurateHelper(CommentedConfigurationNode configuration) {

    public String getString(String path) {
        return get(String.class, path);
    }

    public Integer getInt(String path) {
        return get(Integer.class, path);
    }

    public Boolean getBoolean(String path) {
        return get(Boolean.class, path);
    }

    public Long getLong(String path) {
        return get(Long.class, path);
    }

    public <T> T get(Class<T> clazz, String path) {
        return configurationFunction(path, node -> {
            if (node.isList()) throw new CorruptedConfigurationException("Node is a list!");
            return node.get(clazz);
        });
    }

    public List<String> getStringList(String path) {
        return getList(String.class, path);
    }

    public <T> List<T> getList(Class<T> clazz, String path) {
        return configurationFunction(path, node -> {
            if (!node.isList()) throw new CorruptedConfigurationException("Node is not a list!");
            return node.getList(clazz);
        });
    }

    public Multimap<String, String> getServerMap(String path) {
        return configurationFunction(path, node -> {
            if (!node.isMap()) throw new CorruptedConfigurationException("Node is not a map!");

            var map = HashMultimap.<String, String>create();

            for (Map.Entry<Object, CommentedConfigurationNode> entry : node.childrenMap().entrySet()) {
                if (!entry.getValue().isList()) throw new CorruptedConfigurationException("Node is not a list!");

                var list = entry.getValue().getList(String.class);

                if (list == null) throw new CorruptedConfigurationException("List is null!");

                for (String s : list) {
                    map.put(entry.getKey().toString().replace('§', '.'), s);
                }
            }

            return map;
        });
    }

    public void set(String path, Object value) {
        try {
            resolve(path)
                    .set(value);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    public CommentedConfigurationNode resolve(String key) {
        return configuration.node(Splitter.on('.').splitToList(key).toArray());
    }

    public <T> T configurationFunction(String path, ThrowableFunction<CommentedConfigurationNode, T, Exception> function) {
        try {
            var node = resolve(path);
            if (node == null || node.virtual()) return null;
            return function.run(resolve(path));
        } catch (Exception e) {
            return null;
        }
    }

    public void setDefault(ConfigurationKey<?> key) {
        try {
            var node = resolve(key.key());

            var defaultValue = key.defaultValue();

            if (defaultValue != null) {
                if (key.defaultValue() instanceof Multimap<?, ?> multimap) {
                    for (Map.Entry<?, ? extends Collection<?>> entry : multimap.asMap().entrySet()) {
                        node.node(entry.getKey().toString()).set(entry.getValue());
                    }
                } else {
                    node.set(defaultValue);
                }

            }
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    public void setComment(ConfigurationKey<?> key) {
        resolve(key.key())
                .comment(key.comment());
    }

    public <T> T get(ConfigurationKey<T> key) {
        return key.compute(this);
    }
}
