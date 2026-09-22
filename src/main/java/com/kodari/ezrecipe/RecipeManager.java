package com.kodari.ezrecipe;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class RecipeManager {
    private final EzRecipePlugin plugin;

    public RecipeManager(EzRecipePlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureLatestDefaults() {
        ConfigurationSection recipes = plugin.getConfig().getConfigurationSection("recipes");
        if (recipes == null) {
            recipes = plugin.getConfig().createSection("recipes");
        }

        boolean changed = repairGoldenCarrot(recipes);
        ConfigurationSection section = recipes.getConfigurationSection("exp-bottle");
        if (section == null) {
            section = recipes.createSection("exp-bottle");
        }

        if (!section.contains("enabled")) {
            section.set("enabled", true);
            changed = true;
        }
        if (!section.contains("display-name")) {
            section.set("display-name", "&bBottle o' Enchanting");
            changed = true;
        }
        if (!section.contains("result")) {
            section.set("result", "EXPERIENCE_BOTTLE");
            changed = true;
        }
        if (!section.contains("amount")) {
            section.set("amount", 1);
            changed = true;
        }
        if (!section.contains("shape")) {
            section.set("shape", List.of("GDG", "GAG", " G "));
            changed = true;
        }

        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (ingredients == null) {
            ingredients = section.createSection("ingredients");
        }
        if (!ingredients.contains("G")) {
            ingredients.set("G", "GLASS");
            changed = true;
        }
        if (!ingredients.contains("D")) {
            ingredients.set("D", "GLOWSTONE_DUST");
            changed = true;
        }
        if (!ingredients.contains("A")) {
            ingredients.set("A", "AMETHYST_SHARD");
            changed = true;
        }

        if (!validateRecipe("exp-bottle", section, false)) {
            section.set("result", "EXPERIENCE_BOTTLE");
            section.set("amount", 1);
            section.set("shape", List.of("GDG", "GAG", " G "));
            section.set("ingredients.G", "GLASS");
            section.set("ingredients.D", "GLOWSTONE_DUST");
            section.set("ingredients.A", "AMETHYST_SHARD");
            changed = true;
        }

        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("Added or repaired the exp-bottle recipe in config.yml.");
        }
    }

    private boolean repairGoldenCarrot(ConfigurationSection recipes) {
        ConfigurationSection section = recipes.getConfigurationSection("golden-carrot");
        if (section != null && validateRecipe("golden-carrot", section, false)) {
            return false;
        }
        if (section == null) {
            section = recipes.createSection("golden-carrot");
        }

        section.set("enabled", true);
        section.set("display-name", "&6Golden Carrot");
        section.set("result", "GOLDEN_CARROT");
        section.set("amount", 1);
        section.set("shape", List.of(" N ", "NCN", " N "));
        section.set("ingredients.N", "GOLD_NUGGET");
        section.set("ingredients.C", "CARROT");
        section.set("ingredients.G", null);
        return true;
    }

    public void setup() {
        ensureLatestDefaults();
        plugin.getConfig().set("setup-complete", true);
        plugin.saveConfig();
        registerConfiguredRecipes();
    }

    public void registerConfiguredRecipes() {
        ConfigurationSection recipes = plugin.getConfig().getConfigurationSection("recipes");
        if (recipes == null) {
            plugin.getLogger().warning("No recipes section was found in config.yml.");
            return;
        }

        removePluginRecipes();

        for (String id : recipes.getKeys(false)) {
            ConfigurationSection section = recipes.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            registerRecipe(id, section);
        }
    }

    public void sendStatus(CommandSender sender) {
        ConfigurationSection recipes = plugin.getConfig().getConfigurationSection("recipes");
        sender.sendMessage("§6§lEZRecipe Status");
        if (recipes == null) {
            sender.sendMessage("§cNo recipe configuration was found.");
            return;
        }

        for (String id : recipes.getKeys(false)) {
            ConfigurationSection section = recipes.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                sender.sendMessage("§7- §f" + id + ": §eDisabled");
                continue;
            }

            NamespacedKey key = recipeKey(id);
            if (key == null) {
                sender.sendMessage("§7- §f" + id + ": §cInvalid recipe ID");
                continue;
            }
            Recipe recipe = Bukkit.getRecipe(key);
            if (recipe == null) {
                sender.sendMessage("§7- §f" + id + ": §cNot registered");
            } else {
                sender.sendMessage("§7- §f" + id + ": §aWorking");
            }
        }
    }

    public void sendRecipes(CommandSender sender) {
        ConfigurationSection recipes = plugin.getConfig().getConfigurationSection("recipes");
        sender.sendMessage("§6§lEZRecipe Recipes");
        if (recipes == null) {
            sender.sendMessage("§cNo recipes are configured.");
            return;
        }

        for (String id : recipes.getKeys(false)) {
            ConfigurationSection section = recipes.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }

            String displayName = section.getString("display-name", id);
            String result = section.getString("result", "UNKNOWN");
            int amount = section.getInt("amount", 1);
            List<String> shape = section.getStringList("shape");
            ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
            StringJoiner ingredientText = new StringJoiner("§7, §f");
            if (ingredients != null) {
                for (String symbol : ingredients.getKeys(false)) {
                    ingredientText.add(symbol + " = " + ingredients.getString(symbol, "UNKNOWN"));
                }
            }

            sender.sendMessage("§e" + color(displayName) + " §7-> §f" + amount + "x " + result);
            sender.sendMessage("§7  Pattern: §f" + String.join(" / ", shape));
            sender.sendMessage("§7  Ingredients: §f" + ingredientText);
        }
    }

    public List<String> getEnabledRecipeIds() {
        ConfigurationSection recipes = plugin.getConfig().getConfigurationSection("recipes");
        List<String> ids = new ArrayList<>();
        if (recipes == null) {
            return ids;
        }

        for (String id : recipes.getKeys(false)) {
            ConfigurationSection section = recipes.getConfigurationSection(id);
            if (section != null && section.getBoolean("enabled", true)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public ConfigurationSection getRecipeSection(String id) {
        return plugin.getConfig().getConfigurationSection("recipes." + id);
    }

    public Material parseMaterial(String name) {
        return material(name);
    }

    public boolean isRecipeValid(String id) {
        ConfigurationSection section = getRecipeSection(id);
        return section != null && validateRecipe(id, section, false);
    }

    private void registerRecipe(String id, ConfigurationSection section) {
        if (!validateRecipe(id, section, true)) {
            return;
        }

        Material result = material(section.getString("result"));
        List<String> shape = section.getStringList("shape");
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        NamespacedKey key = recipeKey(id);
        try {
            ItemStack output = new ItemStack(result, section.getInt("amount", 1));
            ShapedRecipe recipe = new ShapedRecipe(key, output);
            recipe.shape(shape.toArray(new String[0]));

            for (String symbol : ingredients.getKeys(false)) {
                Material ingredient = material(ingredients.getString(symbol));
                if (ingredient != null) {
                    recipe.setIngredient(symbol.charAt(0), ingredient);
                }
            }

            if (!Bukkit.addRecipe(recipe)) {
                plugin.getLogger().warning("Could not register recipe: " + id);
            }
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipping recipe " + id + ": " + exception.getMessage());
        }
    }

    private boolean validateRecipe(String id, ConfigurationSection section, boolean log) {
        NamespacedKey key = recipeKey(id);
        Material result = material(section.getString("result"));
        List<String> shape = section.getStringList("shape");
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        int amount = section.getInt("amount", 1);

        if (key == null) {
            return invalid(id, "recipe IDs may only contain lowercase letters, numbers, '.', '_' and '-'.", log);
        }
        if (result == null) {
            return invalid(id, "unknown result material.", log);
        }
        if (amount < 1 || amount > result.getMaxStackSize()) {
            return invalid(id, "amount must be between 1 and " + result.getMaxStackSize() + ".", log);
        }
        if (shape.isEmpty() || shape.size() > 3) {
            return invalid(id, "shape must contain between 1 and 3 rows.", log);
        }
        int width = shape.get(0).length();
        if (width < 1 || width > 3) {
            return invalid(id, "shape rows must contain between 1 and 3 characters.", log);
        }
        for (String row : shape) {
            if (row.length() != width || row.length() > 3) {
                return invalid(id, "all shape rows must have the same length between 1 and 3 characters.", log);
            }
        }
        if (ingredients == null) {
            return invalid(id, "ingredients are missing.", log);
        }
        for (String symbol : ingredients.getKeys(false)) {
            if (symbol.length() != 1 || symbol.charAt(0) == ' ') {
                return invalid(id, "ingredient keys must be one non-space character.", log);
            }
            if (material(ingredients.getString(symbol)) == null) {
                return invalid(id, "unknown ingredient " + symbol + ".", log);
            }
        }
        for (String row : shape) {
            for (int column = 0; column < row.length(); column++) {
                char symbol = row.charAt(column);
                if (symbol != ' ' && !ingredients.contains(String.valueOf(symbol))) {
                    return invalid(id, "shape symbol " + symbol + " has no ingredient.", log);
                }
            }
        }
        return true;
    }

    private boolean invalid(String id, String reason, boolean log) {
        if (log) {
            plugin.getLogger().warning("Skipping recipe " + id + ": " + reason);
        }
        return false;
    }

    private void removePluginRecipes() {
        List<NamespacedKey> keys = new ArrayList<>();
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (recipe instanceof ShapedRecipe shaped) {
                NamespacedKey key = shaped.getKey();
                String namespace = key.getNamespace();
                if (namespace.equals(plugin.getName().toLowerCase(Locale.ROOT)) || namespace.equals("ezrecipes")) {
                    keys.add(key);
                }
            }
        }

        for (NamespacedKey key : keys) {
            if (key != null) {
                Bukkit.removeRecipe(key);
            }
        }
    }

    private NamespacedKey recipeKey(String id) {
        if (id == null || !id.toLowerCase(Locale.ROOT).matches("[a-z0-9._-]+")) {
            return null;
        }
        return new NamespacedKey(plugin, "recipe_" + id.toLowerCase(Locale.ROOT));
    }

    private Material material(String name) {
        if (name == null) {
            return null;
        }
        return XMaterial.matchXMaterial(name).map(XMaterial::parseMaterial).orElse(null);
    }

    private String color(String text) {
        return text.replace('&', '§');
    }
}