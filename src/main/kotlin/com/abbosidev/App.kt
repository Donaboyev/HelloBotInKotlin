package com.abbosidev

import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.sender_chat
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.extensions.utils.formatting.linkMarkdownV2
import dev.inmo.tgbotapi.extensions.utils.formatting.textMentionMarkdownV2
import dev.inmo.tgbotapi.extensions.utils.ifFromChannelGroupContentMessage
import dev.inmo.tgbotapi.types.chat.BusinessChat
import dev.inmo.tgbotapi.types.chat.ChannelChat
import dev.inmo.tgbotapi.types.chat.GroupChat
import dev.inmo.tgbotapi.types.chat.PreviewBusinessChat
import dev.inmo.tgbotapi.types.chat.PreviewChannelChat
import dev.inmo.tgbotapi.types.chat.PreviewGroupChat
import dev.inmo.tgbotapi.types.chat.PreviewPrivateChat
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.chat.SupergroupChat
import dev.inmo.tgbotapi.types.chat.UnknownChatType
import dev.inmo.tgbotapi.types.message.MarkdownV2
import dev.inmo.tgbotapi.utils.PreviewFeature
import dev.inmo.tgbotapi.utils.RiskFeature
import dev.inmo.tgbotapi.utils.extensions.escapeMarkdownV2Common
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class App(
    @ConfigProperty(name = "telegram.bot.token")
    private val token: String,
) {
    @OptIn(PreviewFeature::class, RiskFeature::class)
    fun startEventListening(
        @Observes event: StartupEvent,
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            telegramBotWithBehaviourAndLongPolling(
                token,
                CoroutineScope(Dispatchers.IO)
            ) {
                val me = getMe()
                onContentMessage(
                    initialFilter = initialFilter@{
                        it.text?.contains(
                            me.username?.full ?: return@initialFilter false
                        ) == true
                    }
                ) { message ->
                    val answerText = when (val chat = message.chat) {
                        is PreviewChannelChat -> {
                            val sender = message.sender_chat
                            val answer = "Hi everybody in this channel \"${chat.title}\"" + if (sender != null) {
                                " and you, " + when (sender) {
                                    is BusinessChat -> "business chat (wat) ${sender.original}"
                                    is PrivateChat -> "${sender.lastName} ${sender.firstName}"
                                    is GroupChat -> "group ${sender.title}"
                                    is ChannelChat -> "channel ${sender.title}"
                                    is UnknownChatType -> "wat chat (${sender})"
                                }
                            } else {
                                ""
                            }
                            reply(message, answer.escapeMarkdownV2Common(), MarkdownV2)
                            return@onContentMessage
                        }

                        is PreviewPrivateChat -> {
                            reply(
                                message,
                                "Hi, " + "${chat.firstName} ${chat.lastName}".textMentionMarkdownV2(chat.id),
                                MarkdownV2
                            )
                            return@onContentMessage
                        }

                        is PreviewGroupChat -> {
                            message.ifFromChannelGroupContentMessage<Unit> {
                                val answer = "Hi, ${it.senderChat.title}"
                                reply(message, answer, MarkdownV2)
                                return@onContentMessage
                            }
                            "Oh, hi, " + when (chat) {
                                is SupergroupChat -> (chat.username?.username ?: getChat(chat).inviteLink)?.let {
                                    chat.title.linkMarkdownV2(it)
                                } ?: chat.title

                                else -> bot.getChat(chat).inviteLink?.let {
                                    chat.title.linkMarkdownV2(it)
                                } ?: chat.title
                            }
                        }

                        is PreviewBusinessChat -> {
                            reply(
                                message,
                                "Hi, " + "${chat.original.firstName} ${chat.original.lastName} (as business chat :) )".textMentionMarkdownV2(
                                    chat.original.id
                                ),
                                MarkdownV2
                            )
                            return@onContentMessage
                        }

                        is UnknownChatType -> "Unknown :(".escapeMarkdownV2Common()
                    }
                    reply(
                        message,
                        answerText,
                        MarkdownV2
                    )
                }
                allUpdatesFlow.subscribeSafelyWithoutExceptions(this) { print(it) }
            }.second.join()
        }
    }
}