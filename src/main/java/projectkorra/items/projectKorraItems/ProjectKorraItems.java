package projectkorra.items.projectKorraItems;

import org.bukkit.plugin.java.JavaPlugin;

public final class ProjectKorraItems extends JavaPlugin {

    public ProjectKorraItems ProjectKorraItems;

    @Override
    public void onEnable() {
        getLogger().info("ProjectKorraItems starting boot.");


        // Plugin startup logic

        getLogger().info("ProjectKorraItems has booted.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling ProjectKorraItems");
        // Plugin shutdown logic


        getLogger().info("Disabled ProjectKorraItems");
    }

    public ProjectKorraItems ProjectKorraItems() {
        return ProjectKorraItems;
    }
}
