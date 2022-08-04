package org.elliotnash

import org.elliotnash.blueify.Client
import org.elliotnash.blueify.EventListener
import org.elliotnash.blueify.model.Message
import org.elliotnash.blueify.model.requests.MessageBuilder
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun main(args: Array<String>) {
    DadBot(args[0], args[1])
}

class DadBot(url: String, password: String) : EventListener {
    private val client = Client(url, password)
    private val config = Config()
    init {
        client.registerEventListener(this)
        client.run()
    }

    override fun onReady(client: Client) {
        println("Connected to BlueBubbles with info: "+client.serverInfo)
    }

    private val imRegex = Regex("\\bi['‘’]?m+\\b", RegexOption.IGNORE_CASE)
    private val dotRegex = Regex("(?<!\\d)[.!?](?=[ !?.]|$)")
    override fun onMessage(message: Message) {
        onDad(message)

        val trimmed = message.text.trim();
        if (trimmed.startsWith("!")) {
            val inp = Regex(" ").split(trimmed.substring(1), 2)
            val command = inp[0].trim()
            var args: String? = null
            if (inp.size > 1)
                args = inp[1].trim()

            when (command) {
                "dad" -> dadCommand(message, args)
                "help" -> helpCommand(message)
            }
        }
    }

    private fun onDad(message: Message) {
        if (!message.isFromMe) {
            val parts = imRegex.split(message.text, 2)
            if (parts.size > 1) {
                val name = parts[1].trim()
                val content = "Hi ${name}, I'm dad"
                if (config.dadModeEnabled(message.chat.guid)) {
                    println("Responding: $content")
                    message.reply(
                        MessageBuilder(content)
                            .setEffect(Message.Effect.SLAM)
                    )
                } else {
                    println("Would respond $content, but dad mode disabled")
                }
            }
        }
    }

    private fun dadCommand(message: Message, args: String?) {
        val state = args?.split(" ")?.get(0)?.trim()?.lowercase()
        if (state == null || (state != "on" && state != "off" && state != "status")) {
            message.reply("""
                Invalid Command: `${message.text}`
                
                Usage: !dad <on|off|status>
            """.trimIndent())
            return
        }
        if (state == "status") {
            val on = config.dadModeEnabled(message.chat.guid)
            message.reply("Dad mode is currently ${if (on) "enabled" else "disabled" } on chat ${message.chat.guid}")
            return
        }
        val on = state == "on"
        if (!on) {
            if (!message.isFromMe) {
                message.reply("You do not have permission to deactivate dad mode")
                return
            }
        }
        config.setDadMode(message.chat.guid, on)
        message.reply("${if (on) "Enabled" else "Disabled" } dad mode on chat ${message.chat.guid}")
    }

    private fun helpCommand(message: Message) {
        message.reply("""
            Eloit Bot Help:
            !dad <on|off|status> -> (de)activates dad mode on the current chat
        """.trimIndent())
    }
}

private class Config {
    init {
        val dbPath = System.getProperty("user.dir")+"/config"
        Database.connect("jdbc:h2:${dbPath}", driver = "org.h2.Driver")
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Chats)
        }
    }

    fun dadModeEnabled(guid: String): Boolean {
        return transaction {
            Chats.select {
                Chats.guid eq guid
            }.forEach {
                return@transaction it[Chats.dadOn]
            }
            return@transaction false
        }
    }

    fun setDadMode(guid: String, dadOn: Boolean) {
        transaction {
            if (Chats.select { Chats.guid eq guid }.empty()) {
                Chats.insert {
                    it[Chats.guid] = guid
                    it[Chats.dadOn] = dadOn
                }
            } else {
                Chats.update({ Chats.guid eq guid}) {
                    it[Chats.dadOn] = dadOn
                }
            }
        }
    }

    private object Chats: Table() {
        val guid: Column<String> = text("guid").uniqueIndex()
        val dadOn: Column<Boolean> = bool("dad_on")
    }
}
