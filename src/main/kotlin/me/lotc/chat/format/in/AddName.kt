package me.lotc.chat.format.`in`

import me.lotc.chat.message.Message
import me.lotc.chat.message.Text
import co.lotc.core.util.MessageUtil
import net.korvic.rppersonas.RPPersonas
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit

class AddName(private val displayName : Boolean) : InFormatter {
    override fun format(message: Message) {
        val username = message.sender.name
        var name = username

        val p = message.player
        if(displayName){
            name = if(Bukkit.getPluginManager().isPluginEnabled("RPPersonas") && p != null) RPPersonas.get().personaHandler.getLoadedPersona(p).nickName
            else p?.displayName ?: name
        }

        val new = false; //RPPersonasBridge.isEnabled && RPPersonasBridge.isNew(p)

        val color = if(new) ChatColor.LIGHT_PURPLE else ChatColor.DARK_GRAY
        val continuum = if(new) "\n${ChatColor.LIGHT_PURPLE}${ChatColor.ITALIC}Player is new" else ""
        val format = Text(name, color = color)

        lateinit var hover : String
        lateinit var click : ClickEvent
        if(displayName){
            hover = "${ChatColor.WHITE}$username\n${ChatColor.GRAY}View profile."
            click = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/persona info $username")
        } else {
            hover = "${ChatColor.GRAY}Send message."
            click = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg $username ")
        }

        format.click = click
        format.hover = MessageUtil.hoverEvent(hover+continuum)

        message.prefixes.addLast(format)
        message.prefixes.addLast(Text(": ", color=ChatColor.DARK_GRAY))
    }
}