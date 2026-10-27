package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class VoiceLibraryTest {
    @get:Rule val files = TemporaryFolder()
    private fun provider(id: String, enabled: Boolean = true) = TtsProvider(id, id, TtsInfo(
        voices = listOf(VoiceInfo("one", "第一声", "zh", packId = "pack"), VoiceInfo("two", "第二声", "zh", packId = "pack")),
        packs = listOf(VoicePackInfo("pack", VoicePackMetadata(id, "备注"), listOf("one", "two"), editable = true, exportable = true))), enabled)
    @Test fun oneRowPerPackAcrossEnginesIncludingDisabledCachedPacks() {
        val storage = PluginStorage(files.newFolder()); val library = VoiceLibrary(storage); val settings = ServiceSettings(storage)
        val pages = VoiceLibraryPages(settings, library) { listOf(provider("first.engine"), provider("second.engine", false)) }
        val page = pages.page("packs")!!
        assertEquals(2, page.management!!.items.size)
        assertTrue(page.management!!.items.last().subtitle.contains("已停用"))
        assertEquals(listOf("first.engine"), page.management!!.items.first().badges)
        assertEquals("备注", page.management!!.items.first().subtitle)
        assertTrue(page.management!!.items.first().selection!!.enabled)
        assertFalse(page.management!!.items.last().selection!!.enabled)
        assertEquals(page.management!!.items.first().primaryAction, page.management!!.items.first().selection!!.action)
        assertEquals(2, pages.page("pack/${encodeId("first.engine")}/${encodeId("pack")}")!!.management!!.items.size)
    }
    @Test fun crossEngineReorderAndFilteredReorderPersistWithoutMovingHiddenEntries() {
        val storage = PluginStorage(files.newFolder()); val entries = voicePacks(listOf(provider("a.engine"), provider("b.engine"), provider("c.engine")))
        val library = VoiceLibrary(storage)
        library.reorder(entries, entries.map { it.key }, entries.reversed().map { it.key })
        assertEquals(entries.reversed().map { it.key }, VoiceLibrary(storage).ordered(entries).map { it.key })
        library.reorder(entries, listOf(entries[0].key, entries[2].key), listOf(entries[0].key, entries[2].key))
        assertEquals(listOf(entries[0].key, entries[1].key, entries[2].key), VoiceLibrary(storage).ordered(entries).map { it.key })
        library.pin(entries[2].key, true)
        assertEquals(entries[2].key, VoiceLibrary(storage).ordered(entries).first().key)
    }
    @Test fun detailsUseIdentityHeaderAndNestedSettingsWithNoTopLevelActionTabs() {
        val storage = PluginStorage(files.newFolder()); val library = VoiceLibrary(storage); val settings = ServiceSettings(storage)
        val source = provider("example.engine").let { item -> item.copy(info = item.info.copy(packs = item.info.packs.map {
            it.copy(avatarPath = "resources/avatar.png", parameters = listOf(VoiceParameter("speed", "语速", 1f, .5f, 2f, 1f)))
        })) }
        val pages = VoiceLibraryPages(settings, library) { listOf(source) }
        val page = pages.page("pack/${encodeId(source.pluginId)}/${encodeId("pack")}")!!
        assertEquals(source.pluginId, page.header!!.image!!.pluginId)
        assertFalse(page.blocks.any { it is PageBlock.Actions || it is PageBlock.Choice })
        val groups = page.blocks.filterIsInstance<PageBlock.Group>().associateBy { it.title }
        assertTrue(groups.keys.containsAll(listOf("详细信息", "语音包设置", "音频设置", "管理")))
        assertEquals(1, groups.getValue("音频设置").children.filterIsInstance<PageBlock.Slider>().size)
        assertEquals(listOf("名称", "备注"), groups.getValue("语音包设置").children.filterIsInstance<PageBlock.Input>().map { it.title })
        assertTrue(page.management!!.items.all { it.group == "声音" && it.actions.isEmpty() && it.selection!!.enabled })
    }
    @Test fun disabledEngineStillShowsPackIdentityAndInformationWithoutEditableControls() {
        val storage = PluginStorage(files.newFolder())
        val pages = VoiceLibraryPages(ServiceSettings(storage), VoiceLibrary(storage)) { listOf(provider("disabled.engine", false)) }
        val page = pages.page("pack/${encodeId("disabled.engine")}/${encodeId("pack")}")!!
        assertNotNull(page.header)
        assertTrue(page.blocks.filterIsInstance<PageBlock.Group>().any { it.title == "详细信息" })
        assertFalse(page.blocks.filterIsInstance<PageBlock.Group>().any { it.title == "语音包设置" })
        assertTrue(page.management!!.items.all { it.primaryAction == null && !it.selection!!.enabled })
    }
}
