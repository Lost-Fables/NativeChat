package me.lotc.chat.user

import co.lotc.core.agnostic.Sender
import co.lotc.core.bukkit.util.PlayerUtil
import co.lotc.core.bukkit.wrapper.BukkitSender
import co.lotc.core.command.brigadier.TooltipProvider
import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import me.lotc.chat.NativeChat
import me.lotc.chat.ProxiedSender
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.user.User
import net.luckperms.api.node.Node
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.BiConsumer
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KProperty

val Player.chat: Chatter get() = NativeChat.get().chatManager.getChatSettings(this)
val Sender.player: Player? get() = ((this as? BukkitSender)?.handle as? Player)
val Sender.chat: Chatter? get() = this.player?.chat
val Sender.uuid: UUID? get() {
    this.player?.let { return it.uniqueId }
    (this as? ProxiedSender)?.let { return it.uniqueId }
    return null
}

operator fun Multimap<*,*>.contains(key: Any) = this.containsKey(key)

class Chatter(player: Player) {
    private val lock = ReentrantReadWriteLock()
    private val uuid = player.uniqueId
    val player get() = Bukkit.getPlayer(uuid)!! //Chatter must only exist if Player is online

    val channels = ChannelData(uuid, lock)
    val channel get() = channels.channel

    val continuity = StringBuilder()
    val focus = Focus(uuid)

    var emoteColor: ChatColor by Lockable(lock, ChatColor.YELLOW)
    var wantsTimestamps by Lockable(lock, false)
    var emoteStyle by Lockable(lock, EmoteStyle.QUOTATIONS)
    var correctPunctuation by Lockable(lock, true)
    var isMentionable by Lockable(lock, true)
    var shouldRedirect by Lockable(lock, true)


    fun saveSettings(){
        val provider = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)
        provider?: throw IllegalStateException("saving Chatter settings but no LuckPerms for $uuid")

        val api = provider.provider
        api.userManager.savePlayerData(uuid, PlayerUtil.getPlayerName(uuid))
        val userFuture = api.userManager.loadUser(uuid)
        userFuture.whenCompleteAsync(BiConsumer { user, throwable ->
            if (throwable != null) {
                return@BiConsumer
            }
            user.data().clear { n->n.key.startsWith("meta.rp_") }
            lock.read {
                for(chan in channels.subscribedChannels)
                    if(!chan.isPermanent) metaNode(user, "rp_channel", chan.cmd)

                metaNode(user, "rp_focus", channel.cmd)
                metaNode(user, "rp_emotecolor", emoteColor.name, "YELLOW")
                metaNode(user, "rp_emotestyle", emoteStyle.name, "QUOTATIONS")

                metaNode(user, "rp_timestamps", wantsTimestamps.toString(), "false")
                metaNode(user, "rp_punctuate", correctPunctuation.toString(), "true")
                metaNode(user, "rp_mention", isMentionable.toString(), "true")
                metaNode(user, "rp_redirect", shouldRedirect.toString(), "true")
            }
            api.userManager.saveUser(user)
            api.userManager.cleanupUser(user)
        }, Executor { runnable: Runnable? -> Bukkit.getScheduler().runTask(NativeChat.get(), runnable!!) })
    }

    private fun metaNode(user: User, key: String, value: String, default: String? = null) {
        if(value != default) user.data().add(Node.builder("meta.$key.$value").build())
    }

    fun loadSettings(){
        //Doesn't need a lock: Only called onJoin when object not yet exposed to other threads
        val chatManager = NativeChat.get().chatManager

        val provider = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)
        provider?: throw IllegalStateException("saving Chatter settings but no LuckPerms")

        val api = provider.provider
        val user = api.userManager.getUser(uuid)
        user?: throw IllegalStateException("loading Chatter settings but no LuckPerms for $uuid")
        @Suppress("UnstableApiUsage")
        val settings = MultimapBuilder.hashKeys().arrayListValues().build<String, String>()

        //Go through LuckPerms to find all the meta nodes that are RPEngine settings nodes
         user.nodes.stream().filter { it.key.startsWith("meta.rp_") }.forEach {
             val split = it.key.split(".")
             if (split.size >= 2) {
                 settings.put(split[1], split[2])
             }
         }

        settings["rp_channel"].mapNotNull { chatManager.getByAlias(it) }
            .filter { player.hasPermission(it.permission) }
            .forEach { channels.subscribedChannels.add(it) }

        chatManager.channels.stream().filter { it.isPermanent }
            .filter { !channels.isSubscribed(it) }
            .filter { player.hasPermission(it.permission)}
            .forEach{ channels.subscribedChannels.add(it) }

        if("rp_focus" in settings) channels.channel = chatManager.getByAlias(settings["rp_focus"][0]) ?: channel
        if(channel !in channels.subscribedChannels) channels.subscribedChannels.add(channel)

        if("rp_emotecolor" in settings) emoteColor = ChatColor.of(settings["rp_emotecolor"][0])
        if("rp_emotestyle" in settings) emoteStyle = EmoteStyle.valueOf(settings["rp_emotestyle"][0])

        if("rp_timestamps" in settings) wantsTimestamps = settings["rp_timestamps"][0]!!.toBoolean()
        if("rp_punctuate" in settings) correctPunctuation = settings["rp_punctuate"][0]!!.toBoolean()
        if("rp_mention" in settings) isMentionable = settings["rp_mention"][0]!!.toBoolean()
        if("rp_redirect" in settings) shouldRedirect = settings["rp_redirect"][0]!!.toBoolean()
    }
}

enum class EmoteStyle(private val tip: String) : TooltipProvider {
    ALWAYS("All your roleplay chat will be emoted by default"),
    QUOTATIONS("Roleplay chat will be send as an emote as long as you use a quotation mark (\")"),
    EXPLICIT("You must explicitly declare your intent to emote by starting chat with an asterix (*)");

    override fun getTooltip() = tip
}

class Lockable<T>(private val lock: ReentrantReadWriteLock, private var field : T)  {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        lock.read { return field }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        lock.write { field = value }
    }
}