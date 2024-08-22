package me.demonducky.protectiongems

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*

class ProtectionGems : JavaPlugin(), Listener {

    private lateinit var customConfig: FileConfiguration

    private val configFile = File(this.dataFolder, "protected-items.yml")
    private val yamlConfig = YamlConfiguration.loadConfiguration(configFile)


    override fun onEnable() {

        if (!configFile.exists()) {
            this.saveResource("protected-items.yml", false)
        }

        // Register events when the plugin is enabled
        server.pluginManager.registerEvents(this, this)

        // Register the command executor
        getCommand("protectiongem")?.setExecutor(this)

        // Save the default configuration file
        saveDefaultConfig()
        // Get the custom configuration file
        customConfig = this.config
        // Log a message when the plugin is enabled
        logger.info("ProtectionGems đã được kích hoạt!")
    }

    override fun onDisable() {
        // Log a message when the plugin is disabled
        logger.info("ProtectionGems đã được tắt!")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Handle the protectiongem command
        if (command.name.equals("protectiongem", ignoreCase = true)) {
            return handleProtectionGemCommand(sender, args)
        }
        // Return false if the command is not handled
        return false
    }

    private fun handleProtectionGemCommand(sender: CommandSender, args: Array<out String>): Boolean {
        // Check if the command is valid
        if (args.size < 3 || args[0] != "give") {
            // Send a message with the usage
            sender.sendMessage(
                customConfig.getString("messages.usage") ?: "Sử dụng: /protectiongem give <player> <amount>"
            )
            return true
        }

        // Get the player
        val player = server.getPlayer(args[1])
        if (player == null) {
            // Send a message with the player not found
            sender.sendMessage(
                customConfig.getString("messages.player_not_found")?.replace("%player%", args[1])
                    ?: "Không tìm thấy người chơi ${args[1]}"
            )
            return true
        }

        // Get the amount
        val amount = args[2].toIntOrNull()
        if (amount == null || amount <= 0) {
            // Send a message with the invalid amount
            sender.sendMessage(customConfig.getString("messages.invalid_amount") ?: "Số lượng không hợp lệ")
            return true
        }

        // Give the protection gem
        try {
            giveProtectionGem(player, amount)
            // Send a message with the gem given
            sender.sendMessage(
                customConfig.getString("messages.gem_given")?.replace("%amount%", amount.toString())
                    ?.replace("%player%", player.name) ?: "Đã cấp $amount Ngọc Bảo Vệ cho ${player.name}"
            )
        } catch (e: Exception) {
            // Log an error
            logger.severe("Lỗi khi cấp Ngọc Bảo Vệ: ${e.message}")
            // Send a message with the error
            sender.sendMessage(customConfig.getString("messages.error") ?: "Đã xảy ra lỗi khi cấp Ngọc Bảo Vệ")
        }
        return true
    }

    private fun giveProtectionGem(player: Player, amount: Int) {
        // Create the protection gem
        val gem = createProtectionGem(amount)
        // Add the gem to the player's inventory
        player.inventory.addItem(gem)
    }

    private fun createProtectionGem(amount: Int): ItemStack {
        // Create an item stack with the material and amount
        val gem = ItemStack(Material.valueOf(customConfig.getString("gem.material") ?: "EMERALD"), amount)
        // Set the display name
        val meta = gem.itemMeta
        meta?.setDisplayName(
            ChatColor.translateAlternateColorCodes(
                '&',
                customConfig.getString("gem.name") ?: "&6&lNgọc Bảo Vệ"
            )
        )
        // Set the item meta
        gem.itemMeta = meta
        return gem
    }

    // Handle inventory click events
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        // Get the current item and the cursor item
        val currentItem = event.currentItem
        val cursorItem = event.cursor

