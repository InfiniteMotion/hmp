package com.hearablemusic.player.ui.library.viewmodel

import com.hearablemusic.player.ui.platform.MusicTagEditService
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hmp.domain.music.usecase.EditMusicTagsUseCase
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D2-12 判据（VM 那一半）：能力申报要在**首帧**就落到状态里。
 *
 * 为什么强调首帧：申报原先要等 `loadTags` 走完才写进 state，于是 iOS 上会
 * 先闪一次可用的保存按钮、再收回去 —— 用户正好可能点在那一下。
 * 现在构造 `_uiState` 时就从 `platformServices.musicTagEdit.canWriteTags` 取。
 *
 * 这里刻意不调 `loadTags`：那会走进 `getString(Res.string.…)` 的资源解析，
 * 而本测试源集没接 Robolectric（`AGENTS.md` §八.1 记的那处限制，同 `PlaylistViewModel`）。
 * 断言因此只管"申报有没有被正确带进来"，渲染那半仍是人眼看。
 */
class TagEditCapabilityDeclarationTest {

    private fun viewModel(canWriteTags: Boolean): EditMusicTagsViewModel {
        val tagEdit = mockk<MusicTagEditService>()
        every { tagEdit.canWriteTags } returns canWriteTags
        val services = mockk<PlatformServices>()
        every { services.musicTagEdit } returns tagEdit
        return EditMusicTagsViewModel(
            getAllMusicUseCase = mockk(),
            editMusicTagsUseCase = mockk(),
            platformServices = services,
        )
    }

    @Test
    fun unsupportedPlatform_declaresItOnTheFirstFrame() {
        assertFalse(viewModel(canWriteTags = false).uiState.value.canWriteTags)
    }

    @Test
    fun capablePlatform_doesNotLoseEditingOnTheFirstFrame() {
        // 反向这一半要紧：实现写成"恒报不支持"，上一条也照样绿，而 Android/Desktop 会整页失效
        assertTrue(viewModel(canWriteTags = true).uiState.value.canWriteTags)
    }
}
