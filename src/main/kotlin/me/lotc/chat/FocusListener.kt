package me.lotc.chat

import com.comphenix.protocol.AsynchronousManager
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.EnumWrappers
import me.lotc.chat.user.chat
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.ChatColor.*
import net.md_5.bungee.chat.ComponentSerializer

object FocusListener {

    fun listen(plugin: NativeChat){
        manager().registerAsyncHandler(object : PacketAdapter(plugin, PacketType.Play.Server.CHAT){
            override fun onPacketSending(event: PacketEvent){
                if(event.isCancelled) return
                if(!plugin.chatManager.hasFullyConnected(event.player)) return

                val type = event.packet.chatTypes.read(0)

                if(type == EnumWrappers.ChatType.SYSTEM){
                    //val chat = event.packet.chatComponents.read(0)
                    val focus = event.player.chat.focus

                    val chat = event.packet.chatComponents.read(0)?:return
                    try {
                        val msg = ComponentSerializer.parse(chat.json)

                        focus.acceptSystem(msg)

                        val txt = BaseComponent.toPlainText(*msg)

                        if (!txt.startsWith("Error: ") && !focus.willAcceptSystem()) {
                            event.isCancelled = true
                            event.player.sendActionBar("${GRAY}Missed message in$LIGHT_PURPLE System")
                        }
                    } catch (npe: NullPointerException) {
                        plugin.logger.warning("Failed to parse chat.json. Likely a Bungee chat issue. If messages are getting eaten, look into this.")
                    }
                }
            }
        }).start()
    }

    private fun manager(): AsynchronousManager {
        return ProtocolLibrary.getProtocolManager().asynchronousManager
    }
}