        // Check if the cursor item is a protection gem, the current item is not null or air
        if (isProtectionGem(cursorItem) && currentItem != null && !currentItem.type.isAir) {
            // Try to apply the protection gem
            try {
                applyProtectionToItem(currentItem)
                // Decrease the gem amount
                decreaseGemAmount(cursorItem)
                // Cancel the event
                event.isCancelled = true
            } catch (e: Exception) {
                // Log an error
                logger.severe("Lỗi khi áp dụng Ngọc Bảo Vệ: ${e.message}")
                // Send a message with the error
                event.whoClicked.sendMessage(
                    customConfig.getString("messages.error") ?: "Đã xảy ra lỗi khi áp dụng Ngọc Bảo Vệ"
                )
            }
        }
    }

    private fun isProtectionGem(item: ItemStack?): Boolean {
        // Check if the item is a protection gem
        return item != null &&
                item.type == Material.valueOf(customConfig.getString("gem.material") ?: "EMERALD") &&
                item.itemMeta?.displayName == ChatColor.translateAlternateColorCodes(
            '&',
            customConfig.getString("gem.name") ?: "&6&lNgọc Bảo Vệ"
        )
    }

    private fun applyProtectionToItem(item: ItemStack) {
        // Get the item meta
        val meta = item.itemMeta ?: return
        // Get the lore
        val lore = meta.lore ?: mutableListOf()

        // Get the protection count
        val protectionCount = getProtectionCount(lore)
        // Update the protection lore
        updateProtectionLore(lore, protectionCount)

        // Set the item meta
        meta.lore = lore
        item.itemMeta = meta
    }

    private fun getProtectionCount(lore: List<String>): Int {
        // Get the protection lore
        val protectionLore = customConfig.getString("protection.lore") ?: "&6&lLượt bảo vệ: %amount%"
        // Find the protection line
        val protectionLine = lore.find {
            it.startsWith(
                ChatColor.translateAlternateColorCodes(
                    '&',
                    protectionLore.replace("%amount%", "")
                )
            )
        }
        // Get the protection count
        return protectionLine?.substringAfterLast(": ")?.toIntOrNull() ?: 0
    }

    private fun updateProtectionLore(lore: MutableList<String>, currentProtection: Int) {
        // Get the new protection
        val newProtection = currentProtection + customConfig.getInt("protection.added_amount", 3)
        // Get the protection lore
        val protectionLore = ChatColor.translateAlternateColorCodes(
            '&',
            customConfig.getString("protection.lore") ?: "&6&lLượt bảo vệ: %amount%"
        )
        // Update the protection lore
        val updatedLore = protectionLore.replace("%amount%", newProtection.toString())

        // Find the existing index
        val existingIndex = lore.indexOfFirst {
            it.startsWith(
                ChatColor.translateAlternateColorCodes(
                    '&',
                    protectionLore.replace("%amount%", "")
                )
            )
        }
        // Update the lore
        if (existingIndex != -1) {
            lore[existingIndex] = updatedLore
        } else {
            lore.add(updatedLore)
        }
    }

    private fun decreaseGemAmount(gem: ItemStack?) {
        // Decrease the gem amount
        gem?.amount = gem?.amount?.minus(1) ?: 0
    }

    // Handle player death events
    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        // Get the player
        val player = event.entity
        // Get the drops
        val drops = event.drops
        // Get the protected items
        val protectedItems = mutableListOf<ItemStack>()

        // Iterate through the drops and get the protected items
        drops.iterator().forEach { item ->
            if (hasProtection(item)) {
                protectedItems.add(item)
            }
        }

        // Remove the protected items from the drops
        drops.removeAll(protectedItems)


        // Iterate through the protected items and update the protection

        saveProtectedItems(player.uniqueId, protectedItems.map { item ->
            updateItemProtection(item)
        })


        player.inventory.removeItem(*protectedItems.toTypedArray())
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val protectedItems = getProtectedItems(player.uniqueId)

        // Schedule a task to give items back after respawn
        server.scheduler.runTask(this, Runnable {
            for (item in protectedItems) {
                val leftover = player.inventory.addItem(item)
                if (leftover.isNotEmpty()) {
                    // Drop items that didn't fit in the inventory
                    player.world.dropItem(player.location, leftover.values.first())
                }
            }
            // Clear the saved items for this player
            clearProtectedItems(player.uniqueId)
        })
    }

    private fun saveProtectedItems(playerUUID: UUID, items: List<ItemStack>) {
        yamlConfig.set("protected-items.$playerUUID", items)
        yamlConfig.save(configFile)
    }

    private fun getProtectedItems(playerUUID: UUID): List<ItemStack> {
        return yamlConfig.getList("protected-items.$playerUUID") as? List<ItemStack> ?: emptyList()
    }

    private fun clearProtectedItems(playerUUID: UUID) {
        yamlConfig.set("protected-items.$playerUUID", null)
        yamlConfig.save(configFile)
    }


    private fun hasProtection(item: ItemStack): Boolean {
        // Check if the item has protection
        val meta = item.itemMeta ?: return false
        val lore = meta.lore ?: return false
        val protectionLore = customConfig.getString("protection.lore") ?: "&6&lLượt bảo vệ: %amount%"
        return lore.any {
            it.startsWith(
                ChatColor.translateAlternateColorCodes(
                    '&',
                    protectionLore.replace("%amount%", "")
                )
            )
        }
    }

    private fun updateItemProtection(item: ItemStack): ItemStack {
        // Get the item meta
        val meta = item.itemMeta!!
        // Get the lore
        val lore = meta.lore!!

        // Get the protection count
        val protectionCount = getProtectionCount(lore)
        if (protectionCount > 1) {
            // Update the protection lore
            updateProtectionLore(lore, protectionCount - 1 - customConfig.getInt("protection.added_amount", 3))
        } else {
            // Remove the protection lore
            val protectionLore = customConfig.getString("protection.lore") ?: "&6&lLượt bảo vệ: %amount%"
            lore.removeAll {
                it.startsWith(
                    ChatColor.translateAlternateColorCodes(
                        '&',
                        protectionLore.replace("%amount%", "")
                    )
                )
            }
        }

        // Set the item meta
        meta.lore = lore
        item.itemMeta = meta

        return item
    }
}