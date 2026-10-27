// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*

/** A settings detail page: identity header, nested information/settings cards, then voice choices. */
internal fun voicePackDetails(id: String, entry: PackEntry, settings: ServiceSettings, library: VoiceLibrary): PluginPage {
    val pack = entry.pack; val owner = entry.provider.pluginId; val available = entry.provider.enabled
    val voices = entry.provider.info.voices.filter { it.id in pack.voiceIds }.distinctBy { it.id }
    val languages = voices.map { it.language }.filter(String::isNotBlank).distinct()
    val blocks = buildList {
        if (!available) add(PageBlock.Notice("引擎已停用，启用后可使用和编辑语音包。"))
        add(PageBlock.Group("详细信息", buildList {
            add(PageBlock.Text("所属引擎\n${entry.provider.name}"))
            if (pack.providerLabel.isNotBlank()) add(PageBlock.Text("服务商\n${pack.providerLabel}"))
            add(PageBlock.Text("声音数量\n${pack.voiceIds.distinct().size}"))
            if (languages.isNotEmpty()) add(PageBlock.Text("语言\n${languages.joinToString("、")}"))
            if (pack.metadata.remark.isNotBlank() && !pack.editable) add(PageBlock.Text("备注\n${pack.metadata.remark}"))
        }))
        if (available) {
            add(PageBlock.Group("语音包设置", buildList {
                add(PageBlock.Actions(listOf(PageAction(packAction("pack-use", entry), "使用语音包", PluginIcon.SPEAKER))))
                add(PageBlock.Toggle("置顶", library.pinned(entry.key, pack.metadata.pinned), packAction("pack-pin", entry), icon = PluginIcon.PIN))
                if (pack.editable) {
                    add(PageBlock.Input("名称", pack.metadata.name, packAction("pack-name", entry)))
                    add(PageBlock.Input("备注", pack.metadata.remark, packAction("pack-remark", entry), multiline = true, icon = PluginIcon.TEXT))
                    add(PageBlock.Actions(listOf(PageAction(packAction("pack-avatar", entry), "更换头像", PluginIcon.IMAGE))))
                }
            }))
            if (pack.parameters.isNotEmpty()) add(PageBlock.Group("音频设置",
                packSettingsControls(entry).controls.filterIsInstance<PluginControl.Slider>().map {
                    PageBlock.Slider(it.copy(actionId = it.actionId.replaceFirst("pack-setting/", "pack-setting-inline/")))
                } +
                    PageBlock.Actions(listOf(PageAction(packAction("pack-settings-inline-reset", entry), "恢复默认", PluginIcon.SETTINGS)))))
            val files = buildList {
                if (entry.provider.info.importsVoicePacks) add(PageAction(packAction("pack-update", entry), "更新语音包", PluginIcon.ADD))
                if (pack.exportable) add(PageAction(packAction("pack-export", entry), "导出语音包", PluginIcon.EXPORT))
                if (pack.deletable) add(PageAction(packAction("pack-delete", entry), "删除语音包", PluginIcon.CLOSE, symbol = "delete"))
            }
            if (files.isNotEmpty()) add(PageBlock.Group("管理", listOf(PageBlock.Actions(files))))
        }
    }
    val choices = if (owner == SYSTEM_PROVIDER || voices.isEmpty()) null else ManagementList("pack-voices", voices.map { voice ->
        val action = "voice/${encodeId(owner)}/${encodeId(voice.id)}"
        ManagementItem(voice.id, voice.name, voice.language, PluginIcon.SPEAKER, group = "声音",
            selected = settings.provider == owner && settings.voice == voice.id,
            primaryAction = action.takeIf { available }, selection = ManagementSelection(action, available))
    })
    return PluginPage(id, pack.metadata.name, blocks, choices,
        header = ManagementItem("voice-pack-header", pack.metadata.name,
            pack.metadata.remark.ifBlank { pack.providerLabel.ifBlank { entry.provider.name } }, PluginIcon.SPEAKER,
            image = pack.avatarPath?.let { PluginResource(it, owner) }))
}
