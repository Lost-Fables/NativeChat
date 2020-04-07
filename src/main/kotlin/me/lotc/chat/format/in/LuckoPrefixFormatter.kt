package me.lotc.chat.format.`in`

import me.lotc.chat.message.Message
import me.lotc.chat.message.Text
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.context.ContextManager
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.PrefixNode
import net.luckperms.api.query.QueryOptions
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.ChatColor

class LuckoPrefixFormatter : InFormatter {
    override fun format(message: Message) {
        val p = message.player
        p?:return

        val user = LuckPermsProvider.get().userManager.getUser(p.uniqueId)
        user?: throw IllegalStateException("the fuck?")

        val prefix = user.cachedData.getMetaData(QueryOptions.defaultContextualOptions()).prefix

        prefix?:return
        val colorful = ChatColor.translateAlternateColorCodes('&', prefix.toString()).trim()
        if(colorful.isBlank()) return
        message.prefixes.addFirst(Text(" "))
        TextComponent.fromLegacyText(colorful).reversed().forEach {
            message.prefixes.addFirst(Text(it))
        }
    }
}