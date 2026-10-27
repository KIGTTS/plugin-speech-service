// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import java.util.Base64

internal fun encodeId(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
internal fun decodeId(value: String) = String(Base64.getUrlDecoder().decode(value))
internal fun packAction(verb: String, entry: PackEntry) = "$verb/${encodeId(entry.provider.pluginId)}/${encodeId(entry.pack.id)}"

internal class VoiceLibraryPages(private val settings: ServiceSettings, private val library: VoiceLibrary, private val providers: () -> List<TtsProvider>) {
    fun entries() = library.ordered(voicePacks(providers()))
    fun visible() = entries().filter { library.filter == "all" || it.provider.pluginId == library.filter }
    fun page(id: String): PluginPage? {
        if (id == "packs") {
            val sources = providers()
            val filters = listOf(ManagementFilter("all", "全部", "pack-filter/${encodeId("all")}", library.filter == "all")) + sources.map {
                ManagementFilter(it.pluginId, it.name, "pack-filter/${encodeId(it.pluginId)}", library.filter == it.pluginId)
            }
            val importers = sources.filter { it.info.importsVoicePacks }.map { PageAction("import/${encodeId(it.pluginId)}", "导入${it.name}语音包", PluginIcon.ADD) }
            return PluginPage(id, "语音包管理", listOf(PageBlock.Actions(importers)),
                ManagementList("voice-packs", visible().map { entry ->
                    val pack = entry.pack; val pinned = library.pinned(entry.key, pack.metadata.pinned)
                    val actions = mutableListOf(PageAction(packAction("pack-pin", entry), if (pinned) "取消置顶" else "置顶", PluginIcon.PIN),
                        PageAction(packAction("pack", entry), "详细信息", PluginIcon.INFO))
                    if (pack.parameters.isNotEmpty() && entry.provider.enabled)
                        actions += PageAction(packAction("pack-settings", entry), "音频设置", PluginIcon.SETTINGS)
                    if (pack.exportable && entry.provider.enabled) actions += PageAction(packAction("pack-export", entry), "导出语音包", PluginIcon.EXPORT)
                    if (pack.deletable && entry.provider.enabled) actions += PageAction(packAction("pack-delete", entry), "删除语音包", PluginIcon.CLOSE, symbol = "delete")
                    ManagementItem(entry.key, pack.metadata.name,
                        if (entry.provider.pluginId == SYSTEM_PROVIDER) "系统语音合成" else listOfNotNull(
                            pack.metadata.remark.takeIf { it.isNotBlank() }, "引擎已停用".takeUnless { entry.provider.enabled }).joinToString(" · "), PluginIcon.SPEAKER,
                        pack.avatarPath?.let { PluginResource(it, entry.provider.pluginId) },
                        selected = if (entry.provider.pluginId == SYSTEM_PROVIDER) settings.provider == null
                            else settings.provider == entry.provider.pluginId && settings.voice in pack.voiceIds,
                        pinned = pinned, group = if (pinned) "pinned" else "normal", primaryAction = if (entry.provider.enabled) packAction("pack-use", entry) else packAction("pack", entry), actions = actions,
                        badges = listOf(if (entry.provider.pluginId == SYSTEM_PROVIDER) "系统" else pack.providerLabel.ifBlank { entry.provider.name.take(32) }),
                        selection = ManagementSelection(packAction("pack-use", entry), entry.provider.enabled))
                }, "pack-order", "此引擎还没有语音包", filters))
        }
        if (!id.startsWith("pack/")) return null
        val parts = id.split('/'); val owner = decodeId(parts[1]); val key = decodeId(parts[2])
        val entry = entries().firstOrNull { it.provider.pluginId == owner && it.pack.id == key }
            ?: return PluginPage(id, "语音包不可用", listOf(PageBlock.Notice("该语音包或引擎已移除。")))
        return voicePackDetails(id, entry, settings, library)
    }
}
